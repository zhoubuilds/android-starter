package com.whisper.core.viewmodel

import com.whisper.core.uistate.UiMessage
import kotlinx.coroutines.flow.Flow


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
interface CoreUiStateProvider {

    val backgroundCountFlow: Flow<Int>
    val uiMessageFlow: Flow<UiMessage?>

}