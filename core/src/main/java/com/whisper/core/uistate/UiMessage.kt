package com.whisper.core.uistate


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
data class UiMessage(
    val message: CharSequence,
    val level: UiMessageLevel,
    val tone: UiMessageTone,
)