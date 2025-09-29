package com.whisper.architecture.logger

import android.util.Log


/**
 *
 * @author whisper
 * @since 2025/9/29
 */
class LogcatTree(
    private val _enable: Boolean = true,
    private val _level: LogLevel = LogLevel.VERBOSE
) : Tree {

    override fun log(
        level: LogLevel,
        tag: String,
        msg: String,
        throwable: Throwable?
    ) {
        if (!_enable) {
            return
        }
        if (level.ordinal < _level.ordinal) {
            return
        }
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, msg, throwable)
            LogLevel.DEBUG -> Log.d(tag, msg, throwable)
            LogLevel.INFO -> Log.i(tag, msg, throwable)
            LogLevel.WARN -> Log.w(tag, msg, throwable)
            LogLevel.ERROR -> Log.e(tag, msg, throwable)
        }
    }
}