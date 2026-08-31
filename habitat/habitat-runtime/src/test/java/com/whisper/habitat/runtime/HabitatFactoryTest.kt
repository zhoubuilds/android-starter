package com.whisper.habitat.runtime

import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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

        assert(exception.message == "HabitatFactory.initialize(application) must be called before getting DAOs.")
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

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val method: Method = HabitatFactory::class.java.getDeclaredMethod(
            "installProviders",
            List::class.java
        )
        method.isAccessible = true
        method.invoke(HabitatFactory, providers)
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

    private companion object {

        private const val ACCOUNT_QUALIFIER: String = "user.account"

        private const val ARCHIVE_QUALIFIER: String = "user.archive"

    }
}
