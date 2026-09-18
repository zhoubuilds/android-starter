package com.whisper.feature.function.recyclerview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.color.MaterialColors
import com.whisper.feature.function.R
import com.whisper.feature.function.databinding.FunctionRecyclerActivityBinding
import com.whisper.kit.extension.applyWindowInsetsPadding
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.recyclerview.decoration.RegularItemDividerDecoration
import com.whisper.kit.recyclerview.decoration.StaggeredItemDividerDecoration
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

/**
 * 展示 Kit RecyclerView 分割线装饰器及不同 LayoutManager 的使用.
 *
 * 仅供 Function 模块内部显式跳转, 不作为公开路由.
 *
 * @author whisper
 * @since 2026/09/15
 */
class RecyclerViewDemoActivity : AppCompatActivity() {

    companion object {
        private const val STATE_LAYOUT_MODE: String = "recycler.layoutMode"
        private const val STATE_ORIENTATION: String = "recycler.orientation"
        private const val STATE_DIVIDERS_ENABLED: String = "recycler.dividersEnabled"
        private const val SPAN_COUNT: Int = 2

        internal fun createIntent(context: Context): Intent = Intent(context, RecyclerViewDemoActivity::class.java)
    }

    @get:MainThread
    private val viewBinding by viewBinding(FunctionRecyclerActivityBinding::inflate)

    private var mode: RecyclerLayoutMode = RecyclerLayoutMode.LINEAR
    private var orientation: Int = RecyclerView.VERTICAL
    private var dividersEnabled: Boolean = true

    /**
     * 当前页面安装的唯一分割线实例, 切换配置时只移除本实例, 不影响其它装饰器.
     */
    private var dividerDecoration: RecyclerView.ItemDecoration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewBinding.root)
        viewBinding.root.applyWindowInsetsPadding()
        viewBinding.functionRecyclerToolbar.setNavigationOnClickListener { finish() }

        mode = RecyclerLayoutMode.fromName(savedInstanceState?.getString(STATE_LAYOUT_MODE))
        orientation = savedInstanceState?.getInt(STATE_ORIENTATION, RecyclerView.VERTICAL) ?: RecyclerView.VERTICAL
        dividersEnabled = savedInstanceState?.getBoolean(STATE_DIVIDERS_ENABLED, true) ?: true
        configureControls()
        configureList()
    }

    /**
     * 保存演示选项, 滚动布局状态交由 RecyclerView 自身的 View 状态恢复处理.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LAYOUT_MODE, mode.name)
        outState.putInt(STATE_ORIENTATION, orientation)
        outState.putBoolean(STATE_DIVIDERS_ENABLED, dividersEnabled)
        super.onSaveInstanceState(outState)
    }

    private fun configureControls() {
        viewBinding.functionRecyclerLayoutGroup.check(mode.buttonId)
        val orientationButtonId: Int = if (orientation == RecyclerView.VERTICAL) {
            R.id.function_recycler_vertical
        } else {
            R.id.function_recycler_horizontal
        }
        viewBinding.functionRecyclerOrientationGroup.check(orientationButtonId)
        viewBinding.functionRecyclerDividers.isChecked = dividersEnabled

        viewBinding.functionRecyclerLayoutGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedMode = RecyclerLayoutMode.fromButtonId(checkedId)
                if (mode != selectedMode) {
                    mode = selectedMode
                    configureList()
                }
            }
        }
        viewBinding.functionRecyclerOrientationGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedOrientation = if (checkedId == R.id.function_recycler_vertical) {
                    RecyclerView.VERTICAL
                } else {
                    RecyclerView.HORIZONTAL
                }
                if (orientation != selectedOrientation) {
                    orientation = selectedOrientation
                    configureList()
                }
            }
        }
        viewBinding.functionRecyclerDividers.setOnCheckedChangeListener { _, isChecked ->
            dividersEnabled = isChecked
            configureDividers()
        }
    }

    /**
     * 以当前选项重建布局和静态 Adapter, 清除旧模式遗留的 item 尺寸与 LayoutParams.
     *
     * 运行时切换只保留首个可见项的位置, 不保留像素偏移. 首次装配不主动滚动, 避免覆盖 RecyclerView
     * 随后恢复的 LayoutManager 状态; 启用分割线时只安装与当前布局匹配的一个实例.
     */
    private fun configureList() {
        val list = viewBinding.functionRecyclerList
        val previousLayoutManager: RecyclerView.LayoutManager? = list.layoutManager
        val firstVisiblePosition: Int = when (val layoutManager = previousLayoutManager) {
            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            is StaggeredGridLayoutManager -> layoutManager.findFirstVisibleItemPositions(null).minOrNull() ?: 0
            else -> 0
        }.coerceAtLeast(0)

        list.stopScroll()
        dividerDecoration?.let { list.removeItemDecoration(it) }
        dividerDecoration = null
        list.adapter = null
        list.layoutManager = when (mode) {
            RecyclerLayoutMode.LINEAR -> LinearLayoutManager(this, orientation, false)
            RecyclerLayoutMode.GRID -> GridLayoutManager(this, SPAN_COUNT, orientation, false)
            RecyclerLayoutMode.STAGGERED -> StaggeredGridLayoutManager(SPAN_COUNT, orientation)
        }
        list.adapter = RecyclerDividerDemoAdapter(mode, orientation)
        configureDividers()
        if (previousLayoutManager != null) {
            list.scrollToPosition(firstVisiblePosition)
        }
    }

    /**
     * 替换或移除页面拥有的分割线, 规整布局与瀑布流分别使用匹配的 Kit 装饰器.
     *
     * 变更后立即失效 item 的装饰器间距缓存, 避免开关关闭后仍保留之前的占位.
     */
    private fun configureDividers() {
        val list = viewBinding.functionRecyclerList
        dividerDecoration?.let { list.removeItemDecoration(it) }
        dividerDecoration = if (dividersEnabled) {
            val size: Int = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
            val color: Int = MaterialColors.getColor(list, MaterialR.attr.colorOutlineVariant)
            when (mode) {
                RecyclerLayoutMode.LINEAR, RecyclerLayoutMode.GRID -> RegularItemDividerDecoration(
                    dividerSize = size,
                    dividerMargin = 0,
                    dividerColor = color,
                )
                RecyclerLayoutMode.STAGGERED -> StaggeredItemDividerDecoration(
                    dividerSize = size,
                    dividerMargin = 0,
                    dividerColor = color,
                )
            }
        } else {
            null
        }
        dividerDecoration?.let { list.addItemDecoration(it) }
        list.invalidateItemDecorations()
    }
}
