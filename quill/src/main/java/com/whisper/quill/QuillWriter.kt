package com.whisper.quill

/**
 * Quill 日志写入器.
 *
 * 写入器用于接收 Quill 分发的日志, 可以输出到 Logcat、文件或远端服务.
 *
 * @aegis 保护 Writer API, lazy 判断职责, 直接写入职责和返回值语义.
 *
 * @author whisper
 * @since 2026/07/28
 */
interface QuillWriter {

    /**
     * 判断当前日志是否需要由该写入器处理.
     *
     * Quill 只有在至少一个写入器返回 true 时才会执行日志消息构建函数.
     */
    fun isLoggable(level: QuillLevel, tag: String?): Boolean {
        return true
    }

    /**
     * 处理一条已经构建好的日志.
     *
     * Quill 已经通过 [isLoggable] 完成判断, 调用到这里时写入器应该直接处理日志.
     * 正数表示至少完成一次写入, 0 表示未写入.
     */
    fun write(level: QuillLevel, tag: String?, throwable: Throwable?, message: String): Int
}
