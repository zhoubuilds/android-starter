package com.whisper.foundation.model.transmit

import com.whisper.architecture.business.exception.BusinessException
import com.whisper.foundation.model.business.BusinessError
import com.whisper.foundation.model.business.BusinessMetadata
import com.whisper.foundation.model.business.BusinessOutcome
import com.whisper.foundation.model.business.BusinessSuccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 API 响应到业务状态的转换.
 *
 * @author whisper
 * @since 2026/07/27
 */
class ApiResponseTest {

    @Test
    fun toBusiness_success_keepsMetadata() {
        val response: ApiResponse<Int> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "saved",
            data = 2,
        )

        val outcome: BusinessOutcome<String> = response.toBusiness { data: Int? -> "value-$data" }

        assertTrue(outcome is BusinessSuccess<String>)
        val success: BusinessSuccess<String> = outcome as BusinessSuccess<String>
        assertEquals("value-2", success.data)
        assertEquals(
            BusinessMetadata(
                code = ApiResponse.CODE_SUCCESS,
                message = "saved",
            ),
            success.metadata
        )
    }

    @Test
    fun toBusiness_error_keepsDataAndMetadata() {
        val response: ApiResponse<String> = ApiResponse(
            code = 401,
            message = "need captcha",
            data = "token",
        )

        val outcome: BusinessOutcome<String> = response.toBusiness { data: String? -> data?.uppercase() ?: "" }

        assertTrue(outcome is BusinessError<String>)
        val error: BusinessError<String> = outcome as BusinessError<String>
        assertTrue(error.exception is BusinessException)
        assertEquals("need captcha", error.exception.message)
        assertEquals("TOKEN", error.data)
        assertEquals(
            BusinessMetadata(
                code = 401,
                message = "need captcha",
            ),
            error.metadata
        )
    }

    @Test
    fun toBusiness_withoutTransformer_keepsNullableData() {
        val response: ApiResponse<String> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "empty",
            data = null,
        )

        val outcome: BusinessOutcome<String?> = response.toBusiness()

        assertTrue(outcome is BusinessSuccess<String?>)
        val success: BusinessSuccess<String?> = outcome as BusinessSuccess<String?>
        assertEquals(null, success.data)
        assertEquals(
            BusinessMetadata(
                code = ApiResponse.CODE_SUCCESS,
                message = "empty",
            ),
            success.metadata
        )
    }
}
