package com.whisper.architecture

import android.app.Application


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
object AppGlobal {

    private lateinit var _application: Application

    val application: Application
        get() {
            if (this::_application.isInitialized) {
                return _application
            } else {
                throw IllegalStateException("AppGlobal not initialized")
            }
        }

    fun initialize(application: Application) {
        if (!this::_application.isInitialized) {
            _application = application
        }
    }

}