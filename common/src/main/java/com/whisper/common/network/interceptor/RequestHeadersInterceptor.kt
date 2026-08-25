package com.whisper.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 将应用提供的公共请求头注入 OkHttp 请求.
 *
 * Common 不读取 BuildConfig、环境变体或应用状态. 当前值由 app 提供的
 * [RequestHeadersProvider] 在请求运行期生成. 同名 Header 使用覆盖写入.
 *
 * @author whisper
 * @since 2026/08/25
 */
class RequestHeadersInterceptor(
    private val provider: RequestHeadersProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder: Request.Builder = chain.request().newBuilder()
        provider.currentHeaders().forEach { (name: String, value: String) ->
            builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}
