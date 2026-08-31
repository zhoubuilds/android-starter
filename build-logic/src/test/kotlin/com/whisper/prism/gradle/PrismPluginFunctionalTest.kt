package com.whisper.prism.gradle

import com.android.build.api.dsl.Lint
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 验证 Android Environment 插件的配置解析、诊断和辅助任务注册.
 *
 * @author whisper
 * @since 2026/08/24
 */
class PrismPluginFunctionalTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证显式配置文件存在时不要求默认回退文件同时存在.
     */
    @Test
    fun explicitConfigPath_whenFileExists_doesNotRequireFallbackFile() {
        val projectDir: File = createProject(
            config = "",
            configFilePath = "app-configs/test.toml",
            selectedConfigFilePath = "app-configs/test.toml"
        )

        runBuild(projectDir = projectDir, "help")

        assertFalse(File(projectDir, "app-config.toml").exists())
    }

    /**
     * 验证未指定显式路径时默认回退文件必须存在.
     */
    @Test
    fun fallbackConfigPath_whenFileMissing_reportsResolvedPath() {
        val projectDir: File = createProject(
            config = null
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(
            result.output.contains(
                File(projectDir, "app-config.toml").path
            )
        )
        assertTrue(result.output.contains("App config file does not exist"))
    }

    /**
     * 验证显式配置文件不存在时不静默回退到默认文件.
     */
    @Test
    fun explicitConfigPath_whenFileMissing_reportsSelectedPath() {
        val selectedPath: String = "app-configs/missing.toml"
        val projectDir: File = createProject(
            config = "",
            selectedConfigFilePath = selectedPath
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(result.output.contains(File(projectDir, selectedPath).path))
        assertFalse(result.output.contains(File(projectDir, "app-config.toml").path))
    }

    /**
     * 验证仅包含 exports 时仍按最终 BuildConfig feature 挂接全部变体任务.
     */
    @Test
    fun generateBuildConfig_withExportsOnlyConfig_dependsOnAllVariants() {
        val projectDir: File = createProject(
            config =
                """
                [values]
                applicationId = "com.example.test"

                [exports]
                applicationId = { reference = "values.applicationId" }
                """.trimIndent(),
            buildConfigEnabled = true
        )

        val result: BuildResult = runBuild(
            projectDir = projectDir,
            "generateBuildConfig",
            "--dry-run"
        )

        assertTrue(result.output.contains(":generateDebugBuildConfig SKIPPED"))
        assertTrue(result.output.contains(":generateReleaseBuildConfig SKIPPED"))
    }

    /**
     * 验证 exports 同时支持标量字面量和 values 引用.
     */
    @Test
    fun exports_withLiteralsAndValueReference_exposesResolvedValues() {
        val projectDir: File = createProject(
            config =
                """
                [values]
                sharedValue = "from-values"

                [exports]
                literalString = "literal"
                literalBoolean = true
                literalInteger = 7
                literalLong = 2147483648
                literalDouble = 1.5
                referencedString = { reference = "values.sharedValue" }
                """.trimIndent(),
            additionalBuildScript =
                """
                tasks.register('verifyExports') {
                    doLast {
                        def stringType = kotlin.jvm.JvmClassMappingKt.getKotlinClass(String.class)
                        def booleanType = kotlin.jvm.JvmClassMappingKt.getKotlinClass(Boolean.class)
                        def intType = kotlin.jvm.JvmClassMappingKt.getKotlinClass(Integer.class)
                        def longType = kotlin.jvm.JvmClassMappingKt.getKotlinClass(Long.class)
                        def doubleType = kotlin.jvm.JvmClassMappingKt.getKotlinClass(Double.class)

                        assert prismAppConfig.get('literalString', stringType) == 'literal'
                        assert prismAppConfig.get('literalBoolean', booleanType)
                        assert prismAppConfig.get('literalInteger', intType) == 7
                        assert prismAppConfig.get('literalLong', longType) == 2147483648L
                        assert prismAppConfig.get('literalDouble', doubleType) == 1.5d
                        assert prismAppConfig.get('referencedString', stringType) == 'from-values'
                    }
                }
                """.trimIndent()
        )

        runBuild(projectDir = projectDir, "verifyExports")
    }

    /**
     * 验证未开启 BuildConfig 时聚合任务不会引用不存在的 AGP 任务.
     */
    @Test
    fun generateBuildConfig_whenFeatureDisabled_hasNoVariantDependencies() {
        val projectDir: File = createProject(
            config = "",
            buildConfigEnabled = false
        )

        val result: BuildResult = runBuild(
            projectDir = projectDir,
            "generateBuildConfig",
            "--dry-run"
        )

        assertTrue(result.output.contains(":generateBuildConfig SKIPPED"))
        assertFalse(result.output.contains(":generateDebugBuildConfig"))
        assertFalse(result.output.contains(":generateReleaseBuildConfig"))
    }

    /**
     * 验证多个 flavor dimension 使用 AGP 提供的完整 variant 名称挂接任务.
     */
    @Test
    fun generateBuildConfig_withMultipleFlavorDimensions_dependsOnCombinedVariants() {
        val projectDir: File = createProject(
            config =
                """
                [[environments]]
                name = "dev"
                """.trimIndent(),
            buildConfigEnabled = true,
            additionalBuildScript =
                """
                android {
                    flavorDimensions.add('distribution')
                    productFlavors.create('direct').dimension = 'distribution'
                    productFlavors.create('store').dimension = 'distribution'
                }
                """.trimIndent()
        )

        val result: BuildResult = runBuild(
            projectDir = projectDir,
            "generateBuildConfig",
            "--dry-run"
        )

        assertTrue(result.output.contains(":generateDevDirectDebugBuildConfig SKIPPED"))
        assertTrue(result.output.contains(":generateDevDirectReleaseBuildConfig SKIPPED"))
        assertTrue(result.output.contains(":generateDevStoreDebugBuildConfig SKIPPED"))
        assertTrue(result.output.contains(":generateDevStoreReleaseBuildConfig SKIPPED"))
    }

    /**
     * 验证 environment 名称包含空白时报告配置文件和结构路径.
     */
    @Test
    fun environmentName_withWhitespace_reportsConfigPath() {
        val projectDir: File = createProject(
            config =
                """
                [[environments]]
                name = " dev "
                """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(result.output.contains("app-config.toml"))
        assertTrue(
            result.output.contains(
                "environments[0].name must not contain whitespace"
            )
        )
        assertFalse(result.output.contains("ProductFlavor names cannot contain whitespace"))
    }

    /**
     * 验证 BuildConfig 字段名错误包含完整结构路径.
     */
    @Test
    fun buildConfigName_whenInvalid_reportsConfigPath() {
        val projectDir: File = createProject(
            config =
                """
                [default]
                buildConfig."INVALID-NAME" = true
                """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(
            result.output.contains(
                "default.buildConfig.INVALID-NAME has invalid BuildConfig field name"
            )
        )
    }

    /**
     * 验证非法 reference 包含使用引用的完整结构路径.
     */
    @Test
    fun reference_whenNamespaceUnsupported_reportsConfigPath() {
        val projectDir: File = createProject(
            config =
                """
                [values]
                applicationId = "com.example.test"

                [default]
                buildConfig.INVALID_REFERENCE = { reference = "exports.applicationId" }
                """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(
            result.output.contains(
                "Unsupported app config reference at " +
                    "default.buildConfig.INVALID_REFERENCE: exports.applicationId"
            )
        )
    }

    /**
     * 验证 exports 引用仍然只能指向 values.
     */
    @Test
    fun exportReference_whenNamespaceUnsupported_reportsExportPath() {
        val projectDir: File = createProject(
            config =
                """
                [exports]
                invalidReference = { reference = "exports.other" }
                """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(
            result.output.contains(
                "Unsupported app config reference at exports.invalidReference: exports.other"
            )
        )
    }

    /**
     * 验证 exports 引用不存在的 values 字段时报告导出路径.
     */
    @Test
    fun exportReference_whenValueMissing_reportsExportPath() {
        val projectDir: File = createProject(
            config =
                """
                [exports]
                missingReference = { reference = "values.missing" }
                """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir = projectDir, "help")

        assertTrue(
            result.output.contains(
                "Unknown app config reference at exports.missingReference: values.missing"
            )
        )
    }

    private fun createProject(
        config: String?,
        buildConfigEnabled: Boolean = false,
        configFilePath: String = "app-config.toml",
        selectedConfigFilePath: String? = null,
        additionalBuildScript: String = ""
    ): File {
        val projectDir: File = temporaryFolder.newFolder("fixture")
        File(projectDir, "settings.gradle").writeText(
            """
            rootProject.name = 'environment-plugin-functional-test'
            """.trimIndent()
        )
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.application'
                id 'com.whisper.prism'
            }

            $additionalBuildScript
            """.trimIndent()
        )
        File(projectDir, "gradle.properties").writeText(
            buildString {
                appendLine("fake.android.buildConfigEnabled=$buildConfigEnabled")
                if (selectedConfigFilePath != null) {
                    appendLine("prism.appConfig.file=$selectedConfigFilePath")
                }
            }
        )
        if (config != null) {
            val configFile: File = File(projectDir, configFilePath)
            check(configFile.parentFile.mkdirs() || configFile.parentFile.isDirectory)
            configFile.writeText(config)
        }
        return projectDir
    }

    private fun runBuild(projectDir: File, vararg arguments: String): BuildResult {
        return runner(projectDir = projectDir, arguments = arguments.toList()).build()
    }

    private fun runBuildAndFail(projectDir: File, vararg arguments: String): BuildResult {
        return runner(projectDir = projectDir, arguments = arguments.toList()).buildAndFail()
    }

    private fun runner(projectDir: File, arguments: List<String>): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath(pluginClasspath())
            .withArguments(arguments + "--stacktrace")
    }

    private fun pluginClasspath(): List<File> {
        val metadataFile: File = File(
            System.getProperty("user.dir"),
            "build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"
        )
        val properties: Properties = Properties()
        FileInputStream(metadataFile).use(properties::load)
        val implementationClasspath: List<File> = properties
            .getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
        return (implementationClasspath + listOf(
            File(System.getProperty("user.dir"), "build/classes/kotlin/test"),
            File(System.getProperty("user.dir"), "build/resources/test"),
            codeSourceFile(type = ApplicationAndroidComponentsExtension::class.java),
            codeSourceFile(type = Lint::class.java)
        )).distinct()
    }

    private fun codeSourceFile(type: Class<*>): File {
        val location = requireNotNull(type.protectionDomain.codeSource).location
        return File(location.toURI())
    }
}
