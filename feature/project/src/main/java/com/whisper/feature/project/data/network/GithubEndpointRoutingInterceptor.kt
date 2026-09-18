package com.whisper.feature.project.data.network

import com.whisper.architecture.network.interceptor.EndpointRoutingInterceptor
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * 将 GitHub API 请求路由到 app 提供的 Endpoint.
 *
 * 具体 Endpoint 和实例生命周期由 app 的 NetworkComponentManager 装配.
 *
 * @property endpoint GitHub API 的目标 origin, 路径与查询参数沿用原请求.
 *
 * @author whisper
 * @since 2026/09/15
 */
class GithubEndpointRoutingInterceptor(
    private val endpoint: HttpUrl,
) : EndpointRoutingInterceptor() {

    override fun resolveTargetEndpoint(request: Request): HttpUrl = endpoint
}
