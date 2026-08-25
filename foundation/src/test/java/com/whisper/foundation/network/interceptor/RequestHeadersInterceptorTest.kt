package com.whisper.foundation.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证公共请求头由 Provider 在请求执行时注入并覆盖同名值.
 *
 * @author whisper
 * @since 2026/08/25
 */
class RequestHeadersInterceptorTest {

    @Test
    fun interceptAddsCurrentHeaders() {
        var proceededRequest: Request? = null
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                RequestHeadersInterceptor {
                    mapOf(
                        "Platform" to "android",
                        "Timestamp" to "1234567890",
                    )
                }
            )
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
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
}
