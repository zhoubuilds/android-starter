package com.whisper.architecture.network.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 将子类解析的公共 Header 注入 OkHttp 请求.
 *
 * 每次请求都会调用 [resolveRequestHeaders], 同名 Header 使用覆盖写入,
 * 避免重试或上游请求产生重复值. Provider、DI 和具体 Header 契约属于实现层.
 *
 * @author whisper
 * @since 2026/08/25
 */
abstract class RequestHeadersInterceptor : Interceptor {

    final override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val headers: Map<String, String> = resolveRequestHeaders(request)
        if (headers.isEmpty()) {
            return chain.proceed(request)
        }

        val requestBuilder: Request.Builder = request.newBuilder()
        headers.forEach { (name: String, value: String) ->
            requestBuilder.header(name, value)
        }
        return chain.proceed(requestBuilder.build())
    }

    /**
     * 解析当前请求需要注入的 Header.
     *
     * @param request 当前请求.
     * @return Header 名称与值; 返回空集合时保持原请求.
     */
    protected abstract fun resolveRequestHeaders(request: Request): Map<String, String>
}
