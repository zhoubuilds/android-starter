package com.whisper.habitat.compiler

import com.squareup.kotlinpoet.ClassName
import java.io.File
import java.net.URL
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout

/**
 * 验证 Habitat KSP 处理器的源码生成和错误处理行为.
 *
 * 测试使用真实 compiler JAR 和 KSP Gradle 插件, 不使用处理器内部实现的 Mock.
 *
 * @author whisper
 * @since 2026/07/28
 */
class HabitatCompilerFunctionalTest {

    @get:Rule
    val testTimeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证 property 类型数据库实例入口会生成延迟读取实例的 Dao 工厂.
     */
    @Test
    fun generatesProviderForPropertyInstanceAccessor() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @Dao
                interface UserDao

                class UserEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val providerSource: String = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").readText()
        val registrySource: String = generatedRegistryFile(projectDir).readText()
        assertTrue(providerSource.contains("UserDao::class to { AppDatabase.instance.userDao() }"))
        assertTrue(registrySource.contains("AppDatabaseHabitatDaoProvider()"))
    }

    /**
     * 验证 function 类型数据库实例入口会生成函数调用形式的 Dao 工厂.
     */
    @Test
    fun generatesProviderForFunctionInstanceAccessor() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @Dao
                interface UserDao

                class UserEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        fun instance(): AppDatabase = error("No test instance.")
                    }

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val providerSource: String = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").readText()
        assertTrue(providerSource.contains("UserDao::class to { AppDatabase.instance().userDao() }"))
    }

    /**
     * 验证 nullable 数据库实例入口会在 KSP 阶段失败.
     */
    @Test
    fun rejectsNullableInstanceAccessor() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @Dao
                interface UserDao

                class UserEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase? = null
                    }

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(
            result.output.contains(
                "@HabitatDatabaseInstance property must return a non-null com.example.database.AppDatabase."
            )
        )
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证同一个 Dao 不能注册到多个 Habitat 数据库.
     */
    @Test
    fun rejectsDuplicateDaoRegistrations() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @Dao
                interface UserDao

                class UserEntity
                class LogEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }

                    abstract fun userDao(): UserDao
                }

                @HabitatDatabase
                @Database(entities = [LogEntity::class], version = 1)
                abstract class LogDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: LogDatabase
                            get() = error("No test instance.")
                    }

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Dao com.example.database.UserDao is registered in multiple Habitat databases."))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    private fun createProject(source: String): File {
        val projectDir: File = temporaryFolder.newFolder("habitat-compiler-fixture")
        val processorJar: File = File(System.getProperty("user.dir"), "build/libs/habitat-compiler.jar")
        val kotlinPoetJar: File = codeSourceFile(ClassName::class.java)
        val kotlinVersion: String = requireNotNull(System.getProperty("habitat.test.kotlinVersion")) {
            "Missing habitat.test.kotlinVersion test system property."
        }
        val kspVersion: String = requireNotNull(System.getProperty("habitat.test.kspVersion")) {
            "Missing habitat.test.kspVersion test system property."
        }
        require(processorJar.isFile) {
            "Habitat compiler JAR was not built: ${processorJar.absolutePath}"
        }

        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "settings.gradle.kts",
            source = """
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

                rootProject.name = "habitat-compiler-functional-test"
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "build.gradle.kts",
            source = """
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
                }

                ksp {
                    arg("habitat.registryPackage", "com.example.habitat.generated")
                }
            """.trimIndent()
        )
        writeRuntimeStubs(projectDir)
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/database/AppDatabase.kt",
            source = source
        )
        return projectDir
    }

    private fun writeRuntimeStubs(projectDir: File) {
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/androidx/room/RoomStubs.kt",
            source = """
                package androidx.room

                import kotlin.reflect.KClass

                abstract class RoomDatabase

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.BINARY)
                annotation class Database(
                    val entities: Array<KClass<*>>,
                    val version: Int,
                    val exportSchema: Boolean = false
                )

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.BINARY)
                annotation class Dao
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/annotation/HabitatDatabase.kt",
            source = """
                package com.whisper.habitat.runtime.annotation

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                annotation class HabitatDatabase
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/annotation/HabitatDatabaseInstance.kt",
            source = """
                package com.whisper.habitat.runtime.annotation

                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                annotation class HabitatDatabaseInstance
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/registry/HabitatDaoProvider.kt",
            source = """
                package com.whisper.habitat.runtime.registry

                import kotlin.reflect.KClass

                interface HabitatDaoProvider {
                    val daoFactories: Map<KClass<*>, () -> Any>
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/registry/HabitatRegistry.kt",
            source = """
                package com.whisper.habitat.runtime.registry

                interface HabitatRegistry {
                    fun providers(): List<HabitatDaoProvider>
                }
            """.trimIndent()
        )
    }

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

    private fun runBuild(projectDir: File): BuildResult {
        return gradleRunner(projectDir).build()
    }

    private fun runBuildAndFail(projectDir: File): BuildResult {
        return gradleRunner(projectDir).buildAndFail()
    }

    private fun gradleRunner(projectDir: File): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("--stacktrace", ":compileKotlin")
            .forwardOutput()
    }

    private fun generatedRegistryFile(projectDir: File): File {
        return File(
            projectDir,
            "build/generated/ksp/main/kotlin/com/example/habitat/generated/GeneratedHabitatRegistry.kt"
        )
    }

    private fun generatedProviderFile(projectDir: File, fileName: String): File {
        return File(
            projectDir,
            "build/generated/ksp/main/kotlin/com/example/habitat/generated/providers/" +
                "com/example/database/$fileName.kt"
        )
    }

    private fun codeSourceFile(type: Class<*>): File {
        val location: URL = requireNotNull(type.protectionDomain.codeSource).location
        return File(location.toURI())
    }

    private fun String.escapeGradleString(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private companion object {

        /**
         * 单个功能测试的超时时间, 单位为秒.
         */
        private const val TEST_TIMEOUT_SECONDS: Long = 300L
    }
}
