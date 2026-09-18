package com.whisper.feature.project.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GitHub 项目概览的本地缓存记录.
 *
 * 可空字段保留 GitHub 未提供信息的状态, 不以 0, false 或空字符串代替未知值.
 *
 * @property repositoryId GitHub 仓库唯一标识.
 * @property fullName 包含 owner 的仓库名称.
 * @property description 仓库描述.
 * @property htmlUrl 仓库网页地址.
 * @property starCount Star 数量.
 * @property forkCount Fork 数量.
 * @property openItemCount 开放 Issue 与 Pull Request 总数.
 * @property language 主要开发语言.
 * @property licenseName 优先保存 SPDX 标识, 不可用时保存许可证名称.
 * @property defaultBranch 默认分支.
 * @property pushedAt GitHub 返回的最近推送 UTC 时间字符串, 格式为 yyyy-MM-dd'T'HH:mm:ss'Z'.
 * @property privateRepository 是否为私有仓库.
 * @property cachedAt 本次刷新开始时取得并随成功结果保存的 Unix 毫秒时间, 用于判断新鲜期.
 *
 * @author whisper
 * @since 2026/09/14
 */
@Entity(
    tableName = "project_github_project",
    indices = [Index(value = ["full_name"], unique = true)],
)
data class GithubProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "repository_id")
    val repositoryId: Long,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "html_url")
    val htmlUrl: String,
    @ColumnInfo(name = "star_count")
    val starCount: Int?,
    @ColumnInfo(name = "fork_count")
    val forkCount: Int?,
    @ColumnInfo(name = "open_item_count")
    val openItemCount: Int?,
    @ColumnInfo(name = "language")
    val language: String?,
    @ColumnInfo(name = "license_name")
    val licenseName: String?,
    @ColumnInfo(name = "default_branch")
    val defaultBranch: String?,
    @ColumnInfo(name = "pushed_at")
    val pushedAt: String?,
    @ColumnInfo(name = "private_repository")
    val privateRepository: Boolean?,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long,
)
