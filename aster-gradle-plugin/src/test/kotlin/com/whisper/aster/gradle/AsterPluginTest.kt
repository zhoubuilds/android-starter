package com.whisper.aster.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 aster Gradle 插件的模块类型白名单.
 *
 * @author whisper
 * @since 2026/07/21
 */
class AsterPluginTest {

    /**
     * 验证未应用 Android application 或 library 插件的模块会被拒绝.
     */
    @Test
    fun rejectsNonAndroidModule() {
        val project = ProjectBuilder.builder().build()

        val exception = assertThrows(org.gradle.api.GradleException::class.java) {
            AsterPlugin().apply(project)
        }

        assertTrue(
            exception.message?.contains(
                "can only be applied to Android application or library modules"
            ) == true
        )
    }
}
