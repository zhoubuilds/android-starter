package com.whisper.architecture.network.annotation

import okhttp3.Interceptor
import kotlin.reflect.KClass

/**
 * 声明 API 使用的 OkHttp network interceptor.
 *
 * 类型按声明顺序添加. network interceptor 只在真实网络交换时执行,
 * 缓存命中时不会执行, 重定向或重试时可能执行多次.
 *
 * @aegis 保护注解目标, 运行时保留策略, 参数类型和声明顺序语义.
 *
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkInterceptors(vararg val value: KClass<out Interceptor>)
