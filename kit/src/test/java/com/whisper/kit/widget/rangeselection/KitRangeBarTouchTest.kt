package com.whisper.kit.widget.rangeselection

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.KeyEvent
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
 * 验证范围选择的双指跟踪, 重合仲裁, 离散刻度和中断后的手势清理.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitRangeBarTouchTest {

    @Test
    fun drag_nonzeroPointerIdMovesSelectedThumb() {
        val bar = createBar(start = 20, end = 80)

        assertTrue(bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50)))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 90))
        bar.touch(MotionEvent.ACTION_UP, pointer(7, 90))

        assertRange(bar, 40, 80)
    }

    @Test
    fun drag_unchangedRangeWritebackPreservesSubstepMovement() {
        val writebacks: List<(KitRangeBar, Int, Int) -> Unit> = listOf(
            { _, _, _ -> },
            { bar, start, end -> bar.setSelectedRange(start, end) },
            { bar, start, end -> bar.setSelectedRange(end - 1, start + 1) },
            { bar, start, _ -> bar.start = start + 1 },
            { bar, _, end -> bar.end = end - 1 },
        )
        for (writeback in writebacks) {
            val bar = createBar(start = 20, end = 80, step = 10)
            val reports = mutableListOf<Pair<Int, Int>>()
            bar.setOnRangeChangedListener { start, end ->
                reports.add(start to end)
                writeback(bar, start, end)
            }
            bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50))
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 61))
            assertRange(bar, 30, 80)

            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 80))
            bar.touch(MotionEvent.ACTION_UP, pointer(7, 80))

            assertRange(bar, 30, 80)
            assertEquals(listOf(30 to 80), reports)
        }
    }

    @Test
    fun drag_dualPointerWritebackPreservesBothSubstepMovementsWithoutExtraTerminalTick() {
        val bar = createBar(start = 0, end = 90, step = 30, total = 100)
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end ->
            reports.add(start to end)
            bar.setSelectedRange(start, end)
            bar.start = start
            bar.end = end
        }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 10))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 10), pointer(17, 190), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 41), pointer(17, 159))
        assertRange(bar, 30, 60)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 127), pointer(5, 73))
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 127), pointer(5, 73), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 127))

        assertRange(bar, 30, 60)
        assertEquals(listOf(30 to 60), reports)
    }

    @Test
    fun interaction_releaseCommitsFinalPositionBeforeReportingEnd() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 70))

        bar.touch(MotionEvent.ACTION_UP, pointer(7, 90))

        assertRange(bar, 40, 80)
        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 30, 80),
                RangeEvent("change", 40, 80),
                RangeEvent("end", 40, 80),
            ),
            events,
        )
    }

    @Test
    fun interaction_releaseWithoutMoveResolvesOverlappingEndpointFromFinalDirection() {
        val bar = createBar(start = 50, end = 50, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 110))

        bar.touch(MotionEvent.ACTION_UP, pointer(7, 70))

        assertRange(bar, 30, 50)
        assertEquals(
            listOf(RangeEvent("start", 50, 50), RangeEvent("change", 30, 50), RangeEvent("end", 30, 50)),
            events,
        )
    }

    @Test
    fun interaction_pointerReleaseCommitsBothFinalPositionsInOneNotification() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 210), pointer(5, 90), actionIndex = 1)

        assertRange(bar, 40, 100)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("change", 40, 100)),
            events,
        )
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 190))
        assertRange(bar, 40, 90)
        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 30, 90),
                RangeEvent("change", 40, 100),
                RangeEvent("change", 40, 90),
                RangeEvent("end", 40, 90),
            ),
            events,
        )
    }

    @Test
    fun interaction_cancelledPointerReleasePreservesItsLastCommittedPosition() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(17, 190),
            pointer(5, 130),
            actionIndex = 1,
            flags = MotionEvent.FLAG_CANCELED,
        )

        assertRange(bar, 30, 90)
        assertEquals(listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90)), events)
        assertEquals(true, (bar.parent as RecordingParent).interceptionRequests.last())
        assertTrue(bar.touch(MotionEvent.ACTION_UP, pointer(17, 190)))
        assertEquals(RangeEvent("end", 30, 90), events.last())
        assertEquals(3, events.size)
    }

    @Test
    fun interaction_cancelledPointerReleaseCommitsRemainingPointerAndKeepsSessionActive() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(5, 130),
            pointer(17, 210),
            actionIndex = 0,
            flags = MotionEvent.FLAG_CANCELED,
        )

        assertRange(bar, 30, 100)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("change", 30, 100)),
            events,
        )
        assertTrue(bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170)))
        assertRange(bar, 30, 80)
        assertTrue(bar.touch(MotionEvent.ACTION_UP, pointer(17, 150)))
        assertRange(bar, 30, 70)
        assertEquals(
            listOf(RangeEvent("change", 30, 80), RangeEvent("change", 30, 70), RangeEvent("end", 30, 70)),
            events.takeLast(3),
        )
    }

    @Test
    fun interaction_cancelledUnresolvedPointerDoesNotChooseEndpointFromRejectedCoordinates() {
        val bar = createBar(start = 50, end = 50, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 110), pointer(17, 110), actionIndex = 1)

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(5, 50),
            pointer(17, 110),
            actionIndex = 0,
            flags = MotionEvent.FLAG_CANCELED,
        )

        assertRange(bar, 50, 50)
        assertEquals(listOf(RangeEvent("start", 50, 50)), events)
        assertTrue(bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170)))
        assertRange(bar, 50, 80)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 170))
        assertEquals(
            listOf(RangeEvent("start", 50, 50), RangeEvent("change", 50, 80), RangeEvent("end", 50, 80)),
            events,
        )
    }

    @Test
    fun drag_cancelledPointerReplacementDoesNotReplayWaitingPointersEarlierMovement() {
        val bar = createBar(start = 50, end = 50, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 110), pointer(17, 110), actionIndex = 1)
        bar.touch(
            MotionEvent.ACTION_POINTER_DOWN,
            pointer(5, 110),
            pointer(17, 110),
            pointer(23, 110),
            actionIndex = 2,
        )
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 50), pointer(17, 170), pointer(23, 200))
        assertRange(bar, 20, 80)

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(23, 210),
            pointer(5, 90),
            pointer(17, 170),
            actionIndex = 1,
            flags = MotionEvent.FLAG_CANCELED,
        )

        assertRange(bar, 20, 80)
        assertEquals(listOf(RangeEvent("start", 50, 50), RangeEvent("change", 20, 80)), events)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170), pointer(23, 210))
        assertRange(bar, 20, 80)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170), pointer(23, 190))
        assertRange(bar, 10, 80)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 170), pointer(23, 190), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 170))
        assertEquals(listOf(RangeEvent("change", 10, 80), RangeEvent("end", 10, 80)), events.takeLast(2))
    }

    @Test
    fun interaction_wholeCancelAfterPartialCancelReportsOneCancelWithPreservedValues() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(5, 130),
            pointer(17, 190),
            actionIndex = 0,
            flags = MotionEvent.FLAG_CANCELED,
        )

        bar.touch(MotionEvent.ACTION_CANCEL, pointer(17, 210))

        assertRange(bar, 30, 90)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("cancel", 30, 90)),
            events,
        )
        assertFalse(bar.touch(MotionEvent.ACTION_UP, pointer(17, 210)))
        assertEquals(false, (bar.parent as RecordingParent).interceptionRequests.last())
    }

    @Test
    fun interaction_partialCancelValueCallbackDoesNotReleaseReplacementGesturePointer() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        var replacementStarted = false
        bar.setOnRangeChangedListener { start, end ->
            events.add(RangeEvent("change", start, end))
            if (!replacementStarted) {
                replacementStarted = true
                bar.isEnabled = false
                bar.isEnabled = true
                assertTrue(bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50)))
            }
        }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(5, 130),
            pointer(17, 190),
            actionIndex = 0,
            flags = MotionEvent.FLAG_CANCELED,
        )

        assertRange(bar, 20, 90)
        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 20, 90),
                RangeEvent("cancel", 20, 90),
                RangeEvent("start", 20, 90),
            ),
            events,
        )
        assertEquals(true, (bar.parent as RecordingParent).interceptionRequests.last())
        assertTrue(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70)))
        assertTrue(bar.touch(MotionEvent.ACTION_UP, pointer(5, 70)))
        assertRange(bar, 30, 90)
        assertEquals(listOf(RangeEvent("change", 30, 90), RangeEvent("end", 30, 90)), events.takeLast(2))
    }

    @Test
    @Config(sdk = [24])
    fun interaction_regularPointerReleaseOnMinimumSdkCommitsBothFinalPositions() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 90), pointer(17, 190), actionIndex = 0)

        assertRange(bar, 40, 90)
        assertEquals(listOf(RangeEvent("start", 20, 80), RangeEvent("change", 40, 90)), events)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 190))
        assertEquals(RangeEvent("end", 40, 90), events.last())
    }

    @Test
    fun drag_twoPointersMoveTheirOwnThumbsAfterPointerOrderChanges() {
        val bar = createBar(start = 20, end = 80)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 190), pointer(5, 70))

        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 190), pointer(5, 70), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 150))
        assertRange(bar, 30, 70)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 150))
    }

    @Test
    fun drag_twoPointersMovingRightUseSpaceReleasedInTheSameEvent() {
        val bar = createBar(start = 20, end = 60)
        bar.setMinSpan(20)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 130), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 130), pointer(17, 210))

        assertRange(bar, 60, 100)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 130), pointer(17, 210), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 210))
    }

    @Test
    fun drag_replacingReleasedPointerKeepsThumbIdentityAndDoesNotJump() {
        val bar = createBar(start = 20, end = 80)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 70), pointer(17, 190), actionIndex = 0)

        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(17, 190), pointer(23, 75), actionIndex = 1)
        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 190), pointer(23, 75))
        assertRange(bar, 30, 90)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(23, 95), pointer(17, 190))
        assertRange(bar, 40, 90)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(23, 95), pointer(17, 190), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 190))
    }

    @Test
    fun drag_overlapMovingLeftWithoutHistorySelectsStartAndKeepsItsIdentity() {
        val bar = createBar(start = 50, end = 50)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(9, 110))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(9, 50))
        assertRange(bar, 20, 50)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(9, 190))
        assertRange(bar, 50, 50)
        bar.touch(MotionEvent.ACTION_UP, pointer(9, 190))
    }

    @Test
    fun drag_overlapMovingRightWithoutHistorySelectsEndAndKeepsItsIdentity() {
        val bar = createBar(start = 50, end = 50)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(9, 110))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(9, 170))
        assertRange(bar, 50, 80)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(9, 30))
        assertRange(bar, 50, 50)
        bar.touch(MotionEvent.ACTION_UP, pointer(9, 30))
    }

    @Test
    fun drag_twoPointersCanSeparateOverlappingThumbsWithoutHistory() {
        val bar = createBar(start = 50, end = 50)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 110), pointer(17, 110), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 50), pointer(17, 170))

        assertRange(bar, 20, 80)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 50), pointer(17, 170), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 170))
    }

    @Test
    fun drag_thirdPendingPointerTakesOverWithoutReplayingEarlierMovement() {
        val bar = createBar(start = 50, end = 50)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 110), pointer(17, 110), actionIndex = 1)
        bar.touch(
            MotionEvent.ACTION_POINTER_DOWN,
            pointer(5, 110),
            pointer(17, 110),
            pointer(23, 110),
            actionIndex = 2,
        )
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 50), pointer(17, 170), pointer(23, 200))
        assertRange(bar, 20, 80)

        bar.touch(
            MotionEvent.ACTION_POINTER_UP,
            pointer(5, 50),
            pointer(17, 170),
            pointer(23, 200),
            actionIndex = 0,
        )
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170), pointer(23, 200))
        assertRange(bar, 20, 80)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 170), pointer(23, 180))
        assertRange(bar, 10, 80)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 170), pointer(23, 180), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 170))
    }

    @Test
    fun drag_endpointsCanMeetButCannotCross() {
        val bar = createBar(start = 20, end = 60)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(3, 50))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(3, 210))
        assertRange(bar, 60, 60)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(3, 10))
        assertRange(bar, 0, 60)
        bar.touch(MotionEvent.ACTION_UP, pointer(3, 10))

        bar.touch(MotionEvent.ACTION_DOWN, pointer(11, 130))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(11, 10))
        assertRange(bar, 0, 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(11, 10))
    }

    @Test
    fun drag_disablingDuringGestureClearsBothTrackedPointers() {
        val bar = createBar(start = 20, end = 80)
        val parent = bar.parent as RecordingParent
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.isEnabled = false
        assertEquals(false, parent.interceptionRequests.last())
        assertFalse(bar.touch(MotionEvent.ACTION_CANCEL, pointer(5, 70), pointer(17, 190)))
        bar.isEnabled = true

        assertFalse(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 10), pointer(17, 210)))
        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(17, 70))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 50))
        assertRange(bar, 20, 90)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 50))
    }

    @Test
    fun drag_cancelClearsTrackingAndReleasesParentInterception() {
        val bar = createBar(start = 20, end = 80)
        val parent = bar.parent as RecordingParent
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.touch(MotionEvent.ACTION_CANCEL, pointer(5, 70), pointer(17, 190))

        assertEquals(false, parent.interceptionRequests.last())
        assertFalse(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 10), pointer(17, 210)))
        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(17, 190))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 150))
        assertRange(bar, 30, 70)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 150))
    }

    @Test
    fun touch_unmatchedRemainingPointerStillFinishesAcceptedGesture() {
        val bar = createBar(start = 20, end = 80)
        val parent = bar.parent as RecordingParent
        val releasedRanges = mutableListOf<Pair<Int, Int>>()
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionEnd(start: Int, end: Int) {
                releasedRanges.add(start to end)
            }
        })
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 110), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 110))

        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 70), pointer(17, 110), actionIndex = 0)

        assertEquals(false, parent.interceptionRequests.last())
        assertTrue(releasedRanges.isEmpty())
        assertTrue(bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 130)))
        assertTrue(bar.touch(MotionEvent.ACTION_UP, pointer(17, 130)))
        assertEquals(listOf(30 to 80), releasedRanges)
        assertRange(bar, 30, 80)
    }

    @Test
    fun touch_newMatchingPointerReacquiresParentInterceptionWithinExistingGesture() {
        val bar = createBar(start = 20, end = 80)
        val parent = bar.parent as RecordingParent
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 110), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 50), pointer(17, 110), actionIndex = 0)
        assertEquals(false, parent.interceptionRequests.last())

        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(17, 110), pointer(23, 50), actionIndex = 1)

        assertEquals(true, parent.interceptionRequests.last())
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 110), pointer(23, 90))
        assertRange(bar, 40, 80)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 110), pointer(23, 90), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 110))
    }

    @Test
    fun touch_blankTrackDoesNotConsumeGestureOrPreventParentScrolling() {
        val bar = createBar(start = 20, end = 80)
        val parent = bar.parent as RecordingParent

        assertFalse(bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 110)))
        assertFalse(bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 50)))
        assertFalse(parent.interceptionRequests.contains(true))
        assertRange(bar, 20, 80)
    }

    @Test
    fun touch_disabledThumbsDoNotPreventParentScrolling() {
        val bar = createBar(start = 20, end = 80, thumbsEnabled = false)
        val parent = bar.parent as RecordingParent

        assertFalse(bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 50)))
        assertFalse(parent.interceptionRequests.contains(true))
        assertRange(bar, 20, 80)
    }

    @Test
    fun drag_nondivisibleTotalNeverCreatesExtraTerminalStep() {
        val bar = createBar(start = 0, end = 60, step = 30, total = 100)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 130))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 210))
        assertRange(bar, 0, 90)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 400))
        assertRange(bar, 0, 90)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 400))
    }

    @Test
    fun drag_startLimitBetweenStepsUsesLastAllowedStep() {
        val bar = createBar(start = 0, end = 90, step = 30)
        bar.setStartMax(50)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 10))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 210))

        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 210))
    }

    @Test
    fun drag_startMinimumConstrainsBothOverlappingPointersInLtrAndRtl() = withRtlSupport {
        for (rtl in listOf(false, true)) {
            val bar = createBar(start = 60, end = 60, step = 30)
            bar.setStartMin(20)
            if (rtl) {
                setDirection(bar, View.LAYOUT_DIRECTION_RTL)
            }
            fun x(ltrPosition: Int): Int = if (rtl) 220 - ltrPosition else ltrPosition

            assertTrue(bar.touch(MotionEvent.ACTION_DOWN, pointer(5, x(130))))
            bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, x(130)), pointer(17, x(130)), actionIndex = 1)
            bar.touch(MotionEvent.ACTION_MOVE, pointer(5, x(10)), pointer(17, x(190)))
            assertRange(bar, 30, 90)

            bar.touch(MotionEvent.ACTION_MOVE, pointer(5, x(10)), pointer(17, x(10)))
            assertRange(bar, 30, 30)
            bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, x(10)), pointer(17, x(10)), actionIndex = 0)
            bar.touch(MotionEvent.ACTION_UP, pointer(17, x(10)))
            assertRange(bar, 30, 30)
        }
    }

    @Test
    fun drag_startMinimumProjectionRebasesActivePointerAtNewPosition() {
        val bar = createBar(start = 20, end = 80, step = 10)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))
        assertRange(bar, 30, 80)

        bar.setStartMin(45)
        assertRange(bar, 50, 80)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))
        assertRange(bar, 50, 80)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90))
        assertRange(bar, 60, 80)
        bar.touch(MotionEvent.ACTION_UP, pointer(5, 90))
    }

    @Test
    fun drag_endLimitBetweenStepsUsesFirstAllowedStep() {
        val bar = createBar(start = 0, end = 90, step = 30)
        bar.setEndMin(40)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 190))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 10))

        assertRange(bar, 0, 60)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 10))
    }

    @Test
    fun drag_minimumSpanBetweenStepsDoesNotCreateNonStepValues() {
        val bar = createBar(start = 0, end = 90, step = 30)
        bar.setMinSpan(35)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 10))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 190))

        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 190))
    }

    @Test
    fun drag_adjustedMinimumSpanImmediatelyMovesThumbsToTheirNewPositions() {
        val bar = createBar(start = 20, end = 60)
        bar.setMinSpan(70)
        assertRange(bar, 5, 75)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 20))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 10))
        assertRange(bar, 0, 75)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 10))

        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 160))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 190))
        assertRange(bar, 0, 90)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 190))
    }

    @Test
    fun drag_rejectedConstraintPreservesExistingRangeAndPointerAnchor() {
        val bar = createBar(start = 0, end = 100, step = 30)
        assertRange(bar, 0, 90)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 190))

        assertThrows(IllegalArgumentException::class.java) { bar.setEndMin(95) }
        assertRange(bar, 0, 90)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 130))

        assertRange(bar, 0, 60)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 130))
    }

    @Test
    fun drag_constraintUpdateRebasesBothPointersWithoutReplayingEarlierMovement() {
        val bar = createBar(start = 20, end = 80, step = 10)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        assertRange(bar, 30, 90)

        bar.setMinSpan(80)
        assertRange(bar, 20, 100)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        assertRange(bar, 20, 100)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 50), pointer(17, 170))
        assertRange(bar, 10, 90)

        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 50), pointer(17, 170), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 170))
    }

    @Test
    fun drag_repeatedTotalAndConstraintsPreserveSubstepMovement() {
        val configure: List<(KitRangeBar) -> Unit> = listOf(
            { bar -> bar.setTotal(100) },
            { bar -> bar.setStartMin(10) },
            { bar -> bar.setStartMax(70) },
            { bar -> bar.setEndMin(40) },
            { bar -> bar.setMinSpan(20) },
        )
        for (applyConfiguration in configure) {
            val bar = createBar(start = 20, end = 80, step = 10)
            applyConfiguration(bar)
            val reports = mutableListOf<Pair<Int, Int>>()
            bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }
            bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50))
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 61))
            assertRange(bar, 30, 80)

            applyConfiguration(bar)
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 80))
            bar.touch(MotionEvent.ACTION_UP, pointer(7, 80))

            assertRange(bar, 30, 80)
            assertEquals(listOf(30 to 80), reports)
        }
    }

    @Test
    fun drag_changedConstraintRebasesPointerEvenWhenRangeIsUnchanged() {
        val configure: List<(KitRangeBar) -> Unit> = listOf(
            { bar -> bar.setStartMin(10) },
            { bar -> bar.setStartMax(70) },
            { bar -> bar.setEndMin(40) },
            { bar -> bar.setMinSpan(20) },
        )
        for (applyConfiguration in configure) {
            val bar = createBar(start = 20, end = 80, step = 10)
            val reports = mutableListOf<Pair<Int, Int>>()
            bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }
            bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50))
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 61))
            assertRange(bar, 30, 80)

            applyConfiguration(bar)
            assertRange(bar, 30, 80)
            assertEquals(listOf(30 to 80), reports)
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 61))
            assertRange(bar, 30, 80)
            bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 80))
            bar.touch(MotionEvent.ACTION_UP, pointer(7, 80))

            assertRange(bar, 40, 80)
            assertEquals(listOf(30 to 80, 40 to 80), reports)
        }
    }

    @Test
    fun drag_changedTotalRebasesBothPointersEvenWhenRangeIsUnchanged() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val reports = mutableListOf<Pair<Int, Int>>()
        bar.setOnRangeChangedListener { start, end -> reports.add(start to end) }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        assertRange(bar, 30, 90)

        bar.setTotal(200)
        assertRange(bar, 30, 90)
        assertEquals(listOf(30 to 90), reports)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))
        assertRange(bar, 30, 90)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 80), pointer(17, 180))
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 80), pointer(17, 180), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 180))

        assertRange(bar, 40, 80)
        assertEquals(listOf(30 to 90, 40 to 80), reports)
    }

    @Test
    fun drag_equalDistanceChoosesLowerStepConsistentlyWithProgramAssignments() {
        val bar = createBar(start = 0, end = 80, step = 10)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 170))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 160))
        assertRange(bar, 0, 70)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 161))
        assertRange(bar, 0, 80)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 161))

        bar.end = 75
        assertRange(bar, 0, 70)
    }

    @Test
    fun drag_zeroStartMaximumPreventsStartMovement() {
        val bar = createBar(start = 0, end = 80)
        bar.setStartMax(0)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 10))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 150))

        assertRange(bar, 0, 80)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 150))
    }

    @Test
    fun drag_largeIntegerDomainPreservesValueAtExactTrackMidpoint() {
        val total = 1_000_000_100
        val bar = createBar(start = 0, end = total, total = total)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 210))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 110))

        assertRange(bar, 0, 500_000_050)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 110))
    }

    @Test
    fun drag_maximumIntegerDomainKeepsExactStepAtMidpointAndUpperBoundary() {
        val bar = createBar(start = 0, end = Int.MAX_VALUE, total = Int.MAX_VALUE, step = 7)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 210))

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 110))
        assertRange(bar, 0, 1_073_741_823)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 210))
        assertRange(bar, 0, 2_147_483_646)
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 210))
    }

    @Test
    fun drag_zeroLengthTrackDoesNotCrashOrChangeRange() {
        val bar = createBar(start = 20, end = 80, width = 20)

        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 10))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 200))
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 200))

        assertRange(bar, 20, 80)
    }

    @Test
    fun drag_thumbWiderThanViewDoesNotCrashOrChangeRange() {
        val bar = createBar(start = 20, end = 80, width = 10)

        bar.touch(MotionEvent.ACTION_DOWN, pointer(0, 5))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(0, 200))
        bar.touch(MotionEvent.ACTION_UP, pointer(0, 200))

        assertRange(bar, 20, 80)
    }

    @Test
    fun interaction_twoPointersReportOneAtomicChangeAndFinishOnlyAfterFinalPointer() {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)

        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 190), pointer(5, 70))
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 190), pointer(5, 70), actionIndex = 1)

        assertEquals(listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90)), events)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 190))
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 190))
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("end", 30, 90)),
            events,
        )
    }

    @Test
    fun interaction_unchangedTapHasLifecycleWithoutValueNotification() {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)

        assertFalse(bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 110)))
        assertTrue(events.isEmpty())
        bar.touch(MotionEvent.ACTION_DOWN, pointer(7, 50))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(7, 50))
        bar.touch(MotionEvent.ACTION_UP, pointer(7, 50))

        assertEquals(listOf(RangeEvent("start", 20, 80), RangeEvent("end", 20, 80)), events)
    }

    @Test
    fun interaction_cancelPreservesCurrentRangeAndNotifiesOnce() {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        bar.touch(MotionEvent.ACTION_CANCEL, pointer(5, 90), pointer(17, 210))
        assertFalse(bar.touch(MotionEvent.ACTION_CANCEL, pointer(5, 90), pointer(17, 210)))
        assertFalse(bar.touch(MotionEvent.ACTION_UP, pointer(5, 70)))

        assertRange(bar, 30, 90)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("cancel", 30, 90)),
            events,
        )
    }

    @Test
    fun interaction_disabledAndHiddenGesturesCancelWithoutExtraChanges() {
        for (interrupt in listOf<(KitRangeBar) -> Unit>(
            { it.isEnabled = false },
            { it.visibility = View.INVISIBLE },
            { it.onVisibilityAggregated(false) },
        )) {
            val bar = createBar(start = 20, end = 80)
            val events = recordEvents(bar)
            bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
            bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))

            interrupt(bar)
            interrupt(bar)
            assertRange(bar, 30, 80)
            assertEquals(
                listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 80), RangeEvent("cancel", 30, 80)),
                events,
            )
        }
    }

    @Test
    fun interaction_detachingViewCancelsActiveGestureOnce() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val bar = createBar(start = 20, end = 80)
            (bar.parent as FrameLayout).removeView(bar)
            val parent = FrameLayout(controller.get()).apply { addView(bar) }
            controller.get().setContentView(parent)
            bar.measure(exactly(220), exactly(20))
            bar.layout(0, 0, 220, 20)
            assertTrue(bar.isAttachedToWindow)
            val events = recordEvents(bar)
            bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
            bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))

            parent.removeView(bar)

            assertRange(bar, 30, 80)
            assertEquals(
                listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 80), RangeEvent("cancel", 30, 80)),
                events,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun interaction_disablingFromStartCallbackDoesNotRecapturePointers() {
        val bar = createBar(start = 20, end = 80)
        val events = mutableListOf<RangeEvent>()
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                events.add(RangeEvent("start", start, end))
                bar.isEnabled = false
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                events.add(RangeEvent("cancel", start, end))
            }
        })

        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.isEnabled = true
        assertFalse(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90)))
        assertFalse(bar.touch(MotionEvent.ACTION_UP, pointer(5, 90)))

        assertRange(bar, 20, 80)
        assertEquals(listOf(RangeEvent("start", 20, 80), RangeEvent("cancel", 20, 80)), events)
        assertEquals(false, (bar.parent as RecordingParent).interceptionRequests.last())
    }

    @Test
    fun interaction_disablingFromKeyboardCancellationRejectsReplacementTouch() {
        val bar = createBar(start = 20, end = 80, step = 10)
        bar.isFocusableInTouchMode = true
        assertTrue(bar.requestFocus())
        val parent = bar.parent as RecordingParent
        val events = recordEvents(bar)
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                events.add(RangeEvent("start", start, end))
            }

            override fun onInteractionEnd(start: Int, end: Int) {
                events.add(RangeEvent("end", start, end))
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                events.add(RangeEvent("cancel", start, end))
                bar.isEnabled = false
            }
        })
        assertTrue(bar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertRange(bar, 30, 80)

        assertFalse(bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 70)))

        assertFalse(bar.isEnabled)
        assertFalse(parent.interceptionRequests.contains(true))
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 80), RangeEvent("cancel", 30, 80)),
            events,
        )
        bar.isEnabled = true
        assertFalse(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90)))
        assertFalse(bar.touch(MotionEvent.ACTION_UP, pointer(5, 90)))
        assertRange(bar, 30, 80)
        assertEquals(3, events.size)

        assertTrue(bar.touch(MotionEvent.ACTION_DOWN, pointer(17, 70)))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 90))
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 90))

        assertRange(bar, 40, 80)
        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 30, 80),
                RangeEvent("cancel", 30, 80),
                RangeEvent("start", 30, 80),
                RangeEvent("change", 40, 80),
                RangeEvent("end", 40, 80),
            ),
            events,
        )
        assertEquals(false, parent.interceptionRequests.last())
    }

    @Test
    fun interaction_disablingFromAtomicValueCallbackCancelsFinalTwoPointerRange() {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)
        bar.setOnRangeChangedListener { start, end ->
            events.add(RangeEvent("change", start, end))
            bar.isEnabled = false
        }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 50), pointer(17, 170), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70), pointer(17, 190))

        assertRange(bar, 30, 90)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 30, 90), RangeEvent("cancel", 30, 90)),
            events,
        )
    }

    @Test
    fun interaction_disablingFromReleaseValueCallbackCancelsWithoutReportingEnd() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        bar.setOnRangeChangedListener { start, end ->
            events.add(RangeEvent("change", start, end))
            bar.isEnabled = false
        }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))

        bar.touch(MotionEvent.ACTION_UP, pointer(5, 90))

        assertRange(bar, 40, 80)
        assertEquals(
            listOf(RangeEvent("start", 20, 80), RangeEvent("change", 40, 80), RangeEvent("cancel", 40, 80)),
            events,
        )
        assertEquals(false, (bar.parent as RecordingParent).interceptionRequests.last())
    }

    @Test
    fun interaction_releaseCallbackStartingNewGestureDoesNotFinishReplacement() {
        val bar = createBar(start = 20, end = 80, step = 10)
        val events = recordEvents(bar)
        var replacementStarted = false
        bar.setOnRangeChangedListener { start, end ->
            events.add(RangeEvent("change", start, end))
            if (!replacementStarted) {
                replacementStarted = true
                bar.isEnabled = false
                bar.isEnabled = true
                assertTrue(bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 90)))
            }
        }
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))

        bar.touch(MotionEvent.ACTION_UP, pointer(5, 90))

        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 40, 80),
                RangeEvent("cancel", 40, 80),
                RangeEvent("start", 40, 80),
            ),
            events,
        )
        assertEquals(true, (bar.parent as RecordingParent).interceptionRequests.last())
        assertTrue(bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 110)))
        assertTrue(bar.touch(MotionEvent.ACTION_UP, pointer(5, 110)))
        assertRange(bar, 50, 80)
        assertEquals(
            listOf(RangeEvent("change", 50, 80), RangeEvent("end", 50, 80)),
            events.takeLast(2),
        )
    }

    @Test
    fun interaction_programAssignmentsDoNotCreateLifecycleCallbacks() {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)

        bar.setSelectedRange(30, 70)
        bar.setMinSpan(80)

        assertEquals(listOf(RangeEvent("change", 30, 70), RangeEvent("change", 10, 90)), events)
    }

    @Test
    fun rtl_overlappingThumbUsesMirroredDirectionWithoutExchangingIdentity() = withRtlSupport {
        val startBar = createBar(start = 50, end = 50)
        setDirection(startBar, View.LAYOUT_DIRECTION_RTL)
        assertTrue(startBar.isRtlMirroringEnabled)
        startBar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        startBar.touch(MotionEvent.ACTION_MOVE, pointer(5, 170))
        assertRange(startBar, 20, 50)
        startBar.touch(MotionEvent.ACTION_MOVE, pointer(5, 30))
        assertRange(startBar, 50, 50)
        startBar.touch(MotionEvent.ACTION_UP, pointer(5, 30))

        val endBar = createBar(start = 50, end = 50)
        setDirection(endBar, View.LAYOUT_DIRECTION_RTL)
        endBar.touch(MotionEvent.ACTION_DOWN, pointer(17, 110))
        endBar.touch(MotionEvent.ACTION_MOVE, pointer(17, 50))
        assertRange(endBar, 50, 80)
        endBar.touch(MotionEvent.ACTION_MOVE, pointer(17, 190))
        assertRange(endBar, 50, 50)
        endBar.touch(MotionEvent.ACTION_UP, pointer(17, 190))
    }

    @Test
    fun rtl_twoPointersSeparateOverlappingThumbsUsingNumericDirections() = withRtlSupport {
        val bar = createBar(start = 50, end = 50)
        setDirection(bar, View.LAYOUT_DIRECTION_RTL)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 110))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 110), pointer(17, 110), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(17, 50), pointer(5, 170))

        assertRange(bar, 20, 80)
        assertEquals(listOf(RangeEvent("start", 50, 50), RangeEvent("change", 20, 80)), events)
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(17, 50), pointer(5, 170), actionIndex = 1)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 50))
    }

    @Test
    fun rtl_twoPointersMovingTogetherUseSpaceReleasedInSameEvent() = withRtlSupport {
        val bar = createBar(start = 20, end = 60)
        bar.setMinSpan(20)
        setDirection(bar, View.LAYOUT_DIRECTION_RTL)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 170))
        bar.touch(MotionEvent.ACTION_POINTER_DOWN, pointer(5, 170), pointer(17, 90), actionIndex = 1)

        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90), pointer(17, 10))
        assertRange(bar, 60, 100)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 210), pointer(17, 130))
        assertRange(bar, 0, 40)
        assertEquals(
            listOf(RangeEvent("start", 20, 60), RangeEvent("change", 60, 100), RangeEvent("change", 0, 40)),
            events,
        )
        bar.touch(MotionEvent.ACTION_POINTER_UP, pointer(5, 210), pointer(17, 130), actionIndex = 0)
        bar.touch(MotionEvent.ACTION_UP, pointer(17, 130))
    }

    @Test
    fun rtl_runtimeDirectionAndMirroringChangesRebaseActivePointer() = withRtlSupport {
        val bar = createBar(start = 20, end = 80)
        val events = recordEvents(bar)
        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))
        assertRange(bar, 30, 80)

        setDirection(bar, View.LAYOUT_DIRECTION_RTL)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 70))
        assertRange(bar, 30, 80)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90))
        assertRange(bar, 20, 80)

        bar.isRtlMirroringEnabled = false
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90))
        assertRange(bar, 20, 80)
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 110))
        assertRange(bar, 30, 80)
        bar.touch(MotionEvent.ACTION_UP, pointer(5, 110))
        assertEquals(
            listOf(
                RangeEvent("start", 20, 80),
                RangeEvent("change", 30, 80),
                RangeEvent("change", 20, 80),
                RangeEvent("change", 30, 80),
                RangeEvent("end", 30, 80),
            ),
            events,
        )
    }

    @Test
    fun rtl_xmlCanKeepNumericDirectionFixedLeftToRight() = withRtlSupport {
        val bar = createBar(start = 20, end = 80, rtlMirroringEnabled = false)
        setDirection(bar, View.LAYOUT_DIRECTION_RTL)
        assertFalse(bar.isRtlMirroringEnabled)

        bar.touch(MotionEvent.ACTION_DOWN, pointer(5, 50))
        bar.touch(MotionEvent.ACTION_MOVE, pointer(5, 90))
        bar.touch(MotionEvent.ACTION_UP, pointer(5, 90))

        assertRange(bar, 40, 80)
    }

    private fun recordEvents(bar: KitRangeBar): MutableList<RangeEvent> {
        val events = mutableListOf<RangeEvent>()
        bar.setOnRangeChangedListener { start, end ->
            assertEquals(start to end, bar.start to bar.end)
            events.add(RangeEvent("change", start, end))
        }
        bar.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                events.add(RangeEvent("start", start, end))
            }

            override fun onInteractionEnd(start: Int, end: Int) {
                events.add(RangeEvent("end", start, end))
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                events.add(RangeEvent("cancel", start, end))
            }
        })
        return events
    }

    private fun setDirection(bar: KitRangeBar, direction: Int) {
        bar.layoutDirection = direction
        bar.measure(exactly(220), exactly(20))
        bar.layout(0, 0, 220, 20)
        assertEquals(direction, bar.layoutDirection)
    }

    private inline fun withRtlSupport(block: () -> Unit) {
        val applicationInfo = RuntimeEnvironment.getApplication().applicationInfo
        val originalFlags = applicationInfo.flags
        applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        try {
            block()
        } finally {
            applicationInfo.flags = originalFlags
        }
    }

    private fun createBar(
        start: Int,
        end: Int,
        step: Int = 1,
        total: Int = 100,
        width: Int = 220,
        thumbsEnabled: Boolean = true,
        rtlMirroringEnabled: Boolean = true,
    ): KitRangeBar {
        val context: Context = RuntimeEnvironment.getApplication()
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.kitStart, start.toString())
            .addAttribute(R.attr.kitEnd, end.toString())
            .addAttribute(R.attr.kitStep, step.toString())
            .addAttribute(R.attr.kitTotal, total.toString())
            .addAttribute(R.attr.kitThumbWidth, "20px")
            .addAttribute(R.attr.kitThumbHeight, "20px")
            .addAttribute(R.attr.kitStartThumbEnable, thumbsEnabled.toString())
            .addAttribute(R.attr.kitEndThumbEnable, thumbsEnabled.toString())
            .addAttribute(R.attr.kitRtlMirroringEnabled, rtlMirroringEnabled.toString())
            .build()
        return KitRangeBar(context, attributes).apply {
            setThumb(GradientDrawable().apply {
                setColor(Color.BLACK)
                setSize(20, 20)
            })
            RecordingParent(context).addView(this)
            measure(exactly(width), exactly(20))
            layout(0, 0, width, 20)
        }
    }

    private fun KitRangeBar.touch(
        actionMasked: Int,
        vararg pointers: TestPointer,
        actionIndex: Int = 0,
        flags: Int = 0,
    ): Boolean {
        val properties = Array(pointers.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(pointers.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = pointers[index].x.toFloat()
                y = 10f
                pressure = 1f
                size = 1f
            }
        }
        val action = actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            0L,
            16L,
            action,
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            flags,
        )
        return try {
            assertEquals(0, event.historySize)
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun assertRange(bar: KitRangeBar, start: Int, end: Int) {
        assertEquals("Unexpected start value", start, bar.start)
        assertEquals("Unexpected end value", end, bar.end)
    }

    private fun pointer(id: Int, x: Int): TestPointer = TestPointer(id, x)

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    /** 保存子控件对父容器触摸拦截的请求. */
    private class RecordingParent(context: Context) : FrameLayout(context) {
        val interceptionRequests: MutableList<Boolean> = mutableListOf()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            interceptionRequests.add(disallowIntercept)
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    /** 一次原始触摸事件中的指针标识和水平坐标. */
    private data class TestPointer(val id: Int, val x: Int)

    /** 记录值变化与交互通知的有序范围快照. */
    private data class RangeEvent(val kind: String, val start: Int, val end: Int)
}
