package com.whisper.aster.runtime.internal.registry

import android.app.Activity
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 验证路由目标在首次使用时执行结构检查.
 *
 * @author whisper
 * @since 2026/07/23
 */
class RouteRegistryTest {

    /**
     * 合法 Activity 目标可以正常返回.
     */
    @Test
    fun validActivityTargetCanBeResolved() {
        val registry: RouteRegistry = RouteRegistry(
            mapOf("/test/page" to TestActivity::class.java)
        )

        assertSame(TestActivity::class.java, registry.find("/test/page"))
    }

    /**
     * 非法 Activity 目标只在首次获取时抛出异常.
     */
    @Test
    fun invalidActivityTargetsFailWhenResolved() {
        @Suppress("UNCHECKED_CAST")
        val wrongType: Class<out Activity> = String::class.java as Class<out Activity>
        val registry: RouteRegistry = RouteRegistry(
            mapOf(
                "/test/wrong_type" to wrongType,
                "/test/abstract" to AbstractTestActivity::class.java,
                "/test/private" to PrivateTestActivity::class.java,
                "/test/inner" to InnerTestActivity::class.java
            )
        )

        assertThrows(IllegalStateException::class.java) {
            registry.find("/test/wrong_type")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.find("/test/abstract")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.find("/test/private")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.find("/test/inner")
        }
    }

    /**
     * 测试 Activity 目标.
     */
    class TestActivity : Activity()

    /**
     * 抽象 Activity 测试目标.
     */
    abstract class AbstractTestActivity : Activity()

    /**
     * 非公开 Activity 测试目标.
     */
    private class PrivateTestActivity : Activity()

    /**
     * 非静态内部 Activity 测试目标.
     */
    inner class InnerTestActivity : Activity()
}
