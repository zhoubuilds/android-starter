package com.whisper.architecture.network.annotation

import com.whisper.architecture.network.component.RetrofitCustomizer
import kotlin.reflect.KClass

/**
 * 声明 API 使用的唯一 Retrofit Builder 定制入口.
 *
 * 定制器在 app 提供的 Retrofit 默认配置之后执行. 它可以替换 client 或 callFactory,
 * 因而调用方需要自行保证公共网络策略不会被意外绕过.
 * 注解只声明组件类型, 实例来源和生命周期交给 NetworkComponentManager 决定, 不要求额外显式注册.
 *
 * @aegis 保护注解契约和定制器执行顺序语义.
 * @aegis-audit 2026-08-26 | whisper | 使用 Use 前缀区分策略选择注解与定制器组件.
 * @aegis-audit 2026-09-16 | whisper | 经授权取消文档中的强制注册约束, 明确由管理器决定实例来源, 保持注解与执行顺序.
 *
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseRetrofitCustomizer(
    val value: KClass<out RetrofitCustomizer>,
)
