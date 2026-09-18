package com.whisper.feature.project.model

/**
 * Project 页面使用的 GitHub 项目信息.
 *
 * 必需的仓库身份已经校验; 可空字段保留未知状态, 交给页面格式化时决定占位文案.
 * 数量为 null 与 0, 可见性为 null 与 false 分别代表未知与有效值, 不可合并.
 *
 * @property fullName owner/repository 形式的完整仓库名.
 * @property htmlUrl 供外部浏览器打开的仓库网页地址, 不是 API Endpoint.
 * @property openItemCount 开放 Issue 与 Pull Request 的总数, 不只是 Issue 数量.
 * @property licenseName SPDX 标识或回退的许可证名称.
 * @property pushedAt GitHub 返回的最近推送 UTC 时间字符串, 尚未转换为设备时区.
 * @property cachedAt 本次刷新开始时取得并随成功结果保存的 Unix 毫秒时间.
 */
internal data class GithubProject(
    val repositoryId: Long,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val starCount: Int?,
    val forkCount: Int?,
    val openItemCount: Int?,
    val language: String?,
    val licenseName: String?,
    val defaultBranch: String?,
    val pushedAt: String?,
    val privateRepository: Boolean?,
    val cachedAt: Long,
)
