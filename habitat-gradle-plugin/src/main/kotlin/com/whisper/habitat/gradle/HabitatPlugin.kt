package com.whisper.habitat.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Habitat Gradle 配置入口.
 *
 * 插件负责根据 Android namespace 配置 KSP 生成包名, 并为每个 Variant 生成
 * Habitat Registry Manifest metadata.
 *
 * @aegis 保护插件入口, 支持模块白名单, 生成包名和单装配模块协议.
 *
 * @author whisper
 * @since 2026/07/28
 */
class HabitatPlugin : Plugin<Project> {

    /**
     * 注册 Habitat 的 Android Variant 和 KSP 集成.
     *
     * Android application 或 library 插件必须在 Habitat 插件之前应用. 当前 Habitat 只允许
     * 同一个 Gradle Build 内存在一个数据库装配模块.
     *
     * @param project 应用该插件的 Gradle 模块.
     * @exception GradleException 当前模块未应用 Android application 或 library 插件时抛出.
     */
    override fun apply(project: Project) {
        requireSupportedAndroidModule(project)
        val registryModuleService: Provider<HabitatRegistryModuleService> =
            project.gradle.sharedServices.registerIfAbsent(
                REGISTRY_MODULE_SERVICE_NAME,
                HabitatRegistryModuleService::class.java,
            )

        project.pluginManager.withPlugin(KSP_PLUGIN_ID) {
            try {
                HabitatKspIntegration().configure(
                    project = project,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw habitatKspCompatibilityException(project, error)
            }
        }

        project.pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN_ID) {
            try {
                HabitatAndroidIntegration().registerApplicationManifest(
                    project = project,
                    registryModuleService = registryModuleService,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw habitatAgpCompatibilityException(project, error)
            }
        }

        project.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN_ID) {
            try {
                HabitatAndroidIntegration().registerLibraryManifest(
                    project = project,
                    registryModuleService = registryModuleService,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw habitatAgpCompatibilityException(project, error)
            }
        }
    }

    private fun requireSupportedAndroidModule(project: Project) {
        if (SUPPORTED_ANDROID_PLUGIN_IDS.none { pluginId: String ->
                project.pluginManager.hasPlugin(pluginId)
            }
        ) {
            throw GradleException(
                "com.whisper.habitat can only be applied to Android application or library modules. " +
                    "Apply com.android.application or com.android.library before com.whisper.habitat " +
                    "in module ${project.path}."
            )
        }
    }

    private fun registryPackage(namespace: String?): String {
        val value: String = namespace
            ?.takeIf { item: String -> item.isNotBlank() }
            ?: throw GradleException(
                "Habitat requires an Android namespace. Set namespace = " +
                    "\"com.example.app\" inside the android { } block."
            )
        return "$value.$GENERATED_PACKAGE_SUFFIX"
    }

    private companion object {

        /**
         * Android application 插件 ID.
         */
        private const val ANDROID_APPLICATION_PLUGIN_ID: String = "com.android.application"

        /**
         * Android library 插件 ID.
         */
        private const val ANDROID_LIBRARY_PLUGIN_ID: String = "com.android.library"

        /**
         * Habitat 支持的 Android 插件白名单.
         */
        private val SUPPORTED_ANDROID_PLUGIN_IDS: Set<String> = setOf(
            ANDROID_APPLICATION_PLUGIN_ID,
            ANDROID_LIBRARY_PLUGIN_ID,
        )

        /**
         * KSP Gradle 插件 ID.
         */
        private const val KSP_PLUGIN_ID: String = "com.google.devtools.ksp"

        /**
         * Gradle Build 内共享的 Habitat 装配模块注册服务名称.
         */
        private const val REGISTRY_MODULE_SERVICE_NAME: String =
            "habitatRegistryModule"

        /**
         * 生成 Registry 包名相对于 Android namespace 的固定后缀.
         */
        private const val GENERATED_PACKAGE_SUFFIX: String = "habitat.generated"
    }
}
