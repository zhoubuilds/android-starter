package com.whisper.starter.network

import com.whisper.architecture.network.interceptor.RequestHeadersInterceptor
import okhttp3.Request

/**
 * 将 Starter 应用的公共 Header 提供给 Architecture 拦截器模板.
 *
 * Provider 和具体 Header 契约属于 app 实现层, Architecture 只负责通用注入机制.
 *
 * @author whisper
 * @since 2026/08/26
 */
class StarterRequestHeadersInterceptor(
    private val provider: StarterRequestHeadersProvider,
) : RequestHeadersInterceptor() {

    override fun resolveRequestHeaders(request: Request): Map<String, String> = provider.currentHeaders()
}
