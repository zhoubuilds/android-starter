package com.whisper.architecture.network.component

import retrofit2.Retrofit

/**
 * 定制指定 API 的 Retrofit.Builder.
 *
 * 一个 API 只声明一个定制入口, 多项能力由实现方明确安排顺序和冲突处理.
 * 实例由 NetworkComponentManager 解析, 可以采用无参反射、显式工厂或 DI, 生命周期同样由管理器决定.
 * 定制器可以覆盖默认 Retrofit 配置, 实现方需要保证最终配置继续满足应用的公共网络策略.
 *
 * @aegis 保护函数式接口签名和单一定制入口语义.
 * @aegis-audit 2026-09-16 | whisper | 经授权明确组件解析不强制显式注册, 保留单一定制入口和公共网络策略责任.
 *
 * @author whisper
 * @since 2026/07/23
 */
fun interface RetrofitCustomizer {

    fun customize(builder: Retrofit.Builder)
}
