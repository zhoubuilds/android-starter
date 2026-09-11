package com.whisper.kit.widget.rangeselection

import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.whisper.kit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证整数范围状态、程序赋值、配置边界和仅通知有效变化的回调约定.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRangeBarTest {

    @Test
    fun selectedRange_preservesBothEndpointsAndNormalizesBounds() {
        val bar = createBar()
        for ((input, expected) in listOf(
            (20 to 60) to (20 to 60),
            (60 to 20) to (20 to 60),
            (-20 to 160) to (0 to 100),
            (Int.MAX_VALUE to Int.MIN_VALUE) to (0 to 100),
            (50 to 50) to (50 to 50),
        )) {
            bar.setSelectedRange(input.first, input.second)
            assertEquals(expected, bar.start to bar.end)
        }
    }

    @Test
    fun singleEndpoint_gettersAndCallbacksAlwaysObserveSameOrderedRange() {
        val bar = createBar()
        var reported: Pair<Int, Int>? = null
        bar.setSelectedRange(20, 60)
        bar.setOnRangeChangedListener { start, end ->
            assertTrue(start <= end)
            reported = start to end
            assertEquals(reported, bar.start to bar.end)
        }

        bar.start = 80
        assertEquals(60 to 60, reported)
        bar.end = -1
        assertEquals(60 to 60, reported)
        bar.start = -20
        assertEquals(0 to 60, reported)
        bar.end = Int.MAX_VALUE
        assertEquals(0 to 100, reported)
    }

    @Test
    fun total_reductionClampsBothEndpointsAndNeverCreatesNegativeDomain() {
        val bar = createBar()
        bar.setSelectedRange(80, 100)

        bar.setTotal(50)
        assertEquals(50 to 50, bar.start to bar.end)
        bar.setTotal(0)
        assertEquals(1 to 1, bar.start to bar.end)
    }

    @Test
    fun programAssignments_notifyOnlyWhenNormalizedRangeChanges() {
        val bar = createBar(R.attr.kitStep to "30")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end ->
            assertEquals(start to end, bar.start to bar.end)
            reports.add(start to end)
        }
        assertTrue(reports.isEmpty())

        bar.setSelectedRange(25, 95)
        assertEquals(30 to 90, bar.start to bar.end)
        bar.setStartMax(20)
        assertEquals(0 to 90, bar.start to bar.end)
        bar.setEndMin(65)
        bar.setMinSpan(70)
        bar.setTotal(95)
        bar.setSelectedRange(95, -1)
        bar.start = 14
        bar.end = 100

        assertEquals(0 to 90, bar.start to bar.end)
        assertEquals(listOf(30 to 90, 0 to 90), reports)
    }

    @Test
    fun selectedRange_projectsRawSortedValuesWithoutRoundingThemFirst() {
        val bar = createBar(R.attr.kitStep to "10", R.attr.kitMinSpan to "10")

        bar.setSelectedRange(11, 11)
        assertEquals(10 to 20, bar.start to bar.end)
        bar.setSelectedRange(26, 14)
        assertEquals(10 to 30, bar.start to bar.end)
        bar.setSelectedRange(10, 10)
        assertEquals(0 to 10, bar.start to bar.end)
    }

    @Test
    fun singleEndpoint_preservesOtherEndpointAndChoosesNearestLegalStep() {
        val bar = createBar(
            R.attr.kitStep to "10",
            R.attr.kitStart to "20",
            R.attr.kitEnd to "80",
            R.attr.kitStartMax to "45",
            R.attr.kitEndMin to "65",
            R.attr.kitMinSpan to "25",
        )
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end ->
            assertEquals(start to end, bar.start to bar.end)
            reports.add(start to end)
        }

        bar.start = 38
        assertEquals(40 to 80, bar.start to bar.end)
        bar.start = 100
        assertEquals(40 to 80, bar.start to bar.end)
        bar.end = 65
        assertEquals(40 to 70, bar.start to bar.end)
        bar.start = 35
        assertEquals(30 to 70, bar.start to bar.end)
        bar.end = 75
        assertEquals(30 to 70, bar.start to bar.end)
        bar.end = Int.MIN_VALUE
        assertEquals(30 to 70, bar.start to bar.end)
        bar.start = Int.MIN_VALUE
        assertEquals(0 to 70, bar.start to bar.end)
        bar.end = Int.MAX_VALUE
        assertEquals(0 to 100, bar.start to bar.end)
        assertEquals(listOf(40 to 80, 40 to 70, 30 to 70, 0 to 70, 0 to 100), reports)
    }

    @Test
    fun startMinimum_xmlAndEveryProgramAssignmentRespectFirstAvailableTick() {
        val bar = createBar(
            R.attr.kitStep to "30",
            R.attr.kitStartMin to "20",
            R.attr.kitStart to "0",
            R.attr.kitEnd to "10",
        )
        assertEquals(20, bar.getStartMin())
        assertEquals(30 to 30, bar.start to bar.end)
        bar.setSelectedRange(100, -100)
        assertEquals(30 to 90, bar.start to bar.end)
        bar.start = Int.MIN_VALUE
        bar.end = Int.MIN_VALUE
        assertEquals(30 to 30, bar.start to bar.end)
        bar.setSelectedRange(Int.MIN_VALUE, Int.MIN_VALUE)
        assertEquals(30 to 30, bar.start to bar.end)

        bar.setStartMin(0)
        assertEquals(0, bar.getStartMin())
        bar.setSelectedRange(0, 0)
        assertEquals(0 to 0, bar.start to bar.end)
    }

    @Test
    fun startMinimum_normalizesLimitsAndNotifiesOnlyForChangedProjection() {
        val bar = createBar(R.attr.kitStep to "10", R.attr.kitStart to "10", R.attr.kitEnd to "30")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }
        assertEquals(KitRangeBar.THUMB_UNLIMITED, bar.getStartMin())

        bar.setStartMin(25)
        assertEquals(30 to 30, bar.start to bar.end)
        bar.setStartMin(25)
        bar.start = 0
        bar.end = 0
        bar.setSelectedRange(0, 0)
        assertEquals(listOf(30 to 30), reports)

        bar.setStartMin(Int.MAX_VALUE)
        assertEquals(100, bar.getStartMin())
        assertEquals(100 to 100, bar.start to bar.end)
        bar.setStartMin(Int.MIN_VALUE)
        assertEquals(KitRangeBar.THUMB_UNLIMITED, bar.getStartMin())
        bar.start = 0
        assertEquals(0 to 100, bar.start to bar.end)
        assertEquals(listOf(30 to 30, 100 to 100, 0 to 100), reports)
    }

    @Test
    fun startMinimum_rejectedLimitsLeaveAllConstraintsAndSelectionUnchanged() {
        val bar = createBar(
            R.attr.kitStep to "30",
            R.attr.kitStart to "30",
            R.attr.kitEnd to "60",
            R.attr.kitStartMin to "20",
            R.attr.kitStartMax to "60",
            R.attr.kitMinSpan to "30",
        )
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        assertThrows(IllegalArgumentException::class.java) { bar.setStartMin(61) }
        assertThrows(IllegalArgumentException::class.java) { bar.setStartMax(29) }
        assertThrows(IllegalArgumentException::class.java) { bar.setMinSpan(61) }
        assertThrows(IllegalArgumentException::class.java) { bar.setTotal(59) }
        assertEquals(20, bar.getStartMin())
        assertEquals(30 to 60, bar.start to bar.end)
        assertTrue(reports.isEmpty())

        bar.start = 0
        bar.end = 100
        assertEquals(30 to 90, bar.start to bar.end)
        bar.start = 100
        assertEquals(60 to 90, bar.start to bar.end)
        assertEquals(listOf(30 to 90, 60 to 90), reports)
    }

    @Test
    fun startMinimum_xmlRejectsEmptyTickIntervalsAndTotalCannotWeakenExistingMinimum() {
        for (entries in listOf(
            arrayOf(R.attr.kitStep to "30", R.attr.kitStartMin to "91"),
            arrayOf(R.attr.kitStep to "30", R.attr.kitStartMin to "31", R.attr.kitStartMax to "59"),
            arrayOf(R.attr.kitStartMin to "20", R.attr.kitMinSpan to "81"),
        )) {
            assertThrows(IllegalArgumentException::class.java) { createBar(*entries) }
        }
        val bar = createBar(R.attr.kitStartMin to "70", R.attr.kitStart to "70", R.attr.kitEnd to "90")
        assertThrows(IllegalArgumentException::class.java) { bar.setTotal(60) }
        assertEquals(70, bar.getStartMin())
        assertEquals(70 to 90, bar.start to bar.end)
        bar.end = Int.MAX_VALUE
        assertEquals(70 to 100, bar.start to bar.end)
    }

    @Test
    fun constraints_projectBothEndpointsToNearestLegalRange() {
        val bar = createBar(R.attr.kitStep to "10", R.attr.kitStart to "40", R.attr.kitEnd to "50")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        bar.setMinSpan(25)
        assertEquals(30 to 60, bar.start to bar.end)
        bar.setStartMax(15)
        assertEquals(10 to 60, bar.start to bar.end)
        bar.setEndMin(75)
        assertEquals(10 to 80, bar.start to bar.end)
        bar.setMinSpan(90)
        assertEquals(0 to 90, bar.start to bar.end)
        assertEquals(listOf(30 to 60, 10 to 60, 10 to 80, 0 to 90), reports)
    }

    @Test
    fun invalidConstraints_leavePreviousSelectionAndLimitsUsable() {
        val bar = createBar(R.attr.kitStep to "30", R.attr.kitStart to "30", R.attr.kitEnd to "60")
        bar.setEndMin(60)
        bar.setMinSpan(30)

        assertThrows(IllegalArgumentException::class.java) { bar.setEndMin(95) }
        assertEquals(30 to 60, bar.start to bar.end)
        assertThrows(IllegalArgumentException::class.java) { bar.setMinSpan(91) }
        assertEquals(30 to 60, bar.start to bar.end)

        bar.end = 0
        assertEquals(30 to 60, bar.start to bar.end)
        bar.start = 90
        assertEquals(30 to 60, bar.start to bar.end)
        bar.end = 90
        bar.start = 90
        assertEquals(60 to 90, bar.start to bar.end)
    }

    @Test
    fun total_reductionProjectsToAvailableStepsIncludingSingleZeroTick() {
        val bar = createBar(R.attr.kitStep to "30", R.attr.kitStart to "60", R.attr.kitEnd to "90")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        bar.setTotal(80)
        assertEquals(60 to 60, bar.start to bar.end)
        bar.setTotal(0)
        assertEquals(0 to 0, bar.start to bar.end)
        assertEquals(listOf(60 to 60, 0 to 0), reports)
    }

    @Test
    fun total_reductionCannotSilentlyWeakenExistingEndMinimum() {
        val bar = createBar(R.attr.kitStep to "10", R.attr.kitStart to "20", R.attr.kitEnd to "80")
        bar.setEndMin(70)

        assertThrows(IllegalArgumentException::class.java) { bar.setTotal(60) }
        assertEquals(20 to 80, bar.start to bar.end)
        bar.end = Int.MAX_VALUE
        assertEquals(20 to 100, bar.start to bar.end)
        bar.end = 0
        assertEquals(20 to 70, bar.start to bar.end)
    }

    @Test
    fun total_reductionCannotSilentlyWeakenExistingMinimumSpan() {
        val bar = createBar(R.attr.kitStep to "10", R.attr.kitStart to "20", R.attr.kitEnd to "90")
        bar.setMinSpan(70)

        assertThrows(IllegalArgumentException::class.java) { bar.setTotal(60) }
        assertEquals(20 to 90, bar.start to bar.end)
        bar.end = Int.MAX_VALUE
        assertEquals(20 to 100, bar.start to bar.end)
        bar.start = Int.MAX_VALUE
        assertEquals(30 to 100, bar.start to bar.end)
    }

    @Test
    fun xml_normalizesInitialOrderAndHonorsZeroStartLimit() {
        val bar = createBar(
            R.attr.kitStart to "80",
            R.attr.kitEnd to "20",
            R.attr.kitStartMax to "0",
            R.attr.kitEndMin to "90",
        )
        assertEquals(0 to 90, bar.start to bar.end)
    }

    @Test
    fun xml_nonpositiveStepIsRejectedAtConstruction() {
        for (step in listOf("0", "-1")) {
            assertThrows(IllegalArgumentException::class.java) { createBar(R.attr.kitStep to step) }
        }
    }

    @Test
    fun xml_projectsOriginalValuesWithAllConstraintsAndNoExtraTerminalTick() {
        val bar = createBar(
            R.attr.kitStart to "80",
            R.attr.kitEnd to "20",
            R.attr.kitStep to "30",
            R.attr.kitStartMax to "0",
            R.attr.kitEndMin to "61",
            R.attr.kitMinSpan to "70",
        )
        assertEquals(0 to 90, bar.start to bar.end)

        val midpointBar = createBar(
            R.attr.kitStart to "11",
            R.attr.kitEnd to "11",
            R.attr.kitStep to "10",
            R.attr.kitMinSpan to "10",
        )
        assertEquals(10 to 20, midpointBar.start to midpointBar.end)
    }

    @Test
    fun xml_alignsDefaultEndpointAndRejectsConstraintsWithoutAvailableSteps() {
        val bar = createBar(R.attr.kitStep to "30")
        assertEquals(0 to 90, bar.start to bar.end)
        val zeroTickBar = createBar(R.attr.kitTotal to "10", R.attr.kitStep to "30")
        assertEquals(0 to 0, zeroTickBar.start to zeroTickBar.end)

        assertThrows(IllegalArgumentException::class.java) {
            createBar(R.attr.kitStep to "30", R.attr.kitEndMin to "91")
        }
        assertThrows(IllegalArgumentException::class.java) {
            createBar(R.attr.kitStep to "30", R.attr.kitMinSpan to "91")
        }
    }

    @Test
    fun listener_registrationCancellationAndRepeatedMovesDoNotReportUnchangedRange() {
        val bar = createBar()
        bar.setSelectedRange(20, 80)
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }
        assertTrue(reports.isEmpty())

        touch(bar, MotionEvent.ACTION_DOWN, 50)
        touch(bar, MotionEvent.ACTION_MOVE, 50)
        assertTrue(reports.isEmpty())
        touch(bar, MotionEvent.ACTION_MOVE, 70)
        touch(bar, MotionEvent.ACTION_MOVE, 70)
        touch(bar, MotionEvent.ACTION_CANCEL, 70)

        assertEquals(listOf(30 to 80), reports)
        assertEquals(30 to 80, bar.start to bar.end)
    }

    @Test
    fun listener_assigningReportedRangeAgainDoesNotRecursivelyNotify() {
        val bar = createBar(R.attr.kitStep to "10")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end ->
            reports.add(start to end)
            check(reports.size == 1) { "Repeated normalized assignment must not notify" }
            bar.setSelectedRange(end, start)
            bar.start = start + 1
            bar.end = end - 1
        }

        bar.setSelectedRange(24, 76)

        assertEquals(listOf(20 to 80), reports)
    }

    @Test
    fun listener_rejectedConstraintDoesNotNotifyOrAlterLaterAssignments() {
        val bar = createBar(R.attr.kitStep to "30", R.attr.kitStart to "30", R.attr.kitEnd to "60")
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }

        assertThrows(IllegalArgumentException::class.java) { bar.setEndMin(95) }
        assertThrows(IllegalArgumentException::class.java) { bar.setMinSpan(100) }
        assertTrue(reports.isEmpty())

        bar.end = 90
        assertEquals(listOf(30 to 90), reports)
    }

    @Test
    fun drag_totalChangesInCallbackRebaseBothPointersWithoutCrashOrStaleValues() {
        val bar = createBar()
        bar.setSelectedRange(20, 80)
        var moved = false
        bar.setOnRangeChangedListener { _, _ ->
            if (!moved) {
                moved = true
                bar.setTotal(50)
            }
        }
        touch(bar, MotionEvent.ACTION_DOWN, 170)
        touch(bar, MotionEvent.ACTION_MOVE, 190)
        assertEquals(20 to 50, bar.start to bar.end)

        touch(bar, MotionEvent.ACTION_MOVE, 150)
        assertEquals(20 to 40, bar.start to bar.end)
        touch(bar, MotionEvent.ACTION_UP, 150)
    }

    @Test
    fun drag_disablingInCallbackPreventsStaleTrackingFromNextGesture() {
        val bar = createBar()
        bar.setSelectedRange(20, 80)
        bar.setOnRangeChangedListener { _, _ -> bar.isEnabled = false }
        touch(bar, MotionEvent.ACTION_DOWN, 50)
        touch(bar, MotionEvent.ACTION_MOVE, 70)
        bar.setOnRangeChangedListener(null)
        bar.isEnabled = true

        assertFalse(touch(bar, MotionEvent.ACTION_MOVE, 150))
        touch(bar, MotionEvent.ACTION_DOWN, 170)
        touch(bar, MotionEvent.ACTION_MOVE, 150)
        assertEquals(30 to 70, bar.start to bar.end)
    }

    private fun createBar(vararg entries: Pair<Int, String>): KitRangeBar {
        val attrs = Robolectric.buildAttributeSet()
        entries.forEach { (id, value) -> attrs.addAttribute(id, value) }
        val bar = KitRangeBar(RuntimeEnvironment.getApplication(), attrs.build()).apply {
            setThumb(GradientDrawable().apply { setSize(20, 20) })
        }
        FrameLayout(bar.context).addView(bar)
        bar.measure(View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY))
        bar.layout(0, 0, 220, 20)
        return bar
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
