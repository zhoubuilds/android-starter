package com.whisper.foundation.viewmodel

import com.whisper.architecture.business.model.ArchitectureBusiness
import com.whisper.architecture.ui.message.UiMessage
import com.whisper.architecture.ui.state.ArchitectureUiState
import com.whisper.architecture.ui.state.DefaultArchitectureUiState
import com.whisper.architecture.viewmodel.ArchitectureViewModel
import com.whisper.foundation.function.toUiMessage


/**
 *
 * @author whisper
 * @since 2026/7/25
 */
open class BusinessViewModel : ArchitectureViewModel() {

    private val mutableArchitectureUiState: DefaultArchitectureUiState =
        DefaultArchitectureUiState()

    final override val architectureUiState: ArchitectureUiState
        get() = mutableArchitectureUiState

    override fun onBusinessStart() {
        mutableArchitectureUiState.onPendingTaskStarted()
    }

    override fun onBusinessCompletion() {
        mutableArchitectureUiState.onPendingTaskCompleted()
    }

    override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
        showUiMessage(error.exception.toUiMessage())
    }

    /**
     * 发送需要 UI 展示的消息.
     *
     * @param message UI 消息.
     */
    protected fun showUiMessage(message: UiMessage) {
        mutableArchitectureUiState.showUiMessage(message)
    }

}
