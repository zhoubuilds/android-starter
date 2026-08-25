package com.whisper.aster.compiler

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.kotlinpoet.ClassName
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout

/**
 * 验证 Aster KSP 处理器的源码生成和错误处理行为.
 *
 * 测试使用真实的 compiler JAR 和 KSP Gradle 插件, 不使用处理器内部实现的 Mock.
 *
 * @author whisper
 * @since 2026/07/22
 */
class AsterCompilerFunctionalTest {

    private companion object {

        /**
         * 单个功能测试的超时时间, 单位为秒.
         */
        const val TEST_TIMEOUT_SECONDS: Long = 300L
    }

    /**
     * 单个功能测试允许执行的最长时间.
     */
    @get:Rule
    val testTimeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    /**
     * 功能测试临时工程的根目录.
     */
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证非法路由路径会报告错误并使 Kotlin 编译失败.
     */
    @Test
    fun failsBuildForInvalidRoutePath() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature")
                class InvalidRouteActivity : Activity()
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Invalid @Route path '/feature'."))
        assertTrue(result.output.contains("Expected the format '/<segment>/<page>'."))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证非法能力名会报告错误并使 Kotlin 编译失败.
     */
    @Test
    fun failsBuildForInvalidCapabilityName() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable

                @Capable("feature")
                class InvalidCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Invalid @Capable name 'feature'."))
        assertTrue(result.output.contains("Expected at least two dot-separated segments;"))
    }

    /**
     * 验证路由和能力只接受可访问的普通非 inner class.
     */
    @Test
    fun failsBuildForUnsupportedRouteAndCapabilityDeclarations() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/object_route")
                object ObjectRouteActivity : Activity()

                class RouteContainer {
                    @Route("/feature/inner_route")
                    inner class InnerRouteActivity : Activity()

                    @Route("/feature/private_route")
                    private class PrivateRouteActivity : Activity()
                }

                @Capable("feature.object.capability")
                object ObjectCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                class CapabilityContainer {
                    @Capable("feature.inner.capability")
                    inner class InnerCapability : Capability {
                        override fun initialize(application: android.app.Application) = Unit
                    }

                    @Capable("feature.private.capability")
                    private class PrivateCapability : Capability {
                        override fun initialize(application: android.app.Application) = Unit
                    }

                    @Capable("feature.companion.capability")
                    companion object : Capability {
                        override fun initialize(application: android.app.Application) = Unit
                    }
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(
            result.output.contains(
                "@Route target 'com.example.ObjectRouteActivity' must be a regular class."
            )
        )
        assertTrue(
            result.output.contains(
                "@Route target 'com.example.RouteContainer.InnerRouteActivity' must be a " +
                    "regular non-inner class."
            )
        )
        assertTrue(
            result.output.contains(
                "@Route target 'com.example.RouteContainer.PrivateRouteActivity' is not " +
                    "accessible from the generated Registry."
            )
        )
        assertTrue(
            result.output.contains(
                "@Capable target 'com.example.ObjectCapability' must be a regular class."
            )
        )
        assertTrue(
            result.output.contains(
                "@Capable target 'com.example.CapabilityContainer.InnerCapability' must be a " +
                    "regular non-inner class."
            )
        )
        assertTrue(
            result.output.contains(
                "@Capable target 'com.example.CapabilityContainer.PrivateCapability' is not " +
                    "accessible from the generated Registry."
            )
        )
        assertTrue(
            result.output.contains(
                "@Capable target 'com.example.CapabilityContainer.Companion' must be a " +
                    "regular class."
            )
        )
    }

    /**
     * 验证不同 KSP 轮次发现的声明会被汇总到同一个完整 Registry.
     */
    @Test
    fun generatesCompleteRegistryAcrossKspRounds() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/first_round")
                class FirstRoundActivity : Activity()
            """.trimIndent(),
            includeRoundGenerator = true
        )

        runBuild(projectDir)

        val registryFile: File = generatedRegistryFile(projectDir)
        assertTrue(registryFile.isFile)
        val registrySource: String = registryFile.readText()
        assertTrue(
            registrySource.contains(
                "registrar.registerRoute(\"/feature/first_round\", " +
                    "FirstRoundActivity::class.java)"
            )
        )
        assertTrue(
            registrySource.contains(
                "registrar.registerCapability(\"feature.generated.capability\", " +
                    "GeneratedCapability::class.java, true)"
            )
        )
    }

    /**
     * 验证无注解模块仍会生成符合固定协议的空 Registry.
     */
    @Test
    fun generatesEmptyRegistryWithStableProtocol() {
        val projectDir: File = createProject(source = "package com.example")

        runBuild(projectDir)

        val registrySource: String = generatedRegistryFile(projectDir).readText()
        assertRegistryProtocol(registrySource)
        assertFalse(registrySource.contains("registrar.registerRoute("))
        assertFalse(registrySource.contains("registrar.registerCapability("))
    }

    /**
     * 验证 Registry 按名称稳定排序并保留能力的 singleton 配置.
     */
    @Test
    fun generatesRegistryEntriesInStableOrder() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable
                import com.whisper.aster.runtime.annotation.Route

                typealias FeatureActivityAlias = Activity
                typealias FeatureCapabilityAlias = Capability

                @Route("/feature/zeta")
                class ZetaActivity : Activity()

                @Capable(name = "feature.zeta.capability", singleton = false)
                class ZetaCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Route("/feature/alpha")
                class AlphaActivity : Activity()

                @Capable("feature.alpha.capability")
                class AlphaCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Route("/feature/type_alias")
                class TypeAliasActivity : FeatureActivityAlias()

                @Capable("feature.type_alias.capability")
                class TypeAliasCapability : FeatureCapabilityAlias {
                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val registrySource: String = generatedRegistryFile(projectDir).readText()
        val alphaRoute: String =
            "registrar.registerRoute(\"/feature/alpha\", AlphaActivity::class.java)"
        val typeAliasRoute: String =
            "registrar.registerRoute(\"/feature/type_alias\", TypeAliasActivity::class.java)"
        val zetaRoute: String =
            "registrar.registerRoute(\"/feature/zeta\", ZetaActivity::class.java)"
        val alphaCapability: String =
            "registrar.registerCapability(\"feature.alpha.capability\", " +
                "AlphaCapability::class.java, true)"
        val typeAliasCapability: String =
            "registrar.registerCapability(\"feature.type_alias.capability\", " +
                "TypeAliasCapability::class.java, true)"
        val zetaCapability: String =
            "registrar.registerCapability(\"feature.zeta.capability\", " +
                "ZetaCapability::class.java, false)"

        assertRegistryProtocol(registrySource)
        assertAppearsBefore(registrySource, alphaRoute, typeAliasRoute)
        assertAppearsBefore(registrySource, typeAliasRoute, zetaRoute)
        assertAppearsBefore(registrySource, zetaRoute, alphaCapability)
        assertAppearsBefore(registrySource, alphaCapability, typeAliasCapability)
        assertAppearsBefore(registrySource, typeAliasCapability, zetaCapability)
    }

    /**
     * 验证连续增量构建会处理注解的新增、修改和删除, 不保留过时条目.
     */
    @Test
    fun refreshesRegistryAcrossIncrementalBuilds() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/initial")
                class IncrementalActivity : Activity()
            """.trimIndent()
        )

        runBuild(projectDir)
        assertTrue(
            generatedRegistryFile(projectDir).readText()
                .contains("registrar.registerRoute(\"/feature/initial\"")
        )

        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/InvalidDeclaration.kt",
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/changed")
                class IncrementalActivity : Activity()
            """.trimIndent()
        )
        val addedCapabilityPath: String =
            "src/main/kotlin/com/example/AddedIncrementalCapability.kt"
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = addedCapabilityPath,
            source = """
                package com.example

                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable

                @Capable(name = "feature.incremental.capability", singleton = false)
                class AddedIncrementalCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val changedRegistrySource: String = generatedRegistryFile(projectDir).readText()
        assertFalse(changedRegistrySource.contains("/feature/initial"))
        assertTrue(changedRegistrySource.contains("/feature/changed"))
        assertTrue(changedRegistrySource.contains("feature.incremental.capability"))
        assertTrue(changedRegistrySource.contains("AddedIncrementalCapability::class.java, false"))

        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/InvalidDeclaration.kt",
            source = "package com.example\n\nclass PlainClass"
        )
        val addedCapabilityFile: File = File(projectDir, addedCapabilityPath)
        assertTrue(addedCapabilityFile.delete())

        runBuild(projectDir)

        val emptyRegistrySource: String = generatedRegistryFile(projectDir).readText()
        assertRegistryProtocol(emptyRegistrySource)
        assertFalse(emptyRegistrySource.contains("registrar.registerRoute("))
        assertFalse(emptyRegistrySource.contains("registrar.registerCapability("))
    }

    /**
     * 验证最终仍无法解析的声明会使构建失败且不生成部分 Registry.
     */
    @Test
    fun failsBuildWithoutRegistryForUnresolvedDeclaration() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.annotation.Capable
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/valid_route")
                class ValidRouteActivity : Activity()

                @Capable("feature.unresolved.capability")
                class UnresolvedCapability : MissingCapability()
            """.trimIndent()
        )

        runBuildAndFail(projectDir)

        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证声明错误、重复路由和重复能力会被完整报告, 且不会生成部分 Registry.
     */
    @Test
    fun reportsAllValidationErrorsWithoutRegistry() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import android.app.Activity
                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable
                import com.whisper.aster.runtime.annotation.Route

                @Route("/feature/duplicate")
                class FirstRouteActivity : Activity()

                @Route("/feature/duplicate")
                class SecondRouteActivity : Activity()

                @Route("/feature")
                class InvalidRouteActivity : Activity()

                @Capable("feature.duplicate.capability")
                class FirstCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.duplicate.capability")
                class SecondCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature")
                class InvalidCapability : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Invalid @Route path '/feature'."))
        assertTrue(result.output.contains("Invalid @Capable name 'feature'."))
        assertTrue(result.output.contains("Duplicate @Route path '/feature/duplicate'"))
        assertTrue(
            result.output.contains(
                "Duplicate @Capable name 'feature.duplicate.capability'"
            )
        )
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证公开 secondary、全默认参数和 Java public 无参构造均可用于能力实现.
     */
    @Test
    fun acceptsSupportedNoArgConstructorForms() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable

                @Capable("feature.secondary.capability")
                class SecondaryConstructorCapability private constructor(
                    private val value: String
                ) : Capability {
                    constructor() : this("secondary")

                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.defaults.capability")
                class DefaultParameterCapability(
                    private val value: String = "default"
                ) : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/java/com/example/JavaPublicConstructorCapability.java",
            source = """
                package com.example;

                import com.whisper.aster.runtime.Capability;
                import com.whisper.aster.runtime.annotation.Capable;

                @Capable(name = "feature.java.capability")
                public final class JavaPublicConstructorCapability implements Capability {
                    public JavaPublicConstructorCapability() {
                    }

                    @Override
                    public void initialize(android.app.Application application) {
                    }
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val registrySource: String = generatedRegistryFile(projectDir).readText()
        assertTrue(registrySource.contains("SecondaryConstructorCapability::class.java"))
        assertTrue(registrySource.contains("DefaultParameterCapability::class.java"))
        assertTrue(registrySource.contains("JavaPublicConstructorCapability::class.java"))
    }

    /**
     * 验证不可访问或需要必填参数的构造函数会使构建失败且不生成 Registry.
     */
    @Test
    fun rejectsUnsupportedNoArgConstructorForms() {
        val projectDir: File = createProject(
            source = """
                package com.example

                import com.whisper.aster.runtime.Capability
                import com.whisper.aster.runtime.annotation.Capable

                @Capable("feature.private.capability")
                class PrivateConstructorCapability private constructor() : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.protected.capability")
                class ProtectedConstructorCapability protected constructor() : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.internal.capability")
                class InternalConstructorCapability internal constructor() : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.required.capability")
                class RequiredParameterCapability(
                    private val value: String
                ) : Capability {
                    override fun initialize(application: android.app.Application) = Unit
                }

                @Capable("feature.secondary_defaults.capability")
                class DefaultSecondaryConstructorCapability private constructor(
                    private val value: String
                ) : Capability {
                    constructor(value: Int = 0) : this(value.toString())

                    override fun initialize(application: android.app.Application) = Unit
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/java/com/example/JavaPackageConstructorCapability.java",
            source = """
                package com.example;

                import com.whisper.aster.runtime.Capability;
                import com.whisper.aster.runtime.annotation.Capable;

                @Capable(name = "feature.java_package.capability")
                public final class JavaPackageConstructorCapability implements Capability {
                    JavaPackageConstructorCapability() {
                    }

                    @Override
                    public void initialize(android.app.Application application) {
                    }
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertConstructorError(result, "com.example.PrivateConstructorCapability")
        assertConstructorError(result, "com.example.ProtectedConstructorCapability")
        assertConstructorError(result, "com.example.InternalConstructorCapability")
        assertConstructorError(result, "com.example.RequiredParameterCapability")
        assertConstructorError(result, "com.example.DefaultSecondaryConstructorCapability")
        assertConstructorError(result, "com.example.JavaPackageConstructorCapability")
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 创建使用真实 Aster KSP 处理器的临时 Kotlin 工程.
     *
     * @param source 待编译的测试源码.
     * @param includeRoundGenerator 是否加入在后续轮次生成能力的测试处理器.
     * @return 临时工程目录.
     */
    private fun createProject(
        source: String,
        includeRoundGenerator: Boolean = false
    ): File {
        val projectDir: File = temporaryFolder.newFolder("aster-compiler-fixture")
        val processorJar: File = File(
            System.getProperty("user.dir"),
            "build/libs/aster-compiler.jar"
        )
        val kotlinPoetJar: File = codeSourceFile(ClassName::class.java)
        val symbolProcessingApiJar: File = codeSourceFile(SymbolProcessorProvider::class.java)
        val kotlinStdlibJar: File = codeSourceFile(KotlinVersion::class.java)
        val kotlinVersion: String = requireNotNull(
            System.getProperty("aster.test.kotlinVersion")
        ) {
            "Missing aster.test.kotlinVersion test system property."
        }
        val kspVersion: String = requireNotNull(
            System.getProperty("aster.test.kspVersion")
        ) {
            "Missing aster.test.kspVersion test system property."
        }
        val includedProjects: String = if (includeRoundGenerator) {
            "include(\":round-generator\")"
        } else {
            ""
        }
        val roundGeneratorDependency: String = if (includeRoundGenerator) {
            "ksp(project(\":round-generator\"))"
        } else {
            ""
        }
        require(processorJar.isFile) {
            "Aster compiler JAR was not built: ${processorJar.absolutePath}"
        }

        File(projectDir, "settings.gradle.kts").writeText(
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
                        mavenCentral()
                    }
                }

                rootProject.name = "aster-compiler-functional-test"
                $includedProjects
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                    id("com.google.devtools.ksp") version "$kspVersion"
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    ksp(
                        files(
                            "${processorJar.absolutePath.escapeGradleString()}",
                            "${kotlinPoetJar.absolutePath.escapeGradleString()}"
                        )
                    )
                    $roundGeneratorDependency
                }

                ksp {
                    arg("aster.segment", "feature")
                    arg("aster.registryPackage", "com.example.aster.generated")
                }
            """.trimIndent()
        )

        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/android/app/Activity.kt",
            source = "package android.app\n\nopen class Activity"
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/android/app/Application.kt",
            source = "package android.app\n\nopen class Application"
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/aster/runtime/annotation/Route.kt",
            source = """
                package com.whisper.aster.runtime.annotation

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.BINARY)
                annotation class Route(val path: String)
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/aster/runtime/annotation/Capable.kt",
            source = """
                package com.whisper.aster.runtime.annotation

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.BINARY)
                annotation class Capable(val name: String, val singleton: Boolean = true)
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/aster/runtime/Capability.kt",
            source = """
                package com.whisper.aster.runtime

                import android.app.Application

                interface Capability {
                    fun initialize(application: Application)
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/aster/runtime/registry/AsterRegistrar.kt",
            source = """
                package com.whisper.aster.runtime.registry

                import android.app.Activity
                import com.whisper.aster.runtime.Capability

                interface AsterRegistrar {
                    fun registerRoute(path: String, activityClass: Class<out Activity>)
                    fun registerCapability(
                        name: String,
                        implClass: Class<out Capability>,
                        singleton: Boolean
                    )
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath =
                "src/main/kotlin/com/whisper/aster/runtime/registry/AsterRegistryInstaller.kt",
            source = """
                package com.whisper.aster.runtime.registry

                interface AsterRegistryInstaller {
                    fun install(registrar: AsterRegistrar)
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/InvalidDeclaration.kt",
            source = source
        )
        if (includeRoundGenerator) {
            writeRoundGenerator(projectDir, symbolProcessingApiJar, kotlinStdlibJar)
        }
        return projectDir
    }

    /**
     * 创建用于验证 KSP 多轮处理的测试 Processor 模块.
     *
     * @param projectDir 临时工程目录.
     * @param symbolProcessingApiJar KSP Symbol Processing API JAR.
     * @param kotlinStdlibJar KSP API 编译所需的 Kotlin 标准库 JAR.
     */
    private fun writeRoundGenerator(
        projectDir: File,
        symbolProcessingApiJar: File,
        kotlinStdlibJar: File
    ) {
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "round-generator/build.gradle.kts",
            source = """
                plugins {
                    `java-library`
                }

                dependencies {
                    compileOnly(
                        files(
                            "${symbolProcessingApiJar.absolutePath.escapeGradleString()}",
                            "${kotlinStdlibJar.absolutePath.escapeGradleString()}"
                        )
                    )
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "round-generator/src/main/java/com/example/generator/" +
                "RoundGeneratingProcessorProvider.java",
            source = """
                package com.example.generator;

                import com.google.devtools.ksp.processing.CodeGenerator;
                import com.google.devtools.ksp.processing.Dependencies;
                import com.google.devtools.ksp.processing.Resolver;
                import com.google.devtools.ksp.processing.SymbolProcessor;
                import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
                import com.google.devtools.ksp.processing.SymbolProcessorProvider;
                import com.google.devtools.ksp.symbol.KSAnnotated;
                import com.google.devtools.ksp.symbol.KSFile;
                import java.io.IOException;
                import java.io.OutputStreamWriter;
                import java.nio.charset.StandardCharsets;
                import java.util.Collections;
                import java.util.List;

                public final class RoundGeneratingProcessorProvider
                        implements SymbolProcessorProvider {
                    @Override
                    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
                        return new RoundGeneratingProcessor(environment.getCodeGenerator());
                    }

                    private static final class RoundGeneratingProcessor
                            implements SymbolProcessor {
                        private final CodeGenerator codeGenerator;
                        private boolean generated;

                        private RoundGeneratingProcessor(CodeGenerator codeGenerator) {
                            this.codeGenerator = codeGenerator;
                        }

                        @Override
                        public List<KSAnnotated> process(Resolver resolver) {
                            if (generated) {
                                return Collections.emptyList();
                            }
                            Dependencies dependencies = new Dependencies(false, new KSFile[0]);
                            try (OutputStreamWriter writer = new OutputStreamWriter(
                                    codeGenerator.createNewFile(
                                            dependencies,
                                            "com.example",
                                            "GeneratedCapability",
                                            "kt"
                                    ),
                                    StandardCharsets.UTF_8
                            )) {
                                writer.write(
                                        "package com.example\n\n" +
                                        "import com.whisper.aster.runtime.Capability\n" +
                                        "import com.whisper.aster.runtime.annotation.Capable\n\n" +
                                        "@Capable(\"feature.generated.capability\")\n" +
                                        "class GeneratedCapability : Capability {\n" +
                                        "    override fun initialize(" +
                                        "application: android.app.Application) = Unit\n" +
                                        "}\n"
                                );
                            } catch (IOException exception) {
                                throw new IllegalStateException(
                                        "Failed to generate test capability.",
                                        exception
                                );
                            }
                            generated = true;
                            return Collections.emptyList();
                        }
                    }
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "round-generator/src/main/resources/META-INF/services/" +
                "com.google.devtools.ksp.processing.SymbolProcessorProvider",
            source = "com.example.generator.RoundGeneratingProcessorProvider"
        )
    }

    /**
     * 写入临时工程中的 Kotlin 源文件.
     *
     * @param projectDir 临时工程目录.
     * @param relativePath 源文件相对于工程目录的路径.
     * @param source 源文件内容.
     */
    private fun writeFixtureSource(
        projectDir: File,
        relativePath: String,
        source: String
    ) {
        File(projectDir, relativePath).apply {
            parentFile.mkdirs()
            writeText(source)
        }
    }

    /**
     * 执行临时工程编译并要求失败.
     *
     * @param projectDir 临时工程目录.
     * @return 失败构建结果.
     */
    private fun runBuildAndFail(projectDir: File): BuildResult {
        return gradleRunner(projectDir).buildAndFail()
    }

    /**
     * 执行临时工程编译并要求成功.
     *
     * @param projectDir 临时工程目录.
     * @return 成功构建结果.
     */
    private fun runBuild(projectDir: File): BuildResult {
        return gradleRunner(projectDir).build()
    }

    /**
     * 创建执行临时工程编译的 GradleRunner.
     *
     * @param projectDir 临时工程目录.
     * @return 已完成公共配置的 GradleRunner.
     */
    private fun gradleRunner(projectDir: File): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("--stacktrace", ":compileKotlin")
            .forwardOutput()
    }

    private fun generatedRegistryFile(projectDir: File): File {
        return File(
            projectDir,
            "build/generated/ksp/main/kotlin/com/example/aster/generated/" +
                "AsterGeneratedRegistry.kt"
        )
    }

    /**
     * 断言生成类遵循 Runtime 和 Manifest 依赖的固定 Registry 协议.
     *
     * @param registrySource 生成的 Registry Kotlin 源码.
     */
    private fun assertRegistryProtocol(registrySource: String) {
        assertTrue(registrySource.startsWith("package com.example.aster.generated\n"))
        assertTrue(registrySource.contains("@author aster"))
        assertTrue(
            registrySource.contains(
                "public class AsterGeneratedRegistry : AsterRegistryInstaller"
            )
        )
        assertEquals(
            1,
            registrySource.lineSequence().count { line: String ->
                line.contains(
                    "public override fun install(registrar: AsterRegistrar)"
                )
            }
        )
    }

    /**
     * 断言两段生成代码均存在且第一段稳定出现在第二段之前.
     *
     * @param source 完整生成源码.
     * @param first 应先出现的代码.
     * @param second 应后出现的代码.
     */
    private fun assertAppearsBefore(source: String, first: String, second: String) {
        val firstIndex: Int = source.indexOf(first)
        val secondIndex: Int = source.indexOf(second)
        assertTrue("Missing generated code: $first", firstIndex >= 0)
        assertTrue("Missing generated code: $second", secondIndex >= 0)
        assertTrue("Generated code is not in stable order.", firstIndex < secondIndex)
    }

    private fun assertConstructorError(result: BuildResult, qualifiedName: String) {
        assertTrue(
            result.output.contains(
                "@Capable class '$qualifiedName' must provide a public no-argument constructor."
            )
        )
    }

    /**
     * 获取指定类型所在的插件 JAR.
     *
     * @param type 插件实现或扩展类型.
     * @return 类型所在的 JAR 文件.
     */
    private fun codeSourceFile(type: Class<*>): File {
        val location = requireNotNull(type.protectionDomain.codeSource).location
        return File(location.toURI())
    }

    private fun String.escapeGradleString(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
