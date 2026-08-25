package com.android.build.api.dsl

/**
 * AGP 测试用 CompileSdkVersion API 占位类型.
 *
 * gradle-api 的 Variant 签名引用该类型, 但 API jar 未包含对应 class.
 * TestKit fake Android 插件不会调用该 API, 仅需要类型可被 classloader 解析.
 *
 * @author whisper
 * @since 2026/07/24
 */
interface CompileSdkVersion
