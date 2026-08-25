package com.whisper.kit.activity

import android.app.Application
import androidx.activity.ComponentActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * 验证全局 Activity 生命周期跟踪和栈顶读取语义.
 *
 * @author whisper
 * @since 2026/08/17
 */
@RunWith(RobolectricTestRunner::class)
class ActivityLifecycleTrackerTest {

    @Test
    fun topActivity_activityStopped_keepsLastStackTopUntilDestroyed() {
        installTracker()
        val controller: ActivityController<TestActivity> = createActivity()
        val activity: TestActivity = controller.get()

        assertSame(activity, ActivityLifecycleTracker.topActivity)

        controller.pause().stop()

        assertSame(activity, ActivityLifecycleTracker.topActivity)

        controller.destroy()

        assertNull(ActivityLifecycleTracker.topActivity)
    }

    @Test
    fun topActivity_newActivityCreated_returnsNewActivity() {
        installTracker()
        val firstController: ActivityController<TestActivity> = createActivity()
        val secondController: ActivityController<TestActivity> = createActivity()
        val secondActivity: TestActivity = secondController.get()

        assertSame(secondActivity, ActivityLifecycleTracker.topActivity)

        firstController.pause().stop().destroy()

        assertSame(secondActivity, ActivityLifecycleTracker.topActivity)

        secondController.pause().stop().destroy()

        assertNull(ActivityLifecycleTracker.topActivity)
    }

    @Test
    fun topActivity_previousActivityStarted_returnsPreviousActivityBeforeResume() {
        installTracker()
        val previousController: ActivityController<TestActivity> = createActivity()
        val previousActivity: TestActivity = previousController.get()
        previousController.pause().stop()
        val topController: ActivityController<TestActivity> = createActivity()
        val topActivity: TestActivity = topController.get()
        topActivity.finish()
        topController.pause().stop()

        previousController.restart().start()

        assertSame(previousActivity, ActivityLifecycleTracker.topActivity)

        previousController.resume().pause().stop().destroy()
        topController.destroy()
    }

    @Test
    fun topActivity_activityFinishing_returnsNull() {
        installTracker()
        val controller: ActivityController<TestActivity> = createActivity()
        val activity: TestActivity = controller.get()

        activity.finish()

        assertNull(ActivityLifecycleTracker.topActivity)

        controller.pause().stop().destroy()
    }

    private fun installTracker() {
        val application: Application = RuntimeEnvironment.getApplication()
        ActivityLifecycleTracker.install(application)
    }

    private fun createActivity(): ActivityController<TestActivity> =
        Robolectric.buildActivity(TestActivity::class.java).setup()

    /**
     * 用于触发生命周期回调的轻量测试 Activity.
     */
    class TestActivity : ComponentActivity()
}
