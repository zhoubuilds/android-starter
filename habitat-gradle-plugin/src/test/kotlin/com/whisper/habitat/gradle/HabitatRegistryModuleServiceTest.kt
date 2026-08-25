package com.whisper.habitat.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Gradle Build 内 Habitat 装配模块唯一性规则.
 *
 * @author whisper
 * @since 2026/07/28
 */
class HabitatRegistryModuleServiceTest {

    /**
     * 验证同一模块重复注册保持幂等.
     */
    @Test
    fun acceptsIdempotentRegistration() {
        val service: HabitatRegistryModuleService = createService()

        service.register(":app")
        service.register(":app")
    }

    /**
     * 验证不同模块重复注册时中断构建并报告冲突模块路径.
     */
    @Test
    fun rejectsMultipleAssemblyModules() {
        val service: HabitatRegistryModuleService = createService()
        service.register(":app")

        val exception: GradleException = assertThrows(GradleException::class.java) {
            service.register(":database")
        }

        assertTrue(exception.message?.contains("Only one Habitat assembly module") == true)
        assertTrue(exception.message?.contains(":app") == true)
        assertTrue(exception.message?.contains(":database") == true)
    }

    private fun createService(): HabitatRegistryModuleService {
        val project: Project = ProjectBuilder.builder().build()
        return project.gradle.sharedServices.registerIfAbsent(
            "habitatRegistryModuleTest",
            HabitatRegistryModuleService::class.java
        ).get()
    }
}
