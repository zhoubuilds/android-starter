package com.whisper.starter.viewmodel

import androidx.lifecycle.viewModelScope
import com.whisper.architecture.business.function.consumeSuccessMeta
import com.whisper.foundation.viewmodel.BusinessViewModel
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
class GettingViewModel : BusinessViewModel() {

    private val _gettingMinimumSate: MutableStateFlow<GettingResp?> = MutableStateFlow(null)

    val gettingMinimumSate: StateFlow<GettingResp?> = _gettingMinimumSate


    fun getting(id: Long) {
        viewModelScope.launch {
            GettingRepository().getting(id)
                .flowOn(Dispatchers.IO)
                .consumeLoading()
                .consumeError()
                .consumeSuccessMeta()
                .collect {
                    _gettingMinimumSate.value = it
                }
        }

    }


}
