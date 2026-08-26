package com.whisper.foundation.viewmodel

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.model.ui.notice.NoticeUi
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import com.whisper.architecture.ui.state.ArchitectureUiState
import com.whisper.architecture.ui.state.DefaultArchitectureUiState
import com.whisper.architecture.viewmodel.ArchitectureViewModel
import com.whisper.foundation.function.toNoticeUi
import com.whisper.foundation.model.business.BusinessMetadata


/**
 *
 * @author whisper
 * @since 2026/7/25
 */
open class BusinessViewModel :
    ArchitectureViewModel(),
    BusinessProgressProcessor,
    BusinessErrorProcessor<BusinessMetadata> {

    private val mutableArchitectureUiState: DefaultArchitectureUiState =
        DefaultArchitectureUiState()

    final override val architectureUiState: ArchitectureUiState
        get() = mutableArchitectureUiState

    override fun onBusinessStart() {
        mutableArchitectureUiState.onPendingTaskStarted()
    }

    override fun onBusinessCompletion() {
        mutableArchitectureUiState.onPendingTaskCompleted()
    }

    override fun onBusinessError(error: Business.Failure<BusinessMetadata, *>) {
        showNotice(error.exception.toNoticeUi())
    }

    /**
     * 发送需要 UI 展示的通知.
     *
     * @param notice UI 通知.
     */
    protected fun showNotice(notice: NoticeUi) {
        mutableArchitectureUiState.showNotice(notice)
    }

}
