package com.whisper.feature.function

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.whisper.feature.function.databinding.FunctionDemoItemBinding
import com.whisper.kit.recyclerview.holder.ViewBindingHolder

/**
 * 绑定 Function 静态演示目录.
 *
 * @property onDemoSelected 交由页面处理的选择回调, Adapter 不负责构造 Intent 或启动详情.
 */
internal class FunctionCatalogAdapter(
    private val onDemoSelected: (FunctionDemo) -> Unit,
) : RecyclerView.Adapter<ViewBindingHolder<FunctionDemoItemBinding>>() {

    private val demos: List<FunctionDemo> = FunctionDemo.entries

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewBindingHolder<FunctionDemoItemBinding> =
        ViewBindingHolder.create(parent, FunctionDemoItemBinding::inflate)

    /**
     * 使用原生点击监听保留键盘, 无障碍与 performClick 激活能力.
     *
     * Kit 的 item 手势监听只旁路观察触摸事件, 不替代这些入口, 也不应同时执行相同导航.
     */
    override fun onBindViewHolder(
        holder: ViewBindingHolder<FunctionDemoItemBinding>,
        position: Int,
    ) {
        val demo: FunctionDemo = demos[position]
        with(holder.binding) {
            functionDemoIcon.setImageResource(demo.iconRes)
            functionDemoTitle.setText(demo.titleRes)
            functionDemoComponentName.setText(demo.componentNameRes)
            root.setOnClickListener {
                onDemoSelected(demo)
            }
        }
    }

    override fun getItemCount(): Int = demos.size
}
