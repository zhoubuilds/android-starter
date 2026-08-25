package com.whisper.foundation.function

import com.whisper.foundation.model.business.BusinessError
import com.whisper.foundation.model.business.BusinessOutcome
import com.whisper.foundation.model.business.BusinessSuccess
import com.whisper.foundation.model.transmit.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 验证 API Flow 手动转换的异常边界.
 *
 * 手动转换应把网络层异常转换为业务错误, 并继续向上传播协程取消.
 *
 * @author whisper
 * @since 2026/07/27
 */
class ApiFlowFunctionsTest {

    @Test
    fun callAsBusinessOutcomeFlow_successResponse_emitsSuccess() = runBlocking {
        val values: List<BusinessOutcome<Int?>> = callAsBusinessOutcomeFlow {
            ApiResponse(
                code = ApiResponse.CODE_SUCCESS,
                message = "ok",
                data = 1,
            )
        }.toList()

        assertEquals(1, values.size)
        assertTrue(values[0] is BusinessSuccess<Int?>)
        assertEquals(1, (values[0] as BusinessSuccess<Int?>).data)
    }

    @Test
    fun callAsBusinessOutcomeFlow_networkException_emitsError() = runBlocking {
        val networkException: IOException = IOException("Network failed.")

        val values: List<BusinessOutcome<Int?>> = callAsBusinessOutcomeFlow<Int> {
            throw networkException
        }.toList()

        assertEquals(1, values.size)
        assertTrue(values[0] is BusinessError<Int?>)
        assertSame(networkException, (values[0] as BusinessError<Int?>).exception)
    }

    @Test
    fun callAsBusinessOutcomeFlow_httpException_emitsError() = runBlocking {
        val httpException: HttpException = HttpException(
            Response.error<Int>(500, "server error".toResponseBody())
        )

        val values: List<BusinessOutcome<Int?>> = callAsBusinessOutcomeFlow<Int> {
            throw httpException
        }.toList()

        assertEquals(1, values.size)
        assertTrue(values[0] is BusinessError<Int?>)
        assertSame(httpException, (values[0] as BusinessError<Int?>).exception)
    }

    @Test
    fun callAsBusinessOutcomeFlow_cancellationException_rethrows() = runBlocking {
        val cancellationException: CancellationException = CancellationException("cancelled")

        try {
            callAsBusinessOutcomeFlow<Int> {
                throw cancellationException
            }.toList()
            fail("CancellationException should be rethrown.")
        } catch (exception: CancellationException) {
            assertSame(cancellationException, exception)
        }
    }
}
