package com.whisper.foundation.function

import com.whisper.architecture.exception.BusinessException
import com.whisper.architecture.model.ui.notice.NoticeImportance
import com.whisper.architecture.model.ui.notice.NoticeTone
import com.whisper.architecture.model.ui.notice.NoticeUiModel


fun Throwable.toNoticeUiModel(): NoticeUiModel = when (this) {
    is BusinessException -> this.message
        ?.takeIf { m -> m.isNotBlank() }
        ?.let {
            NoticeUiModel(it, NoticeImportance.LOW, NoticeTone.ERROR)
        } ?: NoticeUiModel("未知错误", NoticeImportance.LOW, NoticeTone.ERROR)

    else -> NoticeUiModel("网络错误", NoticeImportance.LOW, NoticeTone.ERROR)
}
