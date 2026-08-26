package com.whisper.foundation.network

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.exception.BusinessException
import com.whisper.foundation.model.business.BusinessMetadata
import com.whisper.foundation.model.transmit.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Type

/** 验证业务 Flow 调用适配器的类型识别、数据保留和异常边界. */
class BusinessFlowCallAdapterFactoryTest {

    @Test
    fun get_recognizesOnlyBusinessFlow() {
        assertNotNull(createFactoryAdapter(businessFlowReturnType))
        assertNull(createFactoryAdapter(otherFlowReturnType))
    }

    @Test
    fun adapt_successResponseEmitsSingletonLoadingAndSuccess() = runBlocking {
        val response: ApiResponse<Int> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "ok",
            data = 1,
        )

        val values: List<Business<BusinessMetadata, Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(response))).toList()

        assertEquals(2, values.size)
        assertSame(Business.Loading, values[0])
        val success = values[1] as Business.Success<BusinessMetadata, Int?>
        assertEquals(1, success.data)
        assertEquals(BusinessMetadata(ApiResponse.CODE_SUCCESS, "ok"), success.meta)
    }

    @Test
    fun adapt_businessFailureKeepsMetaAndData() = runBlocking {
        val response: ApiResponse<Int> = ApiResponse(
            code = 401,
            message = "Unauthorized.",
            data = 1,
        )

        val values: List<Business<BusinessMetadata, Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(response))).toList()

        val failure = values[1] as Business.Failure<BusinessMetadata, Int?>
        assertTrue(failure.exception is BusinessException)
        assertEquals("Unauthorized.", failure.exception.message)
        assertEquals(BusinessMetadata(401, "Unauthorized."), failure.meta)
        assertEquals(1, failure.data)
    }

    @Test
    fun adapt_transportFailuresUseEmptyMetaAndNullData() = runBlocking {
        val networkException: IOException = IOException("Network failed.")
        val networkValues: List<Business<BusinessMetadata, Int?>> =
            createBusinessFlow(FakeCall.failure(networkException)).toList()
        val networkFailure = networkValues[1] as Business.Failure<BusinessMetadata, Int?>
        assertTrue(networkFailure.exception is IOException)
        assertEquals(networkException.message, networkFailure.exception.message)
        assertEquals(BusinessMetadata.EMPTY, networkFailure.meta)
        assertEquals(null, networkFailure.data)

        val httpResponse: Response<ApiResponse<Int>> =
            Response.error(500, "server error".toResponseBody())
        val httpValues: List<Business<BusinessMetadata, Int?>> =
            createBusinessFlow(FakeCall.success(httpResponse)).toList()
        val httpFailure = httpValues[1] as Business.Failure<BusinessMetadata, Int?>
        assertTrue(httpFailure.exception is HttpException)
        assertEquals(BusinessMetadata.EMPTY, httpFailure.meta)
        assertEquals(null, httpFailure.data)

        val emptyValues: List<Business<BusinessMetadata, Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(null))).toList()
        val emptyFailure = emptyValues[1] as Business.Failure<BusinessMetadata, Int?>
        assertTrue(emptyFailure.exception is NullPointerException)
        assertEquals(BusinessMetadata.EMPTY, emptyFailure.meta)
        assertEquals(null, emptyFailure.data)
    }

    @Test
    fun adapt_cancellationExceptionRethrows() = runBlocking {
        val cancellationException: CancellationException = CancellationException("cancelled")
        try {
            createBusinessFlow(FakeCall.failure(cancellationException)).toList()
            fail("CancellationException should be rethrown.")
        } catch (exception: CancellationException) {
            assertEquals(cancellationException.message, exception.message)
        }
    }

    private fun createFactoryAdapter(returnType: Type): CallAdapter<*, *>? {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .build()
        return BusinessFlowCallAdapterFactory.create().get(
            returnType = returnType,
            annotations = emptyArray(),
            retrofit = retrofit,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBusinessFlow(
        call: Call<ApiResponse<Int>>,
    ): Flow<Business<BusinessMetadata, Int?>> {
        val adapter: CallAdapter<ApiResponse<Int>, Flow<Business<BusinessMetadata, Int?>>> =
            createFactoryAdapter(businessFlowReturnType)
                as CallAdapter<ApiResponse<Int>, Flow<Business<BusinessMetadata, Int?>>>
        return adapter.adapt(call)
    }

    private interface TestApi {
        fun businessFlow(): Flow<Business<BusinessMetadata, Int?>>

        fun otherFlow(): Flow<String>
    }

    private companion object {
        private const val REQUEST_URL: String = "https://example.test/"

        private val businessFlowReturnType: Type = TestApi::class.java
            .getDeclaredMethod("businessFlow")
            .genericReturnType

        private val otherFlowReturnType: Type = TestApi::class.java
            .getDeclaredMethod("otherFlow")
            .genericReturnType
    }

    private class FakeCall<T>(
        private val result: CallResult<T>,
    ) : Call<T> {

        companion object {
            fun <T> success(response: Response<T>): FakeCall<T> =
                FakeCall(CallResult.Success(response))

            fun <T> failure(throwable: Throwable): FakeCall<T> =
                FakeCall(CallResult.Failure(throwable))
        }

        private var canceled: Boolean = false

        override fun execute(): Response<T> =
            throw UnsupportedOperationException("Synchronous execution is not supported.")

        override fun enqueue(callback: Callback<T>) {
            when (result) {
                is CallResult.Success<T> -> callback.onResponse(this, result.response)
                is CallResult.Failure<T> -> callback.onFailure(this, result.throwable)
            }
        }

        override fun isExecuted(): Boolean = false

        override fun cancel() {
            canceled = true
        }

        override fun isCanceled(): Boolean = canceled

        override fun clone(): Call<T> = FakeCall(result)

        override fun request(): Request = Request.Builder().url(REQUEST_URL).build()

        override fun timeout(): Timeout = Timeout()
    }

    private sealed class CallResult<T> {
        data class Success<T>(val response: Response<T>) : CallResult<T>()

        data class Failure<T>(val throwable: Throwable) : CallResult<T>()
    }
}
