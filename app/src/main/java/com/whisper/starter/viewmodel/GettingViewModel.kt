package com.whisper.starter.viewmodel

import androidx.lifecycle.viewModelScope
import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.extension.onlySuccess
import com.whisper.architecture.viewmodel.ArchViewModel
import com.whisper.common.utils.ApiUtils
import com.whisper.starter.data.bean.GettingResp
import com.whisper.starter.data.repo.GettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


/**
 *
 * @author whisper
 * @since 2025/9/22
 */
class GettingViewModel : ArchViewModel() {

    private val _gettingMinimumSate: MutableStateFlow<GettingResp?> = MutableStateFlow(null)

    private val _gettingFullState: MutableStateFlow<Business<GettingResp?>?> =
        MutableStateFlow(null)


    fun getting(id: Long) {
        viewModelScope.launch {

        }

        viewModelScope.launch {
            GettingRepository().getting(id)
                .flowOn(Dispatchers.IO)
                .onlySuccess(archUiStatePack, ApiUtils::transformErrorToUiMessage)
                .collect {
                    it
                }
        }

    }

}