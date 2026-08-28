package com.whisper.architecture.network.component

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import kotlin.reflect.KClass

/**
 * 管理 API 网络组件的默认配置和实例解析.
 *
 * Architecture 只读取 API 接口声明, app 通过本接口决定实例来源、生命周期、域名、
 * 序列化和安全策略. 所有方法都在 API 构建锁内串行执行, 不得反向调用 ApiFactory,
 * 不得等待可能创建 API 的任务. 返回的 OkHttp 组件仍必须满足运行期线程安全要求.
 *
 * @aegis 保护组件解析 API, 默认配置顺序和构建期并发/递归约束.
 *
 * @author whisper
 * @since 2026/07/23
 */
interface NetworkComponentManager {

    companion object {

        /** Retrofit 使用运行期 Endpoint 路由时可配置的占位 BaseUrl. */
        const val ROUTING_PLACEHOLDER_BASE_URL: String = "https://placeholder.invalid/"
    }

    /**
     * 配置所有 API 共用且允许接口级定制器覆盖的 OkHttp 参数.
     */
    fun configureDefaultOkHttp(builder: OkHttpClient.Builder)

    /**
     * 配置所有 API 共用且允许接口级定制器覆盖的 Retrofit 参数.
     *
     * [okHttpBuilder] 已包含默认配置和 API 接口声明的 OkHttp 组件.
     */
    fun configureDefaultRetrofit(
        retrofitBuilder: Retrofit.Builder,
        okHttpBuilder: OkHttpClient.Builder,
    )

    /**
     * 解析 API 接口声明的拦截器实例.
     */
    fun resolveInterceptor(
        apiClass: KClass<*>,
        interceptorClass: KClass<out Interceptor>,
    ): Interceptor

    /**
     * 解析 API 接口声明的 OkHttp 定制器实例.
     */
    fun resolveOkHttpCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out OkHttpCustomizer>,
    ): OkHttpCustomizer

    /**
     * 解析 API 接口声明的 Retrofit 定制器实例.
     */
    fun resolveRetrofitCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out RetrofitCustomizer>,
    ): RetrofitCustomizer
}
