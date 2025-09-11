package com.whisper.core.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.whisper.core.component.CoreUiStateHandler
import com.whisper.core.viewmodel.CoreUiStateProvider

/**
 *
 * @author whisper
 * @since 2025/9/2
 */
abstract class CoreActivity<VM> : AppCompatActivity() where VM : CoreUiStateProvider {

    protected abstract val viewModel: VM
    protected abstract val coreUiStateHandler: CoreUiStateHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coreUiStateHandler.bind(viewModel, this)
    }

}