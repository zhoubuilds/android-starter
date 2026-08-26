package com.whisper.foundation.viewmodel

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import com.whisper.architecture.ui.owner.DefaultArchitectureUiOwner
import com.whisper.architecture.ui.owner.MutableArchitectureUiOwner
import com.whisper.architecture.viewmodel.ArchitectureViewModel
import com.whisper.foundation.function.toNoticeUiModel
import com.whisper.foundation.model.business.BusinessMetadata
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow


/**
 *
 * @author whisper
 * @since 2026/7/25
 */
open class BusinessViewModel :
    ArchitectureViewModel(),
    BusinessProgressProcessor,
    BusinessErrorProcessor<BusinessMetadata> {

    private val mutableArchitectureUiOwner: MutableArchitectureUiOwner =
        DefaultArchitectureUiOwner()

    final override val activeOperationCountFlow: StateFlow<Int>
        get() = mutableArchitectureUiOwner.activeOperationCountFlow

    final override val noticeUiEffectFlow: SharedFlow<NoticeUiModel>
        get() = mutableArchitectureUiOwner.noticeUiEffectFlow

    override fun onBusinessStart() {
        mutableArchitectureUiOwner.onOperationStarted()
    }

    override fun onBusinessCompletion() {
        mutableArchitectureUiOwner.onOperationCompleted()
    }

    override fun onBusinessError(error: Business.Failure<BusinessMetadata, *>) {
        showNotice(error.exception.toNoticeUiModel())
    }

    /**
     * 发送需要 UI 展示的通知.
     *
     * @param notice UI 通知.
     */
    protected fun showNotice(notice: NoticeUiModel) {
        mutableArchitectureUiOwner.notice(notice)
    }

}
