package com.whisper.starter.viewmodel

import androidx.lifecycle.viewModelScope
import com.whisper.architecture.function.onlySuccess
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import com.whisper.common.architecture.viewmodel.CommonViewModel
import com.whisper.starter.data.bean.GettingResp
import com.whisper.starter.data.repo.GettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


/**
 *
 * @author whisper
 * @since 2025/9/22
 */
class GettingViewModel : CommonViewModel(), BusinessProgressProcessor,
    BusinessErrorProcessor {

    private val _gettingMinimumSate: MutableStateFlow<GettingResp?> = MutableStateFlow(null)

    val gettingMinimumSate: StateFlow<GettingResp?> = _gettingMinimumSate


    fun getting(id: Long) {
        viewModelScope.launch {
            GettingRepository().getting(id)
                .flowOn(Dispatchers.IO)
                .withBusinessProgress()
                .withBusinessError()
                .onlySuccess()
                .collect {
                    _gettingMinimumSate.value = it
                }
        }

    }


}