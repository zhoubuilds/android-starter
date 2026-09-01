package com.whisper.buildlogic.prism

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Android 应用构建配置约定插件.
 *
 * 插件 ID 为 `com.whisper.prism`. Prism 只协调配置加载、AGP DSL 写入和 BuildConfig 辅助任务注册;
 * TOML 解析与 Android DSL 适配分别由独立组件负责. 插件只能应用于 Android application 或 library 模块,
 * 不会自动开启 BuildConfig 或 resValues, 也不会设置 applicationId、版本、签名等发布元数据.
 *
 * @aegis 保护插件 ID 与入口, `prismAppConfig` 扩展名称, Android 模块支持边界和配置编排顺序.
 *
 * @author whisper
 * @since 2026/07/25
 */
class PrismPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        var configured: Boolean = false
        var managedEnvironmentNames: Set<String> = emptySet()
        val configLoader: PrismConfigLoader = PrismConfigLoader()
        val androidConfigurator: PrismAndroidConfigurator = PrismAndroidConfigurator()
        val buildConfigTaskRegistrar: PrismBuildConfigTaskRegistrar = PrismBuildConfigTaskRegistrar()
        val appConfigExtension: PrismAppConfigExtension = target.extensions.create(
            PRISM_APP_CONFIG_EXTENSION_NAME,
            PrismAppConfigExtension::class.java
        )

        buildConfigTaskRegistrar.register(target = target)

        target.plugins.withId(ANDROID_APPLICATION_PLUGIN_ID) {
            val android: ApplicationExtension =
                target.extensions.getByType(ApplicationExtension::class.java)
            val config: AppConfig = configLoader.load(target = target)
            configured = true
            managedEnvironmentNames = config.environmentNames
            appConfigExtension.configure(values = config.exportedValues)
            androidConfigurator.configure(android = android, config = config)

            val androidComponents: ApplicationAndroidComponentsExtension =
                target.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            buildConfigTaskRegistrar.configure(
                target = target,
                androidComponents = androidComponents,
                isBuildConfigEnabled = { extension: ApplicationExtension ->
                    extension.buildFeatures.buildConfig == true
                }
            )
        }

        target.plugins.withId(ANDROID_LIBRARY_PLUGIN_ID) {
            val android: LibraryExtension =
                target.extensions.getByType(LibraryExtension::class.java)
            val config: AppConfig = configLoader.load(target = target)
            configured = true
            managedEnvironmentNames = config.environmentNames
            appConfigExtension.configure(values = config.exportedValues)
            androidConfigurator.configure(android = android, config = config)

            val androidComponents: LibraryAndroidComponentsExtension =
                target.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
            buildConfigTaskRegistrar.configure(
                target = target,
                androidComponents = androidComponents,
                isBuildConfigEnabled = { extension: LibraryExtension ->
                    extension.buildFeatures.buildConfig == true
                }
            )
        }

        target.afterEvaluate {
            if (!configured) {
                throw GradleException(
                    "com.whisper.prism can only be applied to Android application or library modules. " +
                        "Apply com.android.application or com.android.library before com.whisper.prism " +
                        "in module ${target.path}."
                )
            }
            androidConfigurator.warnSupplementalEnvironmentFlavors(
                target = target,
                managedEnvironmentNames = managedEnvironmentNames
            )
        }
    }

    private val AppConfig.environmentNames: Set<String>
        get() = environments
            .map { environment: EnvironmentConfig -> environment.name }
            .toSet()

    private companion object {
        private const val PRISM_APP_CONFIG_EXTENSION_NAME: String = "prismAppConfig"
        private const val ANDROID_APPLICATION_PLUGIN_ID: String = "com.android.application"
        private const val ANDROID_LIBRARY_PLUGIN_ID: String = "com.android.library"
    }
}
