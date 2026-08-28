package com.whisper.architecture.network.interceptor

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 支持按请求切换目标 Endpoint 的基础拦截器.
 *
 * 子类只负责通过 [resolveTargetEndpoint] 返回目标地址. 默认实现替换请求 URL 的 scheme、host 和 port,
 * 并保留原请求的 path、query 和 fragment. Origin 发生变化时会移除原请求显式设置的 Host Header,
 * 由 OkHttp 根据最终 URL 重新生成. 目标地址包含特殊 path 前缀时, 子类可以覆写 [buildTargetUrl] 明确组合规则.
 *
 * @author whisper
 * @since 2026/08/26
 */
abstract class EndpointRoutingInterceptor : Interceptor {

    final override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val targetEndpoint: HttpUrl = resolveTargetEndpoint(request)
            ?: return chain.proceed(request)
        val targetUrl: HttpUrl = buildTargetUrl(request.url, targetEndpoint)
        val targetRequestBuilder: Request.Builder = request.newBuilder()
            .url(targetUrl)
        if (request.url.hasDifferentOriginFrom(targetUrl)) {
            targetRequestBuilder.removeHeader(HOST_HEADER)
        }
        val targetRequest: Request = targetRequestBuilder.build()
        return chain.proceed(targetRequest)
    }

    /**
     * 解析当前请求需要使用的目标 Endpoint.
     *
     * @param request 当前请求.
     * @return 目标地址; 返回 null 时保持原请求地址.
     */
    protected abstract fun resolveTargetEndpoint(request: Request): HttpUrl?

    /**
     * 根据原请求地址和目标 Endpoint 构建最终请求地址.
     *
     * 默认只替换 origin, 不使用 [targetEndpoint] 的 path、query 或 fragment.
     *
     * @param sourceUrl 原请求地址.
     * @param targetEndpoint 目标 Endpoint.
     * @return 最终请求地址.
     */
    protected open fun buildTargetUrl(
        sourceUrl: HttpUrl,
        targetEndpoint: HttpUrl,
    ): HttpUrl = sourceUrl.newBuilder()
        .scheme(targetEndpoint.scheme)
        .host(targetEndpoint.host)
        .port(targetEndpoint.port)
        .build()

    private fun HttpUrl.hasDifferentOriginFrom(other: HttpUrl): Boolean =
        scheme != other.scheme || host != other.host || port != other.port

    private companion object {

        const val HOST_HEADER: String = "Host"
    }
}
