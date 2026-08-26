package com.whisper.foundation.function

import com.whisper.architecture.exception.BusinessException
import com.whisper.architecture.model.ui.notice.NoticeImportance
import com.whisper.architecture.model.ui.notice.NoticeTone
import com.whisper.architecture.model.ui.notice.NoticeUi


fun Throwable.toNoticeUi(): NoticeUi = when (this) {
    is BusinessException -> this.message
        ?.takeIf { m -> m.isNotBlank() }
        ?.let {
            NoticeUi(it, NoticeImportance.LOW, NoticeTone.ERROR)
        } ?: NoticeUi("未知错误", NoticeImportance.LOW, NoticeTone.ERROR)

    else -> NoticeUi("网络错误", NoticeImportance.LOW, NoticeTone.ERROR)
}
