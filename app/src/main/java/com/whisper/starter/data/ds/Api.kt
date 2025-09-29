package com.whisper.starter.data.ds

import com.whisper.architecture.bean.transmit.ApiResponse
import com.whisper.architecture.net.annotation.BaseUrl
import com.whisper.architecture.net.annotation.Interceptors
import com.whisper.common.net.interceptor.SystemParamsAppendInterceptor
import com.whisper.starter.BuildConfig
import com.whisper.starter.data.bean.GettingResp
import retrofit2.http.GET
import retrofit2.http.Query


/**
 *
 * @author whisper
 * @since 2025/9/19
 */
@BaseUrl(BuildConfig.SCHEME + BuildConfig.HOST)
@Interceptors([SystemParamsAppendInterceptor::class])
interface Api {

    @GET("api/stater/getting")
    suspend fun getting(@Query("id") id: Long?): ApiResponse<GettingResp?>

}