package com.whisper.core.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.whisper.core.component.CoreUiStateHandler
import com.whisper.core.viewmodel.CoreUiStateProvider


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class CoreFragment<VM> : Fragment() where VM : CoreUiStateProvider {

    protected abstract val viewModel: VM
    protected abstract val coreUiStateHandler: CoreUiStateHandler

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        coreUiStateHandler.bind(viewModel, this)
    }

}