package com.whisper.starter.network

import com.whisper.architecture.network.interceptor.RequestHeadersInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit

/**
 * 验证 app 组合根提供的 API Host 会成为 Retrofit 的唯一默认域名.
 *
 * @author whisper
 * @since 2026/08/25
 */
class StarterNetworkComponentManagerTest {

    @Test
    fun configureDefaultRetrofitUsesNormalizedApiHost() {
        val manager: StarterNetworkComponentManager = StarterNetworkComponentManager(
            apiHost = "https://example.test/service",
            requestHeadersInterceptor = object : RequestHeadersInterceptor() {
                override fun resolveRequestHeaders(request: Request): Map<String, String> = emptyMap()
            },
        )
        val builder: Retrofit.Builder = Retrofit.Builder()

        manager.configureDefaultRetrofit(builder, OkHttpClient.Builder())

        assertEquals("https://example.test/service/", builder.build().baseUrl().toString())
    }
}
