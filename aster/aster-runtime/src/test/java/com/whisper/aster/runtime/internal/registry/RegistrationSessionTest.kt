package com.whisper.aster.runtime.internal.registry

import android.app.Activity
import android.app.Application
import com.whisper.aster.runtime.Capability
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证注册会话的冻结、校验和关闭行为.
 *
 * @author whisper
 * @since 2026/07/23
 */
class RegistrationSessionTest {

    /**
     * 完整注册结果可以一次性冻结为查询状态.
     */
    @Test
    fun registrationsFreezeIntoCompleteState() {
        val application: Application = Application()
        val session: RegistrationSession = RegistrationSession()
        session.registerRoute("/test/page", TestActivity::class.java)
        session.registerCapability(
            name = "test.sample.capability",
            implClass = TestCapability::class.java,
            singleton = true
        )

        val state: RegistryState = session.freeze(application)

        assertSame(application, state.application)
        assertTrue(state.routeRegistry.contains("/test/page"))
        assertTrue(state.capabilityRegistry.contains("test.sample.capability"))
    }

    /**
     * 注册会话冻结后拒绝任何后续写入.
     */
    @Test
    fun frozenSessionRejectsLateRegistration() {
        val session: RegistrationSession = RegistrationSession()
        session.freeze(Application())

        assertThrows(IllegalStateException::class.java) {
            session.registerRoute("/test/page", TestActivity::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            session.registerCapability(
                name = "test.sample.capability",
                implClass = TestCapability::class.java,
                singleton = true
            )
        }
    }

    /**
     * 同一个能力名不能注册两次.
     */
    @Test
    fun duplicateCapabilityFailsRegistration() {
        val session: RegistrationSession = RegistrationSession()
        session.registerCapability(
            name = "test.sample.capability",
            implClass = TestCapability::class.java,
            singleton = true
        )

        assertThrows(IllegalArgumentException::class.java) {
            session.registerCapability(
                name = "test.sample.capability",
                implClass = AnotherTestCapability::class.java,
                singleton = false
            )
        }

        val state: RegistryState = session.freeze(Application())
        assertTrue(state.capabilityRegistry.get("test.sample.capability") is TestCapability)
    }

    /**
     * 相同路径不能指向不同的 Activity.
     */
    @Test
    fun conflictingRouteFailsRegistration() {
        val session: RegistrationSession = RegistrationSession()
        session.registerRoute("/test/page", TestActivity::class.java)
        session.registerRoute("/test/page", TestActivity::class.java)

        assertThrows(IllegalArgumentException::class.java) {
            session.registerRoute("/test/page", AnotherTestActivity::class.java)
        }

        val state: RegistryState = session.freeze(Application())
        assertSame(TestActivity::class.java, state.routeRegistry.find("/test/page"))
    }

    /**
     * 注册会话只保存提供方提交的映射, 不校验名称格式和目标类型.
     */
    @Test
    fun providerMappingsAreStoredWithoutTargetValidation() {
        val session: RegistrationSession = RegistrationSession()

        @Suppress("UNCHECKED_CAST")
        val wrongActivity: Class<out Activity> = String::class.java as Class<out Activity>
        @Suppress("UNCHECKED_CAST")
        val wrongCapability: Class<out Capability> = String::class.java as Class<out Capability>
        session.registerRoute("invalid", wrongActivity)
        session.registerCapability("invalid", wrongCapability, true)

        val state: RegistryState = session.freeze(Application())

        assertTrue(state.routeRegistry.contains("invalid"))
        assertTrue(state.capabilityRegistry.contains("invalid"))
    }

    /**
     * 测试 Activity 路由目标.
     */
    class TestActivity : Activity()

    /**
     * 另一个测试 Activity 路由目标.
     */
    class AnotherTestActivity : Activity()

    /**
     * 测试能力实现.
     */
    class TestCapability : Capability {

        override fun initialize(application: Application) = Unit
    }

    /**
     * 另一个测试能力实现.
     */
    class AnotherTestCapability : Capability {

        override fun initialize(application: Application) = Unit
    }

}
