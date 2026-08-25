package com.whisper.architecture.network

import com.whisper.architecture.network.annotation.Interceptors
import com.whisper.architecture.network.annotation.NetworkInterceptors
import com.whisper.architecture.network.annotation.OkHttpCustomizer
import com.whisper.architecture.network.annotation.RetrofitCustomizer
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer as OkHttpCustomizerComponent
import com.whisper.architecture.network.component.RetrofitCustomizer as RetrofitCustomizerComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import kotlin.reflect.KClass

/**
 * 验证 ApiFactory 对接口级网络组件声明的解析和执行行为.
 *
 * @author whisper
 * @since 2026/07/23
 */
class ApiFactoryTest {

    @Test
    fun createAppliesDeclarationsInOrderAndRejectsDuplicates() {
        val uninstalledException: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            ApiFactory.create(DefaultApi::class)
        }
        assertEquals(
            "ApiFactory.install() must be called before creating APIs.",
            uninstalledException.message,
        )

        val events: MutableList<String> = mutableListOf()
        ApiFactory.install(TestComponentManager(events))

        val orderedApi: OrderedApi = ApiFactory.create(OrderedApi::class)

        assertNotNull(orderedApi)
        assertEquals(
            listOf(
                "configureDefaultOkHttp",
                "resolveInterceptor:FirstApplicationInterceptor",
                "resolveInterceptor:SecondApplicationInterceptor",
                "resolveInterceptor:NetworkInterceptor",
                "resolveOkHttpCustomizer:CompositeOkHttpCustomizer",
                "customizeOkHttp:2:1",
                "configureDefaultRetrofit:2:1",
                "resolveRetrofitCustomizer:CompositeRetrofitCustomizer",
                "customizeRetrofit",
            ),
            events,
        )

        events.clear()
        assertNotNull(ApiFactory.create(DefaultApi::class))
        assertEquals(
            listOf(
                "configureDefaultOkHttp",
                "configureDefaultRetrofit:0:0",
            ),
            events,
        )

        events.clear()
        val duplicateException: IllegalArgumentException =
            assertThrows(IllegalArgumentException::class.java) {
                ApiFactory.create(DuplicateInterceptorApi::class)
            }
        assertTrue(
            duplicateException.message.orEmpty().contains(
                "the same types in @Interceptors and @NetworkInterceptors"
            )
        )
        assertTrue(events.isEmpty())
    }

    @Interceptors(
        FirstApplicationInterceptor::class,
        SecondApplicationInterceptor::class,
    )
    @NetworkInterceptors(NetworkInterceptor::class)
    @OkHttpCustomizer(CompositeOkHttpCustomizer::class)
    @RetrofitCustomizer(CompositeRetrofitCustomizer::class)
    private interface OrderedApi

    private interface DefaultApi

    @Interceptors(FirstApplicationInterceptor::class)
    @NetworkInterceptors(FirstApplicationInterceptor::class)
    private interface DuplicateInterceptorApi

    private class FirstApplicationInterceptor : ProceedingInterceptor()

    private class SecondApplicationInterceptor : ProceedingInterceptor()

    private class NetworkInterceptor : ProceedingInterceptor()

    private open class ProceedingInterceptor : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }

    private class CompositeOkHttpCustomizer(
        private val events: MutableList<String>,
    ) : OkHttpCustomizerComponent {

        override fun customize(builder: OkHttpClient.Builder) {
            events += "customizeOkHttp:${builder.interceptors().size}:${builder.networkInterceptors().size}"
        }
    }

    private class CompositeRetrofitCustomizer(
        private val events: MutableList<String>,
    ) : RetrofitCustomizerComponent {

        override fun customize(builder: Retrofit.Builder) {
            events += "customizeRetrofit"
        }
    }

    private class TestComponentManager(
        private val events: MutableList<String>,
    ) : NetworkComponentManager {

        override fun configureDefaultOkHttp(builder: OkHttpClient.Builder) {
            events += "configureDefaultOkHttp"
        }

        override fun configureDefaultRetrofit(
            retrofitBuilder: Retrofit.Builder,
            okHttpBuilder: OkHttpClient.Builder,
        ) {
            events += "configureDefaultRetrofit:${okHttpBuilder.interceptors().size}:" +
                okHttpBuilder.networkInterceptors().size
            retrofitBuilder
                .baseUrl("https://example.com/")
                .client(okHttpBuilder.build())
        }

        override fun resolveInterceptor(
            apiClass: KClass<*>,
            interceptorClass: KClass<out Interceptor>,
        ): Interceptor {
            events += "resolveInterceptor:${interceptorClass.simpleName}"
            return when (interceptorClass) {
                FirstApplicationInterceptor::class -> FirstApplicationInterceptor()
                SecondApplicationInterceptor::class -> SecondApplicationInterceptor()
                NetworkInterceptor::class -> NetworkInterceptor()
                else -> throw unknownComponent(apiClass, interceptorClass)
            }
        }

        override fun resolveOkHttpCustomizer(
            apiClass: KClass<*>,
            customizerClass: KClass<out OkHttpCustomizerComponent>,
        ): OkHttpCustomizerComponent {
            events += "resolveOkHttpCustomizer:${customizerClass.simpleName}"
            return when (customizerClass) {
                CompositeOkHttpCustomizer::class -> CompositeOkHttpCustomizer(events)
                else -> throw unknownComponent(apiClass, customizerClass)
            }
        }

        override fun resolveRetrofitCustomizer(
            apiClass: KClass<*>,
            customizerClass: KClass<out RetrofitCustomizerComponent>,
        ): RetrofitCustomizerComponent {
            events += "resolveRetrofitCustomizer:${customizerClass.simpleName}"
            return when (customizerClass) {
                CompositeRetrofitCustomizer::class -> CompositeRetrofitCustomizer(events)
                else -> throw unknownComponent(apiClass, customizerClass)
            }
        }

        private fun unknownComponent(
            apiClass: KClass<*>,
            componentClass: KClass<*>,
        ): IllegalArgumentException = IllegalArgumentException(
            "Unknown component ${componentClass.qualifiedName} for ${apiClass.qualifiedName}."
        )
    }
}
