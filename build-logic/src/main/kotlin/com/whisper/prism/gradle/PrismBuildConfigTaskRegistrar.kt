package com.whisper.prism.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Project

/**
 * BuildConfig 生成任务注册器
 *
 * 注册 Android Studio Gradle 侧边栏中的 BuildConfig 快捷任务, 用于 clean 后预生成各变体的 BuildConfig 源码.
 *
 * @author whisper
 * @since 2026/07/25
 */
internal class PrismBuildConfigTaskRegistrar {
    /**
     * 注册 BuildConfig 聚合任务
     *
     * @param target 当前 Gradle 项目
     */
    fun register(target: Project) {
        target.tasks.register(GENERATE_PRISM_BUILD_CONFIG_SOURCES_TASK_NAME) {
            group = TASK_GROUP
            description = "Generate BuildConfig sources for all configured Android variants."
        }
    }

    /**
     * 配置 BuildConfig 聚合任务
     *
     * @param target 当前 Gradle 项目
     * @param androidComponents Android Components 扩展
     * @param isBuildConfigEnabled 读取最终 Android DSL 是否开启 BuildConfig
     */
    fun <DslExtensionT : Any, VariantBuilderT : VariantBuilder, VariantT : Variant> configure(
        target: Project,
        androidComponents: AndroidComponentsExtension<DslExtensionT, VariantBuilderT, VariantT>,
        isBuildConfigEnabled: (DslExtensionT) -> Boolean
    ) {
        var buildConfigEnabled: Boolean = false
        androidComponents.finalizeDsl { android: DslExtensionT ->
            buildConfigEnabled = isBuildConfigEnabled(android)
        }

        androidComponents.onVariants { variant: VariantT ->
            if (!buildConfigEnabled) {
                return@onVariants
            }
            val buildConfigTaskName: String =
                "generate${variant.name.toTaskNameSegment()}BuildConfig"
            target.tasks.named(GENERATE_PRISM_BUILD_CONFIG_SOURCES_TASK_NAME) {
                dependsOn(buildConfigTaskName)
            }
        }
    }

    /**
     * 转换为 Gradle 任务名片段
     *
     * @return Gradle 任务名片段
     */
    private fun String.toTaskNameSegment(): String {
        return replaceFirstChar { char: Char -> char.uppercaseChar() }
    }

    private companion object {
        /**
         * 任务分组
         */
        private const val TASK_GROUP: String = "prism"

        /**
         * BuildConfig 聚合任务名称
         */
        private const val GENERATE_PRISM_BUILD_CONFIG_SOURCES_TASK_NAME: String =
            "generatePrismBuildConfigSources"
    }
}
