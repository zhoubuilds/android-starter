package com.whisper.architecture.uistate

import com.whisper.architecture.uimode.message.UiMessage

interface MutableArchUiStateProvider : ArchUiStateProvider {

    fun onWorkStarted()

    fun onWorkCompleted()

    fun showUiMessage(message: UiMessage)

}