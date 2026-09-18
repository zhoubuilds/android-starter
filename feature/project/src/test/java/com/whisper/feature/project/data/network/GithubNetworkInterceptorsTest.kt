package com.whisper.feature.project.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 验证 GitHub 网络组件使用注入的 Endpoint 和应用标识改写请求.
 *
 * @author whisper
 * @since 2026/09/15
 */
class GithubNetworkInterceptorsTest {

    @Test
    fun interceptors_withInjectedConfiguration_routeRequestAndOverrideHeaders() {
        var proceededRequest: Request? = null
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(GithubEndpointRoutingInterceptor("https://github.example.test:8443/".toHttpUrl()))
            .addInterceptor(
                GithubSystemParametersInterceptor(
                    userAgent = TEST_USER_AGENT,
                )
            )
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                successfulResponse(chain.request())
            }
            .build()
        val request: Request = Request.Builder()
            .url("https://placeholder.invalid/repos/zhoubuilds/android-starter?source=demo")
            .header("Host", "placeholder.invalid")
            .header("Accept", "application/json")
            .header("X-GitHub-Api-Version", "old-version")
            .header("User-Agent", "old-user-agent")
            .build()

        client.newCall(request).execute().close()

        val routedRequest: Request = checkNotNull(proceededRequest)
        assertEquals(
            "https://github.example.test:8443/repos/zhoubuilds/android-starter?source=demo",
            routedRequest.url.toString(),
        )
        assertNull(routedRequest.header("Host"))
        assertEquals("application/vnd.github+json", routedRequest.header("Accept"))
        assertEquals("2026-03-10", routedRequest.header("X-GitHub-Api-Version"))
        assertEquals(TEST_USER_AGENT, routedRequest.header("User-Agent"))
    }

    private fun successfulResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()

    private companion object {

        const val TEST_USER_AGENT: String = "com.whisper.starter/1.0"
    }
}
