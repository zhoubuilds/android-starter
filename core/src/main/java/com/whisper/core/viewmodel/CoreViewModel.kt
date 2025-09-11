package com.whisper.core.viewmodel

import androidx.lifecycle.ViewModel
import com.whisper.core.uistate.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
open class CoreViewModel : ViewModel(), CoreUiStateProvider {

    private val _backgroundCountFlow: MutableStateFlow<Int> = MutableStateFlow(0)

    private val _uiMessageFlow: MutableSharedFlow<UiMessage?> = MutableSharedFlow()

    override val backgroundCountFlow: Flow<Int>
        get() = _backgroundCountFlow
    override val uiMessageFlow: Flow<UiMessage?>
        get() = _uiMessageFlow

}