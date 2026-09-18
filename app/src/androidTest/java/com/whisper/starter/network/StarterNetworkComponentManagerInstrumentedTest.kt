package com.whisper.starter.network

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import com.whisper.architecture.network.component.RetrofitCustomizer
import com.whisper.feature.project.data.network.GithubEndpointRoutingInterceptor
import com.whisper.feature.project.data.network.GithubSystemParametersInterceptor
import com.whisper.kit.utils.ClientInfoUtils
import com.whisper.starter.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * 验证管理器使用真实 Application 装配默认配置和 GitHub 网络组件.
 *
 * 请求链末端使用本地成功响应截断网络交换, 只验证请求构建和组件装配, 不访问 GitHub.
 *
 * @author whisper
 * @since 2026/09/16
 */
@RunWith(AndroidJUnit4::class)
class StarterNetworkComponentManagerInstrumentedTest {

    private companion object {

        const val TIMEOUT_SECONDS: Long = 10L
    }

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun configureDefaults_withoutApiInterceptors_keepsPlaceholderAndCommonHeaders() {
        val manager = StarterNetworkComponentManager(application)
        val clientBuilder = OkHttpClient.Builder()
        manager.configureDefaultOkHttp(clientBuilder)
        var proceededRequest: Request? = null
        clientBuilder.addInterceptor { chain ->
            proceededRequest = chain.request()
            successfulResponse(chain.request())
        }
        val retrofitBuilder = Retrofit.Builder()
        manager.configureDefaultRetrofit(retrofitBuilder, clientBuilder)
        val retrofit: Retrofit = retrofitBuilder.build()

        retrofit.create(TestApi::class.java).getProject("demo").execute().body()?.close()

        val request: Request = checkNotNull(proceededRequest)
        assertEquals(NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL, retrofit.baseUrl().toString())
        assertEquals("placeholder.invalid", request.url.host)
        assertEquals(application.packageName, request.header("Package-Name"))
        assertEquals(
            (AppCompatDelegate.getApplicationLocales()[0]
                ?: ClientInfoUtils.getPrimaryAppLocale(application)).toLanguageTag(),
            request.header("Locale"),
        )
        assertEquals(BuildConfig.VERSION_NAME, request.header("App-Version-Name"))
        assertNull(request.header("X-GitHub-Api-Version"))
        assertNull(request.header("Accept"))
    }

    @Test
    fun configureDefaultOkHttp_withMultipleBuilders_reusesPooledHeaders() {
        val manager = StarterNetworkComponentManager(application)
        val firstBuilder = OkHttpClient.Builder()
        val secondBuilder = OkHttpClient.Builder()

        manager.configureDefaultOkHttp(firstBuilder)
        manager.configureDefaultOkHttp(secondBuilder)

        val headersInterceptor: Interceptor = firstBuilder.interceptors().single()
        assertSame(headersInterceptor, secondBuilder.interceptors().single())
        assertSame(
            headersInterceptor,
            manager.resolveInterceptor(TestApi::class, StarterRequestHeadersInterceptor::class),
        )
    }

    @Test
    fun resolveInterceptor_withGithubDeclarations_reusesComponentsAndRoutesRequests() {
        val manager = StarterNetworkComponentManager(application)
        val endpointInterceptor: Interceptor =
            manager.resolveInterceptor(TestApi::class, GithubEndpointRoutingInterceptor::class)
        val parametersInterceptor: Interceptor =
            manager.resolveInterceptor(TestApi::class, GithubSystemParametersInterceptor::class)
        assertSame(
            endpointInterceptor,
            manager.resolveInterceptor(AnotherTestApi::class, GithubEndpointRoutingInterceptor::class),
        )
        assertSame(
            parametersInterceptor,
            manager.resolveInterceptor(AnotherTestApi::class, GithubSystemParametersInterceptor::class),
        )

        val clientBuilder = OkHttpClient.Builder()
        manager.configureDefaultOkHttp(clientBuilder)
        var proceededRequest: Request? = null
        clientBuilder
            .addInterceptor(endpointInterceptor)
            .addInterceptor(parametersInterceptor)
            .addInterceptor { chain ->
                proceededRequest = chain.request()
                successfulResponse(chain.request())
            }
        val retrofitBuilder = Retrofit.Builder()
        manager.configureDefaultRetrofit(retrofitBuilder, clientBuilder)
        val call: Call<ResponseBody> = retrofitBuilder.build()
            .create(TestApi::class.java).getProject("demo list")
        assertEquals("placeholder.invalid", call.request().url.host)

        call.execute().body()?.close()

        val request: Request = checkNotNull(proceededRequest)
        val githubEndpoint: HttpUrl = BuildConfig.GITHUB_API_ENDPOINT.toHttpUrl()
        assertEquals(githubEndpoint.scheme, request.url.scheme)
        assertEquals(githubEndpoint.host, request.url.host)
        assertEquals(githubEndpoint.port, request.url.port)
        assertEquals("/repos/zhoubuilds/android-starter", request.url.encodedPath)
        assertEquals("demo list", request.url.queryParameter("source"))
        assertEquals("application/vnd.github+json", request.header("Accept"))
        assertEquals("2026-03-10", request.header("X-GitHub-Api-Version"))
        assertEquals("${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}", request.header("User-Agent"))
        assertEquals(application.packageName, request.header("Package-Name"))
    }

    @Test
    fun resolveInterceptor_whenFirstResolvedConcurrently_createsOneInstancePerDeclaration() {
        val manager = StarterNetworkComponentManager(application)
        CountingInterceptor.constructionCount.set(0)
        val interceptorClasses: List<KClass<out Interceptor>> = listOf(
            StarterRequestHeadersInterceptor::class,
            GithubEndpointRoutingInterceptor::class,
            GithubSystemParametersInterceptor::class,
            CountingInterceptor::class,
        )

        for (interceptorClass in interceptorClasses) {
            val interceptors: List<Interceptor> = resolveConcurrently { workerIndex ->
                val apiClass: KClass<*> = if (workerIndex % 2 == 0) {
                    TestApi::class
                } else {
                    AnotherTestApi::class
                }
                manager.resolveInterceptor(apiClass, interceptorClass)
            }

            for (interceptor in interceptors) {
                assertSame(interceptors.first(), interceptor)
            }
        }
        assertEquals(1, CountingInterceptor.constructionCount.get())
    }

    @Test
    fun resolveInterceptor_withDifferentManagers_keepsInstancesIsolated() {
        val firstManager = StarterNetworkComponentManager(application)
        val secondManager = StarterNetworkComponentManager(application)
        val interceptorClasses: List<KClass<out Interceptor>> = listOf(
            StarterRequestHeadersInterceptor::class,
            GithubEndpointRoutingInterceptor::class,
            GithubSystemParametersInterceptor::class,
        )

        for (interceptorClass in interceptorClasses) {
            assertNotSame(
                firstManager.resolveInterceptor(TestApi::class, interceptorClass),
                secondManager.resolveInterceptor(TestApi::class, interceptorClass),
            )
        }
    }

    @Test
    fun resolveCustomizers_withNoArgConstructors_reusesInstancesAndAppliesConfiguration() {
        val manager = StarterNetworkComponentManager(application)
        val okHttpCustomizer: OkHttpCustomizer =
            manager.resolveOkHttpCustomizer(TestApi::class, NoArgOkHttpCustomizer::class)
        val retrofitCustomizer: RetrofitCustomizer =
            manager.resolveRetrofitCustomizer(TestApi::class, NoArgRetrofitCustomizer::class)

        assertSame(
            okHttpCustomizer,
            manager.resolveOkHttpCustomizer(AnotherTestApi::class, NoArgOkHttpCustomizer::class),
        )
        assertSame(
            retrofitCustomizer,
            manager.resolveRetrofitCustomizer(AnotherTestApi::class, NoArgRetrofitCustomizer::class),
        )

        val clientBuilder = OkHttpClient.Builder()
        manager.configureDefaultOkHttp(clientBuilder)
        okHttpCustomizer.customize(clientBuilder)
        assertEquals(17_000, clientBuilder.build().readTimeoutMillis)

        val retrofitBuilder = Retrofit.Builder()
        manager.configureDefaultRetrofit(retrofitBuilder, clientBuilder)
        retrofitCustomizer.customize(retrofitBuilder)
        val retrofit: Retrofit = retrofitBuilder.build()
        assertEquals("https://customizer.example/api/", retrofit.baseUrl().toString())
        assertEquals(
            "https://customizer.example/api/repos/zhoubuilds/android-starter?source=demo",
            retrofit.create(TestApi::class.java).getProject("demo").request().url.toString(),
        )
    }

    @Test
    fun resolveInterceptor_whenConstructorFails_preservesDiagnosticsAndRetriesCreation() {
        val manager = StarterNetworkComponentManager(application)
        RetryableInterceptor.constructionCount.set(0)

        val exception: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            manager.resolveInterceptor(TestApi::class, RetryableInterceptor::class)
        }

        val message: String = checkNotNull(exception.message)
        assertTrue(message.contains(TestApi::class.java.name))
        assertTrue(message.contains(checkNotNull(RetryableInterceptor::class.qualifiedName)))
        assertSame(RetryableInterceptor.constructorFailure, exception.cause)
        assertEquals(1, RetryableInterceptor.constructionCount.get())

        val interceptor: Interceptor =
            manager.resolveInterceptor(AnotherTestApi::class, RetryableInterceptor::class)
        assertSame(interceptor, manager.resolveInterceptor(TestApi::class, RetryableInterceptor::class))
        assertEquals(2, RetryableInterceptor.constructionCount.get())
    }

    /**
     * 等待所有工作线程就绪后同时解析, 避免首个线程已完成创建而掩盖实例池的首次竞争.
     */
    private fun resolveConcurrently(resolve: (Int) -> Interceptor): List<Interceptor> {
        val workerCount: Int = 8
        val workersReady = CountDownLatch(workerCount)
        val startWorkers = CountDownLatch(1)
        val executor: ExecutorService = Executors.newFixedThreadPool(workerCount)
        try {
            val futures: List<Future<Interceptor>> = List(workerCount) { workerIndex ->
                executor.submit<Interceptor> {
                    workersReady.countDown()
                    check(startWorkers.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        "Timed out waiting to start concurrent component resolution."
                    }
                    resolve(workerIndex)
                }
            }
            assertTrue(
                "Timed out waiting for component resolution workers.",
                workersReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            startWorkers.countDown()
            return futures.map { future -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            startWorkers.countDown()
            executor.shutdownNow()
        }
    }

    /**
     * 返回不经过网络的空 JSON 响应, 供末端拦截器保留实际构建出的请求.
     */
    private fun successfulResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("{}".toResponseBody())
        .build()

    /**
     * 提供用于观察 Retrofit 请求构建结果的测试接口.
     */
    private interface TestApi {

        @GET("repos/zhoubuilds/android-starter")
        fun getProject(@Query("source") source: String): Call<ResponseBody>
    }

    /**
     * 提供不同的 API 类型以验证组件可以跨接口复用.
     */
    private interface AnotherTestApi

    /**
     * 记录无参反射组件的构造次数, 验证并发首次解析不会重复创建.
     */
    private class CountingInterceptor : Interceptor {

        companion object {

            val constructionCount: AtomicInteger = AtomicInteger(0)
        }

        init {
            constructionCount.incrementAndGet()
        }

        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }

    /**
     * 无依赖定制器通过管理器反射创建, 用于验证接口级 OkHttp 配置.
     */
    private class NoArgOkHttpCustomizer : OkHttpCustomizer {

        override fun customize(builder: OkHttpClient.Builder) {
            builder.readTimeout(17L, TimeUnit.SECONDS)
        }
    }

    /**
     * 无依赖定制器通过管理器反射创建, 用于验证 Retrofit 配置仍可按接口覆盖.
     */
    private class NoArgRetrofitCustomizer : RetrofitCustomizer {

        override fun customize(builder: Retrofit.Builder) {
            builder.baseUrl("https://customizer.example/api/")
        }
    }

    /**
     * 首次构造失败, 用于验证反射诊断和实例池的失败重试行为.
     */
    private class RetryableInterceptor : Interceptor {

        companion object {

            val constructionCount: AtomicInteger = AtomicInteger(0)
            val constructorFailure: IllegalStateException = IllegalStateException("First construction failed.")
        }

        init {
            if (constructionCount.incrementAndGet() == 1) {
                throw constructorFailure
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }
}
