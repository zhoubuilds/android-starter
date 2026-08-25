package com.whisper.kit.recyclerview.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * 持有 ViewBinding 的 RecyclerView ViewHolder.
 *
 * 该 ViewHolder 只保存 binding, root View 由 [ViewBinding.getRoot] 提供.
 * 推荐通过 [create] 和生成 binding 的 `inflate` 方法引用创建实例, 避免反射创建 binding.
 *
 * @property binding 当前 item 的 ViewBinding.
 *
 * @author whisper
 * @since 2026/07/30
 */
class ViewBindingHolder<VB : ViewBinding>(
    val binding: VB,
) : RecyclerView.ViewHolder(binding.root) {

    companion object {

        /**
         * 使用 ViewBinding inflate 方法创建 ViewHolder.
         *
         * 示例:
         *
         * ```kotlin
         * override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder<ItemUserBinding> =
         *     ViewBindingHolder.create(parent, ItemUserBinding::inflate)
         * ```
         *
         * @param parent item 所属 RecyclerView 父容器.
         * @param inflater ViewBinding 生成类的 inflate 方法引用.
         * @return 持有 [VB] 的 ViewHolder.
         */
        fun <VB : ViewBinding> create(
            parent: ViewGroup,
            inflater: ViewBindingInflater<VB>,
        ): ViewBindingHolder<VB> =
            ViewBindingHolder(
                inflater.inflate(
                    inflater = LayoutInflater.from(parent.context),
                    parent = parent,
                    attachToParent = false,
                ),
            )
    }
}
