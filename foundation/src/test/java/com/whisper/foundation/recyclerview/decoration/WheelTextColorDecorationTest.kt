package com.whisper.foundation.recyclerview.decoration

import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 验证滚轮文字渐变的中心色带、边缘颜色和参数边界.
 *
 * @author whisper
 * @since 2026/10/22
 */
@RunWith(RobolectricTestRunner::class)
class WheelTextColorDecorationTest {

    @Test
    fun gradient_whenFiveSegmentsAreVisible_keepsCenterSelectedAndBlendsTowardEdges() {
        val decoration = WheelTextColorDecoration(
            selectedColor = Color.RED,
            centerColor = Color.GREEN,
            edgeColor = Color.BLUE,
            segmentCount = 5f,
        )

        assertArrayEquals(
            intArrayOf(
                Color.BLUE,
                Color.GREEN,
                Color.RED,
                Color.RED,
                Color.GREEN,
                Color.BLUE,
            ),
            decoration.getGradientColors(),
        )
        assertArrayEquals(
            floatArrayOf(0f, 0.4f, 0.4f, 0.6f, 0.6f, 1f),
            decoration.getGradientPositions(),
            POSITION_TOLERANCE,
        )
    }

    @Test
    fun create_whenSegmentCountIsLessThanOne_rejectsConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            WheelTextColorDecoration(
                selectedColor = Color.RED,
                centerColor = Color.GREEN,
                edgeColor = Color.BLUE,
                segmentCount = 0.5f,
            )
        }
    }

    private companion object {

        private const val POSITION_TOLERANCE: Float = 0.0001f
    }
}
