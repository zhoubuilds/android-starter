package com.whisper.architecture.net

import com.whisper.architecture.net.security.AllowAnyHostnameVerifier
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 *
 * @author whisper
 * @since 2025/9/4
 */
object OkhttpFactory {


    private const val CONNECT_TIMEOUT_SECONDS: Long = 8
    private const val WRITE_TIMEOUT_SECONDS: Long = 8
    private const val READ_TIMEOUT_SECONDS: Long = 8

    // 基础 Client，包含通用配置
    private val BASE_CLIENT: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .hostnameVerifier(AllowAnyHostnameVerifier())
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun creteClient(interceptors: Iterable<Interceptor>): OkHttpClient {
        if (interceptors.none()) return BASE_CLIENT

        // 使用 newBuilder() 共享连接池和线程池，只添加差异化的拦截器
        val builder = BASE_CLIENT.newBuilder()
        interceptors.forEach { builder.addInterceptor(it) }
        return builder.build()
    }

}