package com.whisper.feature.project.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 读写 GitHub 项目概览缓存.
 *
 * @author whisper
 * @since 2026/09/14
 */
@Dao
interface GithubProjectDao {

    /**
     * 按 owner/repository 形式的完整仓库名读取快照; 未命中时返回 null.
     */
    @Query("SELECT * FROM project_github_project WHERE full_name = :fullName LIMIT 1")
    suspend fun findByFullName(fullName: String): GithubProjectEntity?

    /**
     * 以完整快照替换主键或完整仓库名冲突的记录, 不与旧记录逐字段合并.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GithubProjectEntity)

    @Query("DELETE FROM project_github_project WHERE full_name = :fullName")
    suspend fun deleteByFullName(fullName: String)
}
