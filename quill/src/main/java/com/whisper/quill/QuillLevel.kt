package com.whisper.quill

import android.util.Log

/**
 * Quill 日志级别.
 *
 * 封装 Android Logcat 使用的 priority, 便于写入器按级别过滤日志.
 *
 * @aegis 保护日志级别枚举成员和 Android priority 映射.
 *
 * @author whisper
 * @since 2026/07/28
 */
enum class QuillLevel(
    /** Android Logcat priority. */
    val priority: Int,
) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    ASSERT(Log.ASSERT),
}
