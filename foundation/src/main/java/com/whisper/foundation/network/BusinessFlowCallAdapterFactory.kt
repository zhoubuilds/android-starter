package com.whisper.foundation.network

import com.whisper.architecture.model.domain.Business
import com.whisper.foundation.function.runAsBusinessOutcome
import com.whisper.foundation.model.business.BusinessMetadata
import com.whisper.foundation.model.transmit.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 将 Retrofit 调用适配为业务 Flow.
 *
 * 该工厂只处理 `Flow<Business<BusinessMetadata, T>>` 返回类型, 不影响 Retrofit 对 suspend 函数的默认支持.
 * 实际响应按 [ApiResponse] 解析并转换为 Loading、Success 或 Failure 业务状态.
 *
 * @author whisper
 * @since 2026/07/27
 */
class BusinessFlowCallAdapterFactory private constructor() : CallAdapter.Factory() {

    companion object {

        fun create(): BusinessFlowCallAdapterFactory = BusinessFlowCallAdapterFactory()
    }

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Flow::class.java) {
            return null
        }
        require(returnType is ParameterizedType) {
            "Flow return type must be parameterized as Flow<Business<M, D>>."
        }

        val businessType: Type = getParameterUpperBound(0, returnType)
        if (getRawType(businessType) != Business::class.java) {
            return null
        }
        require(businessType is ParameterizedType) {
            "Business return type must be parameterized as Business<M, D>."
        }
        requireBusinessMetadata(businessType)

        val dataType: Type = getParameterUpperBound(1, businessType)
        return BusinessFlowCallAdapter<Any>(ApiResponseType(dataType))
    }

    private fun requireBusinessMetadata(businessType: ParameterizedType) {
        val metadataType: Type = getParameterUpperBound(0, businessType)
        require(metadataType == BusinessMetadata::class.java) {
            "Business Flow metadata type must be BusinessMetadata."
        }
    }

    private class ApiResponseType(
        private val dataType: Type,
    ) : ParameterizedType {

        override fun getActualTypeArguments(): Array<Type> = arrayOf(dataType)

        override fun getRawType(): Type = ApiResponse::class.java

        override fun getOwnerType(): Type? = null

        override fun toString(): String = "${ApiResponse::class.java.name}<$dataType>"
    }

    private class BusinessFlowCallAdapter<T>(
        private val responseType: Type,
    ) : CallAdapter<ApiResponse<T>, Flow<Business<BusinessMetadata, T?>>> {

        override fun responseType(): Type = responseType

        override fun adapt(call: Call<ApiResponse<T>>): Flow<Business<BusinessMetadata, T?>> = flow {
            emit(Business.Loading)
            emit(
                runAsBusinessOutcome(
                    onFailure = { exception: Exception ->
                        Business.Failure(
                            exception = exception,
                            meta = BusinessMetadata.EMPTY,
                            data = null,
                        )
                    },
                    block = {
                        val response: ApiResponse<T> = call.clone().awaitBody()
                        response.toBusiness()
                    },
                )
            )
        }

        private suspend fun Call<ApiResponse<T>>.awaitBody(): ApiResponse<T> =
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancel() }
                enqueue(
                    object : Callback<ApiResponse<T>> {

                        override fun onResponse(
                            call: Call<ApiResponse<T>>,
                            response: Response<ApiResponse<T>>,
                        ) {
                            if (!continuation.isActive) {
                                return
                            }
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(HttpException(response))
                                return
                            }
                            val body: ApiResponse<T>? = response.body()
                            if (body == null) {
                                continuation.resumeWithException(
                                    NullPointerException("Response body was null.")
                                )
                                return
                            }
                            continuation.resume(body)
                        }

                        override fun onFailure(
                            call: Call<ApiResponse<T>>,
                            throwable: Throwable,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(throwable)
                            }
                        }
                    }
                )
            }
    }
}
