package com.whisper.starter.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.whisper.architecture.activity.ArchActivity
import com.whisper.architecture.component.ArchUiStateHandler
import com.whisper.architecture.extension.viewBinding
import com.whisper.architecture.logger.Logger
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.starter.BuildConfig
import com.whisper.starter.R
import com.whisper.starter.databinding.ActivityMainBinding
import com.whisper.starter.viewmodel.GettingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ArchActivity<GettingViewModel>() {

    companion object {
        const val TAG: String = "whisper"
    }

    private val _viewBinding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel = GettingViewModel()
    override val archUiStateHandler: ArchUiStateHandler = object : ArchUiStateHandler(this) {
        override fun onBackgroundCountChanged(count: Int) {
            Logger.i("loading") { "count: $count" }
        }

        override fun handleUiMessage(message: UiMessage) {
            Toast.makeText(
                context,
                "${message.level}-${message.tone}: ${message.message}",
                Toast.LENGTH_SHORT
            ).show()

        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(_viewBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        _viewBinding.tv.text = BuildConfig.DEBUG.toString()

        lifecycleScope.launch {
            viewModel.gettingMinimumSate.collectLatest {
                _viewBinding.tv.text = it?.toString() ?: "null"
            }
        }

        _viewBinding.btGet.setOnClickListener {
            viewModel.getting(1)
        }

    }
}