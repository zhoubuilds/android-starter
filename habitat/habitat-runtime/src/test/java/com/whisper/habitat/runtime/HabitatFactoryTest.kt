package com.whisper.habitat.runtime

import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KClass
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 验证 HabitatFactory 状态发布和 Dao 获取边界.
 *
 * @author whisper
 * @since 2026/07/28
 */
class HabitatFactoryTest {

    @Before
    fun setUp() {
        resetDaoBindings()
    }

    @After
    fun tearDown() {
        resetDaoBindings()
    }

    /**
     * 验证未初始化时获取 Dao 会按使用错误直接失败.
     */
    @Test
    fun getBeforeInitializeThrows() {
        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            HabitatFactory.get(TestDao::class)
        }

        assertEquals(
            "HabitatFactory.initialize(application) must be called before getting DAOs.",
            exception.message,
        )
    }

    /**
     * 验证已安装 Provider 后可以按 Dao 类型获取实例.
     */
    @Test
    fun getsDaoFromInstalledProvider() {
        val dao: TestDao = TestDao()
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(null to { dao })
                )
            )
        )

        assertSame(dao, HabitatFactory.get(TestDao::class))
    }

    /**
     * 验证唯一显式绑定支持类型安全的限定和非限定获取, 且工厂只在获取时执行.
     */
    @Test
    fun getsSingleQualifiedDaoLazilyThroughTypedApis() {
        val dao: TestDao = TestDao()
        var factoryCallCount: Int = 0
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(
                        ACCOUNT_QUALIFIER to {
                            factoryCallCount += 1
                            dao
                        }
                    )
                )
            )
        )

        assertEquals(0, factoryCallCount)
        val unqualifiedDao: TestDao? = HabitatFactory.get<TestDao>()
        val qualifiedDao: TestDao? = HabitatFactory.get(TestDao::class, ACCOUNT_QUALIFIER)
        val reifiedQualifiedDao: TestDao? = HabitatFactory.get<TestDao>(ACCOUNT_QUALIFIER)

        assertSame(dao, unqualifiedDao)
        assertSame(dao, qualifiedDao)
        assertSame(dao, reifiedQualifiedDao)
        assertEquals(3, factoryCallCount)
    }

    /**
     * 验证多个绑定下的限定获取只执行并返回精确匹配的工厂.
     */
    @Test
    fun getsMatchingQualifiedDaoFromMultipleBindings() {
        val accountDao: TestDao = TestDao()
        val archiveDao: TestDao = TestDao()
        installProviders(
            listOf(
                TestDaoProvider(
                    mapOf(
                        ACCOUNT_QUALIFIER to { accountDao },
                        ARCHIVE_QUALIFIER to { archiveDao },
                    )
                )
            )
        )

        val actualDao: TestDao? = HabitatFactory.get<TestDao>(ARCHIVE_QUALIFIER)

        assertSame(archiveDao, actualDao)
    }

    /**
     * 验证运行时传入空白查询 qualifier 时按调用错误直接失败.
     */
    @Test
    fun getWithBlankQualifierThrows() {
        val exception: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            HabitatFactory.get<TestDao>(" ")
        }

        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDao::class.qualifiedName)))
    }

    /**
     * 验证生成 ABI 出现空白 binding qualifier 时中断安装并定位 Provider.
     */
    @Test
    fun installProviderWithBlankBindingQualifierThrows() {
        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            installProviders(
                listOf(
                    TestDaoProvider(mapOf(" " to { TestDao() }))
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("qualifier must not be blank"))
        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDaoProvider::class.qualifiedName)))
    }

    /**
     * 验证生成 ABI 出现重复 binding qualifier 时中断安装并报告冲突限定符.
     */
    @Test
    fun installProvidersWithDuplicateBindingQualifierThrows() {
        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            installProviders(
                listOf(
                    TestDaoProvider(mapOf(ACCOUNT_QUALIFIER to { TestDao() })),
                    TestDaoProvider(mapOf(ACCOUNT_QUALIFIER to { TestDao() })),
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("registered multiple times"))
        assertTrue(exception.message.orEmpty().contains(ACCOUNT_QUALIFIER))
        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDaoProvider::class.qualifiedName)))
    }

    /**
     * 验证生成 ABI 混用限定和非限定 binding 时中断安装并报告全部限定符.
     */
    @Test
    fun installProvidersWithMixedQualifiedBindingsThrows() {
        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            installProviders(
                listOf(
                    TestDaoProvider(mapOf(null to { TestDao() })),
                    TestDaoProvider(mapOf(ACCOUNT_QUALIFIER to { TestDao() })),
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("mixed qualified and unqualified"))
        assertTrue(exception.message.orEmpty().contains("<unqualified>"))
        assertTrue(exception.message.orEmpty().contains(ACCOUNT_QUALIFIER))
    }

    /**
     * 验证 Dao 工厂返回错误类型时中断获取并报告期望类型、实际类型和 qualifier.
     */
    @Test
    fun getWhenFactoryReturnsWrongTypeThrows() {
        installProviders(
            listOf(
                TestDaoProvider(mapOf(ACCOUNT_QUALIFIER to { OtherDao() }))
            )
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            HabitatFactory.get<TestDao>(ACCOUNT_QUALIFIER)
        }

        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(exception.message.orEmpty().contains(checkNotNull(OtherDao::class.qualifiedName)))
        assertTrue(exception.message.orEmpty().contains(ACCOUNT_QUALIFIER))
    }

    /**
     * 验证 Dao 工厂普通异常被附加 Dao 上下文后继续抛出并保留原始 cause.
     */
    @Test
    fun getWhenFactoryThrowsExceptionThrowsWithContext() {
        val expectedCause: IOException = IOException("Dao storage is unavailable.")
        installProviders(
            listOf(
                TestDaoProvider(mapOf(null to { throw expectedCause }))
            )
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            HabitatFactory.get<TestDao>()
        }

        assertSame(expectedCause, exception.cause)
        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(exception.message.orEmpty().contains("<unqualified>"))
    }

    /**
     * 验证 Dao 工厂链接失败被附加 Dao 上下文后继续抛出并保留原始 cause.
     */
    @Test
    fun getWhenFactoryThrowsLinkageErrorThrowsWithContext() {
        val expectedCause: LinkageError = NoClassDefFoundError("MissingDaoDependency")
        installProviders(
            listOf(
                TestDaoProvider(mapOf(ACCOUNT_QUALIFIER to { throw expectedCause }))
            )
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            HabitatFactory.get<TestDao>(ACCOUNT_QUALIFIER)
        }

        assertSame(expectedCause, exception.cause)
        assertTrue(exception.message.orEmpty().contains(checkNotNull(TestDao::class.qualifiedName)))
        assertTrue(exception.message.orEmpty().contains(ACCOUNT_QUALIFIER))
    }

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val method: Method = HabitatFactory::class.java.getDeclaredMethod(
            "installProviders",
            List::class.java
        )
        method.isAccessible = true
        try {
            method.invoke(HabitatFactory, providers)
        } catch (exception: InvocationTargetException) {
            val cause: Throwable = checkNotNull(exception.cause)
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw AssertionError(cause)
            }
        }
    }

    private fun resetDaoBindings() {
        val field: Field = HabitatFactory::class.java.getDeclaredField("daoBindings")
        field.isAccessible = true
        field.set(HabitatFactory, null)
    }

    private class TestDaoProvider(
        factories: Map<String?, () -> Any>,
    ) : HabitatDaoProvider {

        override val daoFactories: Map<KClass<*>, Map<String?, () -> Any>> = mapOf(
            TestDao::class to factories
        )
    }

    private class TestDao

    private class OtherDao

    private companion object {

        private const val ACCOUNT_QUALIFIER: String = "user.account"

        private const val ARCHIVE_QUALIFIER: String = "user.archive"

    }
}
