package com.whisper.feature.function.recyclerview

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.whisper.feature.function.R
import com.whisper.feature.function.databinding.FunctionRecyclerItemBinding
import com.whisper.kit.recyclerview.decoration.StaggeredFullSpanProvider
import com.whisper.kit.recyclerview.holder.ViewBindingHolder
import kotlin.math.roundToInt

/**
 * 绑定静态列表项, 瀑布流使用不同主轴尺寸展示不规则分割线.
 *
 * full-span 查询与实际 LayoutParams 绑定共用同一个结果, 不参与装饰器几何计算.
 */
internal class RecyclerDividerDemoAdapter(
    private val mode: RecyclerLayoutMode,
    private val orientation: Int,
) : RecyclerView.Adapter<ViewBindingHolder<FunctionRecyclerItemBinding>>(), StaggeredFullSpanProvider {

    companion object {
        private const val ITEM_COUNT: Int = 24
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewBindingHolder<FunctionRecyclerItemBinding> =
        ViewBindingHolder.create(parent, FunctionRecyclerItemBinding::inflate)

    override fun onBindViewHolder(holder: ViewBindingHolder<FunctionRecyclerItemBinding>, position: Int) {
        val binding = holder.binding
        val resources = binding.root.resources
        val names = resources.getStringArray(R.array.function_recycler_item_names)
        binding.functionRecyclerItemName.text = names[position % names.size]
        binding.functionRecyclerItemIndex.text = resources.getString(
            R.string.function_recycler_item_index,
            position + 1,
        )

        val mainAxisSizeDp: Int = when (mode) {
            RecyclerLayoutMode.LINEAR -> if (orientation == RecyclerView.VERTICAL) 72 else 192
            RecyclerLayoutMode.GRID -> if (orientation == RecyclerView.VERTICAL) 96 else 192
            RecyclerLayoutMode.STAGGERED -> if (orientation == RecyclerView.VERTICAL) {
                when (position % 4) {
                    0 -> 96
                    1 -> 144
                    2 -> 120
                    else -> 168
                }
            } else {
                176 + position % 4 * 32
            }
        }
        val mainAxisSize: Int = (mainAxisSizeDp * resources.displayMetrics.density).roundToInt()
        val layoutParams = binding.root.layoutParams
        if (orientation == RecyclerView.VERTICAL) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.root.minimumHeight = mainAxisSize
        } else {
            layoutParams.width = mainAxisSize
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.root.minimumHeight = 0
        }
        if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
            layoutParams.isFullSpan = isFullSpan(position)
        }
        binding.root.layoutParams = layoutParams
    }

    override fun getItemCount(): Int = ITEM_COUNT

    override fun isFullSpan(position: Int): Boolean = false
}
