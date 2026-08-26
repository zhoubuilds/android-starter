package com.whisper.foundation.function

import com.whisper.architecture.model.domain.Business
import com.whisper.foundation.model.business.BusinessMetadata
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

/** 验证 API Flow 手动转换的响应保留和异常边界. */
class ApiFlowFunctionsTest {

    @Test
    fun callAsBusinessOutcomeFlow_successResponseEmitsSuccess() = runBlocking {
        val values: List<Business.Outcome<BusinessMetadata, Int?>> = callAsBusinessOutcomeFlow {
            ApiResponse(
                code = ApiResponse.CODE_SUCCESS,
                message = "ok",
                data = 1,
            )
        }.toList()

        assertEquals(1, values.size)
        assertTrue(values[0] is Business.Success)
        val success = values[0] as Business.Success<BusinessMetadata, Int?>
        assertEquals(1, success.data)
        assertEquals(BusinessMetadata(ApiResponse.CODE_SUCCESS, "ok"), success.meta)
    }

    @Test
    fun callAsBusinessOutcomeFlow_networkExceptionEmitsEmptyFailurePayload() = runBlocking {
        val networkException: IOException = IOException("Network failed.")

        val values: List<Business.Outcome<BusinessMetadata, Int?>> = callAsBusinessOutcomeFlow<Int> {
            throw networkException
        }.toList()

        val failure = values.single() as Business.Failure<BusinessMetadata, Int?>
        assertSame(networkException, failure.exception)
        assertEquals(BusinessMetadata.EMPTY, failure.meta)
        assertEquals(null, failure.data)
    }

    @Test
    fun callAsBusinessOutcomeFlow_httpExceptionEmitsEmptyFailurePayload() = runBlocking {
        val httpException: HttpException = HttpException(
            Response.error<Int>(500, "server error".toResponseBody())
        )

        val values: List<Business.Outcome<BusinessMetadata, Int?>> = callAsBusinessOutcomeFlow<Int> {
            throw httpException
        }.toList()

        val failure = values.single() as Business.Failure<BusinessMetadata, Int?>
        assertSame(httpException, failure.exception)
        assertEquals(BusinessMetadata.EMPTY, failure.meta)
        assertEquals(null, failure.data)
    }

    @Test
    fun callAsBusinessOutcomeFlow_cancellationExceptionRethrows() = runBlocking {
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
