package com.whisper.starter

import android.app.Application
import com.whisper.architecture.network.ApiFactory
import com.whisper.aster.runtime.Aster
import com.whisper.kit.KitApplicationHolder
import com.whisper.kit.activity.ActivityLifecycleTracker
import com.whisper.quill.LogcatQuillWriter
import com.whisper.quill.Quill
import com.whisper.quill.QuillLevel
import com.whisper.starter.network.StarterNetworkComponentManager
import com.whisper.starter.network.StarterRequestHeadersInterceptor
import com.whisper.starter.network.StarterRequestHeadersProvider


/**
 *
 * @author whisper
 * @since 2025/9/19
 */
class StarterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Aster.initialize(this)
        KitApplicationHolder.initialize(this)
        ActivityLifecycleTracker.install(this)
        ApiFactory.install(
            StarterNetworkComponentManager(
                apiHost = BuildConfig.API_HOST,
                requestHeadersInterceptor = StarterRequestHeadersInterceptor(
                    StarterRequestHeadersProvider(this),
                ),
            )
        )
        Quill.addWriter(
            LogcatQuillWriter(
                minimumLevel = if (BuildConfig.DEBUG) {
                    QuillLevel.DEBUG
                } else {
                    QuillLevel.WARN
                },
                defaultTag = "AndroidStarter",
            )
        )
    }

}
