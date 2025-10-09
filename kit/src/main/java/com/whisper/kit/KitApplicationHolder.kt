package com.whisper.kit

import android.app.Application

/**
 * 全局 Application 持有器
 * 用于在任何地方安全地获取 Application 或 ApplicationContext，避免频繁传入 Context。
 * 使用方法：
 * 1. 在 Application.onCreate() 中初始化：
 *      ApplicationHolder.initialize(this)
 * 2. 在任何地方获取：
 *      val app = ApplicationHolder.application
 *
 * @author whisper
 * @since 2025/10/9
 */
object KitApplicationHolder {

    @Volatile
    private var _application: Application? = null

    /**
     * 初始化全局 Application
     *
     * @param application Application 实例
     *
     * 注意：只允许初始化一次，多次调用不会覆盖原值。
     */
    fun initialize(application: Application) {
        if (_application == null) {
            synchronized(this) {
                if (_application == null) {
                    _application = application
                }
            }
        }
    }

    /**
     * 获取全局 Application
     *
     * @throws IllegalStateException 如果未初始化
     */
    internal val application: Application
        get() = _application
            ?: throw IllegalStateException("KitApplicationHolder is not initialized. Call KitApplicationHolder.initialize(application) in Application.onCreate().")

}


