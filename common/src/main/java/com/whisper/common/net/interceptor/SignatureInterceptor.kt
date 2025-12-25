package com.whisper.common.net.interceptor

import com.whisper.common.net.HttpHeaderKey
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
class SignatureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 如果没有 body，直接处理
        val requestBody = request.body ?: return chain.proceed(request)

        if (requestBody.isOneShot()) {
            // 如果是一次性流，不建议读取 body 进行签名，或者需要特殊处理
            return chain.proceed(request)
        }

        // 确定字符集（默认 UTF-8）
        val contentType = requestBody.contentType()

        if (contentType?.subtype?.contains("json") != true && contentType?.subtype?.contains("form") != true) {    // 执行读取并签名逻辑
            return chain.proceed(request)
        }

        // 将 body 写入缓冲区
        val buffer = Buffer()
        requestBody.writeTo(buffer)

        val charset: Charset =
            contentType.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8

        // 读取内容（注意：这里只是读到了 buffer 里的副本）
        val bodyString = buffer.readString(charset)

        // TODO: 使用 bodyString 和其他参数（如 URL、Header）进行签名计算
        val signature = calculateSignature(bodyString)

        // 将签名添加到 Header 中并继续
        val newRequest = request.newBuilder()
            .addHeader(HttpHeaderKey.SIGNATURE, signature)
            .build()

        return chain.proceed(newRequest)
    }

    private fun calculateSignature(content: String): String {
        // 签名逻辑
        return "some-sign"
    }

}