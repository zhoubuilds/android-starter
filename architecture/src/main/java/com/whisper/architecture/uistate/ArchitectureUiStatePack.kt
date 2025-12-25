package com.whisper.architecture.uistate

import com.whisper.architecture.uimode.message.UiMessage
import kotlinx.coroutines.flow.Flow


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
interface ArchitectureUiStatePack {

    val workingCountFlow: Flow<Int>

    val uiMessageFlow: Flow<UiMessage>

}