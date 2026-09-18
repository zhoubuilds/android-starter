package com.whisper.starter.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.habitat.runtime.annotation.HabitatDatabase
import com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance

/**
 * Starter 应用唯一的 Room 数据库装配入口.
 *
 * Feature 定义 Entity 和 Dao, app 负责汇总数据库结构、创建实例并提供给 Habitat.
 *
 * @author whisper
 * @since 2026/09/14
 */
@HabitatDatabase
@Database(
    entities = [GithubProjectEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class StarterDatabase : RoomDatabase() {

    abstract fun githubProjectDao(): GithubProjectDao

    companion object {

        private const val DATABASE_NAME: String = "starter_cache.db"

        /**
         * 仅重命名旧 About 缓存表, 保留已安装版本中的项目缓存, 不使用破坏性迁移重建表.
         */
        internal val MIGRATION_1_2: Migration = Migration(1, 2) { database ->
            database.execSQL(
                "ALTER TABLE about_github_project RENAME TO project_github_project"
            )
        }

        /**
         * 通过 volatile 发布唯一实例, 配合初始化锁保证并发调用不会重复创建数据库.
         */
        @Volatile
        private var databaseInstance: StarterDatabase? = null

        private val initializeLock: Any = Any()

        /**
         * 供 Habitat 生成的 provider 取得应用数据库, 不在读取时隐式初始化.
         *
         * @throws IllegalStateException 在 [initialize] 成功完成前访问时抛出.
         */
        @HabitatDatabaseInstance
        val instance: StarterDatabase
            get() = checkNotNull(databaseInstance) {
                "StarterDatabase has not been initialized."
            }

        /**
         * 使用 Application Context 创建并发布进程内唯一实例, 重复调用直接复用.
         *
         * 必须在 Habitat 的 Dao provider 被首次使用前调用. 此处只构建 Room 实例,
         * 实际打开数据库和执行迁移由后续 Room 访问触发.
         */
        fun initialize(context: Context) {
            if (databaseInstance != null) {
                return
            }
            synchronized(initializeLock) {
                if (databaseInstance == null) {
                    databaseInstance = Room.databaseBuilder(
                        context.applicationContext,
                        StarterDatabase::class.java,
                        DATABASE_NAME,
                    ).addMigrations(MIGRATION_1_2)
                        .build()
                }
            }
        }
    }
}
