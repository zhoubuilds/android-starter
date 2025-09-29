package com.whisper.architecture.logger


/**
 *
 * @author whisper
 * @since 2025/9/29
 */
interface Tree {

    fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable? = null)

}