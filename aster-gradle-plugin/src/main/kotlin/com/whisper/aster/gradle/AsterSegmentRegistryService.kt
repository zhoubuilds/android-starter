package com.whisper.aster.gradle

import org.gradle.api.GradleException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * 当前 Gradle Build 的 Aster segment 注册中心.
 *
 * 所有应用 Aster 插件的源码模块在 Android DSL 最终确定后注册 segment. 服务的生命周期由
 * Gradle 管理, 不依赖跨 Project 读取、静态状态或任何构建产物.
 *
 * @aegis 保护 Build 内 segment 唯一性, 幂等注册和冲突失败语义.
 * @author whisper
 * @since 2026/07/23
 */
internal abstract class AsterSegmentRegistryService :
    BuildService<BuildServiceParameters.None> {

    /**
     * 每个 segment 对应的模块路径.
     */
    private val projectsBySegment: MutableMap<String, String> = mutableMapOf()

    /**
     * 每个模块路径已经注册的 segment.
     */
    private val segmentsByProject: MutableMap<String, String> = mutableMapOf()

    /**
     * 注册一个模块最终使用的 segment.
     *
     * 同一模块注册相同 segment 时保持幂等. 不同模块注册相同 segment, 或同一模块尝试注册
     * 不同 segment 时直接中断构建. 方法同步执行以支持 Gradle 并行配置.
     *
     * @param projectPath 声明 segment 的 Gradle 模块路径.
     * @param segment 模块最终使用的 segment.
     * @exception GradleException segment 已被其它模块占用或模块重复注册不同 segment 时抛出.
     */
    @Synchronized
    fun register(projectPath: String, segment: String) {
        val registeredSegment: String? = segmentsByProject[projectPath]
        if (registeredSegment == segment) {
            return
        }
        if (registeredSegment != null) {
            throw GradleException(
                "Aster module '$projectPath' cannot change its registered segment from " +
                    "'$registeredSegment' to '$segment'."
            )
        }

        val registeredProject: String? = projectsBySegment[segment]
        if (registeredProject != null && registeredProject != projectPath) {
            val declarations: String = listOf(registeredProject, projectPath)
                .sorted()
                .joinToString(separator = "\n") { "- $it" }
            throw GradleException(
                "Duplicate Aster segment '$segment'.\n\n" +
                    "Declared by:\n$declarations\n\n" +
                    "Each Aster module in the same Gradle build must declare a unique segment."
            )
        }

        projectsBySegment[segment] = projectPath
        segmentsByProject[projectPath] = segment
    }
}
