package com.whisper.common.net.interceptor

import com.whisper.architecture.AppGlobal
import com.whisper.common.net.HttpHeaderKey
import com.whisper.kit.utils.DeviceInfoUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
class SystemParamsAppendInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val newRequest: Request = request.newBuilder()
            .addHeader(HttpHeaderKey.PLATFORM, DeviceInfoUtils.PLATFORM)
            .addHeader(HttpHeaderKey.OS, DeviceInfoUtils.os)
            .addHeader(HttpHeaderKey.DEVICE_NAME, DeviceInfoUtils.deviceName)
            .addHeader(HttpHeaderKey.DEVICE_ID, DeviceInfoUtils.getDeviceId(AppGlobal.application))
            .addHeader(
                HttpHeaderKey.PACKAGE_NAME,
                DeviceInfoUtils.getPackageName(AppGlobal.application)
            )
            .addHeader(
                HttpHeaderKey.LOCAL,
                DeviceInfoUtils.getAppLocal(AppGlobal.application).toLanguageTag()
            )
            .addHeader(
                HttpHeaderKey.APP_VERSION_NAME,
                DeviceInfoUtils.getAppVersionName(AppGlobal.application) ?: ""
            )
            .addHeader(HttpHeaderKey.TIMESTAMP, System.currentTimeMillis().toString())
            .addHeader(
                HttpHeaderKey.USER_AGENT,
                DeviceInfoUtils.getUserAgent(AppGlobal.application)
            )
            .build()
        return chain.proceed(newRequest)
    }

}