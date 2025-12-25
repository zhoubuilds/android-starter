package com.whisper.architecture.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.whisper.architecture.component.ArchitectureUiStateHandler
import com.whisper.architecture.uistate.ArchitectureUiStatePack
import com.whisper.architecture.viewmodel.ArchitectureUiStateOwner


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class ArchitectureFragment<VM> : Fragment() where VM : ArchitectureUiStateOwner {

    protected abstract val viewModel: VM
    protected abstract val architectureUiStateHandler: ArchitectureUiStateHandler

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        architectureUiStateHandler.bind(architectureUiStatePacks(), this)
    }

    open fun architectureUiStatePacks(): Iterable<ArchitectureUiStatePack> =
        listOf(viewModel.architectureUiStatePack)

}