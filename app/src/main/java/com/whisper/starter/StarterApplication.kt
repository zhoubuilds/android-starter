package com.whisper.starter

import android.app.Application
import com.whisper.architecture.network.ApiFactory
import com.whisper.habitat.runtime.HabitatFactory
import com.whisper.quill.LogcatQuillWriter
import com.whisper.quill.Quill
import com.whisper.quill.QuillLevel
import com.whisper.starter.database.StarterDatabase
import com.whisper.starter.network.StarterNetworkComponentManager
import com.whisper.starter.settings.AppAppearancePreferences

/**
 * Starter 应用进程级组件的唯一初始化入口.
 *
 * @author whisper
 * @since 2025/9/19
 */
class StarterApplication : Application() {

    /**
     * 在页面创建前恢复外观配置, 并按数据库、Habitat、网络的依赖顺序完成进程装配.
     *
     * Habitat 生成的 Dao provider 依赖已发布的数据库实例. ApiFactory 每进程只允许安装一次,
     * 不能将此初始化过程作为运行期配置刷新入口.
     */
    override fun onCreate() {
        super.onCreate()
        AppAppearancePreferences.initialize(this)
        StarterDatabase.initialize(this)
        HabitatFactory.initialize(this)
        ApiFactory.install(StarterNetworkComponentManager(this))
        Quill.addWriter(
            LogcatQuillWriter(
                minimumLevel = if (BuildConfig.DEBUG) {
                    QuillLevel.DEBUG
                } else {
                    QuillLevel.WARN
                },
                defaultTag = "AndroidStarter",
            ),
        )
    }
}
