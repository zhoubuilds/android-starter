package com.whisper.feature.project

/**
 * 当前 Project 示例使用的 GitHub 仓库身份.
 *
 * API 相对路径和公开网页地址必须从同一份 owner/repository 身份派生.
 */
internal object GithubProjectIdentity {

    private const val REPOSITORY_OWNER: String = "zhoubuilds"
    private const val REPOSITORY_NAME: String = "android-starter"

    const val API_PATH: String = "repos/$REPOSITORY_OWNER/$REPOSITORY_NAME"
    const val WEB_URL: String = "https://github.com/$REPOSITORY_OWNER/$REPOSITORY_NAME"
}
