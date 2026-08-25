package com.whisper.architecture.network

import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import kotlin.reflect.KClass

/**
 * 基于根 client 派生指定 API 的 OkHttpClient.Builder.
 *
 * 根 client 只复用连接池和 Dispatcher 等运行时资源. 域名、鉴权、超时和拦截器均由
 * app 安装的组件管理器决定.
 *
 * @aegis 保护默认配置, application interceptor, network interceptor 和定制器的执行顺序.
 * @author whisper
 * @since 2026/07/06
 */
internal object OkHttpClientFactory {

    private val baselineClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    fun createBuilder(
        componentManager: NetworkComponentManager,
        apiClass: KClass<*>,
        applicationInterceptorClasses: List<KClass<out Interceptor>>,
        networkInterceptorClasses: List<KClass<out Interceptor>>,
        okHttpCustomizerClass: KClass<out OkHttpCustomizer>?,
    ): OkHttpClient.Builder {
        val builder: OkHttpClient.Builder = baselineClient.newBuilder()
        componentManager.configureDefaultOkHttp(builder)
        applicationInterceptorClasses.forEach { interceptorClass: KClass<out Interceptor> ->
            builder.addInterceptor(componentManager.resolveInterceptor(apiClass, interceptorClass))
        }
        networkInterceptorClasses.forEach { interceptorClass: KClass<out Interceptor> ->
            builder.addNetworkInterceptor(componentManager.resolveInterceptor(apiClass, interceptorClass))
        }
        okHttpCustomizerClass?.let { customizerClass: KClass<out OkHttpCustomizer> ->
            componentManager.resolveOkHttpCustomizer(apiClass, customizerClass).customize(builder)
        }
        return builder
    }
}
