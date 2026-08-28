package com.whisper.aster.runtime.annotation

/**
 * 声明一个可被自动发现的能力实现.
 *
 * `name` 是全局唯一能力名, 用于 [com.whisper.aster.runtime.Aster.resolve] 精确查找.
 * `singleton` 为 true 时按能力名缓存实例, 为 false 时每次获取都创建新实例.
 *
 * @aegis 保护注解目标, 二进制保留策略, 参数和默认单例语义.
 *
 * @author whisper
 * @since 2026/07/21
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Capable(
    val name: String,
    val singleton: Boolean = true
)
