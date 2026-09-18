package com.whisper.feature.project.data.network

import com.google.gson.annotations.SerializedName

/**
 * GitHub 仓库详情响应.
 *
 * 字段保留可空性, 区分缺失信息与有效的 0, false; 必需身份字段由 Repository 映射时校验.
 *
 * @property openItemCount GitHub open_issues_count 字段, 同时包含开放的 Issue 与 Pull Request.
 * @property pushedAt 最近推送的 UTC 时间字符串, 格式为 yyyy-MM-dd'T'HH:mm:ss'Z'.
 * @property license GitHub 识别的许可证信息; null 不代表仓库一定没有许可证文件.
 */
internal data class GithubProjectResp(
    @SerializedName("id")
    val id: Long?,
    @SerializedName("full_name")
    val fullName: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("html_url")
    val htmlUrl: String?,
    @SerializedName("stargazers_count")
    val starCount: Int?,
    @SerializedName("forks_count")
    val forkCount: Int?,
    @SerializedName("open_issues_count")
    val openItemCount: Int?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("license")
    val license: GithubLicenseResp?,
    @SerializedName("default_branch")
    val defaultBranch: String?,
    @SerializedName("pushed_at")
    val pushedAt: String?,
    @SerializedName("private")
    val privateRepository: Boolean?,
)

/**
 * GitHub 仓库响应中的许可证信息.
 *
 * @property spdxId SPDX 标识; NOASSERTION 表示 GitHub 未提供可用标识, 映射时回退到 [name].
 */
internal data class GithubLicenseResp(
    @SerializedName("name")
    val name: String?,
    @SerializedName("spdx_id")
    val spdxId: String?,
)
