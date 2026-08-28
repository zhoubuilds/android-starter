package com.whisper.architecture.network.annotation

import com.whisper.architecture.network.component.RetrofitCustomizer
import kotlin.reflect.KClass

/**
 * 声明 API 使用的唯一 Retrofit Builder 定制入口.
 *
 * 定制器在 app 提供的 Retrofit 默认配置之后执行. 它可以替换 client 或 callFactory,
 * 因而调用方需要自行保证公共网络策略不会被意外绕过.
 *
 * @aegis 保护注解契约和定制器执行顺序语义.
 * @aegis-audit 2026-08-26 | whisper | 使用 Use 前缀区分策略选择注解与定制器组件.
 *
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseRetrofitCustomizer(
    val value: KClass<out RetrofitCustomizer>,
)
