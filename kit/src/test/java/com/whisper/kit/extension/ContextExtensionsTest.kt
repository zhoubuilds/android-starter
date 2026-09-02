package com.whisper.kit.extension

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * 验证 Context 包装链中的 Activity 判断.
 *
 * @author whisper
 * @since 2026/09/02
 */
@RunWith(RobolectricTestRunner::class)
class ContextExtensionsTest {

    @Test
    fun hasActivityContext_whenContextIsActivity_returnsTrue() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(
            Activity::class.java,
        ).setup()

        assertTrue(controller.get().hasActivityContext())

        controller.pause().stop().destroy()
    }

    @Test
    fun hasActivityContext_whenNestedWrappersContainActivity_returnsTrue() {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(
            Activity::class.java,
        ).setup()
        val wrappedContext: Context = ContextWrapper(ContextWrapper(controller.get()))

        assertTrue(wrappedContext.hasActivityContext())

        controller.pause().stop().destroy()
    }

    @Test
    fun hasActivityContext_whenApplicationContextHasNoActivity_returnsFalse() {
        val applicationContext: Context = RuntimeEnvironment.getApplication()

        assertFalse(applicationContext.hasActivityContext())
    }

    @Test
    fun hasActivityContext_whenWrapperReferencesItself_returnsFalse() {
        val applicationContext: Context = RuntimeEnvironment.getApplication()
        val wrappedContext: Context = SelfReferencingContextWrapper(applicationContext)

        assertFalse(wrappedContext.hasActivityContext())
    }

    /**
     * 构造异常包装链以验证对象身份循环保护.
     */
    private class SelfReferencingContextWrapper(
        context: Context,
    ) : ContextWrapper(context) {

        override fun getBaseContext(): Context {
            return this
        }
    }
}
