package com.whisper.core.net

import com.whisper.core.net.security.AllowAnyHostnameVerifier
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 *
 * @author whisper
 * @since 2025/9/4
 */
object OkhttpFactory {

    private val CONNECTION_POOL: ConnectionPool = ConnectionPool()

    private val DISPATCHER: Dispatcher = Dispatcher()

    private const val CONNECT_TIMEOUT_SECONDS: Long = 8
    private const val WRITE_TIMEOUT_SECONDS: Long = 8
    private const val READ_TIMEOUT_SECONDS: Long = 8

    fun creteClient(interceptors: Iterable<Interceptor>): OkHttpClient = OkHttpClient.Builder()
        .hostnameVerifier(AllowAnyHostnameVerifier())
        .retryOnConnectionFailure(true)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(CONNECTION_POOL)
        .dispatcher(DISPATCHER)
        .apply {
            interceptors.forEach { interceptor -> addInterceptor(interceptor) }
        }
        .build()

}