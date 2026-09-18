package com.whisper.feature.function

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.whisper.feature.function.databinding.FunctionDemoActivityBinding
import com.whisper.kit.extension.applyWindowInsetsPadding
import com.whisper.kit.extension.viewBinding

/**
 * 展示单个 Kit 控件的可操作详情.
 *
 * Activity 仅供 Function 模块内部显式跳转, 不作为跨模块路由契约.
 *
 * @author whisper
 * @since 2026/09/14
 */
class FunctionDemoActivity : AppCompatActivity() {

    @get:MainThread
    private val viewBinding by viewBinding(FunctionDemoActivityBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewBinding.root)
        configureWindowInsets()

        val demo: FunctionDemo = checkNotNull(
            FunctionDemo.fromName(intent.getStringExtra(EXTRA_DEMO)),
        ) {
            "Unknown Function demo."
        }
        configureToolbar(demo)
        showDemo(demo)
    }

    /**
     * 在根布局统一避让系统栏, 刘海和输入键盘, 底部取各类型的最大避让量.
     *
     * 低版本依赖 Manifest 的 adjustResize 分发 IME Insets, NestedScrollView 负责保持输入区域可滚动到达.
     */
    private fun configureWindowInsets() {
        viewBinding.functionDemoRoot.applyWindowInsetsPadding(
            typeMask = WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
        )
    }

    private fun configureToolbar(demo: FunctionDemo) {
        viewBinding.functionDemoToolbar.setTitle(demo.titleRes)
        viewBinding.functionDemoToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun showDemo(demo: FunctionDemo) {
        viewBinding.functionRangeDemo.isVisible = demo == FunctionDemo.RANGE
        viewBinding.functionCodeInputDemo.isVisible = demo == FunctionDemo.CODE_INPUT
        viewBinding.functionLineDemo.isVisible = demo == FunctionDemo.LINE
        when (demo) {
            FunctionDemo.RANGE -> configureRangeDemo()
            FunctionDemo.CODE_INPUT -> configureCodeInputDemo()
            FunctionDemo.LINE -> Unit
            FunctionDemo.RECYCLER_DIVIDERS -> error("RecyclerView demo requires RecyclerViewDemoActivity.")
        }
    }

    private fun configureRangeDemo() {
        updateRangeValue(
            start = viewBinding.functionRange.start,
            end = viewBinding.functionRange.end,
        )
        viewBinding.functionRange.setOnRangeChangedListener { start, end ->
            updateRangeValue(start, end)
        }
    }

    private fun configureCodeInputDemo() {
        updateCodeInputCount(viewBinding.functionCodeInput.length())
        viewBinding.functionCodeInput.doAfterTextChanged { text ->
            updateCodeInputCount(text?.length ?: 0)
        }
    }

    private fun updateRangeValue(start: Int, end: Int) {
        viewBinding.functionRangeValue.text = getString(
            R.string.function_range_value,
            start,
            end,
        )
    }

    private fun updateCodeInputCount(length: Int) {
        viewBinding.functionCodeInputCount.text = getString(
            R.string.function_code_input_count,
            length,
        )
    }

    companion object {

        private const val EXTRA_DEMO: String = "com.whisper.feature.function.extra.DEMO"

        /**
         * 创建普通控件详情的模块内部 Intent, RECYCLER_DIVIDERS 应使用独立列表详情的 Intent 工厂.
         */
        internal fun createIntent(context: Context, demo: FunctionDemo): Intent =
            Intent(context, FunctionDemoActivity::class.java)
                .putExtra(EXTRA_DEMO, demo.name)
    }
}
