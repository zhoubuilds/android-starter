package com.whisper.core.component

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.core.uistate.UiMessage
import com.whisper.core.viewmodel.CoreUiStateProvider
import kotlinx.coroutines.launch


/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class CoreUiStateHandler(private val context: Context) {

    abstract fun onBackgroundCountChanged(count: Int)

    abstract fun handleUiMessage(message: UiMessage)

    open fun bind(provider: CoreUiStateProvider, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    provider.backgroundCountFlow.collect {
                        onBackgroundCountChanged(it)
                    }
                }
                launch {
                    provider.uiMessageFlow.collect {
                        it ?: return@collect
                        handleUiMessage(it)
                    }
                }
            }
        }
    }
}
