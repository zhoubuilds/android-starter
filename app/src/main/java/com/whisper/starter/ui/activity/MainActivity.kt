package com.whisper.starter.ui.activity

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import com.whisper.architecture.ui.activity.ArchitectureActivity
import com.whisper.architecture.ui.component.ArchitectureUiComponent
import com.whisper.aster.runtime.annotation.Route
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.recyclerview.listener.addOnItemChildClickListener
import com.whisper.quill.Quill
import com.whisper.starter.BuildConfig
import com.whisper.starter.R
import com.whisper.starter.databinding.ActivityMainBinding
import com.whisper.starter.viewmodel.GettingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Route("/app/main")
class MainActivity : ArchitectureActivity<GettingViewModel>() {

    companion object {
        const val TAG: String = "whisper"
    }

    private val _viewBinding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel = GettingViewModel()
    override val architectureUiComponent: ArchitectureUiComponent =
        object : ArchitectureUiComponent() {
            protected override fun onActiveOperationCountChanged(count: Int) {
            }

            protected override fun handleNotice(notice: NoticeUiModel) {
                Toast.makeText(
                    this@MainActivity,
                    "${notice.importance}-${notice.tone}: ${notice.content}",
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
        _viewBinding.range.setOnRangeChangedListener { _, s, e ->
            Quill.i("range") { "start: $s, end: $e" }
        }
        _viewBinding.range.start = 30

        _viewBinding.tv.text = BuildConfig.DEBUG.toString()

        lifecycleScope.launch {
            viewModel.gettingMinimumSate.collectLatest {
                _viewBinding.tv.text = it?.toString() ?: "null"
            }
        }

        _viewBinding.btGet.setOnClickListener {
            viewModel.getting(1)
        }

        // -- Transform hit test --
        _viewBinding.rvTransformTest.layoutManager = LinearLayoutManager(this)
        _viewBinding.rvTransformTest.adapter = TransformTestAdapter()
        _viewBinding.rvTransformTest.addOnItemChildClickListener { _, view, position ->
            val id = view.id
            val name = try {
                resources.getResourceEntryName(id)
            } catch (_: Exception) {
                "itemView"
            }
            val text = (view as? TextView)?.text?.toString()
            val target = text?.takeIf { it.isNotBlank() } ?: name
            Quill.i(TAG) {
                "click: position=$position, view=$target (${view.javaClass.simpleName})"
            }
            Toast.makeText(this, target, Toast.LENGTH_SHORT).show()
        }

    }
}
