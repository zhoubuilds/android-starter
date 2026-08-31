package com.whisper.habitat.gradle

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout

/**
 * 验证 Habitat 插件与真实 Android Gradle Plugin、KSP 和 Android 构建工具的集成.
 *
 * @author whisper
 * @since 2026/08/31
 */
class HabitatAndroidFunctionalTest {

    @get:Rule
    val testTimeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证 application 的 KSP 参数和 debug Variant Manifest 接线进入真实合并产物.
     */
    @Test
    fun configuresApplicationKspArgumentAndMergedManifest() {
        val projectDir: File = createAndroidProject(
            directoryName = "application",
            projectName = "habitat-application-functional-test",
            androidPluginId = "com.android.application",
            namespace = APPLICATION_NAMESPACE,
        )

        val result: BuildResult = runBuild(
            projectDir = projectDir,
            "verifyHabitatKspArgument",
            "processDebugMainManifest",
        )

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generateHabitatDebugManifest")?.outcome,
        )
        val manifest: String = mergedManifestFile(projectDir).readText()
        assertRegistryMetadata(manifest, APPLICATION_NAMESPACE)
    }

    /**
     * 验证 library 的 KSP 参数和 debug Variant Manifest 接线进入真实 AAR.
     */
    @Test
    fun configuresLibraryKspArgumentAndAarManifest() {
        val projectDir: File = createAndroidProject(
            directoryName = "library",
            projectName = "habitat-library-functional-test",
            androidPluginId = "com.android.library",
            namespace = LIBRARY_NAMESPACE,
        )

        val result: BuildResult = runBuild(
            projectDir = projectDir,
            "verifyHabitatKspArgument",
            "bundleDebugAar",
        )

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generateHabitatDebugManifest")?.outcome,
        )
        val aarFile: File = File(
            projectDir,
            "build/outputs/aar/habitat-library-functional-test-debug.aar",
        )
        val manifest: String = readZipEntry(aarFile, "AndroidManifest.xml")
        assertRegistryMetadata(manifest, LIBRARY_NAMESPACE)
    }

    /**
     * 验证两个外部 AAR 声明不同 Registry value 时由真实 Manifest Merger 阻断构建.
     */
    @Test
    fun failsManifestMergeForConflictingExternalAarRegistryMetadata() {
        val projectDir: File = createProjectRoot(
            directoryName = "manifest-conflict",
            projectName = "habitat-manifest-conflict-functional-test",
        )
        val firstAar: File = File(projectDir, "libs/first-registry.aar")
        val secondAar: File = File(projectDir, "libs/second-registry.aar")
        createAar(
            aarFile = firstAar,
            packageName = "com.example.habitat.first",
            registryClassName = "com.example.habitat.first.FirstRegistry",
        )
        createAar(
            aarFile = secondAar,
            packageName = "com.example.habitat.second",
            registryClassName = "com.example.habitat.second.SecondRegistry",
        )
        writeApplicationProject(
            projectDir = projectDir,
            namespace = CONFLICT_APPLICATION_NAMESPACE,
            aarFiles = listOf(firstAar, secondAar),
            minifyRelease = false,
        )

        val result: BuildResult = runBuildAndFail(
            projectDir = projectDir,
            "processDebugMainManifest",
        )

        assertTrue(result.output.contains(REGISTRY_METADATA_NAME))
        assertTrue(result.output.contains("FirstRegistry"))
        assertTrue(result.output.contains("SecondRegistry"))
    }

    /**
     * 验证 runtime AAR consumer rule 在真实 release R8 中保留 Registry 反射 ABI.
     */
    @Test
    fun keepsRegistryReflectionAbiAfterReleaseR8() {
        val projectDir: File = createProjectRoot(
            directoryName = "r8",
            projectName = "habitat-r8-functional-test",
        )
        val runtimeAar: File = File(projectDir, "libs/habitat-runtime.aar")
        createAar(
            aarFile = runtimeAar,
            packageName = "com.whisper.habitat.runtime.fixture",
            registryClassName = null,
            consumerRules = productionConsumerRulesFile().readText(),
        )
        writeApplicationProject(
            projectDir = projectDir,
            namespace = R8_APPLICATION_NAMESPACE,
            aarFiles = listOf(runtimeAar),
            minifyRelease = true,
        )
        writeFile(
            projectDir = projectDir,
            relativePath = "src/main/AndroidManifest.xml",
            content = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application>
                        <meta-data
                            android:name="$REGISTRY_METADATA_NAME"
                            android:value="$R8_REGISTRY_CLASS_NAME" />
                    </application>
                </manifest>
            """.trimIndent(),
        )
        writeRegistrySources(projectDir)

        val result: BuildResult = runBuild(projectDir, "assembleRelease")

        assertEquals(TaskOutcome.SUCCESS, result.task(":minifyReleaseWithR8")?.outcome)
        val mapping: String = File(
            projectDir,
            "build/outputs/mapping/release/mapping.txt",
        ).readText()
        val mappingHeader: String =
            "$R8_REGISTRY_CLASS_NAME -> $R8_REGISTRY_CLASS_NAME:"
        assertTrue(mapping.contains(mappingHeader))
        val registryMapping: String = mapping.lineSequence()
            .dropWhile { line: String -> line != mappingHeader }
            .drop(1)
            .takeWhile { line: String ->
                line.startsWith("    ") || line.startsWith("#")
            }
            .joinToString(separator = "\n")
        assertTrue(registryMapping.contains(" -> <init>"))
        assertTrue(registryMapping.contains(" -> providers"))

        val apkDirectory: File = File(projectDir, "build/outputs/apk/release")
        val apkFile: File = requireNotNull(
            apkDirectory.listFiles()?.singleOrNull { file: File -> file.extension == "apk" }
        ) {
            "Expected one release APK in ${apkDirectory.absolutePath}"
        }
        assertTrue(apkContainsClassDescriptor(apkFile, R8_REGISTRY_DESCRIPTOR))
    }

    private fun createAndroidProject(
        directoryName: String,
        projectName: String,
        androidPluginId: String,
        namespace: String,
    ): File {
        val projectDir: File = createProjectRoot(directoryName, projectName)
        val habitatClasspath: String = pluginImplementationClasspath()
            .joinToString(separator = ",\n") { file: File ->
                "                            \"${file.absolutePath.escapeGradleString()}\""
            }
        writeFile(
            projectDir = projectDir,
            relativePath = "build.gradle.kts",
            content = """
                import com.android.build.api.dsl.ApplicationExtension
                import com.android.build.api.dsl.LibraryExtension
                import com.google.devtools.ksp.gradle.KspExtension

                buildscript {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                    dependencies {
                        classpath("com.android.tools.build:gradle:$AGP_VERSION")
                        classpath(
                            "com.google.devtools.ksp:" +
                                "symbol-processing-gradle-plugin:$KSP_VERSION"
                        )
                        classpath(
                            files(
$habitatClasspath
                            )
                        )
                    }
                }

                apply(plugin = "$androidPluginId")
                apply(plugin = "com.google.devtools.ksp")
                apply(plugin = "com.whisper.habitat")

                if ("$androidPluginId" == "com.android.application") {
                    extensions.configure<ApplicationExtension> {
                        namespace = "$namespace"
                        compileSdk = $COMPILE_SDK
                        defaultConfig {
                            minSdk = $MIN_SDK
                        }
                    }
                } else {
                    extensions.configure<LibraryExtension> {
                        namespace = "$namespace"
                        compileSdk = $COMPILE_SDK
                        defaultConfig {
                            minSdk = $MIN_SDK
                        }
                    }
                }

                tasks.register("verifyHabitatKspArgument") {
                    doLast {
                        val arguments = project.extensions
                            .getByType(KspExtension::class.java)
                            .arguments
                        val actualPackage = arguments["habitat.registryPackage"]
                        check(actualPackage == "$namespace.habitat.generated") {
                            "Expected habitat.registryPackage for $namespace but was ${'$'}actualPackage"
                        }
                    }
                }
            """.trimIndent(),
        )
        writeFile(
            projectDir = projectDir,
            relativePath = "src/main/AndroidManifest.xml",
            content = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application />
                </manifest>
            """.trimIndent(),
        )
        return projectDir
    }

    private fun createProjectRoot(directoryName: String, projectName: String): File {
        val projectDir: File = temporaryFolder.newFolder(directoryName)
        writeFile(
            projectDir = projectDir,
            relativePath = "settings.gradle.kts",
            content = """
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

                rootProject.name = "$projectName"
            """.trimIndent(),
        )
        return projectDir
    }

    private fun writeApplicationProject(
        projectDir: File,
        namespace: String,
        aarFiles: List<File>,
        minifyRelease: Boolean,
    ) {
        val dependencies: String = aarFiles.joinToString(separator = "\n") { file: File ->
            "                    add(\"implementation\", files(" +
                "\"${file.absolutePath.escapeGradleString()}\"))"
        }
        val releaseConfiguration: String = if (minifyRelease) {
            """
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            """.trimIndent()
        } else {
            ""
        }
        writeFile(
            projectDir = projectDir,
            relativePath = "build.gradle.kts",
            content = """
                import com.android.build.api.dsl.ApplicationExtension

                buildscript {
                    repositories {
                        google()
                        mavenCentral()
                    }
                    dependencies {
                        classpath("com.android.tools.build:gradle:$AGP_VERSION")
                    }
                }

                apply(plugin = "com.android.application")

                extensions.configure<ApplicationExtension> {
                    namespace = "$namespace"
                    compileSdk = $COMPILE_SDK
                    defaultConfig {
                        applicationId = "$namespace"
                        minSdk = $MIN_SDK
                        targetSdk = $COMPILE_SDK
                        versionCode = 1
                        versionName = "1.0"
                    }
$releaseConfiguration
                }

                dependencies {
$dependencies
                }
            """.trimIndent(),
        )
        writeFile(
            projectDir = projectDir,
            relativePath = "src/main/AndroidManifest.xml",
            content = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application />
                </manifest>
            """.trimIndent(),
        )
        if (minifyRelease) {
            writeFile(projectDir, "proguard-rules.pro", "")
        }
    }

    private fun createAar(
        aarFile: File,
        packageName: String,
        registryClassName: String?,
        consumerRules: String? = null,
    ) {
        aarFile.parentFile.mkdirs()
        val metadata: String = registryClassName?.let { className: String ->
            """
                <application>
                    <meta-data
                        android:name="$REGISTRY_METADATA_NAME"
                        android:value="$className" />
                </application>
            """.trimIndent()
        } ?: ""
        val manifest: String = """
            <manifest
                xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName">
                $metadata
            </manifest>
        """.trimIndent()
        ZipOutputStream(FileOutputStream(aarFile)).use { output: ZipOutputStream ->
            writeZipEntry(output, "AndroidManifest.xml", manifest.toByteArray())
            writeZipEntry(output, "classes.jar", emptyJarBytes())
            if (consumerRules != null) {
                writeZipEntry(output, "proguard.txt", consumerRules.toByteArray())
            }
        }
    }

    private fun emptyJarBytes(): ByteArray {
        val file: File = temporaryFolder.newFile()
        JarOutputStream(FileOutputStream(file)).use { _: JarOutputStream -> Unit }
        return file.readBytes()
    }

    private fun writeZipEntry(
        output: ZipOutputStream,
        entryName: String,
        content: ByteArray,
    ) {
        output.putNextEntry(ZipEntry(entryName))
        output.write(content)
        output.closeEntry()
    }

    private fun writeRegistrySources(projectDir: File) {
        writeFile(
            projectDir = projectDir,
            relativePath = "src/main/java/com/whisper/habitat/runtime/registry/HabitatRegistry.java",
            content = """
                package com.whisper.habitat.runtime.registry;

                import java.util.List;

                public interface HabitatRegistry {
                    List<Object> providers();
                }
            """.trimIndent(),
        )
        writeFile(
            projectDir = projectDir,
            relativePath = "src/main/java/com/example/habitat/r8/GeneratedHabitatRegistry.java",
            content = """
                package com.example.habitat.r8;

                import com.whisper.habitat.runtime.registry.HabitatRegistry;
                import java.util.Collections;
                import java.util.List;

                public final class GeneratedHabitatRegistry implements HabitatRegistry {

                    public GeneratedHabitatRegistry() {
                    }

                    @Override
                    public List<Object> providers() {
                        return Collections.emptyList();
                    }
                }
            """.trimIndent(),
        )
    }

    private fun productionConsumerRulesFile(): File {
        val rulesFile: File = File(
            System.getProperty("user.dir"),
            "../habitat/habitat-runtime/consumer-rules.keep",
        ).canonicalFile
        require(rulesFile.isFile) {
            "Habitat runtime consumer rules were not found: ${rulesFile.absolutePath}"
        }
        return rulesFile
    }

    private fun apkContainsClassDescriptor(apkFile: File, descriptor: String): Boolean {
        val expectedBytes: ByteArray = descriptor.toByteArray()
        return ZipFile(apkFile).use { zipFile: ZipFile ->
            zipFile.entries().asSequence()
                .filter { entry: ZipEntry ->
                    entry.name.startsWith("classes") && entry.name.endsWith(".dex")
                }
                .any { entry: ZipEntry ->
                    zipFile.getInputStream(entry).use { input ->
                        input.readBytes().containsBytes(expectedBytes)
                    }
                }
        }
    }

    private fun ByteArray.containsBytes(expected: ByteArray): Boolean {
        if (expected.isEmpty() || expected.size > size) {
            return false
        }
        return indices.any { startIndex: Int ->
            startIndex + expected.size <= size &&
                expected.indices.all { offset: Int ->
                    this[startIndex + offset] == expected[offset]
                }
        }
    }

    private fun runBuild(
        projectDir: File,
        vararg arguments: String,
    ): BuildResult {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(gradleUserHomeDirectory())
            .withArguments(
                listOf(
                    "--offline",
                    "--stacktrace",
                    "--no-configuration-cache",
                ) + arguments,
            )
            .forwardOutput()
            .build()
    }

    private fun runBuildAndFail(
        projectDir: File,
        vararg arguments: String,
    ): BuildResult {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(gradleUserHomeDirectory())
            .withArguments(
                listOf(
                    "--offline",
                    "--stacktrace",
                    "--no-configuration-cache",
                ) + arguments,
            )
            .forwardOutput()
            .buildAndFail()
    }

    private fun gradleUserHomeDirectory(): File {
        val configuredDirectory: String? = System.getenv("GRADLE_USER_HOME")
        return if (configuredDirectory.isNullOrBlank()) {
            File(System.getProperty("user.home"), ".gradle")
        } else {
            File(configuredDirectory)
        }
    }

    private fun pluginImplementationClasspath(): List<File> {
        val metadataFile: File = File(
            System.getProperty("user.dir"),
            "build/pluginUnderTestMetadata/plugin-under-test-metadata.properties",
        )
        val properties: Properties = Properties()
        FileInputStream(metadataFile).use { input: FileInputStream ->
            properties.load(input)
        }
        return properties
            .getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .map { path: String -> File(path) }
    }

    private fun mergedManifestFile(projectDir: File): File {
        return projectDir.walkTopDown()
            .filter { file: File ->
                file.isFile &&
                    file.name == "AndroidManifest.xml" &&
                    file.invariantSeparatorsPath.contains("/build/intermediates/") &&
                    file.readText().contains(REGISTRY_METADATA_NAME)
            }
            .single()
    }

    private fun readZipEntry(archive: File, entryName: String): String {
        require(archive.isFile) {
            "Expected archive was not generated: ${archive.absolutePath}"
        }
        return java.util.zip.ZipFile(archive).use { zipFile: java.util.zip.ZipFile ->
            val entry = requireNotNull(zipFile.getEntry(entryName)) {
                "Missing $entryName in ${archive.absolutePath}"
            }
            zipFile.getInputStream(entry).bufferedReader().use { reader ->
                reader.readText()
            }
        }
    }

    private fun assertRegistryMetadata(manifest: String, namespace: String) {
        assertTrue(manifest.contains("android:name=\"$REGISTRY_METADATA_NAME\""))
        assertTrue(
            manifest.contains(
                "android:value=\"$namespace.habitat.generated.GeneratedHabitatRegistry\""
            )
        )
    }

    private fun writeFile(
        projectDir: File,
        relativePath: String,
        content: String,
    ) {
        File(projectDir, relativePath).apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    private fun String.escapeGradleString(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private companion object {

        private const val TEST_TIMEOUT_SECONDS: Long = 600L
        private const val AGP_VERSION: String = "9.2.1"
        private const val KSP_VERSION: String = "2.3.10"
        private const val COMPILE_SDK: Int = 37
        private const val MIN_SDK: Int = 24
        private const val REGISTRY_METADATA_NAME: String = "com.whisper.habitat.registry"
        private const val APPLICATION_NAMESPACE: String = "com.example.habitat.application"
        private const val LIBRARY_NAMESPACE: String = "com.example.habitat.library"
        private const val CONFLICT_APPLICATION_NAMESPACE: String =
            "com.example.habitat.conflict"
        private const val R8_APPLICATION_NAMESPACE: String = "com.example.habitat.r8"
        private const val R8_REGISTRY_CLASS_NAME: String =
            "com.example.habitat.r8.GeneratedHabitatRegistry"
        private const val R8_REGISTRY_DESCRIPTOR: String =
            "Lcom/example/habitat/r8/GeneratedHabitatRegistry;"
    }
}
