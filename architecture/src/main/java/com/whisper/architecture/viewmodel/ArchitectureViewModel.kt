package com.whisper.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uistate.DefaultUiStatePack
import com.whisper.architecture.uistate.MutableArchUiStatePack


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchitectureViewModel :
    ViewModel(),
    ArchitectureUiStateOwner,
    BusinessProgressProcessor,
    BusinessErrorProcessor {

    override val architectureUiStatePack: MutableArchUiStatePack =
        DefaultUiStatePack()

    override fun onBusinessStart() = architectureUiStatePack.onWorkStarted()

    override fun onBusinessCompletion() = architectureUiStatePack.onWorkCompleted()

    override fun onBusinessError(error: Business.Error): Boolean {
        val msg: UiMessage = transformErrorToUiMessage(error) ?: return false
        architectureUiStatePack.showUiMessage(msg)
        return false
    }

    abstract fun transformErrorToUiMessage(error: Business.Error): UiMessage?

}