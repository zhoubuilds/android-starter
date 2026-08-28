package com.whisper.aster.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create

/**
 * Aster Gradle 配置入口.
 *
 * 插件负责注册 DSL、校验 Build 内的 segment, 并按宿主插件是否存在委托 Android Variant
 * 和 KSP 集成.
 * AGP 与 KSP 类型隔离在对应集成类中, 避免非目标模块加载可选宿主 API.
 *
 * @aegis 保护插件入口, DSL 名称, 支持模块白名单和跨模块协议参数.
 *
 * @author whisper
 * @since 2026/07/21
 */
class AsterPlugin : Plugin<Project> {

    /**
     * 注册 Aster 配置和 Android Variant Manifest 输出.
     *
     * Android application 或 library 插件必须在 Aster 插件之前应用. 只有这两个
     * Android 插件属于支持白名单, 其它模块会在插件应用阶段立即失败.
     *
     * @param project 应用该插件的 Gradle 模块.
     * @exception GradleException 当前模块未应用 Android application 或 library 插件时抛出.
     */
    override fun apply(project: Project) {
        requireSupportedAndroidModule(project)

        val extension: AsterExtension =
            project.extensions.create<AsterExtension>(EXTENSION_NAME)
        val segmentRegistry: Provider<AsterSegmentRegistryService> =
            project.gradle.sharedServices.registerIfAbsent(
                SEGMENT_REGISTRY_SERVICE_NAME,
                AsterSegmentRegistryService::class.java
            )

        project.pluginManager.withPlugin(KSP_PLUGIN_ID) {
            try {
                AsterKspIntegration().configure(
                    project = project,
                    extension = extension,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw asterKspCompatibilityException(project, error)
            }
        }

        project.pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN_ID) {
            try {
                AsterAndroidIntegration().registerApplicationManifest(
                    project = project,
                    extension = extension,
                    segmentRegistry = segmentRegistry,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw asterAgpCompatibilityException(project, error)
            }
        }

        project.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN_ID) {
            try {
                AsterAndroidIntegration().registerLibraryManifest(
                    project = project,
                    extension = extension,
                    segmentRegistry = segmentRegistry,
                    registryPackage = ::registryPackage
                )
            } catch (error: LinkageError) {
                throw asterAgpCompatibilityException(project, error)
            }
        }
    }

    /**
     * 校验当前模块是否属于 Aster 支持的 Android 模块白名单.
     *
     * 该校验故意使用插件 ID, 不检查模块目录名、任务名或构建输出目录.
     *
     * @param project 待校验的 Gradle 模块.
     * @exception GradleException 当前模块不属于 Android application 或 library 时抛出.
     */
    private fun requireSupportedAndroidModule(project: Project) {
        if (SUPPORTED_ANDROID_PLUGIN_IDS.none { pluginId: String ->
                project.pluginManager.hasPlugin(pluginId)
            }
        ) {
            throw GradleException(
                "com.whisper.aster can only be applied to Android application or library " +
                    "modules. Apply com.android.application or com.android.library before " +
                    "com.whisper.aster in module ${project.path}."
            )
        }
    }

    /**
     * 根据 Android namespace 计算生成 Registry 使用的 Kotlin 包名.
     *
     * @param namespace Android DSL 中声明的 namespace.
     * @return 生成 Registry 使用的包名.
     * @exception GradleException namespace 为空时抛出.
     */
    private fun registryPackage(namespace: String?): String {
        val value: String = namespace
            ?.takeIf { value: String -> value.isNotBlank() }
            ?: throw GradleException(
                "Aster requires an Android namespace. Set namespace = " +
                    "\"com.example.app\" inside the android { } block."
            )
        return buildString {
            append(value)
            append('.')
            append(GENERATED_PACKAGE_SUFFIX)
        }
    }

    private companion object {

        // ---------------------------------------------------------------------
        // Gradle 插件内部常量.
        // ---------------------------------------------------------------------

        /**
         * Aster Gradle DSL 扩展名称.
         */
        private const val EXTENSION_NAME: String = "aster"

        /**
         * Android application 插件 ID.
         */
        private const val ANDROID_APPLICATION_PLUGIN_ID: String = "com.android.application"

        /**
         * Android library 插件 ID.
         */
        private const val ANDROID_LIBRARY_PLUGIN_ID: String = "com.android.library"

        /**
         * Aster 支持的 Android 插件白名单.
         */
        private val SUPPORTED_ANDROID_PLUGIN_IDS: Set<String> = setOf(
            ANDROID_APPLICATION_PLUGIN_ID,
            ANDROID_LIBRARY_PLUGIN_ID
        )

        /**
         * KSP Gradle 插件 ID.
         */
        private const val KSP_PLUGIN_ID: String = "com.google.devtools.ksp"

        /**
         * Gradle Build 内共享的 segment 注册服务名称.
         */
        private const val SEGMENT_REGISTRY_SERVICE_NAME: String =
            "asterSegmentRegistry"

        // ---------------------------------------------------------------------
        // Gradle 插件和 Runtime 之间的 Registry 协议常量.
        // ---------------------------------------------------------------------

        /**
         * 生成 Registry 包名相对于 Android namespace 的固定后缀.
         *
         * 必须与 AsterProcessor 生成源码时使用的包名后缀保持一致.
         */
        private const val GENERATED_PACKAGE_SUFFIX: String = "aster.generated"

    }
}
