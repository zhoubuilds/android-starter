package com.whisper.kit.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 进程内 Activity 生命周期跟踪器.
 *
 * 安装到 [Application] 后, 通过弱引用记录最近创建、启动或恢复且尚未销毁的 Activity,
 * 供无法直接获得页面引用的调用方读取当前任务栈顶 Activity.
 *
 * @author whisper
 * @since 2026/08/17
 */
object ActivityLifecycleTracker {

    private val installLock: Any = Any()

    private val lifecycleCallbacks: Application.ActivityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) {
                track(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                track(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                track(activity)
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (topActivityReference?.get() === activity) {
                    topActivityReference = null
                }
            }
        }

    @Volatile
    private var installedApplication: Application? = null

    @Volatile
    private var topActivityReference: WeakReference<Activity>? = null

    /**
     * 当前任务栈顶可用的 Activity.
     *
     * Activity 正在结束、已经销毁或弱引用已释放时为 null. 调用方对返回 Activity 的 UI 操作仍应在主线程执行.
     */
    val topActivity: Activity?
        get() {
            val activity: Activity = topActivityReference?.get() ?: return null
            return activity.takeUnless { it.isFinishing || it.isDestroyed }
        }

    /**
     * 安装全局 Activity 生命周期观察者.
     *
     * 同一个 Application 重复调用时保持幂等. 切换 Application 时会先注销旧观察者并清空旧页面引用.
     *
     * @param application 当前进程的 Application.
     */
    fun install(application: Application) {
        synchronized(installLock) {
            val currentApplication: Application? = installedApplication
            if (currentApplication === application) {
                return
            }
            currentApplication?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            installedApplication = null
            topActivityReference = null
            application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            installedApplication = application
        }
    }

    private fun track(activity: Activity) {
        topActivityReference = WeakReference(activity)
    }
}
