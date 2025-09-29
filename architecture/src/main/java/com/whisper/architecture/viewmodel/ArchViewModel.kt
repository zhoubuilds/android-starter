package com.whisper.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uistate.ArchUiStateProvider
import com.whisper.architecture.uistate.DefaultUiStateProvider
import kotlinx.coroutines.flow.Flow


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
open class ArchViewModel : ViewModel(),
    ArchUiStateProvider {

    private val _uiStateProvider: DefaultUiStateProvider =
        DefaultUiStateProvider()

    override val workingCountFlow: Flow<Int>
        get() = _uiStateProvider.workingCountFlow

    override val uiMessageFlow: Flow<UiMessage?>
        get() = _uiStateProvider.uiMessageFlow

}