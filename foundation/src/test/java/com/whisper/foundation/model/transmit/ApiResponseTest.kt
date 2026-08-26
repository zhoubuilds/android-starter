package com.whisper.foundation.model.transmit

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.exception.BusinessException
import com.whisper.foundation.model.business.BusinessMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 API 响应到业务状态的无损转换. */
class ApiResponseTest {

    @Test
    fun toBusiness_successKeepsMetaAndData() {
        val response: ApiResponse<Int> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "saved",
            data = 2,
        )

        val outcome: Business.Outcome<BusinessMetadata, String> =
            response.toBusiness { data: Int? -> "value-$data" }

        assertTrue(outcome is Business.Success)
        val success: Business.Success<BusinessMetadata, String> =
            outcome as Business.Success<BusinessMetadata, String>
        assertEquals("value-2", success.data)
        assertEquals(BusinessMetadata(ApiResponse.CODE_SUCCESS, "saved"), success.meta)
    }

    @Test
    fun toBusiness_failureKeepsMetaAndFailureData() {
        val response: ApiResponse<String> = ApiResponse(
            code = 401,
            message = "need captcha",
            data = "token",
        )

        val outcome: Business.Outcome<BusinessMetadata, String> =
            response.toBusiness { data: String? -> data?.uppercase() ?: "" }

        assertTrue(outcome is Business.Failure)
        val failure: Business.Failure<BusinessMetadata, String> =
            outcome as Business.Failure<BusinessMetadata, String>
        assertTrue(failure.exception is BusinessException)
        assertEquals("need captcha", failure.exception.message)
        assertEquals("TOKEN", failure.data)
        assertEquals(BusinessMetadata(401, "need captcha"), failure.meta)
    }

    @Test
    fun toBusiness_withoutTransformerKeepsNullableData() {
        val response: ApiResponse<String> = ApiResponse(
            code = ApiResponse.CODE_SUCCESS,
            message = "empty",
            data = null,
        )

        val outcome: Business.Outcome<BusinessMetadata, String?> = response.toBusiness()

        assertTrue(outcome is Business.Success)
        val success: Business.Success<BusinessMetadata, String?> =
            outcome as Business.Success<BusinessMetadata, String?>
        assertEquals(null, success.data)
        assertEquals(BusinessMetadata(ApiResponse.CODE_SUCCESS, "empty"), success.meta)
    }
}
