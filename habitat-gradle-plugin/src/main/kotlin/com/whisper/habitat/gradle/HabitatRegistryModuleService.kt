package com.whisper.habitat.gradle

import org.gradle.api.GradleException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * 当前 Gradle Build 的 Habitat 装配模块注册中心.
 *
 * 同一个 Gradle Build 内只允许一个源码模块应用 Habitat 插件, 避免最终产物出现多个数据库装配入口.
 *
 * @aegis 保护单装配模块, 幂等注册和冲突失败语义.
 * @author whisper
 * @since 2026/07/28
 */
internal abstract class HabitatRegistryModuleService :
    BuildService<BuildServiceParameters.None> {

    /**
     * 已注册 Habitat 装配模块路径.
     */
    private var registeredProjectPath: String? = null

    /**
     * 注册 Habitat 装配模块.
     *
     * 同一模块重复注册保持幂等; 不同模块重复应用插件时直接中断构建.
     *
     * @param projectPath 应用 Habitat 插件的 Gradle 模块路径.
     * @exception GradleException 已有其它模块注册为 Habitat 装配模块时抛出.
     */
    @Synchronized
    fun register(projectPath: String) {
        val registeredPath: String? = registeredProjectPath
        if (registeredPath == projectPath) {
            return
        }
        if (registeredPath != null) {
            throw GradleException(
                "Only one Habitat assembly module is allowed in the same Gradle build.\n\n" +
                    "Declared by:\n" +
                    "- $registeredPath\n" +
                    "- $projectPath\n\n" +
                    "Apply com.whisper.habitat only to the module that owns the final RoomDatabase assembly."
            )
        }
        registeredProjectPath = projectPath
    }
}
