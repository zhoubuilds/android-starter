package com.whisper.architecture.net

import com.whisper.architecture.net.ApiFactory.INTERCEPTOR_REGISTRY
import com.whisper.architecture.net.annotation.BaseUrl
import com.whisper.architecture.net.annotation.Interceptors
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * API工厂类
 *
 * @author whisper
 * @since 2025/9/4
 */
object ApiFactory {

    /**
     * API缓存
     */
    private val API_CACHE: MutableMap<KClass<*>, Any> = ConcurrentHashMap()

    /**
     * 这是拦截器的注册表
     *
     * * key : Class
     * * value : Interceptor
     *
     * 如果使用类似Hilt一类的依赖注入工具, 可以直接注入拦截器单例. 方便构造参数处理, 也能节省反射创建对象的开销
     *
     * example:
     * ```Kotlin
     * @Module
     * @InstallIn(SingletonComponent::class)
     * object NetworkModule {
     *
     *     @Provides
     *     @Singleton
     *     fun provideAuthInterceptor(userMgr: UserManager): AuthInterceptor {
     *         return AuthInterceptor(userMgr).also { ApiFactory.registerInterceptor(it) }
     *     }
     *
     *     @Provides
     *     @Singleton
     *     fun provideSignInterceptor(config: AppConfig): SignInterceptor {
     *         return SignInterceptor(config).also { ApiFactory.registerInterceptor(it) }
     *     }
     * }
     * ```
     *
     * 这个注册表是第一优先级
     */
    private val INTERCEPTOR_REGISTRY: MutableMap<KClass<out Interceptor>, Interceptor> =
        ConcurrentHashMap<KClass<out Interceptor>, Interceptor>()

    /**
     * 这是反射创建的拦截器缓存
     *
     * 避免反复的反射创建对象的开销, 此类拦截器需要注意并发安全
     *
     * 这个缓存是第二优先级
     */
    private val INTERCEPTOR_CACHE: MutableMap<KClass<out Interceptor>, Interceptor> =
        ConcurrentHashMap<KClass<out Interceptor>, Interceptor>()


    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(clazz: KClass<T>): T {
        return API_CACHE[clazz] as? T ?: synchronized(this) {
            API_CACHE[clazz] as? T ?: buildApi(clazz.java).also { API_CACHE[clazz] = it }
        }
    }

    /**
     * 注册拦截器
     *
     * 这由外部的依赖注入工具调用
     *
     * @see INTERCEPTOR_REGISTRY
     */
    fun registerInterceptor(interceptor: Interceptor) {
        INTERCEPTOR_REGISTRY[interceptor::class] = interceptor
    }

    private fun <T : Any> buildApi(clazz: Class<T>): T {
        val baseUrlAnnotation = clazz.getAnnotation(BaseUrl::class.java)
            ?: throw IllegalArgumentException("Api: $clazz must be annotated with @BaseUrl and value must not be empty !")
        val baseUrl = baseUrlAnnotation.value.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Api: $clazz must be annotated with @BaseUrl and value must not be empty !")

        val interceptors: List<Interceptor> = clazz.getAnnotation(Interceptors::class.java)
            ?.value
            ?.map { klass ->
                INTERCEPTOR_REGISTRY[klass]
                    ?: INTERCEPTOR_CACHE[klass]
                    ?: klass.java.getDeclaredConstructor().newInstance()
                        .also { INTERCEPTOR_CACHE[klass] = it }
            }
            ?: emptyList()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkhttpFactory.creteClient(interceptors))
            .build()
            .create(clazz)
    }

}




