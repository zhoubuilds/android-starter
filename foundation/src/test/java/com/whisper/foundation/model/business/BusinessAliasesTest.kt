package com.whisper.foundation.model.business

import com.whisper.architecture.business.exception.BusinessException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证应用级业务状态别名的创建体验.
 *
 * @author whisper
 * @since 2026/07/27
 */
class BusinessAliasesTest {

    @Test
    fun businessAlias_canUseCompanionFactories() {
        val metadata: BusinessMetadata = BusinessMetadata(
            code = 0,
            message = "ok",
        )
        val success: Business<Int> = Business.success(
            data = 1,
            metadata = metadata,
        )
        val error: Business<Int> = Business.error(
            exception = BusinessException("failed"),
            data = 2,
            metadata = metadata,
        )
        val loading: Business<Int> = Business.loading()

        assertTrue(success is BusinessSuccess<Int>)
        assertTrue(error is BusinessError<Int>)
        assertEquals(metadata, (success as BusinessSuccess<Int>).metadata)
        assertEquals(2, (error as BusinessError<Int>).data)
        assertEquals(Business.loading<Int, BusinessMetadata>(), loading)
    }
}
