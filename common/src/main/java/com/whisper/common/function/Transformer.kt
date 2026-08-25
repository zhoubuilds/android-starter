package com.whisper.common.function

import com.whisper.architecture.business.exception.BusinessException
import com.whisper.architecture.ui.message.UiMessage
import com.whisper.architecture.ui.message.UiMessageImportance
import com.whisper.architecture.ui.message.UiMessageTone


fun Throwable.toUiMessage(): UiMessage = when (this) {
    is BusinessException -> this.message
        ?.takeIf { m -> m.isNotBlank() }
        ?.let {
            UiMessage(it, UiMessageImportance.LOW, UiMessageTone.ERROR)
        } ?: UiMessage("未知错误", UiMessageImportance.LOW, UiMessageTone.ERROR)

    else -> UiMessage("网络错误", UiMessageImportance.LOW, UiMessageTone.ERROR)
}
