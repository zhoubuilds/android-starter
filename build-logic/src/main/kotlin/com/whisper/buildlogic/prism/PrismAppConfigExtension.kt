package com.whisper.buildlogic.prism

import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import kotlin.reflect.KClass

/**
 * Prism 应用配置导出
 *
 * 向模块构建脚本暴露当前应用配置文件中的 exports.
 *
 * @aegis 保护 `prismAppConfig.get<T>(name)` API, 导出值冻结, 支持类型, Int 范围和失败诊断契约.
 *
 * @author whisper
 * @since 2026/08/22
 */
abstract class PrismAppConfigExtension {

    /**
     * 当前配置文件导出的值.
     */
    internal abstract val exportedValues: MapProperty<String, Any>

    /**
     * 配置导出值.
     *
     * @param values 已解析的导出值
     */
    internal fun configure(values: Map<String, Any>) {
        exportedValues.set(values)
        exportedValues.disallowChanges()
    }

    /**
     * 获取指定类型的导出值.
     *
     * @param name 导出名称
     * @return 导出值
     */
    inline fun <reified T : Any> get(name: String): T {
        return get(name = name, type = T::class)
    }

    /**
     * 获取指定类型的导出值.
     *
     * @param name 导出名称
     * @param type 目标类型
     * @return 导出值
     */
    @PublishedApi
    internal fun <T : Any> get(name: String, type: KClass<T>): T {
        val value: Any = getExport(name = name)
        val typedValue: Any = when (type) {
            String::class -> value as? String
                ?: throw invalidExportType(name = name, expectedType = "String", value = value)
            Boolean::class -> value as? Boolean
                ?: throw invalidExportType(name = name, expectedType = "Boolean", value = value)
            Int::class -> value.toExportInt(name = name)
            Long::class -> value as? Long
                ?: throw invalidExportType(name = name, expectedType = "Long", value = value)
            Double::class -> value as? Double
                ?: throw invalidExportType(name = name, expectedType = "Double", value = value)
            else -> throw GradleException(
                "Unsupported app config export type: ${type.qualifiedName}. " +
                    "Supported types: String, Boolean, Int, Long and Double."
            )
        }
        @Suppress("UNCHECKED_CAST")
        return typedValue as T
    }

    /**
     * 获取原始导出值.
     *
     * @param name 导出名称
     * @return 原始导出值
     */
    private fun getExport(name: String): Any {
        if (name.isBlank()) {
            throw GradleException("App config export name must not be blank.")
        }
        return exportedValues.get()[name]
            ?: throw GradleException(
                "Missing app config export: $name. Add '$name' to the exports table."
            )
    }

    /**
     * 转换为 Int 导出值.
     *
     * @param name 导出名称
     * @return Int 值
     */
    private fun Any.toExportInt(name: String): Int {
        val longValue: Long = this as? Long
            ?: throw invalidExportType(
                name = name,
                expectedType = "Int",
                value = this
            )
        if (longValue !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw GradleException(
                "Invalid app config export value: $name must be within the Int range, " +
                    "but was $longValue."
            )
        }
        return longValue.toInt()
    }

    /**
     * 创建导出类型错误.
     *
     * @param name 导出名称
     * @param expectedType 期望类型
     * @param value 实际值
     * @return Gradle 配置异常
     */
    private fun invalidExportType(
        name: String,
        expectedType: String,
        value: Any
    ): GradleException {
        return GradleException(
            "Invalid app config export type: $name must be $expectedType, " +
                "but was ${value.javaClass.simpleName}."
        )
    }
}
