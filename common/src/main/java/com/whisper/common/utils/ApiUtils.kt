package com.whisper.common.utils

import com.whisper.architecture.exception.BusinessException
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uimode.message.UiMessageLevel
import com.whisper.architecture.uimode.message.UiMessageTone

object ApiUtils {

    fun transformErrorToUiMessage(e: Throwable): UiMessage? = when (e) {
        is BusinessException -> e.message?.takeIf { m -> m.isNotBlank() }
            ?.let {
                UiMessage(
                    it,
                    UiMessageLevel.LOW,
                    UiMessageTone.ERROR
                )
            }
            ?: UiMessage(
                "未知错误",
                UiMessageLevel.LOW,
                UiMessageTone.ERROR
            )

        else -> UiMessage(
            "网络错误",
            UiMessageLevel.LOW,
            UiMessageTone.ERROR
        )
    }


}