package com.whisper.aster.gradle

import java.io.File
import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 验证 Aster 插件与 Android Gradle Plugin、KSP 的真实构建集成.
 *
 * @author whisper
 * @since 2026/07/21
 */
class AsterPluginFunctionalTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证 application 和 library Variant 都能生成 Manifest, 且 metadata name
     * 和 KSP 最终包名保持一致.
     */
    @Test
    fun registersApplicationAndLibraryVariants() {
        val projectDir: File = createRoot("app", "library")
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "app",
            moduleType = "application",
            namespace = "com.example.app",
            segment = "app"
        )
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "library",
            moduleType = "library",
            namespace = "com.example.library",
            segment = "library"
        )

        runBuild(
            projectDir,
            ":app:generateAsterDebugManifest",
            ":app:assertAsterKsp",
            ":library:generateAsterDebugManifest",
            ":library:assertAsterKsp"
        )

        assertManifest(
            projectDir = projectDir,
            moduleName = "app",
            registryPackage = "com.example.app.aster.generated"
        )
        assertManifest(
            projectDir = projectDir,
            moduleName = "library",
            registryPackage = "com.example.library.aster.generated"
        )
    }

    /**
     * 验证 application 缺少 KSP 时给出明确错误.
     */
    @Test
    fun reportsMissingKspPlugin() {
        val projectDir: File = createRoot("app")
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "app",
            moduleType = "application",
            namespace = "com.example.app",
            segment = "app",
            includeKsp = false
        )

        val result: BuildResult = runBuildAndFailWithoutKspApi(
            projectDir,
            ":app:generateAsterDebugManifest"
        )

        assertTrue(
            result.output.contains(
                "Aster requires the com.google.devtools.ksp plugin"
            )
        )
    }

    /**
     * 验证 library 缺少 segment 时给出明确错误.
     */
    @Test
    fun reportsMissingSegment() {
        val projectDir: File = createRoot("library")
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "library",
            moduleType = "library",
            namespace = "com.example.library",
            segment = null
        )

        val result: BuildResult = runBuildAndFail(
            projectDir,
            ":library:generateAsterDebugManifest"
        )

        assertTrue(
            result.output.contains(
                "Aster requires the aster.segment configuration"
            )
        )
    }

    /**
     * 验证同一 Gradle Build 中的源码模块不能声明相同 segment.
     */
    @Test
    fun reportsDuplicateSegmentsAcrossModules() {
        val projectDir: File = createRoot("app", "library")
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "app",
            moduleType = "application",
            namespace = "com.example.app",
            segment = "shared"
        )
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "library",
            moduleType = "library",
            namespace = "com.example.library",
            segment = "shared"
        )

        val result: BuildResult = runBuildAndFail(
            projectDir,
            ":app:generateAsterDebugManifest"
        )

        assertDuplicateSegmentError(result, "shared")
    }

    /**
     * 验证构建脚本变化使 Configuration Cache 失效后会重新执行 segment 校验.
     */
    @Test
    fun revalidatesSegmentsAfterConfigurationCacheInvalidation() {
        val projectDir: File = createRoot("app", "library")
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "app",
            moduleType = "application",
            namespace = "com.example.app",
            segment = "app"
        )
        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "library",
            moduleType = "library",
            namespace = "com.example.library",
            segment = "library"
        )

        runBuild(
            projectDir,
            "--configuration-cache",
            ":app:generateAsterDebugManifest"
        )

        writeAndroidModule(
            projectDir = projectDir,
            moduleName = "library",
            moduleType = "library",
            namespace = "com.example.library",
            segment = "app"
        )
        val result: BuildResult = runBuildAndFail(
            projectDir,
            "--configuration-cache",
            ":app:generateAsterDebugManifest"
        )

        assertDuplicateSegmentError(result, "app")
    }

    /**
     * 创建临时 Gradle 工程根目录和 Android 插件仓库配置.
     *
     * @param moduleNames 需要写入 settings 的子模块名称.
     * @return 临时工程根目录.
     */
    private fun createRoot(vararg moduleNames: String): File {
        val root: File = temporaryFolder.newFolder("fixture")
        File(root, "settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = 'aster-functional-test'
            ${moduleNames.joinToString(separator = "\n") { "include ':$it'" }}
            """.trimIndent()
        )
        File(root, "build.gradle").writeText("")
        return root
    }

    /**
     * 写入一个使用真实 Android 和 KSP 插件的测试模块.
     *
     * @param projectDir 临时工程根目录.
     * @param moduleName 模块名称.
     * @param moduleType Android application 或 library.
     * @param namespace Android namespace.
     * @param segment 可选的 aster 首段.
     * @param includeKsp 是否应用 KSP 插件.
     */
    private fun writeAndroidModule(
        projectDir: File,
        moduleName: String,
        moduleType: String,
        namespace: String,
        segment: String?,
        includeKsp: Boolean = true
    ) {
        val moduleDir: File = File(projectDir, moduleName).apply { mkdirs() }
        val kspPlugin: String = if (includeKsp) {
            "    id 'com.google.devtools.ksp'\n"
        } else {
            ""
        }
        val asterBlock: String = if (segment != null) {
            """

            aster {
                segment = '$segment'
            }
            """.trimIndent()
        } else {
            ""
        }
        val kspAssertionTask: String = if (includeKsp && segment != null) {
            val expectedPackage: String = registryPackage(namespace)
            """

            tasks.register('assertAsterKsp') {
                doLast {
                    def arguments = project.extensions.getByName('ksp').arguments
                    def actualPackage = arguments['aster.registryPackage']
                    if (actualPackage != '$expectedPackage') {
                        throw new GradleException(
                            "Expected aster.registryPackage '$expectedPackage' but was '${'$'}actualPackage'"
                        )
                    }
                }
            }
            """.trimIndent()
        } else {
            ""
        }

        File(moduleDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.$moduleType'
$kspPlugin                id 'com.whisper.aster'
            }

$asterBlock
$kspAssertionTask
            """.trimIndent()
        )
        File(moduleDir, "gradle.properties").writeText(
            "aster.test.namespace=$namespace\n"
        )
    }

    /**
     * 验证生成的 Manifest 同时包含 metadata name 和 Registry 全限定类名.
     *
     * @param projectDir 临时工程根目录.
     * @param moduleName 模块名称.
     * @param registryPackage Registry 包名.
     */
    private fun assertManifest(
        projectDir: File,
        moduleName: String,
        registryPackage: String
    ) {
        val manifestFile: File = File(
            projectDir,
            "$moduleName/build/generated/aster/debug/manifest/AndroidManifest.xml"
        )
        val manifest: String = manifestFile.readText()
        val qualifiedName: String = "$registryPackage.AsterGeneratedRegistry"
        assertTrue(manifestFile.isFile)
        assertTrue(manifest.contains("android:name=\"$REGISTRY_METADATA_PREFIX$qualifiedName\""))
        assertTrue(manifest.contains("android:value=\"$qualifiedName\""))
    }

    /**
     * 验证构建结果包含完整的重复 segment 诊断.
     *
     * @param result 预期因 segment 重复失败的构建结果.
     * @param segment 预期报告的重复 segment.
     */
    private fun assertDuplicateSegmentError(result: BuildResult, segment: String) {
        assertTrue(result.output.contains("Duplicate Aster segment '$segment'"))
        assertTrue(result.output.contains(":app"))
        assertTrue(result.output.contains(":library"))
        assertTrue(
            result.output.contains(
                "Each Aster module in the same Gradle build must declare a unique segment."
            )
        )
    }

    /**
     * 执行临时工程构建并要求成功.
     *
     * @param projectDir 临时工程根目录.
     * @param arguments Gradle 任务参数.
     * @return 构建结果.
     */
    private fun runBuild(projectDir: File, vararg arguments: String): BuildResult {
        return runner(projectDir, arguments.toList()).build()
    }

    /**
     * 执行临时工程构建并要求失败.
     *
     * @param projectDir 临时工程根目录.
     * @param arguments Gradle 任务参数.
     * @return 失败构建结果.
     */
    private fun runBuildAndFail(
        projectDir: File,
        vararg arguments: String
    ): BuildResult {
        return runner(projectDir, arguments.toList()).buildAndFail()
    }

    /**
     * 执行不提供 KSP API 的临时工程构建并要求失败.
     *
     * @param projectDir 测试工程根目录.
     * @param arguments Gradle 任务参数.
     * @return 构建结果.
     */
    private fun runBuildAndFailWithoutKspApi(
        projectDir: File,
        vararg arguments: String
    ): BuildResult {
        return runner(
            projectDir = projectDir,
            arguments = arguments.toList(),
            includeKspApi = false
        ).buildAndFail()
    }

    private fun runner(
        projectDir: File,
        arguments: List<String>,
        includeKspApi: Boolean = true
    ): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath(pluginClasspath(includeKspApi))
            .withArguments(listOf("--offline", "--stacktrace") + arguments)
            .forwardOutput()
    }

    /**
     * 组合被测插件、测试 KSP host stub 和 KSP 公开类型的 classpath.
     *
     * AGP 由临时工程的 Android 插件提供, 不重复加入插件 classpath, 避免 AGP 类型由
     * 不同 classloader 加载后导致 `findByType` 类型不匹配.
     *
     * @return TestKit 使用的插件 classpath.
     */
    private fun pluginClasspath(includeKspApi: Boolean): List<File> {
        val metadataFile: File = File(
            System.getProperty("user.dir"),
            "build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"
        )
        val properties: Properties = Properties()
        FileInputStream(metadataFile).use { input: FileInputStream ->
            properties.load(input)
        }
        val implementationClasspath: List<File> = properties
            .getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .map { path: String -> File(path) }
        val hostApiClasspath: List<File> = listOf(
            File(System.getProperty("user.dir"), "build/classes/kotlin/test"),
            File(System.getProperty("user.dir"), "build/resources/test"),
            codeSourceFile(ApplicationAndroidComponentsExtension::class.java)
        ) + if (includeKspApi) {
            listOf(codeSourceFile(KspExtension::class.java))
        } else {
            emptyList()
        }
        return implementationClasspath + hostApiClasspath
    }

    /**
     * 获取一个公开宿主 API 类型所在的 jar.
     *
     * @param type 宿主 API 类型.
     * @return 类型所在的 classpath 文件.
     */
    private fun codeSourceFile(type: Class<*>): File {
        val location = requireNotNull(type.protectionDomain.codeSource).location
        return File(location.toURI())
    }

    private fun registryPackage(namespace: String): String {
        return "$namespace.aster.generated"
    }

    private companion object {
        private const val REGISTRY_METADATA_PREFIX: String =
            "com.whisper.aster.runtime.registry."
    }
}
