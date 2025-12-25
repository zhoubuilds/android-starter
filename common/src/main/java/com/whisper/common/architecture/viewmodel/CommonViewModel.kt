package com.whisper.common.architecture.viewmodel

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.viewmodel.ArchitectureViewModel
import com.whisper.common.utils.ApiUtils


/**
 *
 * @author whisper
 * @since 2025/12/25
 */
open class CommonViewModel : ArchitectureViewModel() {

    override fun transformErrorToUiMessage(error: Business.Error): UiMessage? =
        ApiUtils.transformErrorToUiMessage(error.e)

}