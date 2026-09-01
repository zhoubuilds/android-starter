package com.whisper.habitat.compiler

import com.squareup.kotlinpoet.ClassName
import java.io.File
import java.net.URL
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
        assertTrue(providerSource.contains("null to { AppDatabase.instance.userDao() }"))
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
        assertTrue(providerSource.contains("null to { AppDatabase.instance().userDao() }"))
    }

    /**
     * 验证 internal 数据库、实例入口、Dao accessor 和 Dao 对同模块生成代码可见.
     */
    @Test
    fun generatesProviderForInternalDeclarations() {
        val projectDir: File = createValidationProject(
            daoDeclaration = "internal interface UserDao",
            databaseDeclaration = "internal abstract class AppDatabase",
            instanceAccessor = """
                @HabitatDatabaseInstance
                internal val instance: AppDatabase
                    get() = error("No test instance.")
            """.trimIndent(),
            daoAccessor = "internal abstract fun userDao(): UserDao",
        )

        runBuild(projectDir)

        val providerSource: String = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").readText()
        assertTrue(providerSource.contains("null to { AppDatabase.instance.userDao() }"))
    }

    /**
     * 验证不可访问的数据库在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsInaccessibleDatabase() {
        val projectDir: File = createValidationProject(
            databaseDeclaration = "private abstract class AppDatabase",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat database com.example.database.AppDatabase and its containing declarations " +
                "must be public or internal.",
        )
    }

    /**
     * 验证不可访问的 companion object 会使实例入口在 KSP 阶段失败.
     */
    @Test
    fun rejectsInstanceAccessorInInaccessibleCompanionObject() {
        val projectDir: File = createValidationProject(
            companionDeclaration = "private companion object",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDatabaseInstance property and its containing declarations " +
                "must be public or internal.",
        )
    }

    /**
     * 验证 extension property 不能作为数据库实例入口.
     */
    @Test
    fun rejectsExtensionPropertyInstanceAccessor() {
        val projectDir: File = createValidationProject(
            additionalDeclarations = "class InstanceReceiver",
            instanceAccessor = """
                @HabitatDatabaseInstance
                val InstanceReceiver.instance: AppDatabase
                    get() = error("No test instance.")
            """.trimIndent(),
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDatabaseInstance property must not have an extension receiver.",
        )
    }

    /**
     * 验证 extension function 不能作为数据库实例入口.
     */
    @Test
    fun rejectsExtensionFunctionInstanceAccessor() {
        val projectDir: File = createValidationProject(
            additionalDeclarations = "class InstanceReceiver",
            instanceAccessor = """
                @HabitatDatabaseInstance
                fun InstanceReceiver.instance(): AppDatabase = error("No test instance.")
            """.trimIndent(),
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDatabaseInstance function must not have an extension receiver.",
        )
    }

    /**
     * 验证 suspend function 不能作为数据库实例入口.
     */
    @Test
    fun rejectsSuspendFunctionInstanceAccessor() {
        val projectDir: File = createValidationProject(
            instanceAccessor = """
                @HabitatDatabaseInstance
                suspend fun instance(): AppDatabase = error("No test instance.")
            """.trimIndent(),
        )

        val result: BuildResult = assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDatabaseInstance function must not be suspend.",
        )
        assertFalse(
            result.output.contains(
                "Habitat database com.example.database.AppDatabase must declare a companion object property or " +
                    "function annotated with @HabitatDatabaseInstance."
            )
        )
    }

    /**
     * 验证无效入口不会影响其它数据库报告真正缺失的实例入口.
     */
    @Test
    fun reportsMissingInstanceAccessorIndependentlyFromInvalidAccessor() {
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
                abstract class InvalidDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        suspend fun instance(): InvalidDatabase = error("No test instance.")
                    }

                    abstract fun userDao(): UserDao
                }

                @HabitatDatabase
                @Database(entities = [LogEntity::class], version = 1)
                abstract class MissingDatabase : RoomDatabase() {

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)
        val invalidDatabaseMissingMessage: String =
            "Habitat database com.example.database.InvalidDatabase must declare a companion object property or " +
                "function annotated with @HabitatDatabaseInstance."
        val missingDatabaseMessage: String =
            "Habitat database com.example.database.MissingDatabase must declare a companion object property or " +
                "function annotated with @HabitatDatabaseInstance."

        assertTrue(result.output.contains("@HabitatDatabaseInstance function must not be suspend."))
        assertFalse(result.output.contains(invalidDatabaseMissingMessage))
        assertTrue(result.output.contains(missingDatabaseMessage))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证泛型 function 不能作为数据库实例入口.
     */
    @Test
    fun rejectsGenericFunctionInstanceAccessor() {
        val projectDir: File = createValidationProject(
            instanceAccessor = """
                @HabitatDatabaseInstance
                fun <T> instance(): AppDatabase = error("No test instance.")
            """.trimIndent(),
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDatabaseInstance function must not declare type parameters.",
        )
    }

    /**
     * 验证 protected Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsProtectedDaoAccessor() {
        val projectDir: File = createValidationProject(
            daoAccessor = "protected abstract fun userDao(): UserDao",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao and its containing declarations must be public or internal.",
        )
    }

    /**
     * 验证带参数的抽象 Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsParameterizedDaoAccessor() {
        val projectDir: File = createValidationProject(
            daoAccessor = "abstract fun userDao(id: Int): UserDao",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao must not declare parameters.",
        )
    }

    /**
     * 验证 extension Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsExtensionDaoAccessor() {
        val projectDir: File = createValidationProject(
            additionalDeclarations = "class DaoReceiver",
            daoAccessor = "abstract fun DaoReceiver.userDao(): UserDao",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao must not have an extension receiver.",
        )
    }

    /**
     * 验证 suspend Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsSuspendDaoAccessor() {
        val projectDir: File = createValidationProject(
            daoAccessor = "abstract suspend fun userDao(): UserDao",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao must not be suspend.",
        )
    }

    /**
     * 验证泛型 Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsGenericDaoAccessor() {
        val projectDir: File = createValidationProject(
            daoAccessor = "abstract fun <T> userDao(): UserDao",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao must not declare type parameters.",
        )
    }

    /**
     * 验证 nullable Dao accessor 在 KSP 阶段直接失败.
     */
    @Test
    fun rejectsNullableDaoAccessor() {
        val projectDir: File = createValidationProject(
            daoAccessor = "abstract fun userDao(): UserDao?",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "Habitat Dao accessor userDao must return a non-null Dao.",
        )
    }

    /**
     * 验证数据库从父类继承的 Dao accessor 会进入生成 Provider.
     */
    @Test
    fun generatesProviderForInheritedDaoAccessor() {
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

                @Dao
                interface OrderDao

                class UserEntity
                class OrderEntity

                abstract class BaseDatabase<D : Any> : RoomDatabase() {

                    abstract fun userDao(): D
                }

                interface OrderDaoAccessors {

                    fun orderDao(): OrderDao
                }

                @HabitatDatabase
                @Database(entities = [UserEntity::class, OrderEntity::class], version = 1)
                abstract class AppDatabase : BaseDatabase<UserDao>(), OrderDaoAccessors {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val providerSource: String = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").readText()
        assertTrue(providerSource.contains("null to { AppDatabase.instance.userDao() }"))
        assertTrue(providerSource.contains("null to { AppDatabase.instance.orderDao() }"))
    }

    /**
     * 验证继承 accessor 源文件变化时会在增量构建中刷新 Provider.
     */
    @Test
    fun regeneratesProviderWhenInheritedAccessorSourceChanges() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Database
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                class UserEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : BaseDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }
                }
            """.trimIndent()
        )
        val baseDatabasePath: String = "src/main/kotlin/com/example/database/BaseDatabase.kt"
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = baseDatabasePath,
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.RoomDatabase

                @Dao
                interface UserDao

                abstract class BaseDatabase : RoomDatabase() {

                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val providerFile: File = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider")
        assertTrue(providerFile.readText().contains("null to { AppDatabase.instance.userDao() }"))

        writeFixtureSource(
            projectDir = projectDir,
            relativePath = baseDatabasePath,
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.RoomDatabase

                @Dao
                interface UserDao

                @Dao
                interface OrderDao

                abstract class BaseDatabase : RoomDatabase() {

                    abstract fun userDao(): UserDao

                    abstract fun orderDao(): OrderDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val updatedProviderSource: String = providerFile.readText()
        assertTrue(updatedProviderSource.contains("null to { AppDatabase.instance.userDao() }"))
        assertTrue(updatedProviderSource.contains("null to { AppDatabase.instance.orderDao() }"))
    }

    /**
     * 验证子类 override 继承的 Dao accessor 时只生成一个 Dao 工厂.
     */
    @Test
    fun generatesSingleFactoryForOverriddenDaoAccessor() {
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

                abstract class BaseDatabase : RoomDatabase() {

                    abstract fun userDao(): UserDao
                }

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : BaseDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }

                    abstract override fun userDao(): UserDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val providerSource: String = generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").readText()
        val factoryCount: Int = Regex("UserDao::class to").findAll(providerSource).count()
        assertEquals(1, factoryCount)
    }

    /**
     * 验证编译依赖中的父 accessor 已声明 qualifier 时, 子 override 不能静默丢失该声明.
     */
    @Test
    fun rejectsMissingBindingOnOverrideFromCompiledDependency() {
        val projectDir: File = createCompiledParentAccessorProject(childBinding = "")

        val result: BuildResult = runBuildAndFail(projectDir, task = ":app:compileKotlin")

        assertTrue(
            result.output.contains(
                "Habitat Dao accessor userDao overrides an accessor annotated with @HabitatDaoBinding. " +
                    "Qualifiers are not inherited; repeat @HabitatDaoBinding on the most-derived accessor."
            )
        )
        assertTrue(result.output.contains("AppDatabase.kt"))
        assertFalse(generatedRegistryFile(File(projectDir, "app")).exists())
    }

    /**
     * 验证子 override 重复编译依赖父 accessor 的 qualifier 后正常生成绑定.
     */
    @Test
    fun generatesBindingForAnnotatedOverrideFromCompiledDependency() {
        val projectDir: File = createCompiledParentAccessorProject(
            childBinding = "@HabitatDaoBinding(\"user.account\")",
        )

        runBuild(projectDir, task = ":app:compileKotlin")

        val providerSource: String = generatedProviderFile(
            File(projectDir, "app"),
            "AppDatabaseHabitatDaoProvider",
        ).readText()
        assertTrue(providerSource.contains("\"user.account\" to { AppDatabase.instance.userDao() }"))
    }

    /**
     * 验证继承的 Dao accessor 仍参与多绑定显式限定校验.
     */
    @Test
    fun rejectsDuplicateInheritedDaoRegistrations() {
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

                abstract class BaseDatabase : RoomDatabase() {

                    abstract fun userDao(): UserDao
                }

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : BaseDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }
                }

                @HabitatDatabase
                @Database(entities = [LogEntity::class], version = 1)
                abstract class LogDatabase : BaseDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: LogDatabase
                            get() = error("No test instance.")
                    }
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Dao com.example.database.UserDao has multiple Habitat accessors."))
        assertFalse(generatedRegistryFile(projectDir).exists())
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
        assertFalse(
            result.output.contains(
                "Habitat database com.example.database.AppDatabase must declare a companion object property or " +
                    "function annotated with @HabitatDatabaseInstance."
            )
        )
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证同一个 Dao 注册到多个数据库时不能省略绑定注解.
     */
    @Test
    fun rejectsUnqualifiedDuplicateDaoRegistrations() {
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
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/database/LogDatabase.kt",
            source = """
                package com.example.database

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

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
        val conflictMessage: String =
            "Dao com.example.database.UserDao has multiple Habitat accessors. Every accessor must declare " +
                "@HabitatDaoBinding with a unique qualifier. Conflicting accessors: " +
                "com.example.database.AppDatabase.userDao, com.example.database.LogDatabase.userDao."

        assertEquals(2, Regex(Regex.escape(conflictMessage)).findAll(result.output).count())
        assertTrue(result.output.contains("AppDatabase.kt:"))
        assertTrue(result.output.contains("LogDatabase.kt:"))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证同一个 Dao 可以通过不同限定符绑定到多个 Habitat 数据库.
     */
    @Test
    fun generatesQualifiedDuplicateDaoRegistrations() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDaoBinding
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

                    @HabitatDaoBinding("user.account")
                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/database/LogDatabase.kt",
            source = """
                package com.example.database

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDaoBinding
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @HabitatDatabase
                @Database(entities = [LogEntity::class], version = 1)
                abstract class LogDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: LogDatabase
                            get() = error("No test instance.")
                    }

                    @HabitatDaoBinding("user.archive")
                    abstract fun userDao(): UserDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        val appProvider: String = generatedProviderFile(
            projectDir,
            "AppDatabaseHabitatDaoProvider",
        ).readText()
        val logProvider: String = generatedProviderFile(
            projectDir,
            "LogDatabaseHabitatDaoProvider",
        ).readText()
        assertTrue(appProvider.contains("\"user.account\" to { AppDatabase.instance.userDao() }"))
        assertTrue(logProvider.contains("\"user.archive\" to { LogDatabase.instance.userDao() }"))
    }

    /**
     * 验证真实 Room compiler 与 Habitat 可以共同处理同一 Dao 跨数据库限定绑定.
     */
    @Test
    fun compilesQualifiedBindingsWithRealRoomProcessor() {
        val projectDir: File = createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.Entity
                import androidx.room.PrimaryKey
                import androidx.room.Query
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDaoBinding
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @Entity
                data class UserEntity(
                    @PrimaryKey val id: Long,
                )

                @Dao
                interface UserDao {

                    @Query("SELECT * FROM UserEntity")
                    suspend fun users(): List<UserEntity>
                }

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1, exportSchema = false)
                abstract class AccountDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AccountDatabase
                            get() = error("No test instance.")
                    }

                    @HabitatDaoBinding("user.account")
                    abstract fun userDao(): UserDao
                }

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1, exportSchema = false)
                abstract class ArchiveDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: ArchiveDatabase
                            get() = error("No test instance.")
                    }

                    @HabitatDaoBinding("user.archive")
                    abstract fun userDao(): UserDao
                }
            """.trimIndent(),
            roomVersion = requireNotNull(System.getProperty("habitat.test.roomVersion")) {
                "Missing habitat.test.roomVersion test system property."
            },
        )

        runBuild(projectDir)

        val accountProvider: String = generatedProviderFile(
            projectDir,
            "AccountDatabaseHabitatDaoProvider",
        ).readText()
        val archiveProvider: String = generatedProviderFile(
            projectDir,
            "ArchiveDatabaseHabitatDaoProvider",
        ).readText()
        assertTrue(accountProvider.contains("\"user.account\" to { AccountDatabase.instance.userDao() }"))
        assertTrue(archiveProvider.contains("\"user.archive\" to { ArchiveDatabase.instance.userDao() }"))
        assertTrue(
            File(
                projectDir,
                "build/generated/ksp/main/kotlin/com/example/database/AccountDatabase_Impl.kt",
            ).isFile
        )
    }

    /**
     * 验证多绑定 Dao 不能混用显式和省略的绑定注解.
     */
    @Test
    fun rejectsMixedQualifiedAndUnqualifiedDaoRegistrations() {
        val projectDir: File = createProject(
            source = duplicateDaoProjectSource(
                appBinding = "@HabitatDaoBinding(\"user.account\")",
                logBinding = "",
            )
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(result.output.contains("Dao com.example.database.UserDao has multiple Habitat accessors."))
        assertTrue(result.output.contains("Every accessor must declare @HabitatDaoBinding with a unique qualifier."))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证同一个 Dao 的限定符不能重复.
     */
    @Test
    fun rejectsDuplicateDaoQualifiers() {
        val projectDir: File = createProject(
            source = duplicateDaoProjectSource(
                appBinding = "@HabitatDaoBinding(\"user.account\")",
                logBinding = "@HabitatDaoBinding(\"user.account\")",
            )
        )

        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(
            result.output.contains(
                "Dao com.example.database.UserDao uses duplicate Habitat qualifier 'user.account'."
            )
        )
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    /**
     * 验证绑定限定符不能为空白字符串.
     */
    @Test
    fun rejectsBlankDaoQualifier() {
        val projectDir: File = createValidationProject(
            daoAccessor = """
                @HabitatDaoBinding("   ")
                abstract fun userDao(): UserDao
            """.trimIndent(),
            additionalImports = "import com.whisper.habitat.runtime.annotation.HabitatDaoBinding",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage = "@HabitatDaoBinding qualifier must not be blank.",
        )
    }

    /**
     * 验证未标记有效 Dao accessor 的 HabitatDaoBinding 会在 KSP 阶段失败.
     */
    @Test
    fun rejectsBindingOutsideRegisteredDaoAccessor() {
        val projectDir: File = createValidationProject(
            additionalDeclarations = """
                @HabitatDaoBinding("user.account")
                fun storageName(): String = "account"
            """.trimIndent(),
            additionalImports = "import com.whisper.habitat.runtime.annotation.HabitatDaoBinding",
        )

        assertValidationFailure(
            projectDir = projectDir,
            expectedMessage =
                "@HabitatDaoBinding can only annotate a supported Dao accessor in a @HabitatDatabase inheritance " +
                    "hierarchy.",
        )
    }

    /**
     * 验证同一个 Entity 可以由多个 Habitat 数据库交给 Room 管理.
     */
    @Test
    fun allowsEntityInMultipleHabitatDatabases() {
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

                @Dao
                interface LogDao

                class SharedEntity

                @HabitatDatabase
                @Database(entities = [SharedEntity::class], version = 1)
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
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/example/database/LogDatabase.kt",
            source = """
                package com.example.database

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                @HabitatDatabase
                @Database(entities = [SharedEntity::class], version = 1)
                abstract class LogDatabase : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: LogDatabase
                            get() = error("No test instance.")
                    }

                    abstract fun logDao(): LogDao
                }
            """.trimIndent()
        )

        runBuild(projectDir)

        assertTrue(generatedRegistryFile(projectDir).exists())
        assertTrue(generatedProviderFile(projectDir, "AppDatabaseHabitatDaoProvider").exists())
        assertTrue(generatedProviderFile(projectDir, "LogDatabaseHabitatDaoProvider").exists())
    }

    /**
     * 验证 Provider 名称冲突会列出全部数据库并绑定到数据库声明.
     */
    @Test
    fun reportsAllDatabasesForDuplicateProviderNames() {
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

                @Dao
                interface LogDao

                class UserEntity
                class LogEntity

                class Outer {

                    @HabitatDatabase
                    @Database(entities = [UserEntity::class], version = 1)
                    abstract class Inner : RoomDatabase() {

                        companion object {

                            @HabitatDatabaseInstance
                            val instance: Inner
                                get() = error("No test instance.")
                        }

                        abstract fun userDao(): UserDao
                    }
                }

                @HabitatDatabase
                @Database(entities = [LogEntity::class], version = 1)
                abstract class Outer_Inner : RoomDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: Outer_Inner
                            get() = error("No test instance.")
                    }

                    abstract fun logDao(): LogDao
                }
            """.trimIndent()
        )

        val result: BuildResult = runBuildAndFail(projectDir)
        val conflictMessage: String =
            "Habitat provider com.example.habitat.generated.providers.com.example.database." +
                "Outer_InnerHabitatDaoProvider is generated by multiple Habitat databases. Conflicting databases: " +
                "com.example.database.Outer.Inner, com.example.database.Outer_Inner."

        assertEquals(2, Regex(Regex.escape(conflictMessage)).findAll(result.output).count())
        assertTrue(result.output.contains("AppDatabase.kt:"))
        assertFalse(generatedRegistryFile(projectDir).exists())
    }

    private fun createValidationProject(
        additionalDeclarations: String = "",
        additionalImports: String = "",
        daoDeclaration: String = "interface UserDao",
        databaseDeclaration: String = "abstract class AppDatabase",
        companionDeclaration: String = "companion object",
        instanceAccessor: String = """
            @HabitatDatabaseInstance
            val instance: AppDatabase
                get() = error("No test instance.")
        """.trimIndent(),
        daoAccessor: String = "abstract fun userDao(): UserDao",
    ): File {
        return createProject(
            source = """
                package com.example.database

                import androidx.room.Dao
                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance
                $additionalImports

                @Dao
                $daoDeclaration

                class UserEntity

                $additionalDeclarations

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                $databaseDeclaration : RoomDatabase() {

                    $companionDeclaration {

                        $instanceAccessor
                    }

                    $daoAccessor
                }
            """.trimIndent()
        )
    }

    private fun duplicateDaoProjectSource(
        appBinding: String,
        logBinding: String,
    ): String {
        return """
            package com.example.database

            import androidx.room.Dao
            import androidx.room.Database
            import androidx.room.RoomDatabase
            import com.whisper.habitat.runtime.annotation.HabitatDaoBinding
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

                $appBinding
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

                $logBinding
                abstract fun userDao(): UserDao
            }
        """.trimIndent()
    }

    private fun assertValidationFailure(
        projectDir: File,
        expectedMessage: String,
    ): BuildResult {
        val result: BuildResult = runBuildAndFail(projectDir)

        assertTrue(
            "Expected compiler output to contain '$expectedMessage'.\n${result.output}",
            result.output.contains(expectedMessage),
        )
        assertTrue(result.output.contains("AppDatabase.kt"))
        assertFalse(generatedRegistryFile(projectDir).exists())
        return result
    }

    private fun createCompiledParentAccessorProject(childBinding: String): File {
        val projectDir: File = temporaryFolder.newFolder("habitat-compiled-parent-fixture")
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
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "habitat-compiled-parent-functional-test"
                include(":base", ":app")
            """.trimIndent(),
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "build.gradle.kts",
            source = """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
                    id("com.google.devtools.ksp") version "$kspVersion" apply false
                }
            """.trimIndent(),
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "base/build.gradle.kts",
            source = """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                }
            """.trimIndent(),
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "app/build.gradle.kts",
            source = """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("com.google.devtools.ksp")
                }

                dependencies {
                    implementation(project(":base"))
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
            """.trimIndent(),
        )
        val baseProjectDir: File = File(projectDir, "base")
        writeRuntimeStubs(baseProjectDir)
        writeFixtureSource(
            projectDir = baseProjectDir,
            relativePath = "src/main/kotlin/com/example/base/BaseDatabase.kt",
            source = """
                package com.example.base

                import androidx.room.Dao
                import androidx.room.RoomDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDaoBinding

                @Dao
                interface UserDao

                abstract class BaseDatabase : RoomDatabase() {

                    @HabitatDaoBinding("user.account")
                    abstract fun userDao(): UserDao
                }
            """.trimIndent(),
        )
        writeFixtureSource(
            projectDir = File(projectDir, "app"),
            relativePath = "src/main/kotlin/com/example/database/AppDatabase.kt",
            source = """
                package com.example.database

                import androidx.room.Database
                import com.example.base.BaseDatabase
                import com.example.base.UserDao
                import com.whisper.habitat.runtime.annotation.HabitatDaoBinding
                import com.whisper.habitat.runtime.annotation.HabitatDatabase
                import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

                class UserEntity

                @HabitatDatabase
                @Database(entities = [UserEntity::class], version = 1)
                abstract class AppDatabase : BaseDatabase() {

                    companion object {

                        @HabitatDatabaseInstance
                        val instance: AppDatabase
                            get() = error("No test instance.")
                    }

                    $childBinding
                    abstract override fun userDao(): UserDao
                }
            """.trimIndent(),
        )
        return projectDir
    }

    private fun createProject(
        source: String,
        roomVersion: String? = null,
    ): File {
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
        val roomDependencies: String = roomVersion?.let { version: String ->
            """
                implementation("androidx.room:room-runtime:$version")
                ksp("androidx.room:room-compiler:$version")
            """.trimIndent()
        }.orEmpty()

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
                        google()
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
                    google()
                    mavenCentral()
                }

                dependencies {
$roomDependencies
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
        if (roomVersion == null) {
            writeRuntimeStubs(projectDir)
        } else {
            writeHabitatRuntimeStubs(projectDir)
        }
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
        writeHabitatRuntimeStubs(projectDir)
    }

    private fun writeHabitatRuntimeStubs(projectDir: File) {
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
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/annotation/HabitatDaoBinding.kt",
            source = """
                package com.whisper.habitat.runtime.annotation

                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.BINARY)
                annotation class HabitatDaoBinding(val value: String)
            """.trimIndent()
        )
        writeFixtureSource(
            projectDir = projectDir,
            relativePath = "src/main/kotlin/com/whisper/habitat/runtime/registry/HabitatDaoProvider.kt",
            source = """
                package com.whisper.habitat.runtime.registry

                import kotlin.reflect.KClass

                interface HabitatDaoProvider {
                    val daoFactories: Map<KClass<*>, Map<String?, () -> Any>>
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

    private fun runBuild(
        projectDir: File,
        task: String = ":compileKotlin",
    ): BuildResult {
        return gradleRunner(projectDir, task).build()
    }

    private fun runBuildAndFail(
        projectDir: File,
        task: String = ":compileKotlin",
    ): BuildResult {
        return gradleRunner(projectDir, task).buildAndFail()
    }

    private fun gradleRunner(projectDir: File, task: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("--stacktrace", task)
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
