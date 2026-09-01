package com.whisper.buildlogic.prism

import org.gradle.api.GradleException
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseError
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/**
 * Prism TOML 配置解析器.
 *
 * 只负责把 TOML 文本转换为应用配置模型, 不读取 Gradle 属性, 也不访问 Android Gradle DSL.
 *
 * @aegis 保护 TOML 分组与引用协议, 类型/名称校验, BuildConfig 字面量和结构化错误诊断.
 *
 * @author whisper
 * @since 2026/09/01
 */
internal class PrismConfigParser {

    /**
     * 解析应用配置.
     *
     * @param source TOML 文本
     * @param sourcePath 用于错误诊断的配置来源路径
     * @return 应用配置
     * @exception GradleException TOML 语法或配置结构不合法时抛出
     */
    fun parse(source: String, sourcePath: String): AppConfig {
        val result: TomlParseResult = Toml.parse(source)
        if (result.hasErrors()) {
            val messages: String = result.errors()
                .joinToString(separator = "\n") { error: TomlParseError ->
                    val line: Int = error.position().line()
                    val column: Int = error.position().column()
                    "- $sourcePath:$line:$column: ${error.message}"
                }
            throw GradleException("Invalid app config syntax:\n$messages")
        }

        return try {
            readParsedAppConfig(result = result)
        } catch (error: GradleException) {
            throw GradleException(
                "Invalid app config: $sourcePath\n${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    /**
     * 读取已完成语法解析的应用配置
     *
     * @param result TOML 解析结果
     * @return 应用构建配置
     * @exception GradleException 配置结构或字段不合法时抛出
     */
    private fun readParsedAppConfig(
        result: TomlParseResult
    ): AppConfig {

        val unsupportedKeys: List<String> = result.keySet()
            .filterNot { key: String ->
                key == VALUES_KEY || key == EXPORTS_KEY || key == DEFAULT_KEY || key == ENVIRONMENTS_KEY
            }
            .distinct()
            .sorted()
        if (unsupportedKeys.isNotEmpty()) {
            throw GradleException(
                "Unsupported top-level app config item: ${unsupportedKeys.joinToString()}. " +
                    "Only '$VALUES_KEY', '$EXPORTS_KEY', '$DEFAULT_KEY' and '$ENVIRONMENTS_KEY' " +
                    "are allowed."
            )
        }

        val values: Map<String, Any> = readValues(result = result)
        val exportedValues: Map<String, Any> = readExports(
            result = result,
            values = values
        )
        val defaultConfig: VariantConfig = readDefaultConfig(
            result = result,
            values = values
        )
        val environmentArray: TomlArray = if (!result.contains(ENVIRONMENTS_KEY)) {
            return AppConfig(
                exportedValues = exportedValues,
                defaultConfig = defaultConfig,
                environments = emptyList()
            )
        } else {
            if (!result.isArray(ENVIRONMENTS_KEY)) {
                throw GradleException(
                    "Invalid app config item: $ENVIRONMENTS_KEY must be a TOML array of tables."
                )
            }
            result.getArray(ENVIRONMENTS_KEY)
                ?: throw GradleException(
                    "Invalid app config item: $ENVIRONMENTS_KEY must be a TOML array of tables."
                )
        }
        if (environmentArray.isEmpty) {
            return AppConfig(
                exportedValues = exportedValues,
                defaultConfig = defaultConfig,
                environments = emptyList()
            )
        }

        val names: MutableSet<String> = mutableSetOf()
        val environments: List<EnvironmentConfig> = environmentArray.toList().mapIndexed { index: Int, item: Any? ->
            val table: TomlTable = item as? TomlTable
                ?: throw GradleException("Invalid app config item: $ENVIRONMENTS_KEY[$index]")
            val namePath: String = "$ENVIRONMENTS_KEY[$index].$ENVIRONMENT_NAME_KEY"
            if (!table.contains(ENVIRONMENT_NAME_KEY)) {
                throw GradleException("Missing app config item: $namePath")
            }
            if (!table.isString(ENVIRONMENT_NAME_KEY)) {
                throw GradleException(
                    "Invalid app config item: $namePath must be a non-blank String."
                )
            }
            val name: String = table.getString(ENVIRONMENT_NAME_KEY)
                ?.takeIf { value: String -> value.isNotBlank() }
                ?: throw GradleException(
                    "Invalid app config item: $namePath must be a non-blank String."
                )
            if (name.any(Char::isWhitespace)) {
                throw GradleException(
                    "Invalid app config item: $namePath must not contain whitespace, but was '$name'."
                )
            }
            if (!names.add(name)) {
                throw GradleException("Duplicate environment name at $namePath: $name")
            }
            if (name.startsWith(TEST_ENVIRONMENT_PREFIX)) {
                throw GradleException(
                    "Environment name at $namePath cannot start with '$TEST_ENVIRONMENT_PREFIX': $name"
                )
            }

            val environmentConfig: VariantConfig = readVariantConfig(
                table = table,
                path = "$ENVIRONMENTS_KEY[$index]",
                allowedRootKeys = setOf(ENVIRONMENT_NAME_KEY),
                values = values
            )
            EnvironmentConfig(name = name, config = environmentConfig)
        }
        return AppConfig(
            exportedValues = exportedValues,
            defaultConfig = defaultConfig,
            environments = environments
        )
    }

    /**
     * 读取内部复用值
     *
     * @param result TOML 解析结果
     * @return 内部复用值
     */
    private fun readValues(result: TomlParseResult): Map<String, Any> {
        if (!result.contains(VALUES_KEY)) {
            return emptyMap()
        }
        if (!result.isTable(VALUES_KEY)) {
            throw GradleException("Invalid app config item: $VALUES_KEY must be a TOML table.")
        }

        val valuesTable: TomlTable = result.getTable(VALUES_KEY)
            ?: throw GradleException("Invalid app config item: $VALUES_KEY")
        return valuesTable.entrySet()
            .associate { entry: Map.Entry<String, Any> ->
                val name: String = entry.key
                validateValueName(name = name, path = VALUES_KEY)
                name to entry.value.toSupportedValue(path = "$VALUES_KEY.$name")
            }
    }

    /**
     * 读取对外导出值
     *
     * @param result TOML 解析结果
     * @param values 内部复用值
     * @return 对外导出值
     */
    private fun readExports(
        result: TomlParseResult,
        values: Map<String, Any>
    ): Map<String, Any> {
        if (!result.contains(EXPORTS_KEY)) {
            return emptyMap()
        }
        if (!result.isTable(EXPORTS_KEY)) {
            throw GradleException("Invalid app config item: $EXPORTS_KEY must be a TOML table.")
        }

        val exportsTable: TomlTable = result.getTable(EXPORTS_KEY)
            ?: throw GradleException("Invalid app config item: $EXPORTS_KEY")
        return exportsTable.entrySet()
            .associate { entry: Map.Entry<String, Any> ->
                val name: String = entry.key
                val path: String = "$EXPORTS_KEY.$name"
                validateValueName(name = name, path = EXPORTS_KEY)
                name to entry.value.resolveValue(values = values, path = path)
            }
    }

    /**
     * 读取默认变体配置
     *
     * @param result TOML 解析结果
     * @return 默认变体配置
     */
    private fun readDefaultConfig(
        result: TomlParseResult,
        values: Map<String, Any>
    ): VariantConfig {
        if (!result.contains(DEFAULT_KEY)) {
            return VariantConfig.EMPTY
        }
        if (!result.isTable(DEFAULT_KEY)) {
            throw GradleException("Invalid app config item: $DEFAULT_KEY must be a TOML table.")
        }

        val defaultTable: TomlTable = result.getTable(DEFAULT_KEY)
            ?: throw GradleException("Invalid app config item: $DEFAULT_KEY")
        return readVariantConfig(
            table = defaultTable,
            path = DEFAULT_KEY,
            allowedRootKeys = emptySet(),
            values = values
        )
    }

    /**
     * 读取变体维度配置
     *
     * @param table TOML 表
     * @param path 配置路径
     * @param allowedRootKeys 允许的普通字段名
     * @param values 内部复用值
     * @return 变体维度配置
     */
    private fun readVariantConfig(
        table: TomlTable,
        path: String,
        allowedRootKeys: Set<String>,
        values: Map<String, Any>
    ): VariantConfig {
        validateVariantConfigKeys(
            table = table,
            path = path,
            allowedRootKeys = allowedRootKeys
        )
        return VariantConfig(
            buildConfigFields = readBuildConfigFields(
                table = table,
                path = path,
                values = values
            ),
            manifestPlaceholders = readManifestPlaceholders(
                table = table,
                path = path,
                values = values
            ),
            resValues = readResValues(
                table = table,
                path = path,
                values = values
            )
        )
    }

    /**
     * 校验变体配置键
     *
     * @param table TOML 表
     * @param path 配置路径
     * @param allowedRootKeys 允许的普通字段名
     */
    private fun validateVariantConfigKeys(
        table: TomlTable,
        path: String,
        allowedRootKeys: Set<String>
    ) {
        val allowedKeys: Set<String> = allowedRootKeys + VARIANT_CONFIG_SECTION_KEYS
        val unsupportedKeys: List<String> = table.keySet()
            .filterNot { key: String -> allowedKeys.contains(key) }
            .distinct()
            .sorted()
        if (unsupportedKeys.isNotEmpty()) {
            throw GradleException(
                "Unsupported app config item: $path contains ${unsupportedKeys.joinToString()}. " +
                    "Only ${allowedKeys.sorted().joinToString()} are allowed."
            )
        }
    }

    /**
     * 读取 BuildConfig 字段
     *
     * @param table TOML 表
     * @param path 配置路径
     * @param values 内部复用值
     * @return BuildConfig 字段集合
     */
    private fun readBuildConfigFields(
        table: TomlTable,
        path: String,
        values: Map<String, Any>
    ): Map<String, BuildConfigValue> {
        if (!table.contains(BUILD_CONFIG_KEY)) {
            return emptyMap()
        }
        if (!table.isTable(BUILD_CONFIG_KEY)) {
            throw GradleException("Invalid app config item: $path.$BUILD_CONFIG_KEY must be a TOML table.")
        }

        val buildConfigTable: TomlTable = table.getTable(BUILD_CONFIG_KEY)
            ?: throw GradleException("Invalid app config item: $path.$BUILD_CONFIG_KEY")
        return buildConfigTable.entrySet()
            .associate { entry: Map.Entry<String, Any> ->
                val name: String = entry.key
                val itemPath: String = "$path.$BUILD_CONFIG_KEY.$name"
                validateBuildConfigName(name = name, path = itemPath)
                name to entry.value.resolveValue(values = values, path = itemPath)
                    .toBuildConfigValue(path = itemPath)
            }
    }

    /**
     * 读取 Manifest placeholders
     *
     * @param table TOML 表
     * @param path 配置路径
     * @param values 内部复用值
     * @return Manifest placeholder 集合
     */
    private fun readManifestPlaceholders(
        table: TomlTable,
        path: String,
        values: Map<String, Any>
    ): Map<String, String> {
        if (!table.contains(MANIFEST_PLACEHOLDERS_KEY)) {
            return emptyMap()
        }
        if (!table.isTable(MANIFEST_PLACEHOLDERS_KEY)) {
            throw GradleException("Invalid app config item: $path.$MANIFEST_PLACEHOLDERS_KEY must be a TOML table.")
        }

        val placeholderTable: TomlTable = table.getTable(MANIFEST_PLACEHOLDERS_KEY)
            ?: throw GradleException("Invalid app config item: $path.$MANIFEST_PLACEHOLDERS_KEY")
        return placeholderTable.entrySet()
            .associate { entry: Map.Entry<String, Any> ->
                val name: String = entry.key
                val itemPath: String = "$path.$MANIFEST_PLACEHOLDERS_KEY.$name"
                val value: Any = entry.value.resolveValue(values = values, path = itemPath)
                name to (value as? String
                    ?: throw GradleException(
                        "Invalid app config item: $itemPath must be a String or resolve to a String."
                    ))
            }
    }

    /**
     * 解析配置值
     *
     * @param values 内部复用值
     * @param path 配置路径
     * @return 解析后的基础值
     */
    private fun Any.resolveValue(
        values: Map<String, Any>,
        path: String
    ): Any {
        return when (this) {
            is TomlTable -> {
                val tableKeys: List<String> = keySet().sorted()
                if (tableKeys != listOf(REFERENCE_KEY)) {
                    val fields: String = tableKeys
                        .takeIf(List<String>::isNotEmpty)
                        ?.joinToString()
                        ?: "<empty>"
                    throw GradleException(
                        "Unexpected nested table at $path. " +
                            "Expected a scalar value or '$VALUE_REFERENCE_EXAMPLE'. " +
                            "Found fields: $fields."
                    )
                }
                toValueReference(path = path).resolve(values = values, path = path)
            }
            else -> toSupportedValue(path = path)
        }
    }

    /**
     * 转换为内部值引用
     *
     * @param path 配置路径
     * @return 内部值引用
     */
    private fun Any.toValueReference(path: String): ValueReference {
        val referenceTable: TomlTable = this as? TomlTable
            ?: throw GradleException(
                "Invalid app config item: $path must be '$VALUE_REFERENCE_EXAMPLE'."
            )
        val unsupportedKeys: List<String> = referenceTable.keySet()
            .filterNot { key: String -> key == REFERENCE_KEY }
            .sorted()
        if (unsupportedKeys.isNotEmpty() || referenceTable.keySet().isEmpty()) {
            throw GradleException(
                "Invalid app config item: $path must be '$VALUE_REFERENCE_EXAMPLE'."
            )
        }
        if (!referenceTable.isString(REFERENCE_KEY)) {
            throw GradleException(
                "Invalid app config item: $path.$REFERENCE_KEY must be a non-blank String."
            )
        }
        val referenceName: String = referenceTable.getString(REFERENCE_KEY)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GradleException(
                "Invalid app config item: $path.$REFERENCE_KEY must be a non-blank String."
            )
        val prefix: String = "$VALUES_KEY."
        if (!referenceName.startsWith(prefix) || referenceName.length == prefix.length) {
            throw GradleException(
                "Unsupported app config reference at $path: $referenceName. " +
                    "References must use '$VALUES_KEY.<name>'."
            )
        }
        return ValueReference(name = referenceName.removePrefix(prefix))
    }

    /**
     * 解析内部值引用
     *
     * @param values 内部复用值
     * @param path 使用引用的配置路径
     * @return 引用值
     */
    private fun ValueReference.resolve(
        values: Map<String, Any>,
        path: String
    ): Any {
        return values[name]
            ?: throw GradleException(
                "Unknown app config reference at $path: $VALUES_KEY.$name"
            )
    }

    /**
     * 转换为支持的基础值
     *
     * @param path 配置路径
     * @return 基础值
     */
    private fun Any.toSupportedValue(path: String): Any {
        return when (this) {
            is String, is Boolean, is Long, is Double -> this
            else -> throw GradleException(
                "Unsupported app config value type: $path. " +
                    "Only String, Boolean, Integer, Long and Double are supported."
            )
        }
    }

    /**
     * 读取 resValue 配置
     *
     * @param table TOML 表
     * @param path 配置路径
     * @param values 内部复用值
     * @return resValue 配置集合
     */
    private fun readResValues(
        table: TomlTable,
        path: String,
        values: Map<String, Any>
    ): Map<String, ResValueConfig> {
        if (!table.contains(RES_VALUES_KEY)) {
            return emptyMap()
        }
        if (!table.isTable(RES_VALUES_KEY)) {
            throw GradleException(
                "Invalid app config item: $path.$RES_VALUES_KEY must be a TOML table. " +
                    "Use '$RES_VALUE_EXAMPLE'."
            )
        }

        val resValuesTable: TomlTable = table.getTable(RES_VALUES_KEY)
            ?: throw GradleException("Invalid app config item: $path.$RES_VALUES_KEY")
        return resValuesTable.entrySet()
            .associate { entry: Map.Entry<String, Any> ->
                val name: String = entry.key
                val resValueTable: TomlTable = entry.value as? TomlTable
                    ?: throw GradleException(
                        "Invalid app config item: $path.$RES_VALUES_KEY.$name " +
                            "must be a TOML table with '$RES_VALUE_TYPE_KEY' and '$RES_VALUE_VALUE_KEY'. " +
                            "Use '$RES_VALUES_KEY.$name = { $RES_VALUE_TYPE_KEY = \"string\", " +
                            "$RES_VALUE_VALUE_KEY = \"Example\" }'."
                    )
                name to resValueTable.toResValueConfig(
                    path = "$path.$RES_VALUES_KEY.$name",
                    values = values
                )
            }
    }

    /**
     * 转换为 resValue 配置
     *
     * @param path 配置路径
     * @param values 内部复用值
     * @return resValue 配置
     */
    private fun TomlTable.toResValueConfig(
        path: String,
        values: Map<String, Any>
    ): ResValueConfig {
        val unsupportedKeys: List<String> = keySet()
            .filterNot { key: String -> key == RES_VALUE_TYPE_KEY || key == RES_VALUE_VALUE_KEY }
            .sorted()
        if (unsupportedKeys.isNotEmpty()) {
            throw GradleException(
                "Unsupported app config item: $path contains ${unsupportedKeys.joinToString()}. " +
                    "Only '$RES_VALUE_TYPE_KEY' and '$RES_VALUE_VALUE_KEY' are allowed. " +
                    "Use '$RES_VALUE_EXAMPLE'."
            )
        }
        val type: String = getRequiredString(
            key = RES_VALUE_TYPE_KEY,
            path = path,
            allowBlank = false
        )
        if (!contains(RES_VALUE_VALUE_KEY)) {
            throw GradleException("Missing app config item: $path.$RES_VALUE_VALUE_KEY. Use '$RES_VALUE_EXAMPLE'.")
        }
        val rawValue: Any = get(RES_VALUE_VALUE_KEY)
            ?: throw GradleException(
                "Invalid app config item: $path.$RES_VALUE_VALUE_KEY. Use '$RES_VALUE_EXAMPLE'."
            )
        val value: String = rawValue.resolveValue(
            values = values,
            path = "$path.$RES_VALUE_VALUE_KEY"
        ) as? String
            ?: throw GradleException(
                "Invalid app config item: $path.$RES_VALUE_VALUE_KEY " +
                    "must be a String or resolve to a String."
            )
        return ResValueConfig(type = type, value = value)
    }

    /**
     * 读取必填字符串
     *
     * @param key 配置键
     * @param path 配置路径
     * @param allowBlank 是否允许空字符串
     * @return 字符串值
     */
    private fun TomlTable.getRequiredString(
        key: String,
        path: String,
        allowBlank: Boolean
    ): String {
        if (!contains(key)) {
            throw GradleException("Missing app config item: $path.$key. Use '$RES_VALUE_EXAMPLE'.")
        }
        if (!isString(key)) {
            throw GradleException("Invalid app config item: $path.$key must be a String. Use '$RES_VALUE_EXAMPLE'.")
        }
        val value: String = getString(key)
            ?: throw GradleException("Invalid app config item: $path.$key must be a String. Use '$RES_VALUE_EXAMPLE'.")
        if (!allowBlank && value.isBlank()) {
            throw GradleException("Invalid app config item: $path.$key must not be blank. Use '$RES_VALUE_EXAMPLE'.")
        }
        return value
    }

    /**
     * 校验 values 或 exports 字段名称
     *
     * @param name 字段名称
     * @param path 所属配置路径
     */
    private fun validateValueName(name: String, path: String) {
        if (name.isBlank()) {
            throw GradleException("Invalid app config item: $path contains a blank field name.")
        }
    }

    /**
     * 校验 BuildConfig 字段名称
     *
     * @param name 字段名称
     * @param path 配置路径
     * @exception GradleException 字段名称不合法时抛出
     */
    private fun validateBuildConfigName(name: String, path: String) {
        if (!BUILD_CONFIG_NAME_PATTERN.matches(name)) {
            throw GradleException(
                "Invalid app config item: $path has invalid BuildConfig field name '$name'. " +
                    "Field names must match '${BUILD_CONFIG_NAME_PATTERN.pattern}'."
            )
        }
    }

    /**
     * 转换为 BuildConfig 字段值
     *
     * @param path 配置路径
     * @return BuildConfig 字段值
     * @exception GradleException 字段类型不支持时抛出
     */
    private fun Any.toBuildConfigValue(path: String): BuildConfigValue {
        return when (this) {
            is String -> BuildConfigValue(
                type = "String",
                literal = toBuildConfigString()
            )
            is Boolean -> BuildConfigValue(
                type = "boolean",
                literal = toString()
            )
            is Long -> if (this in Int.MIN_VALUE..Int.MAX_VALUE) {
                BuildConfigValue(type = "int", literal = toString())
            } else {
                BuildConfigValue(type = "long", literal = "${this}L")
            }
            is Double -> {
                if (!isFinite()) {
                    throw GradleException(
                        "Invalid BuildConfig field value: $path must be a finite Double, but was $this."
                    )
                }
                BuildConfigValue(
                    type = "double",
                    literal = toString()
                )
            }
            else -> throw GradleException("Unsupported BuildConfig field type: $path")
        }
    }

    /**
     * 转换为 BuildConfig 字符串
     *
     * @return BuildConfig 字符串字面量
     */
    private fun String.toBuildConfigString(): String {
        val escapedValue: String = buildString {
            for (char: Char in this@toBuildConfigString) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> if (char.code < CONTROL_CHAR_LIMIT) {
                        val hex: String = char.code.toString(radix = 16).padStart(length = 4, padChar = '0')
                        append("\\u$hex")
                    } else {
                        append(char)
                    }
                }
            }
        }
        return "\"$escapedValue\""
    }

    private companion object {
        /**
         * 内部复用值配置键
         */
        private const val VALUES_KEY: String = "values"

        /**
         * 对外导出配置键
         */
        private const val EXPORTS_KEY: String = "exports"

        /**
         * 环境集合配置键
         */
        private const val ENVIRONMENTS_KEY: String = "environments"

        /**
         * 默认配置键
         */
        private const val DEFAULT_KEY: String = "default"

        /**
         * BuildConfig 配置键
         */
        private const val BUILD_CONFIG_KEY: String = "buildConfig"

        /**
         * Manifest placeholders 配置键
         */
        private const val MANIFEST_PLACEHOLDERS_KEY: String = "manifestPlaceholders"

        /**
         * 引用配置键
         */
        private const val REFERENCE_KEY: String = "reference"

        /**
         * 内部值引用示例
         */
        private const val VALUE_REFERENCE_EXAMPLE: String =
            "{ reference = \"values.example\" }"

        /**
         * resValue 配置键
         */
        private const val RES_VALUES_KEY: String = "resValues"

        /**
         * resValue 类型配置键
         */
        private const val RES_VALUE_TYPE_KEY: String = "type"

        /**
         * resValue 值配置键
         */
        private const val RES_VALUE_VALUE_KEY: String = "value"

        /**
         * resValue 配置示例
         */
        private const val RES_VALUE_EXAMPLE: String = "resValues.app_name = { type = \"string\", value = \"Example\" }"

        /**
         * 变体配置分组键集合
         */
        private val VARIANT_CONFIG_SECTION_KEYS: Set<String> = setOf(
            BUILD_CONFIG_KEY,
            MANIFEST_PLACEHOLDERS_KEY,
            RES_VALUES_KEY
        )

        /**
         * 环境名称配置键
         */
        private const val ENVIRONMENT_NAME_KEY: String = "name"

        /**
         * AGP 禁止以 test 开头的 product flavor 名称
         */
        private const val TEST_ENVIRONMENT_PREFIX: String = "test"

        /**
         * Java 字符串字面量控制字符上限
         */
        private const val CONTROL_CHAR_LIMIT: Int = 0x20

        /**
         * BuildConfig 字段名规则
         */
        private val BUILD_CONFIG_NAME_PATTERN: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/**
 * 解析期间使用的 values 字段引用.
 */
private data class ValueReference(
    val name: String,
)
