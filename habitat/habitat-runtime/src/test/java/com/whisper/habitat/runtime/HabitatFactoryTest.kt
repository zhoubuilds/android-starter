package com.whisper.habitat.runtime

import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import org.junit.After
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
        resetFactoryState()
    }

    @After
    fun tearDown() {
        resetFactoryState()
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
        installProviders(listOf(TestDaoProvider(dao)))

        assertSame(dao, HabitatFactory.get(TestDao::class))
    }

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val method: Method = HabitatFactory::class.java.getDeclaredMethod(
            "installProviders",
            List::class.java
        )
        method.isAccessible = true
        method.invoke(HabitatFactory, providers)
    }

    private fun resetFactoryState() {
        val field: Field = HabitatFactory::class.java.getDeclaredField("stateReference")
        field.isAccessible = true
        val reference: AtomicReference<*> = field.get(HabitatFactory) as AtomicReference<*>
        reference.set(null)
    }

    private class TestDaoProvider(
        private val dao: TestDao,
    ) : HabitatDaoProvider {

        override val daoFactories: Map<KClass<*>, () -> Any> = mapOf(
            TestDao::class to { dao }
        )
    }

    private class TestDao
}
