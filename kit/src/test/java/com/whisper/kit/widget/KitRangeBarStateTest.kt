package com.whisper.kit.widget

import android.graphics.drawable.GradientDrawable
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证范围和约束通过原生 View 状态机制一致恢复, 且不恢复活动交互.
 *
 * @author whisper
 * @since 2026/09/10
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRangeBarStateTest {

    @Test
    fun restore_parcelRoundTripRestoresRangeAndEveryConstraintAsOneSnapshot() {
        val original = createBar(
            R.attr.kitTotal to "210",
            R.attr.kitStep to "30",
            R.attr.kitStart to "30",
            R.attr.kitEnd to "120",
            R.attr.kitStartMin to "20",
            R.attr.kitStartMax to "65",
            R.attr.kitEndMin to "80",
            R.attr.kitMinSpan to "60",
        )
        val savedState = saveThroughParcel(original)
        val restored = createBar(
            R.attr.kitTotal to "14",
            R.attr.kitStep to "7",
            R.attr.kitStartMax to "0",
            R.attr.kitEndMin to "14",
            R.attr.kitMinSpan to "14",
        )
        val reports = mutableListOf<Pair<Int, Int>>()
        val interactions = recordInteractions(restored)
        restored.setOnRangeChangedListener { start, end ->
            assertEquals(start to end, restored.start to restored.end)
            reports.add(start to end)
        }

        restored.restoreHierarchyState(savedState)

        assertEquals(30 to 120, restored.start to restored.end)
        assertEquals(20, restored.getStartMin())
        assertEquals(listOf(30 to 120), reports)
        assertTrue(interactions.isEmpty())

        restored.end = Int.MAX_VALUE
        assertEquals(30 to 210, restored.start to restored.end)
        restored.start = Int.MAX_VALUE
        assertEquals(60 to 210, restored.start to restored.end)
        restored.start = 35
        assertEquals(30 to 210, restored.start to restored.end)
        restored.start = 0
        restored.end = 0
        assertEquals(30 to 90, restored.start to restored.end)
        restored.end = 210
        restored.start = 60
        restored.end = 0
        assertEquals(60 to 120, restored.start to restored.end)
        restored.setMinSpan(0)
        restored.start = 30
        restored.end = 0
        assertEquals(30 to 90, restored.start to restored.end)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun restore_sameEffectiveSelectionDoesNotNotifyButStillRestoresConstraints() {
        val original = createBar(
            R.attr.kitStep to "10",
            R.attr.kitStart to "20",
            R.attr.kitEnd to "80",
            R.attr.kitMinSpan to "30",
            R.attr.kitStartMin to "15",
        )
        val restored = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80")
        val reports = mutableListOf<Pair<Int, Int>>()
        val interactions = recordInteractions(restored)
        restored.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        restored.restoreHierarchyState(saveThroughParcel(original))

        assertTrue(reports.isEmpty())
        assertEquals(15, restored.getStartMin())
        restored.start = 80
        assertEquals(50 to 80, restored.start to restored.end)
        restored.start = 35
        assertEquals(30 to 80, restored.start to restored.end)
        assertEquals(listOf(50 to 80, 30 to 80), reports)
        restored.start = 0
        assertEquals(20 to 80, restored.start to restored.end)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun restore_unlimitedStartMinimumReplacesRuntimeLimitWithoutChangingSelection() {
        val original = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80")
        val restored = createBar(
            R.attr.kitStart to "20",
            R.attr.kitEnd to "80",
            R.attr.kitStartMin to "20",
        )
        val reports = mutableListOf<Pair<Int, Int>>()
        restored.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        restored.restoreHierarchyState(saveThroughParcel(original))

        assertEquals(KitRangeBar.THUMB_UNLIMITED, restored.getStartMin())
        assertTrue(reports.isEmpty())
        restored.start = 0
        assertEquals(0 to 80, restored.start to restored.end)
        assertEquals(listOf(0 to 80), reports)
    }

    @Test
    fun restore_savedActiveGestureRestoresOnlyRangeAndAcceptsNewGestureNormally() {
        val original = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80")
        touch(original, MotionEvent.ACTION_DOWN, 50)
        touch(original, MotionEvent.ACTION_MOVE, 70)
        val restored = createBar()
        val interactions = recordInteractions(restored)
        val reports = mutableListOf<Pair<Int, Int>>()
        restored.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        restored.restoreHierarchyState(saveThroughParcel(original))

        assertEquals(30 to 80, restored.start to restored.end)
        assertEquals(listOf(30 to 80), reports)
        assertTrue(interactions.isEmpty())
        assertFalse(touch(restored, MotionEvent.ACTION_MOVE, 90))
        assertFalse(touch(restored, MotionEvent.ACTION_UP, 90))

        assertTrue(touch(restored, MotionEvent.ACTION_DOWN, 70))
        touch(restored, MotionEvent.ACTION_MOVE, 90)
        touch(restored, MotionEvent.ACTION_UP, 90)
        assertEquals(40 to 80, restored.start to restored.end)
        assertEquals(listOf("start", "end"), interactions)
        touch(original, MotionEvent.ACTION_CANCEL, 70)
    }

    @Test
    fun restore_clearsExistingTrackingWithoutReportingInteractionCompletion() {
        val original = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80")
        val restored = createBar()
        val interactions = recordInteractions(restored)
        touch(restored, MotionEvent.ACTION_DOWN, 10)
        touch(restored, MotionEvent.ACTION_MOVE, 30)
        assertEquals(listOf("start"), interactions)
        interactions.clear()
        val reports = mutableListOf<Pair<Int, Int>>()
        restored.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        restored.restoreHierarchyState(saveThroughParcel(original))

        assertEquals(20 to 80, restored.start to restored.end)
        assertEquals(listOf(20 to 80), reports)
        assertTrue(interactions.isEmpty())
        assertFalse(touch(restored, MotionEvent.ACTION_MOVE, 50))
        assertFalse(touch(restored, MotionEvent.ACTION_UP, 50))
    }

    @Test
    fun restore_unchangedBindingInCallbackDoesNotRecursivelyNotify() {
        val original = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80", R.attr.kitStep to "10")
        val restored = createBar()
        var changes = 0
        restored.setOnRangeChangedListener { start, end ->
            changes++
            check(changes == 1) { "Unchanged binding during restore must not notify again" }
            restored.setSelectedRange(end, start)
        }

        restored.restoreHierarchyState(saveThroughParcel(original))

        assertEquals(1, changes)
        assertEquals(20 to 80, restored.start to restored.end)
    }

    @Test
    fun save_respectsViewIdAndSaveEnabled() {
        val bar = createBar(R.attr.kitStart to "20", R.attr.kitEnd to "80")
        assertEquals(1, saveThroughParcel(bar).size())

        bar.isSaveEnabled = false
        assertEquals(0, saveThroughParcel(bar).size())

        bar.isSaveEnabled = true
        bar.id = View.NO_ID
        assertEquals(0, saveThroughParcel(bar).size())
    }

    private fun createBar(vararg entries: Pair<Int, String>): KitRangeBar {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.kitThumbWidth, "20px")
            .addAttribute(R.attr.kitThumbHeight, "20px")
        entries.forEach { (id, value) -> attrs.addAttribute(id, value) }
        return KitRangeBar(RuntimeEnvironment.getApplication(), attrs.build()).apply {
            id = 0x123456
            setThumb(GradientDrawable().apply { setSize(20, 20) })
            FrameLayout(context).addView(this)
            measure(
                View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 220, 20)
        }
    }

    private fun saveThroughParcel(bar: KitRangeBar): SparseArray<Parcelable> {
        val state = SparseArray<Parcelable>()
        bar.saveHierarchyState(state)
        val parcel = Parcel.obtain()
        return try {
            parcel.writeSparseArray(state)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readSparseArray(KitRangeBar::class.java.classLoader, Parcelable::class.java))
        } finally {
            parcel.recycle()
        }
    }

    private fun recordInteractions(bar: KitRangeBar): MutableList<String> {
        val interactions = mutableListOf<String>()
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                interactions.add("start")
            }

            override fun onInteractionEnd(start: Int, end: Int) {
                interactions.add("end")
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                interactions.add("cancel")
            }
        })
        return interactions
    }

    private fun touch(bar: KitRangeBar, action: Int, x: Int): Boolean {
        val event = MotionEvent.obtain(0, 16, action, x.toFloat(), 10f, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        return try {
            bar.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
