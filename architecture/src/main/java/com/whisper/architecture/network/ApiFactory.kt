package com.whisper.architecture.network

import android.util.Log
import com.whisper.architecture.network.annotation.Interceptors
import com.whisper.architecture.network.annotation.NetworkInterceptors
import com.whisper.architecture.network.annotation.OkHttpCustomizer
import com.whisper.architecture.network.annotation.RetrofitCustomizer
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer as OkHttpCustomizerComponent
import com.whisper.architecture.network.component.RetrofitCustomizer as RetrofitCustomizerComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * 负责创建并缓存架构层声明的 API 实例.
 *
 * API 实例以接口类型作为缓存键. 架构层只读取接口上的网络组件声明并保持构建顺序,
 * 域名、序列化、安全策略和组件生命周期由 app 安装的 [NetworkComponentManager] 决定.
 *
 * @aegis 保护安装/创建 API 契约, 组件执行顺序, 缓存发布和并发恢复语义.
 * @author whisper
 * @since 2026/07/06
 */
object ApiFactory {

    private const val TAG: String = "ApiFactory"

    private val stateReference: AtomicReference<FactoryState?> = AtomicReference(null)
    private val installLock: Any = Any()

    /**
     * 跨状态代次串行化 API 首次构建的锁.
     *
     * 构建期回调不得反向调用 [create] 或 [install], 也不得等待可能调用 [create] 的任务.
     */
    private val apiBuildLock: Any = Any()

    /**
     * 安装应用层网络组件管理器.
     *
     * 该方法应只在应用启动阶段调用一次. 重复安装会替换管理器和 API 缓存,
     * 但已经被调用方持有的旧 API 实例不会失效.
     *
     * @param componentManager 应用层网络组件管理器.
     */
    fun install(componentManager: NetworkComponentManager) {
        val recoveryState: FactoryState? = synchronized(installLock) {
            val currentState: FactoryState? = stateReference.get()
            val replacementState: FactoryState = FactoryState(
                generation = (currentState?.generation ?: 0L) + 1L,
                componentManager = componentManager,
            )
            stateReference.set(replacementState)
            if (currentState == null) null else replacementState
        }
        if (recoveryState != null) {
            Log.w(
                TAG,
                "ApiFactory.install() was called more than once. The component manager and API " +
                    "cache were atomically replaced as a best-effort recovery. Generation: " +
                    "${recoveryState.generation}."
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
        val currentState: FactoryState = requireInstalled()
        return currentState.apiCache[apiClass] as? T ?: synchronized(apiBuildLock) {
            currentState.apiCache[apiClass] as? T ?: run {
                val declarations: ApiNetworkDeclarations = findNetworkDeclarations(apiClass)
                buildApi(apiClass, currentState.componentManager, declarations).also { api: T ->
                    currentState.apiCache[apiClass] = api
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
        declarations.retrofitCustomizer?.let { customizerClass: KClass<out RetrofitCustomizerComponent> ->
            componentManager.resolveRetrofitCustomizer(apiClass, customizerClass)
                .customize(retrofitBuilder)
        }
        return retrofitBuilder.build().create(apiClass.java)
    }

    private fun findNetworkDeclarations(apiClass: KClass<*>): ApiNetworkDeclarations {
        val applicationInterceptors: List<KClass<out Interceptor>> =
            apiClass.java.getAnnotation(Interceptors::class.java)?.value?.toList().orEmpty()
        val networkInterceptors: List<KClass<out Interceptor>> =
            apiClass.java.getAnnotation(NetworkInterceptors::class.java)?.value?.toList().orEmpty()
        val okHttpCustomizer: KClass<out OkHttpCustomizerComponent>? =
            apiClass.java.getAnnotation(OkHttpCustomizer::class.java)?.value
        val retrofitCustomizer: KClass<out RetrofitCustomizerComponent>? =
            apiClass.java.getAnnotation(RetrofitCustomizer::class.java)?.value

        requireUniqueDeclarations(apiClass, Interceptors::class.simpleName.orEmpty(), applicationInterceptors)
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
                "@${Interceptors::class.simpleName} and @${NetworkInterceptors::class.simpleName}: " +
                "$sharedTypeNames."
        }
    }

    private fun requireInstalled(): FactoryState = checkNotNull(stateReference.get()) {
        "ApiFactory.install() must be called before creating APIs."
    }

    private class FactoryState(
        val generation: Long,
        val componentManager: NetworkComponentManager,
        val apiCache: MutableMap<KClass<*>, Any> = ConcurrentHashMap(),
    )

    private data class ApiNetworkDeclarations(
        val applicationInterceptors: List<KClass<out Interceptor>>,
        val networkInterceptors: List<KClass<out Interceptor>>,
        val okHttpCustomizer: KClass<out OkHttpCustomizerComponent>?,
        val retrofitCustomizer: KClass<out RetrofitCustomizerComponent>?,
    )
}
