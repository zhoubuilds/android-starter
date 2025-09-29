package com.whisper.architecture.net.security

import android.annotation.SuppressLint
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession


/**
 *
 * @author whisper
 * @since 2025/9/4
 */
class AllowAnyHostnameVerifier : HostnameVerifier {

    @SuppressLint("BadHostnameVerifier")
    override fun verify(
        hostname: String?,
        session: SSLSession?
    ): Boolean = true

}