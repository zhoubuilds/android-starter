package com.whisper.buildlogic.prism

/**
 * 应用配置 TOML 解析结果.
 *
 * 保存导出值、默认配置和环境配置集合.
 *
 * @author whisper
 * @since 2026/08/07
 */
internal data class AppConfig(
    /**
     * 对外导出值.
     */
    val exportedValues: Map<String, Any>,
    /**
     * 默认配置.
     */
    val defaultConfig: VariantConfig,
    /**
     * 环境配置集合.
     */
    val environments: List<EnvironmentConfig>,
) {
    /**
     * 是否没有任何需要应用的配置.
     */
    val isEmpty: Boolean
        get() = defaultConfig.isEmpty && environments.isEmpty()

    companion object {
        /**
         * 空配置.
         */
        val EMPTY: AppConfig = AppConfig(
            exportedValues = emptyMap(),
            defaultConfig = VariantConfig.EMPTY,
            environments = emptyList()
        )
    }
}

/**
 * 变体维度配置
 *
 * 保存 defaultConfig 或 productFlavor 可注入的构建配置.
 *
 * @author whisper
 * @since 2026/08/12
 */
internal data class VariantConfig(
    /**
     * BuildConfig 字段集合.
     */
    val buildConfigFields: Map<String, BuildConfigValue>,
    /**
     * Manifest placeholder 集合.
     */
    val manifestPlaceholders: Map<String, String>,
    /**
     * resValue 配置集合.
     */
    val resValues: Map<String, ResValueConfig>,
) {
    /**
     * 是否没有任何配置.
     */
    val isEmpty: Boolean
        get() = buildConfigFields.isEmpty() && manifestPlaceholders.isEmpty() && resValues.isEmpty()

    companion object {
        /**
         * 空配置.
         */
        val EMPTY: VariantConfig = VariantConfig(
            buildConfigFields = emptyMap(),
            manifestPlaceholders = emptyMap(),
            resValues = emptyMap()
        )
    }
}

/**
 * 环境配置
 *
 * 解析后的单个环境配置.
 *
 * @author whisper
 * @since 2026/07/25
 */
internal data class EnvironmentConfig(
    /**
     * 环境名称
     */
    val name: String,
    /**
     * 变体配置
     */
    val config: VariantConfig,
)

/**
 * BuildConfig 字段值
 *
 * 保存 AGP buildConfigField 需要的字段类型和值字面量.
 *
 * @author whisper
 * @since 2026/07/25
 */
internal data class BuildConfigValue(
    /**
     * 字段类型
     */
    val type: String,
    /**
     * 字段字面量
     */
    val literal: String,
)

/**
 * resValue 配置
 *
 * 保存 AGP resValue 需要的资源类型和值.
 *
 * @author whisper
 * @since 2026/08/12
 */
internal data class ResValueConfig(
    /**
     * 资源类型
     */
    val type: String,
    /**
     * 资源值
     */
    val value: String,
)
