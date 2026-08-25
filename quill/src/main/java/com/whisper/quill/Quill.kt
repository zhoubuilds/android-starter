package com.whisper.quill

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Quill 日志入口.
 *
 * 对外提供 lazy message API, 只有存在可处理当前日志的写入器时才会执行消息构建函数.
 *
 * @aegis 保护公开日志 API, lazy message, Writer 隔离和错误不穿透业务的语义.
 * @author whisper
 * @since 2026/07/28
 */
object Quill {

    private val writers: CopyOnWriteArrayList<QuillWriter> = CopyOnWriteArrayList()

    val writerCount: Int
        get() = writers.size

    fun addWriter(writer: QuillWriter): Boolean {
        val added: Boolean = writers.addIfAbsent(writer)
        if (!added) {
            warning("Quill writer has already been added.")
        }
        return added
    }

    fun removeWriter(writer: QuillWriter): Boolean {
        val removed: Boolean = writers.remove(writer)
        if (!removed) {
            warning("Quill writer was not added.")
        }
        return removed
    }

    fun clearWriters() {
        writers.clear()
    }

    fun isLoggable(level: QuillLevel, tag: String? = null): Boolean {
        return selectWriters(level, tag).isNotEmpty()
    }

    inline fun v(messageSupplier: () -> String): Int {
        return log(QuillLevel.VERBOSE, null, null, messageSupplier)
    }

    inline fun v(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.VERBOSE, tag, null, messageSupplier)
    }

    inline fun v(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.VERBOSE, tag, throwable, messageSupplier)
    }

    inline fun v(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.VERBOSE, null, throwable, messageSupplier)
    }

    inline fun d(messageSupplier: () -> String): Int {
        return log(QuillLevel.DEBUG, null, null, messageSupplier)
    }

    inline fun d(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.DEBUG, tag, null, messageSupplier)
    }

    inline fun d(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.DEBUG, tag, throwable, messageSupplier)
    }

    inline fun d(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.DEBUG, null, throwable, messageSupplier)
    }

    inline fun i(messageSupplier: () -> String): Int {
        return log(QuillLevel.INFO, null, null, messageSupplier)
    }

    inline fun i(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.INFO, tag, null, messageSupplier)
    }

    inline fun i(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.INFO, tag, throwable, messageSupplier)
    }

    inline fun i(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.INFO, null, throwable, messageSupplier)
    }

    inline fun w(messageSupplier: () -> String): Int {
        return log(QuillLevel.WARN, null, null, messageSupplier)
    }

    inline fun w(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.WARN, tag, null, messageSupplier)
    }

    fun w(tag: String, throwable: Throwable): Int {
        return logThrowable(QuillLevel.WARN, tag, throwable)
    }

    inline fun w(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.WARN, tag, throwable, messageSupplier)
    }

    inline fun w(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.WARN, null, throwable, messageSupplier)
    }

    inline fun e(messageSupplier: () -> String): Int {
        return log(QuillLevel.ERROR, null, null, messageSupplier)
    }

    inline fun e(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.ERROR, tag, null, messageSupplier)
    }

    inline fun e(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.ERROR, tag, throwable, messageSupplier)
    }

    inline fun e(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.ERROR, null, throwable, messageSupplier)
    }

    inline fun wtf(messageSupplier: () -> String): Int {
        return log(QuillLevel.ASSERT, null, null, messageSupplier)
    }

    inline fun wtf(tag: String, messageSupplier: () -> String): Int {
        return log(QuillLevel.ASSERT, tag, null, messageSupplier)
    }

    fun wtf(tag: String, throwable: Throwable): Int {
        return logThrowable(QuillLevel.ASSERT, tag, throwable)
    }

    inline fun wtf(tag: String, throwable: Throwable?, messageSupplier: () -> String): Int {
        return log(QuillLevel.ASSERT, tag, throwable, messageSupplier)
    }

    inline fun wtf(throwable: Throwable, messageSupplier: () -> String): Int {
        return log(QuillLevel.ASSERT, null, throwable, messageSupplier)
    }

    /**
     * 按指定级别输出日志. 消息只会在至少一个 writer 接收日志时构建.
     */
    inline fun log(
        level: QuillLevel = QuillLevel.DEBUG,
        tag: String? = null,
        throwable: Throwable? = null,
        messageSupplier: () -> String,
    ): Int {
        val selectedWriters: List<QuillWriter> = selectWriters(level, tag)
        if (selectedWriters.isEmpty()) {
            return 0
        }

        val resolvedMessage: String = try {
            messageSupplier()
        } catch (throwableFromSupplier: Throwable) {
            val resolvedException: Throwable = preserveOriginalThrowable(
                throwableFromSupplier,
                throwable,
            )
            val failureMessage: String = messageSupplierFailureMessage(throwable)
            return publish(selectedWriters, level, tag, resolvedException, failureMessage)
        }
        return publish(selectedWriters, level, tag, throwable, resolvedMessage)
    }

    private fun logThrowable(level: QuillLevel, tag: String, throwable: Throwable): Int {
        val selectedWriters: List<QuillWriter> = selectWriters(level, tag)
        if (selectedWriters.isEmpty()) {
            return 0
        }
        return publish(selectedWriters, level, tag, throwable, "")
    }

    @PublishedApi
    internal fun selectWriters(level: QuillLevel, tag: String?): List<QuillWriter> {
        val selectedWriters: MutableList<QuillWriter> = ArrayList()
        writers.forEach { writer: QuillWriter ->
            if (isWriterLoggable(writer, level, tag)) {
                selectedWriters.add(writer)
            }
        }
        return selectedWriters
    }

    @PublishedApi
    internal fun publish(
        selectedWriters: List<QuillWriter>,
        level: QuillLevel,
        tag: String?,
        throwable: Throwable?,
        message: String,
    ): Int {
        var result: Int = 0
        selectedWriters.forEach { writer: QuillWriter ->
            try {
                val writeResult: Int = writer.write(level, tag, throwable, message)
                if (result <= 0 && writeResult > 0) {
                    result = writeResult
                }
            } catch (_: Throwable) {
                // 日志写入器异常不应影响业务流程.
            }
        }
        return result
    }

    private fun isWriterLoggable(
        writer: QuillWriter,
        level: QuillLevel,
        tag: String?,
    ): Boolean {
        return try {
            writer.isLoggable(level, tag)
        } catch (_: Throwable) {
            false
        }
    }

    private fun warning(message: String) {
        try {
            Log.w(INTERNAL_TAG, message)
        } catch (_: Throwable) {
            // Android Log 在 JVM 单元测试中可能没有实现.
        }
    }

    @PublishedApi
    internal fun preserveOriginalThrowable(
        supplierException: Throwable,
        originalThrowable: Throwable?,
    ): Throwable {
        if (originalThrowable == null || originalThrowable === supplierException) {
            return supplierException
        }
        try {
            supplierException.addSuppressed(originalThrowable)
        } catch (_: Throwable) {
            // 保留日志主异常优先, suppressed 写入失败时不影响日志流程.
        }
        return supplierException
    }

    @PublishedApi
    internal fun messageSupplierFailureMessage(originalThrowable: Throwable?): String {
        if (originalThrowable == null) {
            return MESSAGE_SUPPLIER_FAILURE
        }
        return MESSAGE_SUPPLIER_FAILURE_WITH_ORIGINAL_THROWABLE
    }

    @PublishedApi
    internal const val MESSAGE_SUPPLIER_FAILURE: String = "Quill message supplier failed."

    @PublishedApi
    internal const val MESSAGE_SUPPLIER_FAILURE_WITH_ORIGINAL_THROWABLE: String =
        "Quill message supplier failed. Original throwable is attached as suppressed."

    private const val INTERNAL_TAG: String = "Quill"
}
