package com.whisper.kit.recyclerview.decoration

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * StaggeredGridLayoutManager item 间距装饰器.
 *
 * 普通 item 的交叉轴间距使用其实际 span index 计算; full-span item 不增加交叉轴 offset.
 * 主轴间距由非起始 item 的 logical start offset 单边承担. [startSpace] 则只应用于真正接触
 * Adapter 逻辑起始边界的 item.
 * 为保持交叉轴取整和绘制归属稳定, 同一个 RecyclerView 的同一轴最多只能安装一个内部间距非零的 Decoration.
 * 仅设置 [startSpace]、且主轴和交叉轴内部间距均为 0 的边界 Decoration 可以共存.
 * 子类可以在该间距语义上增加绘制行为, 但不能覆写 [getItemOffsets] 改变取整、Provider 或布局拓扑规则.
 *
 * 主轴起始拓扑需要 [StaggeredFullSpanProvider] 时, RecyclerView 的 Adapter 应实现该接口, 并在绑定 item 时
 * 使用相同查询结果设置 [StaggeredGridLayoutManager.LayoutParams.setFullSpan]. 缺少 Provider 时会按实例输出一次
 * 警告日志并禁用主轴 offset, 交叉轴仍按 LayoutParams 的实际 full-span 状态计算. 已提供 Provider 但在非预测布局中
 * 与 LayoutParams 不一致时会立即失败, 避免 Decoration 与 LayoutManager 使用不同拓扑计算间距.
 *
 * 不支持 decorated main-axis measurement 为 0 的 item; 每个 item 包含 decoration inset 和
 * LayoutParams margin 后的主轴占用尺寸必须大于 0. 零尺寸 item 不会推进 span 端点,
 * 无法仅通过 Adapter position 和 [StaggeredFullSpanProvider] 稳定推导起始归属.
 *
 * 所有间距必须为非负 px 值. 该装饰器不提供结束边界间距, 也不支持其它 LayoutManager.
 * 遇到不支持的非空 LayoutManager 时会清空 offset、忽略该装饰器, 并按实例输出一次警告日志.
 * 该装饰器不观察 Adapter 数据更新. 同步 `notifyItem*` 会改变 position、itemCount、span 或起始归属时,
 * 调用方应在更新前通过 AndroidX Core KTX 官方 [androidx.core.view.doOnNextLayout] 注册下一次布局回调,
 * 并在更新布局完成后失效 Decoration:
 *
 * ```
 * recyclerView.doOnNextLayout {
 *     recyclerView.invalidateItemDecorations()
 * }
 * adapter.notifyItemMoved(fromPosition, toPosition)
 * ```
 *
 * `ListAdapter` 或 `AsyncListDiffer` 的 `submitList` 会异步计算差异, 应改在 commit callback 中注册:
 *
 * ```
 * adapter.submitList(newList) {
 *     recyclerView.doOnNextLayout {
 *         recyclerView.invalidateItemDecorations()
 *     }
 * }
 * ```
 *
 * 连续调用 `submitList` 时, 较早但未实际提交的列表可能不会执行 callback; 最终状态恢复必须挂在实际提交的
 * 最新列表 callback 中. 在 `submitList` 前预注册的一次性回调可能被差异提交前的其它布局提前消费.
 *
 * 紧跟 Adapter `notify` 同步调用 [RecyclerView.invalidateItemDecorations] 可能被 predictive pre-layout 消费.
 * 运行时修改 LayoutManager 的 `orientation`、`reverseLayout`、`spanCount`, 或修改 RecyclerView
 * layout direction 后, 应立即调用 [RecyclerView.invalidateItemDecorations],
 * 使下一次布局重算已缓存的 decoration inset.
 *
 * @property mainAxisSpace 主轴 item 间距, 单位 px.
 * @property crossAxisSpace 交叉轴 item 间距, 单位 px.
 * @property startSpace 主轴起始边界间距, 单位 px.
 *
 * @author whisper
 * @since 2026/09/02
 */
open class StaggeredItemSpaceDecoration(
    @param:Px
    @param:IntRange(from = 0)
    private val mainAxisSpace: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val crossAxisSpace: Int,
    @param:Px
    @param:IntRange(from = 0)
    private val startSpace: Int = 0,
) : RecyclerView.ItemDecoration() {

    private var hasLoggedUnsupportedLayoutManagerWarning: Boolean = false
    private var hasLoggedMissingFullSpanProviderWarning: Boolean = false

    init {
        require(mainAxisSpace >= 0) { "mainAxisSpace must be non-negative." }
        require(crossAxisSpace >= 0) { "crossAxisSpace must be non-negative." }
        require(startSpace >= 0) { "startSpace must be non-negative." }
    }

    final override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.setEmpty()
        val currentLayoutManager: RecyclerView.LayoutManager = parent.layoutManager ?: return
        val layoutManager: StaggeredGridLayoutManager =
            currentLayoutManager as? StaggeredGridLayoutManager ?: run {
                logUnsupportedLayoutManagerOnce(currentLayoutManager)
                return
            }
        if (state.itemCount <= 0) return

        val position: Int = parent.getChildAdapterPosition(view)
        if (position !in 0 until state.itemCount) return

        val adapter: RecyclerView.Adapter<*> = parent.adapter ?: run {
            logMissingFullSpanProviderOnce(adapter = null)
            return
        }
        if (position >= adapter.itemCount) return

        val layoutParams: StaggeredGridLayoutManager.LayoutParams =
            view.layoutParams as? StaggeredGridLayoutManager.LayoutParams
                ?: error(
                    "StaggeredItemSpaceDecoration requires " +
                        "StaggeredGridLayoutManager.LayoutParams.",
                )
        val isFullSpan: Boolean = layoutParams.isFullSpan
        val fullSpanProvider: StaggeredFullSpanProvider? = adapter as? StaggeredFullSpanProvider
        val providerIsFullSpan: Boolean? = fullSpanProvider?.isFullSpan(position)
        if (!state.isPreLayout && providerIsFullSpan != null) {
            check(isFullSpan == providerIsFullSpan) {
                "StaggeredFullSpanProvider and LayoutParams.isFullSpan disagree at position $position."
            }
        }

        val requiresFullSpanProvider: Boolean = startSpace != mainAxisSpace &&
            layoutManager.spanCount > 1 && adapter.itemCount > 1
        if (requiresFullSpanProvider && fullSpanProvider == null) {
            logMissingFullSpanProviderOnce(adapter)
        }
        val mainStart: Int = when {
            requiresFullSpanProvider && fullSpanProvider == null -> 0
            startSpace == mainAxisSpace -> mainAxisSpace
            layoutManager.spanCount <= 1 || adapter.itemCount <= 1 -> {
                if (position == 0) startSpace else mainAxisSpace
            }
            isAtStaggeredMainAxisStart(
                position = position,
                itemCount = adapter.itemCount,
                spanCount = layoutManager.spanCount,
                isFullSpan = checkNotNull(providerIsFullSpan),
                fullSpanProvider = checkNotNull(fullSpanProvider),
            ) -> startSpace
            else -> mainAxisSpace
        }
        val mainAxisReversed: Boolean = isMainAxisReversed(layoutManager, parent)

        val crossStart: Int
        val crossEnd: Int
        if (isFullSpan) {
            crossStart = 0
            crossEnd = 0
        } else {
            val spanIndex: Int = layoutParams.spanIndex
            check(spanIndex in 0 until layoutManager.spanCount) {
                "StaggeredGridLayoutManager did not assign a valid span at position $position."
            }
            val logicalSpanIndex: Int = if (
                layoutManager.orientation == RecyclerView.VERTICAL && isRtl(parent)
            ) {
                layoutManager.spanCount - 1 - spanIndex
            } else {
                spanIndex
            }
            crossStart = crossAxisStartOffset(crossAxisSpace, layoutManager.spanCount, logicalSpanIndex)
            crossEnd = crossAxisEndOffset(crossAxisSpace, layoutManager.spanCount, logicalSpanIndex)
        }

        if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            outRect.left = if (mainAxisReversed) 0 else mainStart
            outRect.top = crossStart
            outRect.right = if (mainAxisReversed) mainStart else 0
            outRect.bottom = crossEnd
        } else if (isRtl(parent)) {
            outRect.left = crossEnd
            outRect.top = if (mainAxisReversed) 0 else mainStart
            outRect.right = crossStart
            outRect.bottom = if (mainAxisReversed) mainStart else 0
        } else {
            outRect.left = crossStart
            outRect.top = if (mainAxisReversed) 0 else mainStart
            outRect.right = crossEnd
            outRect.bottom = if (mainAxisReversed) mainStart else 0
        }
    }

    /**
     * 判断主轴布局方向是否相对 Adapter 顺序反向.
     */
    private fun isMainAxisReversed(
        layoutManager: StaggeredGridLayoutManager,
        parent: RecyclerView,
    ): Boolean =
        layoutManager.reverseLayout.xor(
            layoutManager.orientation == RecyclerView.HORIZONTAL && isRtl(parent),
        )

    /**
     * 判断 RecyclerView 是否使用 RTL 布局方向.
     */
    private fun isRtl(parent: RecyclerView): Boolean =
        parent.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /**
     * 按装饰器实例记录一次不支持的 LayoutManager 警告.
     */
    private fun logUnsupportedLayoutManagerOnce(layoutManager: RecyclerView.LayoutManager) {
        if (hasLoggedUnsupportedLayoutManagerWarning) return
        hasLoggedUnsupportedLayoutManagerWarning = true
        Log.w(
            LOG_TAG,
            "Staggered RecyclerView decoration supports only StaggeredGridLayoutManager; " +
                "${layoutManager.javaClass.name} is ignored.",
        )
    }

    /**
     * 按装饰器实例记录一次缺少 full-span 拓扑的警告.
     */
    private fun logMissingFullSpanProviderOnce(adapter: RecyclerView.Adapter<*>?) {
        if (hasLoggedMissingFullSpanProviderWarning) return
        hasLoggedMissingFullSpanProviderWarning = true
        val adapterName: String = adapter?.javaClass?.name ?: "null"
        Log.w(
            LOG_TAG,
            "Staggered RecyclerView decoration disabled its main-axis behavior because Adapter " +
                "$adapterName does not implement StaggeredFullSpanProvider.",
        )
    }

    private companion object {

        private const val LOG_TAG: String = "RecyclerViewDecoration"
    }
}
