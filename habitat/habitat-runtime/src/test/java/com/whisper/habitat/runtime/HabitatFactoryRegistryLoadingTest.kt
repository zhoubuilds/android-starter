package com.whisper.habitat.runtime

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.util.Log
import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import com.whisper.habitat.runtime.registry.HabitatRegistry
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowPackageManager

/**
 * 验证 Habitat Registry Manifest 加载的运行时降级边界.
 *
 * @author whisper
 * @since 2026/08/31
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitatFactoryRegistryLoadingTest {

    @Before
    fun setUp() {
        resetDaoBindings()
        clearRegistryMetadata(RuntimeEnvironment.getApplication())
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        resetDaoBindings()
        clearRegistryMetadata(RuntimeEnvironment.getApplication())
        ShadowLog.clear()
    }

    /**
     * 验证 Registry metadata 缺失时记录 warning 并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenRegistryMetadataIsMissingWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()

        HabitatFactory.initialize(application)

        val registryWarning: ShadowLog.LogItem = warningContaining("No Habitat registry metadata was found")
        assertEquals(Log.WARN, registryWarning.type)
        assertNull(registryWarning.throwable)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证 Registry 类缺失时记录 warning, 完成初始化并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenRegistryClassIsMissingWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRegistryClassMetadata(application, MISSING_REGISTRY_CLASS_NAME)

        HabitatFactory.initialize(application)

        val registryWarning: ShadowLog.LogItem = ShadowLog.getLogsForTag(LOG_TAG)
            .single { item: ShadowLog.LogItem ->
                item.msg.contains(MISSING_REGISTRY_CLASS_NAME)
            }
        assertEquals(Log.WARN, registryWarning.type)
        assertTrue(registryWarning.throwable is ClassNotFoundException)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证 metadata 指向非 Registry 类型时记录 warning 并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenRegistryTypeIsInvalidWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRegistryClassMetadata(application, NotRegistry::class.java.name)

        HabitatFactory.initialize(application)

        val registryWarning: ShadowLog.LogItem = warningContaining("does not implement")
        assertEquals(Log.WARN, registryWarning.type)
        assertTrue(registryWarning.throwable is ClassCastException)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证 Registry 缺少无参构造时记录 warning 并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenRegistryConstructionFailsWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRegistryClassMetadata(application, RegistryWithoutNoArgConstructor::class.java.name)

        HabitatFactory.initialize(application)

        val registryWarning: ShadowLog.LogItem = warningContaining("registry could not be created")
        assertEquals(Log.WARN, registryWarning.type)
        assertTrue(registryWarning.throwable is ReflectiveOperationException)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证 Registry providers 读取失败时记录 warning 并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenRegistryProvidersFailWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRegistryClassMetadata(application, ThrowingRegistry::class.java.name)

        HabitatFactory.initialize(application)

        val registryWarning: ShadowLog.LogItem = warningContaining("providers could not be loaded")
        assertEquals(Log.WARN, registryWarning.type)
        assertTrue(registryWarning.throwable is IllegalStateException)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证 Provider factories 读取失败时记录 warning 并安装空 Dao 注册表.
     */
    @Test
    fun initializeWhenProviderFactoriesFailWarnsAndInstallsEmptyRegistry() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRegistryClassMetadata(application, ThrowingProviderRegistry::class.java.name)

        HabitatFactory.initialize(application)

        val providerWarning: ShadowLog.LogItem = warningContaining("factories could not be loaded")
        assertEquals(Log.WARN, providerWarning.type)
        assertTrue(providerWarning.throwable is IllegalStateException)
        assertNull(HabitatFactory.get<TestDao>())
    }

    /**
     * 验证多绑定 Dao 的非限定获取记录 warning 并返回 null.
     */
    @Test
    fun getWithoutQualifierWhenMultipleBindingsWarnsAndReturnsNull() {
        var factoryCallCount: Int = 0
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(
                        ACCOUNT_QUALIFIER to {
                            factoryCallCount += 1
                            TestDao()
                        },
                        ARCHIVE_QUALIFIER to {
                            factoryCallCount += 1
                            TestDao()
                        },
                    )
                )
            )
        )

        assertNull(HabitatFactory.get<TestDao>())
        assertEquals(0, factoryCallCount)

        val warning: ShadowLog.LogItem = warningContaining("Multiple Dao bindings found")
        assertTrue(warning.msg.contains(ACCOUNT_QUALIFIER))
        assertTrue(warning.msg.contains(ARCHIVE_QUALIFIER))
    }

    /**
     * 验证 Dao 类型下不存在请求的限定符时记录 warning 并返回 null.
     */
    @Test
    fun getWithUnknownQualifierWarnsAndReturnsNull() {
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(ACCOUNT_QUALIFIER to { TestDao() })
                )
            )
        )

        assertNull(HabitatFactory.get<TestDao>(ARCHIVE_QUALIFIER))

        val warning: ShadowLog.LogItem = warningContaining("Dao binding not found")
        assertTrue(warning.msg.contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(warning.msg.contains(ARCHIVE_QUALIFIER))
    }

    /**
     * 验证限定工厂返回错误类型时记录 warning 并返回 null.
     */
    @Test
    fun getWithQualifierWhenFactoryReturnsWrongTypeWarnsAndReturnsNull() {
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(ACCOUNT_QUALIFIER to { OtherDao() })
                )
            )
        )

        assertNull(HabitatFactory.get<TestDao>(ACCOUNT_QUALIFIER))

        val warning: ShadowLog.LogItem = warningContaining("Dao type mismatch")
        assertTrue(warning.msg.contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(warning.msg.contains(checkNotNull(OtherDao::class.qualifiedName)))
        assertTrue(warning.msg.contains(ACCOUNT_QUALIFIER))
    }

    /**
     * 验证 get 会等待正在进行的 initialize 发布状态, 而不是误报未初始化.
     */
    @Test
    fun getWaitsForConcurrentInitializeAndReturnsPublishedDao() {
        val application: Application = RuntimeEnvironment.getApplication()
        val providersStarted: CountDownLatch = CountDownLatch(1)
        val releaseProviders: CountDownLatch = CountDownLatch(1)
        val getStarted: CountDownLatch = CountDownLatch(1)
        val expectedDao: TestDao = TestDao()
        BlockingRegistry.configure(
            providersStarted = providersStarted,
            releaseProviders = releaseProviders,
            dao = expectedDao,
        )
        setRegistryClassMetadata(application, BlockingRegistry::class.java.name)
        val executor: ExecutorService = Executors.newFixedThreadPool(2)

        try {
            val initializeFuture: Future<*> = executor.submit {
                HabitatFactory.initialize(application)
            }
            assertTrue(providersStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val getFuture: Future<TestDao?> = executor.submit<TestDao?> {
                getStarted.countDown()
                HabitatFactory.get<TestDao>()
            }
            assertTrue(getStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            assertThrows(TimeoutException::class.java) {
                getFuture.get(GET_BLOCK_ASSERTION_MILLIS, TimeUnit.MILLISECONDS)
            }

            releaseProviders.countDown()
            initializeFuture.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertSame(expectedDao, getFuture.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            releaseProviders.countDown()
            executor.shutdownNow()
            executor.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun setRegistryClassMetadata(
        application: Application,
        registryClassName: String,
    ) {
        val packageManager: ShadowPackageManager = Shadows.shadowOf(application.packageManager)
        val packageInfo: PackageInfo = packageManager.getInternalMutablePackageInfo(application.packageName)
        val applicationInfo: ApplicationInfo = checkNotNull(packageInfo.applicationInfo)
        applicationInfo.metaData = Bundle().apply {
            putString(REGISTRY_METADATA_NAME, registryClassName)
        }
    }

    private fun clearRegistryMetadata(application: Application) {
        val packageManager: ShadowPackageManager = Shadows.shadowOf(application.packageManager)
        val packageInfo: PackageInfo = packageManager.getInternalMutablePackageInfo(application.packageName)
        val applicationInfo: ApplicationInfo = checkNotNull(packageInfo.applicationInfo)
        applicationInfo.metaData = null
    }

    private fun warningContaining(message: String): ShadowLog.LogItem {
        return ShadowLog.getLogsForTag(LOG_TAG)
            .single { item: ShadowLog.LogItem -> item.msg.contains(message) }
    }

    private fun resetDaoBindings() {
        val field: Field = HabitatFactory::class.java.getDeclaredField("daoBindings")
        field.isAccessible = true
        field.set(HabitatFactory, null)
    }

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val method: Method = HabitatFactory::class.java.getDeclaredMethod(
            "installProviders",
            List::class.java,
        )
        method.isAccessible = true
        method.invoke(HabitatFactory, providers)
    }

    class NotRegistry

    class RegistryWithoutNoArgConstructor(
        @Suppress("UNUSED_PARAMETER") marker: String,
    ) : HabitatRegistry {

        override fun providers(): List<HabitatDaoProvider> = emptyList()
    }

    class ThrowingRegistry : HabitatRegistry {

        override fun providers(): List<HabitatDaoProvider> {
            error("Registry providers failed.")
        }
    }

    class ThrowingProviderRegistry : HabitatRegistry {

        override fun providers(): List<HabitatDaoProvider> = listOf(ThrowingProvider())
    }

    class BlockingRegistry : HabitatRegistry {

        override fun providers(): List<HabitatDaoProvider> {
            providersStarted.countDown()
            check(releaseProviders.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting to release Registry providers."
            }
            return listOf(TestDaoProvider(dao))
        }

        companion object {

            private lateinit var providersStarted: CountDownLatch

            private lateinit var releaseProviders: CountDownLatch

            private lateinit var dao: TestDao

            fun configure(
                providersStarted: CountDownLatch,
                releaseProviders: CountDownLatch,
                dao: TestDao,
            ) {
                this.providersStarted = providersStarted
                this.releaseProviders = releaseProviders
                this.dao = dao
            }
        }
    }

    private class ThrowingProvider : HabitatDaoProvider {

        override val daoFactories: Map<KClass<*>, Map<String?, () -> Any>>
            get() = error("Provider factories failed.")
    }

    private class TestDaoProvider(
        factories: Map<String?, () -> Any>,
    ) : HabitatDaoProvider {

        constructor(dao: TestDao) : this(
            factories = mapOf(null to { dao })
        )

        override val daoFactories: Map<KClass<*>, Map<String?, () -> Any>> = mapOf(
            TestDao::class to factories
        )
    }

    class TestDao

    class OtherDao

    private companion object {

        private const val LOG_TAG: String = "Habitat"

        private const val REGISTRY_METADATA_NAME: String = "com.whisper.habitat.registry"

        private const val ASYNC_TIMEOUT_SECONDS: Long = 5L

        private const val GET_BLOCK_ASSERTION_MILLIS: Long = 200L

        private const val ACCOUNT_QUALIFIER: String = "user.account"

        private const val ARCHIVE_QUALIFIER: String = "user.archive"

        private const val MISSING_REGISTRY_CLASS_NAME: String =
            "com.example.habitat.MissingHabitatRegistry"
    }
}
