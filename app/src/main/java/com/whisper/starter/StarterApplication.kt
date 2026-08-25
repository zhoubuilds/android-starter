package com.whisper.starter

import android.app.Application
import com.whisper.architecture.AppGlobal
import com.whisper.architecture.logger.LogLevel
import com.whisper.architecture.logger.LogcatTree
import com.whisper.architecture.logger.Logger
import com.whisper.aster.runtime.Aster
import com.whisper.kit.KitApplicationHolder


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
        Logger.plant(LogcatTree(true, LogLevel.VERBOSE))
    }

}