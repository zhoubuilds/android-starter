package com.whisper.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.whisper.architecture.uistate.DefaultUiStatePack
import com.whisper.architecture.uistate.MutableArchUiStatePack


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
open class ArchViewModel : ViewModel(), ArchUiStateOwner {

    override val archUiStatePack: MutableArchUiStatePack =
        DefaultUiStatePack()

}