package com.whisper.architecture.network.annotation

import okhttp3.Interceptor
import kotlin.reflect.KClass

/**
 * 声明 API 使用的 OkHttp application interceptor.
 *
 * 类型按声明顺序添加, 实例由 app 安装的 NetworkComponentManager 解析.
 *
 * @aegis 保护注解目标, 运行时保留策略, 参数类型和声明顺序语义.
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Interceptors(vararg val value: KClass<out Interceptor>)
