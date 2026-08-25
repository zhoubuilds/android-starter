package com.whisper.aster.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Gradle Build 内的 Aster segment 注册规则.
 *
 * @author whisper
 * @since 2026/07/23
 */
class AsterSegmentRegistryServiceTest {

    /**
     * 验证同一模块重复注册相同 segment 时保持幂等.
     */
    @Test
    fun acceptsIdempotentRegistration() {
        val service: AsterSegmentRegistryService = createService()

        service.register(":feature:first", "first")
        service.register(":feature:first", "first")
    }

    /**
     * 验证不同模块注册相同 segment 时中断构建并报告冲突模块路径.
     */
    @Test
    fun rejectsDuplicateSegments() {
        val service: AsterSegmentRegistryService = createService()
        service.register(":feature:first", "shared")

        val exception: GradleException = assertThrows(GradleException::class.java) {
            service.register(":feature:second", "shared")
        }

        assertTrue(exception.message?.contains("Duplicate Aster segment 'shared'") == true)
        assertTrue(exception.message?.contains(":feature:first") == true)
        assertTrue(exception.message?.contains(":feature:second") == true)
    }

    /**
     * 创建由 Gradle 管理的测试服务实例.
     *
     * @return 独立的 segment 注册服务.
     */
    private fun createService(): AsterSegmentRegistryService {
        val project: Project = ProjectBuilder.builder().build()
        return project.gradle.sharedServices.registerIfAbsent(
            "asterSegmentRegistryTest",
            AsterSegmentRegistryService::class.java
        ).get()
    }
}
