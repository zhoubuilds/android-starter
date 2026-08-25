package com.whisper.starter.network

import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import com.whisper.architecture.network.component.RetrofitCustomizer
import com.whisper.common.network.BusinessFlowCallAdapterFactory
import com.whisper.common.network.interceptor.RequestHeadersInterceptor
import com.whisper.common.network.interceptor.RequestHeadersProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 管理 Starter 应用最终使用的网络组件.
 *
 * 域名、请求头、序列化和超时属于 app 组合根职责. Architecture 与业务模块只依赖
 * NetworkComponentManager 契约, 不读取 BuildConfig 或环境 flavor.
 *
 * @author whisper
 * @since 2026/08/25
 */
class StarterNetworkComponentManager(
    apiHost: String,
    requestHeadersProvider: RequestHeadersProvider,
) : NetworkComponentManager {

    companion object {

        private const val CONNECT_TIMEOUT_SECONDS: Long = 8L
        private const val WRITE_TIMEOUT_SECONDS: Long = 8L
        private const val READ_TIMEOUT_SECONDS: Long = 8L
    }

    private val baseUrl: HttpUrl = normalizeBaseUrl(apiHost)
    private val requestHeadersInterceptor: RequestHeadersInterceptor =
        RequestHeadersInterceptor(requestHeadersProvider)
    private val componentCache: ConcurrentHashMap<KClass<*>, Any> = ConcurrentHashMap()

    override fun configureDefaultOkHttp(builder: OkHttpClient.Builder) {
        builder
            .addInterceptor(requestHeadersInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun configureDefaultRetrofit(
        retrofitBuilder: Retrofit.Builder,
        okHttpBuilder: OkHttpClient.Builder,
    ) {
        retrofitBuilder
            .baseUrl(baseUrl)
            .addCallAdapterFactory(BusinessFlowCallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpBuilder.build())
    }

    override fun resolveInterceptor(
        apiClass: KClass<*>,
        interceptorClass: KClass<out Interceptor>,
    ): Interceptor = resolveComponent(apiClass, interceptorClass, "interceptor")

    override fun resolveOkHttpCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out OkHttpCustomizer>,
    ): OkHttpCustomizer = resolveComponent(apiClass, customizerClass, "OkHttp customizer")

    override fun resolveRetrofitCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out RetrofitCustomizer>,
    ): RetrofitCustomizer = resolveComponent(apiClass, customizerClass, "Retrofit customizer")

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveComponent(
        apiClass: KClass<*>,
        componentClass: KClass<out T>,
        componentName: String,
    ): T = componentCache.computeIfAbsent(componentClass) {
        createComponentInstance(apiClass, componentClass, componentName)
    } as T

    private fun <T : Any> createComponentInstance(
        apiClass: KClass<*>,
        componentClass: KClass<out T>,
        componentName: String,
    ): T {
        try {
            val constructor: Constructor<out T> = componentClass.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        } catch (exception: NoSuchMethodException) {
            throw createComponentException(
                apiClass,
                componentClass,
                componentName,
                "A no-arg constructor is required.",
                exception,
            )
        } catch (exception: InvocationTargetException) {
            throw createComponentException(
                apiClass,
                componentClass,
                componentName,
                "The constructor threw an exception.",
                exception.targetException ?: exception,
            )
        } catch (exception: ReflectiveOperationException) {
            throw createComponentException(
                apiClass,
                componentClass,
                componentName,
                "Reflective instantiation failed.",
                exception,
            )
        } catch (exception: SecurityException) {
            throw createComponentException(
                apiClass,
                componentClass,
                componentName,
                "Reflective access was denied.",
                exception,
            )
        }
    }

    private fun createComponentException(
        apiClass: KClass<*>,
        componentClass: KClass<*>,
        componentName: String,
        reason: String,
        cause: Throwable,
    ): IllegalArgumentException = IllegalArgumentException(
        "Cannot create $componentName ${componentClass.qualifiedName} for API " +
            "${apiClass.qualifiedName}. $reason",
        cause,
    )

    private fun normalizeBaseUrl(apiHost: String): HttpUrl {
        val parsedUrl: HttpUrl = apiHost.trim().toHttpUrl()
        return if (parsedUrl.pathSegments.lastOrNull().isNullOrEmpty()) {
            parsedUrl
        } else {
            parsedUrl.newBuilder().addPathSegment("").build()
        }
    }
}
