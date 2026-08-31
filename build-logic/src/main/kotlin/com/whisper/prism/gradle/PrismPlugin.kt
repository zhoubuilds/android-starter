package com.whisper.prism.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseError
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.io.File

/**
 * Android 环境变体约定插件
 *
 * 读取当前选中的应用配置 TOML, 创建 Android 环境 product flavors, 并将公共配置与环境差异配置写入 AGP DSL.
 * 插件 ID 为 `com.whisper.prism`, 只能和 `com.android.application` 或 `com.android.library` 一起使用.
 *
 * ## 应用插件
 *
 * ```kotlin
 * plugins {
 *     alias(libs.plugins.android.application)
 *     id("com.whisper.prism")
 * }
 *
 * android {
 *     buildFeatures {
 *         buildConfig = true
 *         resValues = true
 *     }
 *
 *     defaultConfig {
 *         applicationId = prismAppConfig.get<String>("applicationId")
 *     }
 * }
 * ```
 *
 * 插件不会自动开启 `buildFeatures.buildConfig` 或 `buildFeatures.resValues`. 使用对应配置分组的模块必须自行开启功能.
 * 插件也不会设置 application 模块的 `applicationId`、`versionCode`、`versionName` 或签名配置.
 * `applicationId` 可以从 exports 读取后由 app 构建脚本显式赋值, 以保留 AGP DSL 的可发现性.
 *
 * ## 选择配置文件
 *
 * 根目录 `gradle.properties` 可以选择当前应用或马甲包配置:
 *
 * ```properties
 * prism.appConfig.file=app-configs/example.toml
 * ```
 *
 * 设置 `prism.appConfig.file` 时, 指向的文件必须存在; 未设置时, 回退的根目录 `app-config.toml` 必须存在.
 * 相对路径以根工程目录为基准, 也支持绝对路径.
 *
 * ## TOML 配置结构
 *
 * ```toml
 * [values]
 * serviceApiKey = "example-api-key"
 *
 * [exports]
 * applicationId = "com.example.app"
 * serviceApiKey = { reference = "values.serviceApiKey" }
 *
 * [default]
 * buildConfig.API_HOST = "https://api.example.com"
 * buildConfig.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
 * manifestPlaceholders.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
 * resValues.channel_name = { type = "string", value = "official" }
 *
 * [[environments]]
 * name = "dev"
 * buildConfig.API_HOST = "https://dev-api.example.com"
 * manifestPlaceholders.DEPLOYMENT_ENV = "dev"
 *
 * [[environments]]
 * name = "prod"
 * buildConfig.API_HOST = "https://api.example.com"
 * manifestPlaceholders.DEPLOYMENT_ENV = "prod"
 * ```
 *
 * 顶层只允许以下分组:
 *
 * - <code>&#91;values&#93;</code>: 保存 TOML 内部复用的基础值. 支持 String、Boolean、Integer、Long 和 Double, 不支持 reference.
 * - <code>&#91;exports&#93;</code>: 选择允许模块构建脚本读取的值. 每个导出可以使用标量字面量或引用 `values.*`.
 * - <code>&#91;default&#93;</code>: 配置所有 Android variants 共享的 `defaultConfig` 字段.
 * - <code>&#91;&#91;environments&#93;&#93;</code>: 配置 `env` flavor dimension 下的环境 product flavor 和环境差异字段.
 *
 * <code>&#91;&#91;environments&#93;&#93;</code> 可以省略. 此时插件仍应用 <code>&#91;default&#93;</code> 和
 * <code>&#91;exports&#93;</code>, 但不会创建 `env` dimension 或环境 product flavors.
 * environment 的 `name` 必须是非空 String, 名称不能重复且不能以 `test` 开头.
 *
 * ## 可配置字段
 *
 * <code>&#91;default&#93;</code> 和每个 <code>&#91;&#91;environments&#93;&#93;</code> 只允许以下三个分组:
 *
 * - `buildConfig`: 调用 AGP `buildConfigField(...)`. 字段名必须是合法 Java 标识符. 字段值支持 String、Boolean、
 *   TOML Integer 和有限 Double. Integer 在 Int 范围内生成 `int`, 超出后生成 `long`.
 * - `manifestPlaceholders`: 写入 AGP `manifestPlaceholders`. 值必须是 String 或解析为 String 的 reference.
 *   key 包含点号时必须使用 TOML 引号, 例如 `manifestPlaceholders."provider.authorities" = "..."`.
 * - `resValues`: 调用 AGP `resValue(...)`. 每项必须提供非空 String `type` 和 String `value`, `value` 可以使用 reference.
 *
 * 同名字段遵守 AGP 原生合并语义, environment 配置覆盖 default 配置.
 *
 * ## reference 和 exports
 *
 * reference 固定使用 `{ reference = "values.<name>" }` 结构, 只能指向 <code>&#91;values&#93;</code>. reference 不能指向
 * exports, default, environment 或另一个 reference, 因此不会产生递归解析或循环引用.
 *
 * 模块构建脚本通过 `prismAppConfig.get<T>("name")` 读取 <code>&#91;exports&#93;</code>. export 支持 String, Boolean,
 * Integer/Long 和有限 Double 字面量, 也可以引用相同类型的 values. 支持的 `T` 为 `String`, `Boolean`, `Int`,
 * `Long` 和 `Double`. TOML Integer 由解析器保存为 Long; 读取为 Int 时会额外检查 Int 取值范围. 未导出, 类型不匹配或
 * 类型不受支持时会在 Gradle 配置阶段失败.
 *
 * ## 校验和辅助任务
 *
 * 文件不存在、TOML 语法错误、未知分组、字段类型错误、无效 reference 和环境名称错误都会在 Gradle 配置阶段失败.
 * 语法错误包含文件、行号和列号, 深层结构错误包含文件和 TOML 配置路径.
 *
 * 插件注册 `prism > generateBuildConfig` 开发辅助任务, 用于 clean 后预生成已有 Android variants 的 BuildConfig 源码.
 * 该任务不参与 flavor 创建、字段注入、assemble 或打包流程.
 *
 * @author whisper
 * @since 2026/07/25
 */
class PrismPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        var configured: Boolean = false
        var managedEnvironmentNames: Set<String> = emptySet()
        val buildConfigTaskRegistrar: PrismBuildConfigTaskRegistrar = PrismBuildConfigTaskRegistrar()
        val appConfigExtension: PrismAppConfigExtension = target.extensions.create(
            PRISM_APP_CONFIG_EXTENSION_NAME,
            PrismAppConfigExtension::class.java
        )

        // 注册开发期辅助任务, 方便在 Android Studio 侧边栏预生成 BuildConfig.
        buildConfigTaskRegistrar.register(target = target)

        target.plugins.withId(ANDROID_APPLICATION_PLUGIN_ID) {
            val android: ApplicationExtension =
                target.extensions.getByType(ApplicationExtension::class.java)
            val config: AppConfig = readAppConfig(target)
            configured = true
            managedEnvironmentNames = config.environments
                .map { environment: EnvironmentConfig -> environment.name }
                .toSet()
            configureAppConfigExtension(
                extension = appConfigExtension,
                config = config
            )
            configureAndroidEnvironments(
                android = android,
                config = config
            )
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
            val config: AppConfig = readAppConfig(target)
            configured = true
            managedEnvironmentNames = config.environments
                .map { environment: EnvironmentConfig -> environment.name }
                .toSet()
            configureAppConfigExtension(
                extension = appConfigExtension,
                config = config
            )
            configureAndroidEnvironments(
                android = android,
                config = config
            )
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
            warnSupplementalEnvironmentFlavors(
                target = target,
                managedEnvironmentNames = managedEnvironmentNames
            )
        }
    }

    /**
     * 配置应用配置导出
     *
     * @param extension 应用配置导出
     * @param config 应用构建配置
     */
    private fun configureAppConfigExtension(
        extension: PrismAppConfigExtension,
        config: AppConfig
    ) {
        extension.configure(values = config.exportedValues)
    }

    /**
     * 提示手动补充的环境 flavor
     *
     * @param target 当前 Gradle 项目
     * @param managedEnvironmentNames 插件管理的环境名称集合
     */
    private fun warnSupplementalEnvironmentFlavors(
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

    /**
     * 提示手动补充的环境 flavor
     *
     * @param target 当前 Gradle 项目
     * @param managedEnvironmentNames 插件管理的环境名称集合
     * @param actualEnvironmentNames 实际环境名称集合
     */
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

    /**
     * 配置 Android 环境变体
     *
     * @param android Android 应用扩展
     * @param config 应用构建配置
     */
    private fun configureAndroidEnvironments(
        android: ApplicationExtension,
        config: AppConfig
    ) {
        if (config.isEmpty) {
            return
        }
        android.defaultConfig {
            applyVariantConfig(config = config.defaultConfig)
        }
        val environments: List<EnvironmentConfig> = config.environments
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

    /**
     * 配置 Android 环境变体
     *
     * @param android Android 库扩展
     * @param config 应用构建配置
     */
    private fun configureAndroidEnvironments(
        android: LibraryExtension,
        config: AppConfig
    ) {
        if (config.isEmpty) {
            return
        }
        android.defaultConfig {
            applyVariantConfig(config = config.defaultConfig)
        }
        val environments: List<EnvironmentConfig> = config.environments
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

    /**
     * 应用环境配置
     *
     * @param environment 环境配置
     */
    private fun ProductFlavor.applyEnvironmentConfig(environment: EnvironmentConfig) {
        dimension = ENV_DIMENSION
        applyVariantConfig(config = environment.config)
    }

    /**
     * 应用变体维度配置
     *
     * @param config 变体维度配置
     */
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

    /**
     * 读取应用配置文件
     *
     * @param target 当前 Gradle 项目
     * @return 应用构建配置
     * @exception GradleException 配置文件语法错误或配置不合法时抛出
     */
    private fun readAppConfig(target: Project): AppConfig {
        val configFilePath: String = target.providers
            .gradleProperty(CONFIG_FILE_PROPERTY)
            .getOrElse(DEFAULT_CONFIG_FILE_PATH)
            .trim()
        if (configFilePath.isEmpty()) {
            throw GradleException("Gradle property '$CONFIG_FILE_PROPERTY' must not be blank.")
        }
        val file: File = target.rootProject.file(configFilePath)
        if (!file.isFile) {
            throw GradleException(
                "App config file does not exist: ${file.path}. " +
                    "Set '$CONFIG_FILE_PROPERTY' to an existing file. " +
                    "When the property is omitted, the fallback path is '$DEFAULT_CONFIG_FILE_PATH'."
            )
        }
        if (file.readText().isBlank()) {
            logConfigNotice(
                target = target,
                message = "App config is empty at ${file.path}. " +
                    "No exports, default config or environments will be applied."
            )
            return AppConfig.EMPTY
        }

        val result: TomlParseResult = file.inputStream().use { input ->
            Toml.parse(input)
        }
        if (result.hasErrors()) {
            val messages: String = result.errors()
                .joinToString(separator = "\n") { error: TomlParseError ->
                    val line: Int = error.position().line()
                    val column: Int = error.position().column()
                    "- ${file.path}:$line:$column: ${error.message}"
                }
            throw GradleException("Invalid app config syntax:\n$messages")
        }

        return try {
            readParsedAppConfig(
                target = target,
                file = file,
                result = result
            )
        } catch (error: GradleException) {
            throw GradleException(
                "Invalid app config: ${file.path}\n${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    /**
     * 读取已完成语法解析的应用配置
     *
     * @param target 当前 Gradle 项目
     * @param file 应用配置文件
     * @param result TOML 解析结果
     * @return 应用构建配置
     * @exception GradleException 配置结构或字段不合法时抛出
     */
    private fun readParsedAppConfig(
        target: Project,
        file: File,
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
            run {
                logConfigNotice(
                    target = target,
                    message = "No environments configured in ${file.path}. " +
                        "Shared configuration will be applied without creating environment productFlavors."
                )
                return AppConfig(
                    exportedValues = exportedValues,
                    defaultConfig = defaultConfig,
                    environments = emptyList()
                )
            }
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
            logConfigNotice(
                target = target,
                message = "No environments configured in ${file.path}. " +
                    "Shared configuration will be applied without creating environment productFlavors."
            )
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

    /**
     * 输出配置提示
     *
     * @param target 当前 Gradle 项目
     * @param message 提示消息
     */
    private fun logConfigNotice(target: Project, message: String) {
        target.logger.lifecycle(
            """
            |
            |[com.whisper.prism]
            |$message
            """.trimMargin()
        )
    }

    private companion object {
        /**
         * 应用配置导出扩展名称
         */
        private const val PRISM_APP_CONFIG_EXTENSION_NAME: String = "prismAppConfig"

        /**
         * Android 应用插件 ID
         */
        private const val ANDROID_APPLICATION_PLUGIN_ID: String = "com.android.application"

        /**
         * Android 库插件 ID
         */
        private const val ANDROID_LIBRARY_PLUGIN_ID: String = "com.android.library"

        /**
         * 配置文件路径
         */
        private const val DEFAULT_CONFIG_FILE_PATH: String = "app-config.toml"

        /**
         * 应用配置文件 Gradle 属性
         */
        private const val CONFIG_FILE_PROPERTY: String = "prism.appConfig.file"

        /**
         * 内部复用值配置键
         */
        private const val VALUES_KEY: String = "values"

        /**
         * 对外导出配置键
         */
        private const val EXPORTS_KEY: String = "exports"

        /**
         * 环境维度名称
         */
        private const val ENV_DIMENSION: String = "env"

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
 * 应用配置 TOML 解析结果.
 *
 * 保存导出值、默认配置和环境配置集合.
 *
 * @author whisper
 * @since 2026/08/07
 */
private data class AppConfig(
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
private data class VariantConfig(
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
private data class EnvironmentConfig(
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
private data class BuildConfigValue(
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
 * 内部值引用
 *
 * 保存 reference 指向的 values 字段名称.
 *
 * @author whisper
 * @since 2026/08/22
 */
private data class ValueReference(
    /**
     * values 字段名称.
     */
    val name: String,
)

/**
 * resValue 配置
 *
 * 保存 AGP resValue 需要的资源类型和值.
 *
 * @author whisper
 * @since 2026/08/12
 */
private data class ResValueConfig(
    /**
     * 资源类型
     */
    val type: String,
    /**
     * 资源值
     */
    val value: String,
)
