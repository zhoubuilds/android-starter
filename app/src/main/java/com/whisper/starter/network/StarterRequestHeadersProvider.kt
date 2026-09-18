package com.whisper.starter.network

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.whisper.kit.utils.ClientInfoUtils
import com.whisper.starter.BuildConfig

/**
 * 提供 Starter 应用的公共请求头.
 *
 * Header 的具体集合属于最终 app. 本类只向 app 内的
 * [StarterRequestHeadersInterceptor] 提供运行期值, 不构成 Architecture 契约.
 * 模板默认不发送设备唯一标识, 实际项目可按服务端契约和隐私要求扩展.
 *
 * @property context 提供包名和跟随系统时的资源语言, 当前由应用组合根传入 Application.
 *
 * @author whisper
 * @since 2026/08/25
 */
class StarterRequestHeadersProvider(
    private val context: Context,
) {

    /**
     * 每次请求重新读取语言和毫秒时间戳, 不缓存可能随运行期设置变化的 Header 快照.
     */
    fun currentHeaders(): Map<String, String> = linkedMapOf(
        HEADER_PLATFORM to ClientInfoUtils.PLATFORM,
        HEADER_PACKAGE_NAME to context.packageName,
        HEADER_LOCALE to currentLocaleTag(),
        HEADER_APP_VERSION to BuildConfig.VERSION_NAME,
        HEADER_API_VERSION to BuildConfig.API_VERSION,
        HEADER_TIMESTAMP to System.currentTimeMillis().toString(),
    )

    /**
     * 优先读取当前应用语言, 避免低版本 Application 资源未本地化或异步存储尚未完成.
     * 跟随系统时读取注入 Context 的资源配置, 不使用可能被 AppCompat 改写的进程默认语言.
     */
    private fun currentLocaleTag(): String {
        val locale = AppCompatDelegate.getApplicationLocales()[0]
            ?: ClientInfoUtils.getPrimaryAppLocale(context)
        return locale.toLanguageTag()
    }

    companion object {

        private const val HEADER_PLATFORM: String = "Platform"
        private const val HEADER_PACKAGE_NAME: String = "Package-Name"
        private const val HEADER_LOCALE: String = "Locale"
        private const val HEADER_APP_VERSION: String = "App-Version-Name"
        private const val HEADER_API_VERSION: String = "Api-Version"
        private const val HEADER_TIMESTAMP: String = "Timestamp"
    }
}
