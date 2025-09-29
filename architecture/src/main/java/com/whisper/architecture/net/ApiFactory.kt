package com.whisper.architecture.net

import com.whisper.architecture.net.annotation.BaseUrl
import com.whisper.architecture.net.annotation.Interceptors
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.java
import kotlin.reflect.KClass

/**
 *
 * @author whisper
 * @since 2025/9/4
 */
object ApiFactory {

    private val API_CACHE: MutableMap<KClass<*>, Any> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(clazz: KClass<T>): T {
        return API_CACHE[clazz] as? T ?: synchronized(this) {
            API_CACHE[clazz] as? T ?: buildApi(clazz.java).also { API_CACHE[clazz] = it }
        }
    }

    private fun <T : Any> buildApi(clazz: Class<T>): T {
        val baseUrlAnnotation = clazz.getAnnotation(BaseUrl::class.java)
            ?: throw IllegalArgumentException("Api: $clazz must be annotated with @BaseUrl and value must not be empty !")
        val baseUrl = baseUrlAnnotation.value.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Api: $clazz must be annotated with @BaseUrl and value must not be empty !")

        val interceptors: List<Interceptor> = clazz.getAnnotation(Interceptors::class.java)
            ?.value
            ?.map { it.java.getDeclaredConstructor().newInstance() }
            ?: emptyList()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkhttpFactory.creteClient(interceptors))
            .build()
            .create(clazz)
    }

}




