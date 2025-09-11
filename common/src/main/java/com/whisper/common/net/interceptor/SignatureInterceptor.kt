package com.whisper.common.net.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
class SignatureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()

        request.body

        return chain.proceed(request)
    }
}