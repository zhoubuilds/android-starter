package com.whisper.starter

import android.app.Application
import com.whisper.architecture.AppGlobal
import com.whisper.aster.runtime.Aster
import com.whisper.kit.KitApplicationHolder
import com.whisper.quill.LogcatQuillWriter
import com.whisper.quill.Quill
import com.whisper.quill.QuillLevel


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
        AppGlobal.initialize(this)
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
