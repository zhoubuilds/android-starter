package com.whisper.architecture.network.component

import okhttp3.OkHttpClient

/**
 * 定制指定 API 的 OkHttpClient.Builder.
 *
 * 一个 API 只声明一个定制入口, 多项能力由实现方明确安排顺序和冲突处理.
 *
 * @aegis 保护函数式接口签名和单一定制入口语义.
 *
 * @author whisper
 * @since 2026/07/23
 */
fun interface OkHttpCustomizer {

    fun customize(builder: OkHttpClient.Builder)
}
