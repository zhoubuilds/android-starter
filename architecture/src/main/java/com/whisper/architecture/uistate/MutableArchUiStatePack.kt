package com.whisper.architecture.uistate

import com.whisper.architecture.uimode.message.UiMessage

interface MutableArchUiStatePack : ArchUiStatePack {

    fun onWorkStarted()

    fun onWorkCompleted()

    fun showUiMessage(message: UiMessage)

}