package com.whisper.architecture.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 验证 Endpoint 路由基础实现的 URL 改写边界.
 *
 * @author whisper
 * @since 2026/08/26
 */
class EndpointRoutingInterceptorTest {

    @Test
    fun interceptReplacesOriginAndPreservesRequestDetails() {
        var proceededRequest: Request? = null
        val interceptor: EndpointRoutingInterceptor = FixedEndpointRoutingInterceptor(
            targetEndpoint = "https://gateway.example.test:8443/ignored-prefix/".toHttpUrl(),
        )
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                successfulResponse(chain.request())
            }
            .build()
        val request: Request = Request.Builder()
            .url("http://placeholder.invalid/v1/profile?tab=base#summary")
            .header("Host", "placeholder.invalid")
            .build()

        client.newCall(request).execute().close()

        assertEquals(
            "https://gateway.example.test:8443/v1/profile?tab=base#summary",
            proceededRequest?.url.toString(),
        )
        assertNull(proceededRequest?.header("Host"))
    }

    @Test
    fun interceptKeepsOriginalUrlWhenEndpointIsAbsent() {
        var proceededRequest: Request? = null
        val interceptor: EndpointRoutingInterceptor = object : EndpointRoutingInterceptor() {
            override fun resolveTargetEndpoint(request: Request): HttpUrl? = null
        }
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                successfulResponse(chain.request())
            }
            .build()
        val request: Request = Request.Builder()
            .url("https://placeholder.invalid/v1/profile?tab=base")
            .build()

        client.newCall(request).execute().close()

        assertEquals(request.url, proceededRequest?.url)
    }

    private class FixedEndpointRoutingInterceptor(
        private val targetEndpoint: HttpUrl,
    ) : EndpointRoutingInterceptor() {

        override fun resolveTargetEndpoint(request: Request): HttpUrl = targetEndpoint
    }

    private fun successfulResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
}
