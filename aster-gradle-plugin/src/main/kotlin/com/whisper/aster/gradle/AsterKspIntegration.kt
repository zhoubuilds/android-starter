package com.whisper.aster.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType

/**
 * Aster 与 KSP Gradle 插件的可选集成.
 *
 * 该类型单独承载 KSP API, 只有检测到 KSP 插件后才会被 AsterPlugin 调用.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class AsterKspIntegration {

    /**
     * 将模块配置和最终 Registry 包名传递给 KSP.
     *
     * @param project 应用 aster 的 Android 模块.
     * @param extension 当前模块的 aster DSL 扩展.
     * @param registryPackage 根据 Android namespace 计算 Registry 包名的函数.
     * @exception GradleException KSP 扩展不存在或版本不兼容时抛出.
     */
    fun configure(
        project: Project,
        extension: AsterExtension,
        registryPackage: (String?) -> String
    ) {
        val kspExtension: KspExtension = project.extensions.findByType<KspExtension>()
            ?: throw GradleException(
                "Aster could not find the KSP Gradle extension in module ${project.path}. " +
                    "This may indicate an incompatible Kotlin/KSP version. Verified " +
                    "combination: Kotlin $TESTED_KOTLIN_VERSION with KSP $TESTED_KSP_VERSION."
            )

        extension.bindKsp { segment: String ->
            setKspArgument(project, kspExtension, SEGMENT_OPTION, segment)
        }

        project.pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN_ID) {
            project.extensions.getByType<ApplicationAndroidComponentsExtension>()
                .finalizeDsl { dsl: ApplicationExtension ->
                    setKspArgument(
                        project = project,
                        kspExtension = kspExtension,
                        optionName = REGISTRY_PACKAGE_OPTION,
                        optionValue = registryPackage(dsl.namespace)
                    )
                }
        }
        project.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN_ID) {
            project.extensions.getByType<LibraryAndroidComponentsExtension>()
                .finalizeDsl { dsl: LibraryExtension ->
                    setKspArgument(
                        project = project,
                        kspExtension = kspExtension,
                        optionName = REGISTRY_PACKAGE_OPTION,
                        optionValue = registryPackage(dsl.namespace)
                    )
                }
        }
    }

    /**
     * 通过 KSP 公开扩展传递一个处理器参数.
     *
     * @param project 应用 aster 的 Android 模块.
     * @param kspExtension KSP 公开 Gradle 扩展.
     * @param optionName 处理器参数名.
     * @param optionValue 处理器参数值.
     * @exception GradleException KSP 二进制接口不兼容时抛出.
     */
    private fun setKspArgument(
        project: Project,
        kspExtension: KspExtension,
        optionName: String,
        optionValue: String
    ) {
        try {
            kspExtension.arg(optionName, optionValue)
        } catch (error: LinkageError) {
            throw asterKspCompatibilityException(project, error)
        }
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
         * 传递模块 segment 的 KSP 参数名.
         */
        private const val SEGMENT_OPTION: String = "aster.segment"

        /**
         * 传递 Registry 生成包名的 KSP 参数名.
         */
        private const val REGISTRY_PACKAGE_OPTION: String = "aster.registryPackage"

        /**
         * 当前已验证的 Kotlin 版本, 仅用于兼容性错误提示.
         */
        private const val TESTED_KOTLIN_VERSION: String = "2.4.10"

        /**
         * 当前已验证的 KSP 版本, 仅用于兼容性错误提示.
         */
        private const val TESTED_KSP_VERSION: String = "2.3.10"
    }
}

/**
 * 创建 KSP 兼容性错误, 保留底层链接错误便于继续排查.
 *
 * @param project 应用 aster 的 Android 模块.
 * @param cause KSP 类或方法链接失败的原始错误.
 * @return 包含版本排错建议的 Gradle 异常.
 */
internal fun asterKspCompatibilityException(
    project: Project,
    cause: LinkageError
): GradleException {
    return GradleException(
        "Aster could not configure the KSP Gradle extension in module " +
            "${project.path}. This may indicate an incompatible Kotlin/KSP version. " +
            "Verified combination: Kotlin 2.4.10 with KSP 2.3.10. " +
            "Check the Kotlin/KSP compatibility matrix. " +
            "Original error: ${cause.javaClass.name}: ${cause.message.orEmpty()}",
        cause
    )
}
