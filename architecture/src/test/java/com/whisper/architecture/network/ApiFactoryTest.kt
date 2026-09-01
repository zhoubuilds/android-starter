package com.whisper.architecture.network

import com.whisper.architecture.network.annotation.ApplicationInterceptors
import com.whisper.architecture.network.annotation.NetworkInterceptors
import com.whisper.architecture.network.annotation.UseOkHttpCustomizer
import com.whisper.architecture.network.annotation.UseRetrofitCustomizer
import com.whisper.architecture.network.component.NetworkComponentManager
import com.whisper.architecture.network.component.OkHttpCustomizer
import com.whisper.architecture.network.component.RetrofitCustomizer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * 验证 ApiFactory 对接口级网络组件声明的解析和执行行为.
 *
 * @author whisper
 * @since 2026/07/23
 */
class ApiFactoryTest {

    @Before
    fun resetInstallation() {
        // 仅在测试侧隔离单例状态, 避免为生产 API 增加重置入口.
        val installationReferenceField: Field = ApiFactory::class.java.getDeclaredField("installationReference")
        installationReferenceField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val installationReference: AtomicReference<Any?> =
            installationReferenceField.get(ApiFactory) as AtomicReference<Any?>
        installationReference.set(null)
    }

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
        val cachedDefaultApi: DefaultApi = ApiFactory.create(DefaultApi::class)
        assertNotNull(cachedDefaultApi)
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
                "the same types in @ApplicationInterceptors and @NetworkInterceptors"
            )
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun repeatedInstallFailsAndKeepsFirstInstallation() {
        val firstManager: CountingComponentManager = CountingComponentManager()
        val rejectedManager: CountingComponentManager = CountingComponentManager()
        ApiFactory.install(firstManager)
        val firstApi: CachedApi = ApiFactory.create(CachedApi::class)

        val duplicateInstallException: IllegalStateException =
            assertThrows(IllegalStateException::class.java) {
                ApiFactory.install(rejectedManager)
            }

        assertEquals(EXPECTED_DUPLICATE_INSTALL_FAILURE, duplicateInstallException.message)
        assertSame(firstApi, ApiFactory.create(CachedApi::class))
        assertEquals(1, firstManager.buildCount.get())
        assertEquals(0, rejectedManager.buildCount.get())
    }

    @Test
    fun createCachesApiWithinInstallation() {
        val componentManager: CountingComponentManager = CountingComponentManager()
        ApiFactory.install(componentManager)

        val firstApi: CachedApi = ApiFactory.create(CachedApi::class)
        val secondApi: CachedApi = ApiFactory.create(CachedApi::class)

        assertSame(firstApi, secondApi)
        assertEquals(1, componentManager.buildCount.get())
    }

    @Test
    fun concurrentFirstCreateBuildsAndPublishesSingleApi() {
        val workerCount: Int = 8
        val workersReady: CountDownLatch = CountDownLatch(workerCount)
        val startWorkers: CountDownLatch = CountDownLatch(1)
        val buildEntered: CountDownLatch = CountDownLatch(1)
        val allowBuild: CountDownLatch = CountDownLatch(1)
        val componentManager: CountingComponentManager = CountingComponentManager { _: Int ->
            buildEntered.countDown()
            check(allowBuild.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting to continue the concurrent API build."
            }
        }
        ApiFactory.install(componentManager)
        val executor: ExecutorService = Executors.newFixedThreadPool(workerCount)

        try {
            val apiFutures: List<Future<ConcurrentApi>> = List(workerCount) {
                executor.submit<ConcurrentApi> {
                    workersReady.countDown()
                    check(startWorkers.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        "Timed out waiting to start concurrent API creation."
                    }
                    ApiFactory.create(ConcurrentApi::class)
                }
            }
            assertTrue(workersReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            startWorkers.countDown()
            assertTrue(buildEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            allowBuild.countDown()

            val apis: List<ConcurrentApi> = apiFutures.map { future: Future<ConcurrentApi> ->
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            val firstApi: ConcurrentApi = apis.first()
            apis.forEach { api: ConcurrentApi ->
                assertSame(firstApi, api)
            }
            assertEquals(1, componentManager.buildCount.get())
        } finally {
            startWorkers.countDown()
            allowBuild.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentInstallPublishesOneInstallationAndFailsOthers() {
        val installerCount: Int = 8
        val installersReady: CountDownLatch = CountDownLatch(installerCount)
        val startInstallers: CountDownLatch = CountDownLatch(1)
        val componentManagers: List<CountingComponentManager> =
            List(installerCount) { CountingComponentManager() }
        val executor: ExecutorService = Executors.newFixedThreadPool(installerCount)

        try {
            val installFutures: List<Future<Throwable?>> = componentManagers.map { componentManager ->
                executor.submit<Throwable?> {
                    installersReady.countDown()
                    check(startInstallers.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        "Timed out waiting to start concurrent installation."
                    }
                    runCatching {
                        ApiFactory.install(componentManager)
                    }.exceptionOrNull()
                }
            }
            assertTrue(installersReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            startInstallers.countDown()
            val installFailures: List<Throwable> = installFutures.mapNotNull { future: Future<Throwable?> ->
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }

            assertEquals(installerCount - 1, installFailures.size)
            installFailures.forEach { failure: Throwable ->
                assertTrue(failure is IllegalStateException)
                assertEquals(EXPECTED_DUPLICATE_INSTALL_FAILURE, failure.message)
            }

            assertNotNull(ApiFactory.create(ConcurrentInstallApi::class))
            assertEquals(1, componentManagers.sumOf { manager: CountingComponentManager ->
                manager.buildCount.get()
            })
        } finally {
            startInstallers.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun installDuringCreateFailsAndKeepsFirstInstallation() {
        val firstBuildEntered: CountDownLatch = CountDownLatch(1)
        val allowFirstBuild: CountDownLatch = CountDownLatch(1)
        val firstManager: CountingComponentManager = CountingComponentManager { _: Int ->
            firstBuildEntered.countDown()
            check(allowFirstBuild.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting to continue the first API build."
            }
        }
        val rejectedManager: CountingComponentManager = CountingComponentManager()
        val executor: ExecutorService = Executors.newFixedThreadPool(2)
        ApiFactory.install(firstManager)

        try {
            val firstApiFuture: Future<InstallationRaceApi> = executor.submit<InstallationRaceApi> {
                ApiFactory.create(InstallationRaceApi::class)
            }
            assertTrue(firstBuildEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            val duplicateInstallException: IllegalStateException =
                assertThrows(IllegalStateException::class.java) {
                    ApiFactory.install(rejectedManager)
                }
            val cachedApiFuture: Future<InstallationRaceApi> = executor.submit<InstallationRaceApi> {
                ApiFactory.create(InstallationRaceApi::class)
            }
            allowFirstBuild.countDown()

            val firstApi: InstallationRaceApi = firstApiFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val cachedApi: InstallationRaceApi = cachedApiFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertEquals(EXPECTED_DUPLICATE_INSTALL_FAILURE, duplicateInstallException.message)
            assertSame(firstApi, cachedApi)
            assertSame(firstApi, ApiFactory.create(InstallationRaceApi::class))
            assertEquals(1, firstManager.buildCount.get())
            assertEquals(0, rejectedManager.buildCount.get())
        } finally {
            allowFirstBuild.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedCreateCanRetryWithoutPublishingPartialCache() {
        val componentManager: CountingComponentManager = CountingComponentManager { attempt: Int ->
            if (attempt == 1) {
                throw IllegalStateException(EXPECTED_BUILD_FAILURE)
            }
        }
        ApiFactory.install(componentManager)

        val failure: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            ApiFactory.create(RetryApi::class)
        }
        assertEquals(EXPECTED_BUILD_FAILURE, failure.message)

        val recoveredApi: RetryApi = ApiFactory.create(RetryApi::class)
        val cachedApi: RetryApi = ApiFactory.create(RetryApi::class)

        assertSame(recoveredApi, cachedApi)
        assertEquals(2, componentManager.buildCount.get())
    }

    @ApplicationInterceptors(
        FirstApplicationInterceptor::class,
        SecondApplicationInterceptor::class,
    )
    @NetworkInterceptors(NetworkInterceptor::class)
    @UseOkHttpCustomizer(CompositeOkHttpCustomizer::class)
    @UseRetrofitCustomizer(CompositeRetrofitCustomizer::class)
    private interface OrderedApi

    private interface DefaultApi

    private interface CachedApi

    private interface ConcurrentApi

    private interface ConcurrentInstallApi

    private interface InstallationRaceApi

    private interface RetryApi

    @ApplicationInterceptors(FirstApplicationInterceptor::class)
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
    ) : OkHttpCustomizer {

        override fun customize(builder: OkHttpClient.Builder) {
            events += "customizeOkHttp:${builder.interceptors().size}:${builder.networkInterceptors().size}"
        }
    }

    private class CompositeRetrofitCustomizer(
        private val events: MutableList<String>,
    ) : RetrofitCustomizer {

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
            customizerClass: KClass<out OkHttpCustomizer>,
        ): OkHttpCustomizer {
            events += "resolveOkHttpCustomizer:${customizerClass.simpleName}"
            return when (customizerClass) {
                CompositeOkHttpCustomizer::class -> CompositeOkHttpCustomizer(events)
                else -> throw unknownComponent(apiClass, customizerClass)
            }
        }

        override fun resolveRetrofitCustomizer(
            apiClass: KClass<*>,
            customizerClass: KClass<out RetrofitCustomizer>,
        ): RetrofitCustomizer {
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

    private class CountingComponentManager(
        private val onConfigureDefaultOkHttp: (attempt: Int) -> Unit = { _: Int -> },
    ) : NetworkComponentManager {

        val buildCount: AtomicInteger = AtomicInteger(0)

        override fun configureDefaultOkHttp(builder: OkHttpClient.Builder) {
            val attempt: Int = buildCount.incrementAndGet()
            onConfigureDefaultOkHttp(attempt)
        }

        override fun configureDefaultRetrofit(
            retrofitBuilder: Retrofit.Builder,
            okHttpBuilder: OkHttpClient.Builder,
        ) {
            retrofitBuilder
                .baseUrl(NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL)
                .client(okHttpBuilder.build())
        }

        override fun resolveInterceptor(
            apiClass: KClass<*>,
            interceptorClass: KClass<out Interceptor>,
        ): Interceptor = error("No interceptor is declared for ${apiClass.qualifiedName}.")

        override fun resolveOkHttpCustomizer(
            apiClass: KClass<*>,
            customizerClass: KClass<out OkHttpCustomizer>,
        ): OkHttpCustomizer = error("No OkHttp customizer is declared for ${apiClass.qualifiedName}.")

        override fun resolveRetrofitCustomizer(
            apiClass: KClass<*>,
            customizerClass: KClass<out RetrofitCustomizer>,
        ): RetrofitCustomizer = error("No Retrofit customizer is declared for ${apiClass.qualifiedName}.")
    }

    private companion object {

        const val TIMEOUT_SECONDS: Long = 5L
        const val EXPECTED_BUILD_FAILURE: String = "Expected API build failure."
        const val EXPECTED_DUPLICATE_INSTALL_FAILURE: String = "ApiFactory has already been initialized."
    }
}
