package com.whisper.foundation.network.interceptor

/**
 * 提供当前请求需要携带的公共 Header.
 *
 * 实现应在请求运行期返回当前值, 便于读取时间戳、登录态或其它动态应用状态.
 *
 * @author whisper
 * @since 2026/08/25
 */
fun interface RequestHeadersProvider {

    fun currentHeaders(): Map<String, String>
}
