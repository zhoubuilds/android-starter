package com.whisper.architecture.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证公共请求头在请求执行时读取并覆盖同名值.
 *
 * @author whisper
 * @since 2026/08/25
 */
class RequestHeadersInterceptorTest {

    @Test
    fun interceptAddsCurrentHeadersAndOverridesExistingValues() {
        var proceededRequest: Request? = null
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                object : RequestHeadersInterceptor() {
                    override fun resolveRequestHeaders(request: Request): Map<String, String> = mapOf(
                        "Platform" to "android",
                        "Timestamp" to "1234567890",
                    )
                }
            )
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                successfulResponse(chain.request())
            }
            .build()
        val request: Request = Request.Builder()
            .url("https://example.test/")
            .header("Platform", "other")
            .build()

        client.newCall(request).execute().close()

        assertEquals("android", proceededRequest?.header("Platform"))
        assertEquals("1234567890", proceededRequest?.header("Timestamp"))
    }

    @Test
    fun interceptReadsProviderForEveryRequest() {
        var invocationCount: Int = 0
        val proceededRequests: MutableList<Request> = mutableListOf()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                object : RequestHeadersInterceptor() {
                    override fun resolveRequestHeaders(request: Request): Map<String, String> {
                        invocationCount += 1
                        return mapOf("Invocation" to invocationCount.toString())
                    }
                }
            )
            .addInterceptor { chain ->
                proceededRequests += chain.request()
                successfulResponse(chain.request())
            }
            .build()
        val request: Request = Request.Builder()
            .url("https://example.test/")
            .build()

        client.newCall(request).execute().close()
        client.newCall(request).execute().close()

        assertEquals(listOf("1", "2"), proceededRequests.map { it.header("Invocation") })
    }

    private fun successfulResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
}
