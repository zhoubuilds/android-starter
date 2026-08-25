package com.whisper.starter.data.ds

import com.whisper.foundation.model.business.Business
import com.whisper.starter.data.bean.GettingResp
import kotlinx.coroutines.flow.Flow
import retrofit2.http.GET
import retrofit2.http.Query


/**
 *
 * @author whisper
 * @since 2025/9/19
 */
interface Api {

    @GET("api/stater/getting")
    fun getting(@Query("id") id: Long?): Flow<Business<GettingResp?>>

}
