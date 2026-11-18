package com.whisper.foundation.recyclerview.adapter

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.whisper.foundation.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 验证整数序列 Adapter 的即时映射、边界和更新通知.
 *
 * @author whisper
 * @since 2026/10/22
 */
@RunWith(RobolectricTestRunner::class)
class IntSequenceAdapterTest {

    @Test
    fun onBindViewHolder_whenFormatterIsProvided_bindsTextThroughViewBindingHolder() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Foundation_Theme,
        )
        val parent = FrameLayout(context)
        val adapter = IntSequenceAdapter(
            startValue = 7,
            itemCount = 1,
            formatter = IntSequenceAdapter.Formatter { value: Int -> "No. $value" },
        )

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("No. 7", holder.binding.root.text.toString())
    }

    @Test
    fun values_whenSequenceUsesPositiveStep_mapsWithoutBackingList() {
        val adapter = IntSequenceAdapter(startValue = 3, itemCount = 4, step = 2)

        assertEquals(4, adapter.itemCount)
        assertEquals(3, adapter.getValue(0))
        assertEquals(9, adapter.getValue(3))
        assertEquals(2, adapter.getPosition(7))
        assertNull(adapter.getValue(4))
        assertNull(adapter.getPosition(8))
    }

    @Test
    fun values_whenSequenceUsesNegativeStep_mapsInDescendingOrder() {
        val adapter = IntSequenceAdapter(startValue = 5, itemCount = 3, step = -2)

        assertEquals(5, adapter.getValue(0))
        assertEquals(1, adapter.getValue(2))
        assertEquals(1, adapter.getPosition(3))
    }

    @Test
    fun updateSequence_whenMappingAndSizeChange_dispatchesCompleteUpdate() {
        val adapter = IntSequenceAdapter(startValue = 1, itemCount = 4)
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.updateSequence(startValue = 3, itemCount = 2)

        assertEquals(listOf("removed:2:2", "changed:0:2"), observer.events)
        assertEquals(3, adapter.getValue(0))
        assertEquals(4, adapter.getValue(1))
    }

    @Test
    fun create_whenSequenceOverflowsInt_rejectsConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            IntSequenceAdapter(
                startValue = Int.MAX_VALUE,
                itemCount = 2,
                step = 1,
            )
        }
    }

    /**
     * 按发生顺序记录 Adapter 的粒度更新通知.
     */
    private class RecordingObserver : RecyclerView.AdapterDataObserver() {

        val events: MutableList<String> = mutableListOf()

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            events += "changed:$positionStart:$itemCount"
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            events += "inserted:$positionStart:$itemCount"
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            events += "removed:$positionStart:$itemCount"
        }
    }
}
