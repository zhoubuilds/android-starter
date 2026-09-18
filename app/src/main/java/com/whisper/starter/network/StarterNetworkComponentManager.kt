package com.whisper.starter.network

import android.app.Application
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import com.whisper.architecture.network.component.RetrofitCustomizer
import com.whisper.feature.project.data.network.GithubEndpointRoutingInterceptor
import com.whisper.feature.project.data.network.GithubSystemParametersInterceptor
import com.whisper.foundation.network.BusinessFlowCallAdapterFactory
import com.whisper.starter.BuildConfig
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
 * 网络组件按声明类型在当前管理器的实例池中延迟创建并复用.
 * 复用范围是当前管理器, 共享组件自身仍需满足并发请求或配置调用的要求.
 *
 * @property application 提供公共请求头所需的应用信息与资源 Locale 回退, 不对外暴露为全局 Holder.
 *
 * @author whisper
 * @since 2026/08/25
 */
class StarterNetworkComponentManager(
    private val application: Application,
) : NetworkComponentManager {

    companion object {

        private const val CONNECT_TIMEOUT_SECONDS: Long = 8L
        private const val WRITE_TIMEOUT_SECONDS: Long = 8L
        private const val READ_TIMEOUT_SECONDS: Long = 8L
    }

    /**
     * 按组件声明类型持有当前管理器的共享实例, 不按调用 API 或组件类别重复缓存.
     */
    private val componentPool: ConcurrentHashMap<KClass<*>, Any> = ConcurrentHashMap()

    override fun configureDefaultOkHttp(builder: OkHttpClient.Builder) {
        builder
            .addInterceptor(resolveInterceptor(StarterRequestHeadersInterceptor::class))
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * 安装全部 API 共用的转换器, 并绑定已完成 API 级配置的 OkHttp Builder.
     *
     * 占位 base URL 只用于 Retrofit 构建请求, 实际服务地址由 API 声明的路由拦截器在请求时改写.
     */
    override fun configureDefaultRetrofit(
        retrofitBuilder: Retrofit.Builder,
        okHttpBuilder: OkHttpClient.Builder,
    ) {
        retrofitBuilder
            .baseUrl(NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL)
            .addCallAdapterFactory(BusinessFlowCallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpBuilder.build())
    }

    override fun resolveInterceptor(
        apiClass: KClass<*>,
        interceptorClass: KClass<out Interceptor>,
    ): Interceptor = resolveInterceptor(interceptorClass, apiClass.java.name)

    override fun resolveOkHttpCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out OkHttpCustomizer>,
    ): OkHttpCustomizer = getOrCreateComponent(apiClass.java.name, customizerClass, "OkHttp customizer")

    override fun resolveRetrofitCustomizer(
        apiClass: KClass<*>,
        customizerClass: KClass<out RetrofitCustomizer>,
    ): RetrofitCustomizer = getOrCreateComponent(apiClass.java.name, customizerClass, "Retrofit customizer")

    /**
     * 解析默认链或 API 注解声明的拦截器.
     *
     * 两个入口统一在此选择生命周期策略. 当前所有拦截器均按声明类型在管理器内复用,
     * 缺少实例时由内部创建方法装配依赖或执行无参反射. 需要不同作用域时在本入口调整解析策略.
     *
     * @param interceptorClass 请求的拦截器声明类型.
     * @param apiName 声明组件的 API 名称, 仅用于失败诊断; 默认链未关联具体 API 时为 null.
     * @return 与声明类型兼容的拦截器实例.
     * @throws IllegalArgumentException 无参反射创建失败或组件配置无效时抛出.
     */
    private fun resolveInterceptor(
        interceptorClass: KClass<out Interceptor>,
        apiName: String? = null,
    ): Interceptor = getOrCreateComponent(apiName, interceptorClass, "interceptor")

    /**
     * 获取管理器内的共享组件, 缺少实例时通过 [createComponentInstance] 统一构造.
     *
     * 缓存键只包含声明类型, API 名称与组件说明仅用于诊断. 原子计算保证并发解析只成功创建一次,
     * 构造或类型校验失败的结果不会写入实例池, 后续解析可以重试.
     *
     * @param apiName 调用方 API 名称; 默认网络配置使用 null.
     * @param componentClass 用于查找缓存及校验实例的组件声明类型.
     * @param componentName 异常信息中的组件类别说明.
     * @return 当前管理器持有的共享组件实例.
     */
    private fun <T : Any> getOrCreateComponent(
        apiName: String?,
        componentClass: KClass<out T>,
        componentName: String,
    ): T {
        val component: Any = componentPool.computeIfAbsent(componentClass) {
            createComponentInstance(apiName, componentClass, componentName)
        }
        return checkNotNull(componentClass.java.cast(component))
    }

    /**
     * 集中装配需要应用依赖的组件, 其它类型交给无参反射兜底.
     *
     * 本方法在实例池的原子计算期间执行, 构造过程应保持短小, 不执行阻塞 IO,
     * 不递归解析其它组件或读写同一个实例池. 新增带依赖组件时在此显式注入配置.
     */
    private fun <T : Any> createComponentInstance(
        apiName: String?,
        componentClass: KClass<out T>,
        componentName: String,
    ): T {
        val component: Any = when (componentClass) {
            StarterRequestHeadersInterceptor::class ->
                StarterRequestHeadersInterceptor(StarterRequestHeadersProvider(application))
            GithubEndpointRoutingInterceptor::class ->
                GithubEndpointRoutingInterceptor(endpoint = BuildConfig.GITHUB_API_ENDPOINT.toHttpUrl())
            GithubSystemParametersInterceptor::class -> GithubSystemParametersInterceptor(
                userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}",
            )
            else -> createComponentReflectively(apiName, componentClass, componentName)
        }
        return checkNotNull(componentClass.java.cast(component))
    }

    /**
     * 通过无参构造器创建无依赖组件, 并为失败保留 API、组件类型与原始异常信息.
     *
     * 此反射策略由 app 选择, 对应实现类及无参构造器的保留规则也由 app 维护.
     *
     * @throws IllegalArgumentException 缺少无参构造器、反射访问失败或构造器执行失败时抛出.
     */
    private fun <T : Any> createComponentReflectively(
        apiName: String?,
        componentClass: KClass<out T>,
        componentName: String,
    ): T {
        try {
            val constructor: Constructor<out T> = componentClass.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        } catch (exception: NoSuchMethodException) {
            throw createComponentException(
                apiName,
                componentClass,
                componentName,
                "A no-arg constructor is required.",
                exception,
            )
        } catch (exception: InvocationTargetException) {
            throw createComponentException(
                apiName,
                componentClass,
                componentName,
                "The constructor threw an exception.",
                exception.targetException ?: exception,
            )
        } catch (exception: ReflectiveOperationException) {
            throw createComponentException(
                apiName,
                componentClass,
                componentName,
                "Reflective instantiation failed.",
                exception,
            )
        } catch (exception: SecurityException) {
            throw createComponentException(
                apiName,
                componentClass,
                componentName,
                "Reflective access was denied.",
                exception,
            )
        }
    }

    private fun createComponentException(
        apiName: String?,
        componentClass: KClass<*>,
        componentName: String,
        reason: String,
        cause: Throwable,
    ): IllegalArgumentException {
        val configurationSource: String = if (apiName == null) {
            "default network configuration"
        } else {
            "API $apiName"
        }
        return IllegalArgumentException(
            "Cannot create $componentName ${componentClass.qualifiedName} for $configurationSource. $reason",
            cause,
        )
    }
}
