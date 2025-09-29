package com.whisper.architecture.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.whisper.architecture.component.ArchUiStateHandler
import com.whisper.architecture.uistate.ArchUiStateProvider


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchFragment<VM> : Fragment() where VM : ArchUiStateProvider {

    protected abstract val viewModel: VM
    protected abstract val archUiStateHandler: ArchUiStateHandler

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        archUiStateHandler.bind(viewModel, this)
    }

}