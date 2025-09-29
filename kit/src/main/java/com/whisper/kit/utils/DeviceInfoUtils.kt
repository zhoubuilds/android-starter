package com.whisper.kit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.os.Build
import java.util.Locale


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
object DeviceInfoUtils {

    const val PLATFORM: String = "android"

    val os: String = "Android ${Build.VERSION.RELEASE}"

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun getDeviceId(context: Context): String = DeviceIdUtils.getDeviceId(context)

    fun getPackageName(context: Context): String = context.packageName

    fun getAppLocal(context: Context): Locale {
        val config: Configuration = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }

    fun getAppVersionName(context: Context): String? = try {
        val packInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packInfo.versionName
    } catch (_: Exception) {
        null
    }

    fun getUserAgent(context: Context): String {
        return "${getPackageName(context)}/${getAppVersionName(context)} (${os}; ${deviceName})"
    }

}


