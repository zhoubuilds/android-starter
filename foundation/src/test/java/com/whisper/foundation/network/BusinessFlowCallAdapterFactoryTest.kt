package com.whisper.foundation.network

import com.whisper.architecture.business.exception.BusinessException
import com.whisper.architecture.business.model.ArchitectureBusiness
import com.whisper.foundation.model.business.Business
import com.whisper.foundation.model.business.BusinessError
import com.whisper.foundation.model.business.BusinessMetadata
import com.whisper.foundation.model.business.BusinessSuccess
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

/**
 * 验证业务 Flow 调用适配器的类型识别和异常边界.
 *
 * @author whisper
 * @since 2026/07/27
 */
class BusinessFlowCallAdapterFactoryTest {

    @Test
    fun getBusinessReturnTypeReturnsAdapter() {
        assertNotNull(createFactoryAdapter(businessFlowReturnType))
        assertNotNull(createFactoryAdapter(architectureBusinessFlowReturnType))
    }

    @Test
    fun adaptSuccessResponseEmitsLoadingAndSuccess() = runBlocking {
        val response: ApiResponse<Int> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "ok",
            data = 1,
        )

        val values: List<Business<Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(response))).toList()

        assertEquals(2, values.size)
        assertEquals(Business.loading<Int?, BusinessMetadata>(), values[0])
        assertTrue(values[1] is BusinessSuccess<Int?>)
        assertEquals(1, (values[1] as BusinessSuccess<Int?>).data)
    }

    @Test
    fun adaptBusinessErrorResponseEmitsLoadingAndError() = runBlocking {
        val response: ApiResponse<Int> = ApiResponse(
            code = 401,
            message = "Unauthorized.",
            data = 1,
        )

        val values: List<Business<Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(response))).toList()

        assertEquals(2, values.size)
        assertTrue(values[1] is BusinessError<Int?>)
        val error: BusinessError<Int?> = values[1] as BusinessError<Int?>
        assertTrue(error.exception is BusinessException)
        assertEquals("Unauthorized.", error.exception.message)
        assertEquals(1, error.data)
    }

    @Test
    fun adaptFailuresEmitBusinessErrors() = runBlocking {
        val networkException: IOException = IOException("Network failed.")
        val networkValues: List<Business<Int?>> =
            createBusinessFlow(FakeCall.failure(networkException)).toList()
        assertTrue(networkValues[1] is BusinessError<Int?>)
        val emittedNetworkException: Exception =
            (networkValues[1] as BusinessError<Int?>).exception
        assertTrue(emittedNetworkException is IOException)
        assertEquals(networkException.message, emittedNetworkException.message)

        val httpResponse: Response<ApiResponse<Int>> =
            Response.error(500, "server error".toResponseBody())
        val httpValues: List<Business<Int?>> =
            createBusinessFlow(FakeCall.success(httpResponse)).toList()
        assertTrue((httpValues[1] as BusinessError<Int?>).exception is HttpException)

        val emptyValues: List<Business<Int?>> =
            createBusinessFlow(FakeCall.success(Response.success(null))).toList()
        assertTrue((emptyValues[1] as BusinessError<Int?>).exception is NullPointerException)
    }

    @Test
    fun adaptCancellationExceptionRethrows() = runBlocking {
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
    private fun createBusinessFlow(call: Call<ApiResponse<Int>>): Flow<Business<Int?>> {
        val adapter: CallAdapter<ApiResponse<Int>, Flow<Business<Int?>>> =
            createFactoryAdapter(businessFlowReturnType) as CallAdapter<ApiResponse<Int>, Flow<Business<Int?>>>
        return adapter.adapt(call)
    }

    private interface TestApi {

        fun businessFlow(): Flow<Business<Int?>>

        fun architectureBusinessFlow(): Flow<ArchitectureBusiness<Int?, BusinessMetadata>>
    }

    private companion object {

        private const val REQUEST_URL: String = "https://example.test/"

        private val businessFlowReturnType: Type = TestApi::class.java
            .getDeclaredMethod("businessFlow")
            .genericReturnType

        private val architectureBusinessFlowReturnType: Type = TestApi::class.java
            .getDeclaredMethod("architectureBusinessFlow")
            .genericReturnType
    }

    private class FakeCall<T>(
        private val result: CallResult<T>,
    ) : Call<T> {

        companion object {

            fun <T> success(response: Response<T>): FakeCall<T> = FakeCall(CallResult.Success(response))

            fun <T> failure(throwable: Throwable): FakeCall<T> = FakeCall(CallResult.Failure(throwable))
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

        data class Success<T>(
            val response: Response<T>,
        ) : CallResult<T>()

        data class Failure<T>(
            val throwable: Throwable,
        ) : CallResult<T>()
    }
}
