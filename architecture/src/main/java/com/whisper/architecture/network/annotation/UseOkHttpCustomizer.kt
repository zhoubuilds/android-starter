package com.whisper.architecture.network.annotation

import com.whisper.architecture.network.component.OkHttpCustomizer
import kotlin.reflect.KClass

/**
 * 声明 API 使用的唯一 OkHttp Builder 定制入口.
 *
 * 定制器在默认配置和两类拦截器之后执行, 可以覆盖之前的 Builder 配置.
 *
 * @aegis 保护注解契约和定制器执行顺序语义.
 * @aegis-audit 2026-08-26 | whisper | 使用 Use 前缀区分策略选择注解与定制器组件.
 *
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseOkHttpCustomizer(
    val value: KClass<out OkHttpCustomizer>,
)
