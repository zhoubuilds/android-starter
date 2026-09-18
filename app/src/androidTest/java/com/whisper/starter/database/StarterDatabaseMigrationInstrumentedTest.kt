package com.whisper.starter.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.feature.project.data.database.GithubProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 Starter 数据库从版本 1 升级到版本 2 时保留项目缓存.
 *
 * 使用版本化 schema 建立旧库并校验迁移后的完整结构, 再通过生产 Dao 验证内容.
 * 版本 1 快照由原迁移测试保存的建表 SQL 重建, 不是历史构建留存的导出文件.
 *
 * @author whisper
 * @since 2026/09/14
 */
@RunWith(AndroidJUnit4::class)
class StarterDatabaseMigrationInstrumentedTest {

    /**
     * 读取 Room 插件打包到测试 assets 的 schema, 同时管理测试数据库连接的清理.
     */
    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StarterDatabase::class.java,
    )

    @Test
    fun openVersionOneDatabase_migratesProjectTableAndPreservesCache() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE_NAME)
        createVersionOneDatabase()
        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            2,
            true,
            StarterDatabase.MIGRATION_1_2,
        ).close()

        val database: StarterDatabase = Room.databaseBuilder(
            context,
            StarterDatabase::class.java,
            TEST_DATABASE_NAME,
        ).addMigrations(StarterDatabase.MIGRATION_1_2)
            .build()
        try {
            val stored: GithubProjectEntity? = database.githubProjectDao()
                .findByFullName(TEST_ENTITY.fullName)

            assertEquals(TEST_ENTITY, stored)
        } finally {
            database.close()
            context.deleteDatabase(TEST_DATABASE_NAME)
        }
    }

    /**
     * 按旧版本快照建库并直接插入夹具, 避免通过当前 Room 类型访问时提前触发迁移.
     */
    private fun createVersionOneDatabase() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).use { database ->
            database.execSQL(
                INSERT_VERSION_ONE_ENTITY,
                arrayOf<Any?>(
                    TEST_ENTITY.repositoryId,
                    TEST_ENTITY.fullName,
                    TEST_ENTITY.description,
                    TEST_ENTITY.htmlUrl,
                    TEST_ENTITY.starCount,
                    TEST_ENTITY.forkCount,
                    TEST_ENTITY.openItemCount,
                    TEST_ENTITY.language,
                    TEST_ENTITY.licenseName,
                    TEST_ENTITY.defaultBranch,
                    TEST_ENTITY.pushedAt,
                    TEST_ENTITY.privateRepository?.let { privateRepository: Boolean ->
                        if (privateRepository) 1 else 0
                    },
                    TEST_ENTITY.cachedAt,
                ),
            )
        }
    }

    private companion object {

        const val TEST_DATABASE_NAME: String = "starter_migration_test.db"

        val TEST_ENTITY: GithubProjectEntity = GithubProjectEntity(
            repositoryId = 100L,
            fullName = "zhoubuilds/android-starter",
            description = "Version one cache",
            htmlUrl = "https://github.com/zhoubuilds/android-starter",
            starCount = 10,
            forkCount = 2,
            openItemCount = 1,
            language = "Kotlin",
            licenseName = "Apache-2.0",
            defaultBranch = "main",
            pushedAt = "2026-09-14T00:00:00Z",
            privateRepository = false,
            cachedAt = 1_789_315_200_000L,
        )

        const val INSERT_VERSION_ONE_ENTITY: String = """
            INSERT INTO `about_github_project` (
                `repository_id`,
                `full_name`,
                `description`,
                `html_url`,
                `star_count`,
                `fork_count`,
                `open_item_count`,
                `language`,
                `license_name`,
                `default_branch`,
                `pushed_at`,
                `private_repository`,
                `cached_at`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
