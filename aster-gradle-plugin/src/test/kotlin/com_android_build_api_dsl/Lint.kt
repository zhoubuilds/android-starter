package com.android.build.api.dsl

/**
 * AGP 测试用 Lint API 占位类型.
 *
 * gradle-api 的 ApplicationExtension 签名引用该类型, 但 API jar 未包含对应
 * class. TestKit fake Android 插件只需要加载 ApplicationExtension 方法签名,
 * 因此在测试 classpath 中提供最小占位类型.
 *
 * @author whisper
 * @since 2026/07/24
 */
interface Lint
