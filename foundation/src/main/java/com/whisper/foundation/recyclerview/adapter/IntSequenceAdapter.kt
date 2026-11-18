package com.whisper.foundation.recyclerview.adapter

import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.recyclerview.widget.RecyclerView
import com.whisper.foundation.databinding.FoundationItemIntSequenceBinding
import com.whisper.kit.recyclerview.holder.ViewBindingHolder

/**
 * 按起始值、数量和步长即时计算整数序列的 RecyclerView Adapter.
 *
 * Adapter 不创建或持有完整列表. 序列更新后, 值未变化的前缀只接收结构通知,
 * 映射发生变化的可见位置会重新绑定.
 *
 * @property startValue 当前序列 position 0 对应的值.
 * @property step 相邻 position 之间的非零差值.
 *
 * @author whisper
 * @since 2026/10/22
 */
class IntSequenceAdapter(
    startValue: Int = 0,
    itemCount: Int = 0,
    step: Int = 1,
    private var formatter: Formatter = Formatter { value: Int -> value.toString() },
) : RecyclerView.Adapter<ViewBindingHolder<FoundationItemIntSequenceBinding>>() {

    /**
     * 将一个序列值转换为条目显示文本.
     *
     * @author whisper
     * @since 2026/10/22
     */
    fun interface Formatter {

        fun format(value: Int): CharSequence
    }

    var startValue: Int = startValue
        private set

    var step: Int = step
        private set

    private var sequenceSize: Int = itemCount

    init {
        validateSequence(startValue, itemCount, step)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewBindingHolder<FoundationItemIntSequenceBinding> =
        ViewBindingHolder.create(parent, FoundationItemIntSequenceBinding::inflate)

    override fun onBindViewHolder(
        holder: ViewBindingHolder<FoundationItemIntSequenceBinding>,
        position: Int,
    ) {
        holder.binding.root.text = formatter.format(checkNotNull(getValue(position)))
    }

    override fun getItemCount(): Int = sequenceSize

    /**
     * 返回 position 对应的整数值, 越界时返回 null.
     */
    fun getValue(@IntRange(from = 0) position: Int): Int? {
        if (position !in 0 until sequenceSize) {
            return null
        }
        return (startValue.toLong() + position.toLong() * step.toLong()).toInt()
    }

    /**
     * 返回整数值在当前序列中的 position, 不属于序列时返回 null.
     */
    fun getPosition(value: Int): Int? {
        val distance: Long = value.toLong() - startValue.toLong()
        val sequenceStep: Long = step.toLong()
        if (distance % sequenceStep != 0L) {
            return null
        }
        val position: Long = distance / sequenceStep
        return position.takeIf { it in 0 until sequenceSize.toLong() }?.toInt()
    }

    /**
     * 更新由起始值、数量和步长定义的序列, 不分配中间列表.
     *
     * @throws IllegalArgumentException itemCount 小于 0、step 为 0, 或末项超出 Int 范围.
     */
    fun updateSequence(
        startValue: Int,
        @IntRange(from = 0) itemCount: Int,
        step: Int = this.step,
    ) {
        validateSequence(startValue, itemCount, step)
        val previousStartValue: Int = this.startValue
        val previousStep: Int = this.step
        val previousSize: Int = sequenceSize
        if (
            previousStartValue == startValue &&
            previousStep == step &&
            previousSize == itemCount
        ) {
            return
        }

        this.startValue = startValue
        this.step = step
        sequenceSize = itemCount

        val sharedSize: Int = minOf(previousSize, itemCount)
        val valuesChanged: Boolean = previousStartValue != startValue || previousStep != step
        if (itemCount < previousSize) {
            notifyItemRangeRemoved(itemCount, previousSize - itemCount)
        }
        if (valuesChanged && sharedSize > 0) {
            notifyItemRangeChanged(0, sharedSize)
        }
        if (itemCount > previousSize) {
            notifyItemRangeInserted(previousSize, itemCount - previousSize)
        }
    }

    /**
     * 更新显示格式并重新绑定当前序列.
     */
    fun updateFormatter(formatter: Formatter) {
        this.formatter = formatter
        if (sequenceSize > 0) {
            notifyItemRangeChanged(0, sequenceSize)
        }
    }

    private companion object {

        private fun validateSequence(startValue: Int, itemCount: Int, step: Int) {
            require(itemCount >= 0) { "itemCount must be non-negative" }
            require(step != 0) { "step must not be zero" }
            if (itemCount == 0) {
                return
            }
            val endValue: Long = startValue.toLong() +
                (itemCount - 1L) * step.toLong()
            require(endValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "sequence values must fit in Int"
            }
        }
    }
}
