package com.whisper.architecture.network

import com.whisper.architecture.network.ApiFactory.create
import com.whisper.architecture.network.ApiFactory.install
import com.whisper.architecture.network.annotation.ApplicationInterceptors
import com.whisper.architecture.network.annotation.NetworkInterceptors
import com.whisper.architecture.network.annotation.UseOkHttpCustomizer
import com.whisper.architecture.network.annotation.UseRetrofitCustomizer
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import com.whisper.architecture.network.component.RetrofitCustomizer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlin.reflect.KClass

/**
 * 负责创建并缓存架构层声明的 API 实例.
 *
 * API 实例以接口类型作为缓存键. 架构层只读取接口上的网络组件声明并保持构建顺序,
 * 域名、序列化、安全策略和组件生命周期由 app 安装的 [NetworkComponentManager] 决定.
 *
 * @aegis 保护安装/创建 API 契约, 组件执行顺序, 缓存发布和并发恢复语义.
 * @aegis-audit 2026-08-26 | whisper | 移除未使用的代际编号, 使用原子安装快照保留最后配置.
 * @aegis-audit 2026-08-26 | whisper | 重命名网络声明注解以区分精确语义和组件契约.
 *
 * @author whisper
 * @since 2026/07/06
 */
object ApiFactory {

    private val logger: Logger = Logger.getLogger(ApiFactory::class.java.name)

    private val installationReference: AtomicReference<Installation?> = AtomicReference(null)

    /**
     * 串行化 API 首次构建的锁.
     *
     * 构建期回调不得反向调用 [create] 或 [install], 也不得等待可能调用 [create] 的任务.
     */
    private val apiBuildLock: Any = Any()

    /**
     * 安装应用层网络组件管理器.
     *
     * 该方法应只在应用启动阶段调用一次. 重复安装不是受支持的运行模式；发生误用时会原子替换为最后一次安装及其独立缓存,
     * 但已经被调用方持有或正在创建的旧 API 实例不会失效.
     *
     * @param componentManager 应用层网络组件管理器.
     */
    fun install(componentManager: NetworkComponentManager) {
        val previousInstallation: Installation? = installationReference.getAndSet(
            Installation(
                componentManager = componentManager,
            )
        )
        if (previousInstallation != null) {
            logger.warning(
                "ApiFactory.install() was called more than once. The latest component manager " +
                    "and a new API cache were atomically installed as a best-effort recovery."
            )
        }
    }

    /**
     * 获取指定接口的 API 实例.
     *
     * @param apiClass Retrofit API 接口类型.
     * @return 已缓存或新创建的 API 实例.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(apiClass: KClass<T>): T {
        val installation: Installation = requireInstallation()
        return installation.apiCache[apiClass] as? T ?: synchronized(apiBuildLock) {
            installation.apiCache[apiClass] as? T ?: run {
                val declarations: ApiNetworkDeclarations = findNetworkDeclarations(apiClass)
                buildApi(apiClass, installation.componentManager, declarations).also { api: T ->
                    installation.apiCache[apiClass] = api
                }
            }
        }
    }

    private fun <T : Any> buildApi(
        apiClass: KClass<T>,
        componentManager: NetworkComponentManager,
        declarations: ApiNetworkDeclarations,
    ): T {
        val okHttpBuilder: OkHttpClient.Builder = OkHttpClientFactory.createBuilder(
            componentManager = componentManager,
            apiClass = apiClass,
            applicationInterceptorClasses = declarations.applicationInterceptors,
            networkInterceptorClasses = declarations.networkInterceptors,
            okHttpCustomizerClass = declarations.okHttpCustomizer,
        )
        val retrofitBuilder: Retrofit.Builder = Retrofit.Builder()
        componentManager.configureDefaultRetrofit(retrofitBuilder, okHttpBuilder)
        declarations.retrofitCustomizer?.let { customizerClass: KClass<out RetrofitCustomizer> ->
            componentManager.resolveRetrofitCustomizer(apiClass, customizerClass)
                .customize(retrofitBuilder)
        }
        return retrofitBuilder.build().create(apiClass.java)
    }

    private fun findNetworkDeclarations(apiClass: KClass<*>): ApiNetworkDeclarations {
        val applicationInterceptors: List<KClass<out Interceptor>> =
            apiClass.java.getAnnotation(ApplicationInterceptors::class.java)?.value?.toList().orEmpty()
        val networkInterceptors: List<KClass<out Interceptor>> =
            apiClass.java.getAnnotation(NetworkInterceptors::class.java)?.value?.toList().orEmpty()
        val okHttpCustomizer: KClass<out OkHttpCustomizer>? =
            apiClass.java.getAnnotation(UseOkHttpCustomizer::class.java)?.value
        val retrofitCustomizer: KClass<out RetrofitCustomizer>? =
            apiClass.java.getAnnotation(UseRetrofitCustomizer::class.java)?.value

        requireUniqueDeclarations(
            apiClass,
            ApplicationInterceptors::class.simpleName.orEmpty(),
            applicationInterceptors,
        )
        requireUniqueDeclarations(apiClass, NetworkInterceptors::class.simpleName.orEmpty(), networkInterceptors)
        requireDistinctInterceptorChains(apiClass, applicationInterceptors, networkInterceptors)

        return ApiNetworkDeclarations(
            applicationInterceptors = applicationInterceptors,
            networkInterceptors = networkInterceptors,
            okHttpCustomizer = okHttpCustomizer,
            retrofitCustomizer = retrofitCustomizer,
        )
    }

    private fun requireUniqueDeclarations(
        apiClass: KClass<*>,
        annotationName: String,
        declaredTypes: List<KClass<*>>,
    ) {
        val duplicateTypes: Set<KClass<*>> = declaredTypes
            .groupingBy { declaredType: KClass<*> -> declaredType }
            .eachCount()
            .filterValues { count: Int -> count > 1 }
            .keys
        require(duplicateTypes.isEmpty()) {
            val duplicateNames: String = duplicateTypes.joinToString { type: KClass<*> ->
                type.qualifiedName ?: type.toString()
            }
            "API interface ${apiClass.qualifiedName} declares duplicate types in @$annotationName: " +
                "$duplicateNames."
        }
    }

    private fun requireDistinctInterceptorChains(
        apiClass: KClass<*>,
        applicationInterceptors: List<KClass<out Interceptor>>,
        networkInterceptors: List<KClass<out Interceptor>>,
    ) {
        val sharedTypes: Set<KClass<out Interceptor>> =
            applicationInterceptors.toSet().intersect(networkInterceptors.toSet())
        require(sharedTypes.isEmpty()) {
            val sharedTypeNames: String = sharedTypes.joinToString { type: KClass<out Interceptor> ->
                type.qualifiedName ?: type.toString()
            }
            "API interface ${apiClass.qualifiedName} declares the same types in " +
                "@${ApplicationInterceptors::class.simpleName} and " +
                "@${NetworkInterceptors::class.simpleName}: " +
                "$sharedTypeNames."
        }
    }

    private fun requireInstallation(): Installation = checkNotNull(installationReference.get()) {
        "ApiFactory.install() must be called before creating APIs."
    }

    private class Installation(
        val componentManager: NetworkComponentManager,
        val apiCache: MutableMap<KClass<*>, Any> = ConcurrentHashMap(),
    )

    private data class ApiNetworkDeclarations(
        val applicationInterceptors: List<KClass<out Interceptor>>,
        val networkInterceptors: List<KClass<out Interceptor>>,
        val okHttpCustomizer: KClass<out OkHttpCustomizer>?,
        val retrofitCustomizer: KClass<out RetrofitCustomizer>?,
    )
}
