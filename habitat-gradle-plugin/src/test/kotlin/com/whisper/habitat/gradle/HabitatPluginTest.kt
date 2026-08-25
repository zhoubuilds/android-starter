package com.whisper.habitat.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Habitat Gradle 插件的模块类型白名单.
 *
 * @author whisper
 * @since 2026/07/28
 */
class HabitatPluginTest {

    /**
     * 验证未应用 Android application 或 library 插件的模块会被拒绝.
     */
    @Test
    fun rejectsNonAndroidModule() {
        val exception: GradleException = assertThrows(GradleException::class.java) {
            HabitatPlugin().apply(ProjectBuilder.builder().build())
        }

        assertTrue(
            exception.message?.contains(
                "can only be applied to Android application or library modules"
            ) == true
        )
    }
}
