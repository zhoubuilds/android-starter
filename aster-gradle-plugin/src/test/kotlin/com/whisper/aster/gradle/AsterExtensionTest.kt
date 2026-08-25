package com.whisper.aster.gradle

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 验证 aster DSL 扩展配置.
 *
 * @author whisper
 * @since 2026/07/21
 */
class AsterExtensionTest {

    /**
     * 验证 segment 可以使用合法的命名段.
     */
    @Test
    fun acceptsSegment() {
        val extension = AsterExtension()

        extension.segment = "initial"
        extension.segment = "feature"

        assertEquals("feature", extension.segment)
        assertEquals("feature", extension.finalizeSegment(":feature"))
    }

    /**
     * 验证 segment 包含非法字符时中断配置.
     */
    @Test
    fun rejectsInvalidSegment() {
        val extension = AsterExtension()

        val exception = assertThrows(GradleException::class.java) {
            extension.segment = "feature.page"
        }

        assertTrue(exception.message?.contains("Invalid aster.segment") == true)
    }

    /**
     * 验证 segment 最终确定后不能再修改.
     */
    @Test
    fun rejectsChangesAfterFinalization() {
        val extension = AsterExtension()
        extension.segment = "feature"
        extension.finalizeSegment(":feature")

        val exception = assertThrows(GradleException::class.java) {
            extension.segment = "replacement"
        }

        assertTrue(
            exception.message?.contains(
                "Cannot change aster.segment after Android DSL finalization"
            ) == true
        )
        assertEquals("feature", extension.segment)
    }

}
