package com.whisper.aster.runtime.internal

import android.util.Log

/**
 * 按严重程度将 Aster 可恢复问题写入 Logcat.
 *
 * @author whisper
 * @since 2026/07/23
 */
internal object LogcatErrorHandler {

    private const val TAG: String = "Aster"

    /**
     * 输出不阻断当前操作的警告.
     *
     * @param message 警告说明.
     * @param cause 关联异常, 没有关联异常时为 null.
     */
    fun warning(message: String, cause: Throwable? = null) {
        if (cause == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, cause)
        }
    }

    /**
     * 输出导致当前操作失败的可恢复错误.
     *
     * @param message 错误说明.
     * @param cause 关联异常, 没有关联异常时为 null.
     */
    fun error(message: String, cause: Throwable? = null) {
        if (cause == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, cause)
        }
    }
}
