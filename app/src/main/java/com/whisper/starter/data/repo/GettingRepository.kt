package com.whisper.starter.data.repo

import com.whisper.architecture.network.ApiFactory
import com.whisper.foundation.model.business.Business
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
        ApiFactory.create(Api::class).getting(id)

}
