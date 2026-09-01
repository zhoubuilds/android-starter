package com.whisper.buildlogic.prism

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.VariantDimension
import org.gradle.api.Project

/**
 * Prism Android DSL 配置器.
 *
 * 只负责把已解析的 [AppConfig] 写入 application 或 library 的 AGP DSL, 不读取或解析 TOML.
 *
 * @author whisper
 * @since 2026/09/01
 */
internal class PrismAndroidConfigurator {

    /**
     * 配置 Android application DSL.
     */
    fun configure(android: ApplicationExtension, config: AppConfig) {
        if (config.isEmpty) {
            return
        }
        android.defaultConfig {
            applyVariantConfig(config = config.defaultConfig)
        }
        configureApplicationEnvironments(android = android, environments = config.environments)
    }

    /**
     * 配置 Android library DSL.
     */
    fun configure(android: LibraryExtension, config: AppConfig) {
        if (config.isEmpty) {
            return
        }
        android.defaultConfig {
            applyVariantConfig(config = config.defaultConfig)
        }
        configureLibraryEnvironments(android = android, environments = config.environments)
    }

    /**
     * 提示模块中未由 Prism 管理的 env flavor.
     */
    fun warnSupplementalEnvironmentFlavors(
        target: Project,
        managedEnvironmentNames: Set<String>
    ) {
        val android: ApplicationExtension? =
            target.extensions.findByType(ApplicationExtension::class.java)
        if (android != null) {
            warnSupplementalEnvironmentFlavors(
                target = target,
                managedEnvironmentNames = managedEnvironmentNames,
                actualEnvironmentNames = android.productFlavors
                    .filter { flavor: ProductFlavor -> flavor.dimension == ENV_DIMENSION }
                    .map { flavor: ProductFlavor -> flavor.name }
                    .toSet()
            )
            return
        }

        val library: LibraryExtension? =
            target.extensions.findByType(LibraryExtension::class.java)
        if (library != null) {
            warnSupplementalEnvironmentFlavors(
                target = target,
                managedEnvironmentNames = managedEnvironmentNames,
                actualEnvironmentNames = library.productFlavors
                    .filter { flavor: ProductFlavor -> flavor.dimension == ENV_DIMENSION }
                    .map { flavor: ProductFlavor -> flavor.name }
                    .toSet()
            )
        }
    }

    private fun configureApplicationEnvironments(
        android: ApplicationExtension,
        environments: List<EnvironmentConfig>
    ) {
        if (environments.isEmpty()) {
            return
        }
        if (!android.flavorDimensions.contains(ENV_DIMENSION)) {
            android.flavorDimensions += ENV_DIMENSION
        }
        android.productFlavors {
            environments.forEach { environment: EnvironmentConfig ->
                create(environment.name) {
                    applyEnvironmentConfig(environment = environment)
                }
            }
        }
    }

    private fun configureLibraryEnvironments(
        android: LibraryExtension,
        environments: List<EnvironmentConfig>
    ) {
        if (environments.isEmpty()) {
            return
        }
        if (!android.flavorDimensions.contains(ENV_DIMENSION)) {
            android.flavorDimensions += ENV_DIMENSION
        }
        android.productFlavors {
            environments.forEach { environment: EnvironmentConfig ->
                create(environment.name) {
                    applyEnvironmentConfig(environment = environment)
                }
            }
        }
    }

    private fun ProductFlavor.applyEnvironmentConfig(environment: EnvironmentConfig) {
        dimension = ENV_DIMENSION
        applyVariantConfig(config = environment.config)
    }

    private fun VariantDimension.applyVariantConfig(config: VariantConfig) {
        config.buildConfigFields.forEach { (name: String, value: BuildConfigValue) ->
            buildConfigField(value.type, name, value.literal)
        }
        config.manifestPlaceholders.forEach { (name: String, value: String) ->
            manifestPlaceholders[name] = value
        }
        config.resValues.forEach { (name: String, value: ResValueConfig) ->
            resValue(value.type, name, value.value)
        }
    }

    private fun warnSupplementalEnvironmentFlavors(
        target: Project,
        managedEnvironmentNames: Set<String>,
        actualEnvironmentNames: Set<String>
    ) {
        val supplementalEnvironmentNames: List<String> = actualEnvironmentNames
            .filterNot { name: String -> managedEnvironmentNames.contains(name) }
            .sorted()
        if (supplementalEnvironmentNames.isEmpty()) {
            return
        }

        target.logger.warn(
            "Module ${target.path} declares supplemental environment productFlavors not managed by " +
                "the selected app config TOML: ${supplementalEnvironmentNames.joinToString()}. " +
                "Keep environment productFlavors in the selected app config TOML when possible."
        )
    }

    private companion object {
        private const val ENV_DIMENSION: String = "env"
    }
}
