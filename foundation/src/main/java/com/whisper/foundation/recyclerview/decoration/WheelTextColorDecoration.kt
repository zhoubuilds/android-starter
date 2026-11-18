package com.whisper.foundation.recyclerview.decoration

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.recyclerview.widget.RecyclerView

/**
 * 使用纵向渐变为滚轮中心分段和上下分段着色.
 *
 * 中心一个分段保持 [selectedColor]. 相邻区域从 [centerColor] 向 RecyclerView 上下边缘的
 * [edgeColor] 线性渐变. Decoration 使用 `SRC_IN` 为已绘制内容按像素着色, 因此一个字形跨越
 * 分段边界时, 两侧会呈现不同颜色. 目标 RecyclerView 应只包含需要共同着色的文字条目,
 * 且每个 RecyclerView 应使用独立实例.
 *
 * @property selectedColor 中心分段颜色.
 * @property centerColor 中心分段外侧起始颜色.
 * @property edgeColor RecyclerView 上下边缘颜色.
 * @property segmentCount RecyclerView 高度包含的等高分段数量, 可以包含非整数的部分分段.
 *
 * @author whisper
 * @since 2026/10/22
 */
class WheelTextColorDecoration(
    @ColorInt private val selectedColor: Int,
    @ColorInt private val centerColor: Int,
    @ColorInt private val edgeColor: Int,
    @FloatRange(from = 1.0)
    segmentCount: Float,
) : RecyclerView.ItemDecoration() {

    private val selectedBandHalfFraction: Float
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private var layerSaveCount: Int = NO_LAYER_SAVE_COUNT
    private var shaderHeight: Int = 0

    init {
        require(segmentCount.isFinite() && segmentCount >= 1f) {
            "segmentCount must be finite and at least one"
        }
        selectedBandHalfFraction = 0.5f / segmentCount
    }

    override fun onDraw(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (parent.width <= 0 || parent.height <= 0) {
            return
        }
        check(layerSaveCount == NO_LAYER_SAVE_COUNT) {
            "WheelTextColorDecoration cannot start a nested draw pass"
        }
        layerSaveCount = canvas.saveLayer(
            0f,
            0f,
            parent.width.toFloat(),
            parent.height.toFloat(),
            null,
        )
    }

    override fun onDrawOver(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val saveCount: Int = layerSaveCount
        if (saveCount == NO_LAYER_SAVE_COUNT) {
            return
        }
        layerSaveCount = NO_LAYER_SAVE_COUNT
        try {
            updateShader(parent.height)
            canvas.drawRect(
                0f,
                0f,
                parent.width.toFloat(),
                parent.height.toFloat(),
                paint,
            )
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * 返回从上边缘到下边缘的渐变颜色序列.
     */
    internal fun getGradientColors(): IntArray = intArrayOf(
        edgeColor,
        centerColor,
        selectedColor,
        selectedColor,
        centerColor,
        edgeColor,
    )

    /**
     * 返回与渐变颜色对应的归一化纵向位置.
     */
    internal fun getGradientPositions(): FloatArray = floatArrayOf(
        0f,
        0.5f - selectedBandHalfFraction,
        0.5f - selectedBandHalfFraction,
        0.5f + selectedBandHalfFraction,
        0.5f + selectedBandHalfFraction,
        1f,
    )

    private fun updateShader(height: Int) {
        if (shaderHeight == height) {
            return
        }
        shaderHeight = height
        paint.shader = createShader(height)
    }

    private fun createShader(height: Int): Shader {
        require(height > 0) { "height must be positive" }
        return LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            getGradientColors(),
            getGradientPositions(),
            Shader.TileMode.CLAMP,
        )
    }

    private companion object {

        private const val NO_LAYER_SAVE_COUNT: Int = -1
    }
}
