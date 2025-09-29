package com.whisper.starter

import android.app.Application
import com.whisper.architecture.AppGlobal


/**
 *
 * @author whisper
 * @since 2025/9/19
 */
class StarterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGlobal.initialize(this)
    }

}