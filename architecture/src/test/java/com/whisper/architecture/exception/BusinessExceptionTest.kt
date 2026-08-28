package com.whisper.architecture.exception

import com.whisper.architecture.model.domain.Business
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * 验证业务异常的独立身份及其在失败状态中的相等语义.
 *
 * @author whisper
 * @since 2026/08/27
 */
class BusinessExceptionTest {

    @Test
    fun sameMessage_createsDistinctExceptionInstances() {
        val firstException = BusinessException("failed")
        val secondException = BusinessException("failed")

        assertNotSame(firstException, secondException)
        assertNotEquals(firstException, secondException)
        assertEquals(firstException.message, secondException.message)
    }

    @Test
    fun sameFailureContent_withDistinctExceptionsRemainsDistinct() {
        val firstFailure: Business.Failure<Unit, Int> = Business.Failure(
            exception = BusinessException("failed"),
            meta = Unit,
            data = 1,
        )
        val secondFailure: Business.Failure<Unit, Int> = Business.Failure(
            exception = BusinessException("failed"),
            meta = Unit,
            data = 1,
        )

        assertNotEquals(firstFailure, secondFailure)
    }
}
