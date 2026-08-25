package com.whisper.starter.network

import android.content.Context
import com.whisper.foundation.network.interceptor.RequestHeadersProvider
import com.whisper.kit.utils.DeviceInfoUtils
import com.whisper.starter.BuildConfig

/**
 * 提供 Starter 应用的公共请求头.
 *
 * Header 的具体集合属于最终 app. Foundation 只负责在请求运行期调用本 Provider 并注入返回值.
 * 模板默认不发送设备唯一标识, 实际项目可按服务端契约和隐私要求扩展.
 *
 * @author whisper
 * @since 2026/08/25
 */
class StarterRequestHeadersProvider(
    private val context: Context,
) : RequestHeadersProvider {

    override fun currentHeaders(): Map<String, String> = linkedMapOf(
        HEADER_PLATFORM to DeviceInfoUtils.PLATFORM,
        HEADER_PACKAGE_NAME to context.packageName,
        HEADER_LOCALE to DeviceInfoUtils.getAppLocal(context).toLanguageTag(),
        HEADER_APP_VERSION to BuildConfig.VERSION_NAME,
        HEADER_API_VERSION to BuildConfig.API_VERSION,
        HEADER_TIMESTAMP to System.currentTimeMillis().toString(),
    )

    companion object {

        private const val HEADER_PLATFORM: String = "Platform"
        private const val HEADER_PACKAGE_NAME: String = "Package-Name"
        private const val HEADER_LOCALE: String = "Locale"
        private const val HEADER_APP_VERSION: String = "App-Version-Name"
        private const val HEADER_API_VERSION: String = "Api-Version"
        private const val HEADER_TIMESTAMP: String = "Timestamp"
    }
}
