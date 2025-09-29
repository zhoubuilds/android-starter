package com.whisper.starter.data.repo

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.function.callAsBusinessFlow
import com.whisper.architecture.net.ApiFactory
import com.whisper.starter.data.bean.GettingResp
import com.whisper.starter.data.ds.Api
import kotlinx.coroutines.flow.Flow


/**
 *
 * @author whisper
 * @since 2025/9/22
 */
class GettingRepository {

    fun getting(id: Long?): Flow<Business<GettingResp?>> =
        callAsBusinessFlow { ApiFactory.create(Api::class).getting(id) }

}