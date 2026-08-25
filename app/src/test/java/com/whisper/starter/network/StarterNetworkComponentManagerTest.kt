package com.whisper.starter.network

import okhttp3.OkHttpClient
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
            requestHeadersProvider = { emptyMap() },
        )
        val builder: Retrofit.Builder = Retrofit.Builder()

        manager.configureDefaultRetrofit(builder, OkHttpClient.Builder())

        assertEquals("https://example.test/service/", builder.build().baseUrl().toString())
    }
}
