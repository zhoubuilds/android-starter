package com.whisper.kit.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 提供 Android 客户端和宿主应用的基础运行环境信息.
 *
 * 设备与系统属性直接返回 Android 平台原值. 默认 User-Agent 只包含应用标识,
 * 应用版本和 Android 版本, 不加入设备标识或硬件型号.
 *
 * @author whisper
 * @since 2026/09/05
 */
object ClientInfoUtils {

    /** Android 平台标识. */
    const val PLATFORM: String = "android"

    private const val UNKNOWN_TOKEN: String = "unknown"

    private const val MAX_USER_AGENT_TOKEN_LENGTH: Int = 128

    /** 返回 [Build.VERSION.SDK_INT] 原值. */
    val sdkInt: Int
        get() = Build.VERSION.SDK_INT

    /** 返回 [Build.VERSION.RELEASE] 原值. */
    val osRelease: String
        get() = Build.VERSION.RELEASE

    /** 返回 [Build.MANUFACTURER] 原值. */
    val manufacturer: String
        get() = Build.MANUFACTURER

    /** 返回 [Build.BRAND] 原值. */
    val brand: String
        get() = Build.BRAND

    /** 返回 [Build.MODEL] 原值. */
    val model: String
        get() = Build.MODEL

    /** 返回当前 Context 所属应用的包名原值. */
    fun getPackageName(context: Context): String = context.packageName

    /**
     * 返回当前应用的版本名原值.
     *
     * PackageManager 中不存在当前包时返回 `null`.
     */
    fun getAppVersionName(context: Context): String? = getPackageInfo(context)?.versionName

    /**
     * 返回当前应用的完整版本号.
     *
     * API 28 及以上保留 long version code, 低版本转换为 [Long].
     * PackageManager 中不存在当前包时返回 `null`.
     */
    fun getAppVersionCode(context: Context): Long? =
        getPackageInfo(context)?.getVersionCodeCompat()

    /**
     * 返回当前 Context 资源配置中的 Locale 列表原值.
     */
    fun getAppLocales(context: Context): LocaleList = context.resources.configuration.locales

    /**
     * 返回当前 Context 资源配置的首选 Locale.
     *
     * 配置未提供 Locale 时回退到进程的默认 Locale.
     */
    fun getPrimaryAppLocale(context: Context): Locale {
        val locales: LocaleList = getAppLocales(context)
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    /**
     * 返回可直接作为 HTTP User-Agent 请求头值使用的默认客户端标识.
     *
     * 格式为 `<package>/<version> Android/<release> API/<sdk>`. 各字段会转换为
     * RFC 9110 product token, 且每个 token 最长 128 个字符. 不包含设备标识,
     * 厂商或型号, 避免默认增加不必要的设备指纹信息.
     */
    fun getDefaultUserAgent(context: Context): String {
        val packageToken: String = getPackageName(context).toUserAgentToken()
        val packageInfo: PackageInfo? = getPackageInfo(context)
        val versionToken: String = (
            packageInfo?.versionName?.takeIf { it.isNotBlank() }
                ?: packageInfo?.getVersionCodeCompat()?.toString()
            ).toUserAgentToken()
        val releaseToken: String = osRelease.toUserAgentToken()
        return "$packageToken/$versionToken Android/$releaseToken API/$sdkInt"
    }

    private fun getPackageInfo(context: Context): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.getVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }

    /**
     * 将动态字段转换为 RFC 9110 product token, 防止生成无效请求头.
     */
    private fun String?.toUserAgentToken(): String {
        if (this.isNullOrBlank()) {
            return UNKNOWN_TOKEN
        }

        val token: StringBuilder = StringBuilder(minOf(length, MAX_USER_AGENT_TOKEN_LENGTH))
        var previousCharacterWasReplaced = false
        for (character: Char in this) {
            if (token.length == MAX_USER_AGENT_TOKEN_LENGTH) {
                break
            }
            if (character.isUserAgentTokenCharacter()) {
                token.append(character)
                previousCharacterWasReplaced = false
            } else if (!previousCharacterWasReplaced) {
                token.append('-')
                previousCharacterWasReplaced = true
            }
        }
        return token.toString()
    }

    private fun Char.isUserAgentTokenCharacter(): Boolean {
        return this in 'a'..'z' ||
            this in 'A'..'Z' ||
            this in '0'..'9' ||
            this == '!' ||
            this == '#' ||
            this == '$' ||
            this == '%' ||
            this == '&' ||
            this == '\'' ||
            this == '*' ||
            this == '+' ||
            this == '-' ||
            this == '.' ||
            this == '^' ||
            this == '_' ||
            this == '`' ||
            this == '|' ||
            this == '~'
    }
}
