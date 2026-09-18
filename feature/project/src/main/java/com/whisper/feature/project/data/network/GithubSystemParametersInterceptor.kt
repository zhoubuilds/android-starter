package com.whisper.feature.project.data.network

import com.whisper.architecture.network.interceptor.RequestHeadersInterceptor
import okhttp3.Request

/**
 * 提供 GitHub REST API 共用的系统请求参数.
 *
 * GitHub 协议参数由本组件维护, 应用标识和实例生命周期由 app 装配.
 * 同名 Header 由父类覆盖写入, 不与调用方的旧值追加合并; 不持有单次请求的可变状态.
 *
 * @property userAgent app 提供的应用标识与版本.
 *
 * @author whisper
 * @since 2026/09/15
 */
class GithubSystemParametersInterceptor(
    private val userAgent: String,
) : RequestHeadersInterceptor() {

    private companion object {

        const val HEADER_ACCEPT: String = "Accept"
        const val HEADER_API_VERSION: String = "X-GitHub-Api-Version"
        const val HEADER_USER_AGENT: String = "User-Agent"
        const val ACCEPT_GITHUB_JSON: String = "application/vnd.github+json"
        const val API_VERSION: String = "2026-03-10"
    }

    override fun resolveRequestHeaders(request: Request): Map<String, String> = linkedMapOf(
        HEADER_ACCEPT to ACCEPT_GITHUB_JSON,
        HEADER_API_VERSION to API_VERSION,
        HEADER_USER_AGENT to userAgent,
    )
}
