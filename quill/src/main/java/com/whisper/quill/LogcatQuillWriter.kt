package com.whisper.quill

import android.util.Log

/**
 * 将 Quill 日志输出到 Android Logcat 的写入器.
 *
 * 默认不额外限制最低级别, 并尊重 Android Logcat 的可写入判断.
 * 这意味着消息构建函数只会在 Logcat 确实可写入当前 tag 与级别时执行.
 * 消息过长时会按 Logcat 限制拆分.
 *
 * @aegis 保护构造 API, 最低级别/Logcat 可写判断, 分段和 throwable 拼接语义.
 * @author whisper
 * @since 2026/07/28
 */
class LogcatQuillWriter(
    private val minimumLevel: QuillLevel = QuillLevel.VERBOSE,
    private val defaultTag: String = DEFAULT_TAG,
) : QuillWriter {

    override fun isLoggable(level: QuillLevel, tag: String?): Boolean {
        if (level.priority < minimumLevel.priority) {
            return false
        }
        val resolvedTag: String = tag ?: defaultTag
        return try {
            Log.isLoggable(resolvedTag, level.priority)
        } catch (_: Throwable) {
            false
        }
    }

    override fun write(
        level: QuillLevel,
        tag: String?,
        throwable: Throwable?,
        message: String,
    ): Int {
        val resolvedTag: String = tag ?: defaultTag
        val resolvedMessage: String = resolveMessage(message, throwable)
        var startIndex: Int = 0
        var result: Int = 0
        val messageLength: Int = resolvedMessage.length
        while (startIndex < messageLength) {
            val endIndex: Int = minOf(startIndex + MAX_LOG_LENGTH, messageLength)
            val printResult: Int = Log.println(
                level.priority,
                resolvedTag,
                resolvedMessage.substring(startIndex, endIndex),
            )
            if (result <= 0 && printResult > 0) {
                result = printResult
            }
            startIndex = endIndex
        }
        if (messageLength == 0) {
            result = Log.println(level.priority, resolvedTag, "")
        }
        return result
    }

    private fun resolveMessage(message: String, throwable: Throwable?): String {
        if (throwable == null) {
            return message
        }
        val stackTrace: String = Log.getStackTraceString(throwable)
        if (message.isEmpty()) {
            return stackTrace
        }
        return message + "\n" + stackTrace
    }

    private companion object {
        private const val DEFAULT_TAG: String = "Quill"
        private const val MAX_LOG_LENGTH: Int = 4000
    }
}
