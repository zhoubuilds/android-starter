package com.whisper.kit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 使用小数值域穷举验证最近范围投影, 并覆盖整数极值和不可行约束.
 *
 * @author whisper
 * @since 2026/09/09
 */
class KitRangeSelectionTest {

    @Test
    fun nearestRange_matchesExhaustiveOracleForSmallDomains() {
        for (total in 1..5) {
            for (step in 1..total + 1) {
                for (startMin in -1..total) {
                    for (startMax in -1..total) {
                        for (endMin in -1..total) {
                            for (minSpan in 0..total + 1) {
                                val validRanges = buildList {
                                    for (start in 0..total step step) {
                                        for (end in start..total step step) {
                                            if ((startMin == -1 || start >= startMin) &&
                                                (startMax == -1 || start <= startMax) &&
                                                (endMin == -1 || end >= endMin) && end - start >= minSpan
                                            ) {
                                                add(KitRangeSelection(start, end))
                                            }
                                        }
                                    }
                                }
                                if (validRanges.isEmpty()) {
                                    assertThrows(IllegalArgumentException::class.java) {
                                        nearestKitRangeSelection(
                                            0, total, total, step, startMax, endMin, minSpan, startMin,
                                        )
                                    }
                                    continue
                                }
                                for (start in -1..total + 1) {
                                    for (end in -1..total + 1) {
                                        val expected = validRanges.minBy { range ->
                                            val startDistance = range.start.toLong() - start
                                            val endDistance = range.end.toLong() - end
                                            startDistance * startDistance + endDistance * endDistance
                                        }
                                        val actual = nearestKitRangeSelection(
                                            start, end, total, step, startMax, endMin, minSpan, startMin,
                                        )
                                        assertEquals(
                                            "Unexpected range for start=$start, end=$end, total=$total, " +
                                                "step=$step, startMin=$startMin, startMax=$startMax, " +
                                                "endMin=$endMin, minSpan=$minSpan",
                                            expected,
                                            actual,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun nearestRange_usesRawValuesBeforeJointProjection() {
        assertRange(10, 20, start = 11, end = 11, step = 10, minSpan = 10)
        assertRange(30, 60, start = 44, end = 46, step = 10, minSpan = 30)
    }

    @Test
    fun nearestRange_breaksEqualDistancesByStartThenEnd() {
        assertRange(0, 10, start = 10, end = 10, step = 10, minSpan = 10)
        assertRange(0, 0, start = 5, end = 5, step = 10)
        assertRange(0, 0, start = 0, end = 5, step = 10)
    }

    @Test
    fun nearestRange_usesOnlyAvailableTicksAndRoundsConstraintsInward() {
        assertRange(30, 90, start = 25, end = 100, step = 30)
        assertRange(0, 90, start = 25, end = 95, step = 30, minSpan = 70)
        assertRange(30, 90, start = 50, end = 50, step = 30, startMax = 50, endMin = 61)
        assertRange(0, 0, start = 25, end = 95, step = 101)
    }

    @Test
    fun nearestRange_startMinimumRoundsUpWithoutMovingStepOriginOrAddingTerminalTick() {
        assertRange(30, 30, start = 0, end = 0, step = 30, startMin = 20)
        assertRange(60, 90, start = 0, end = 0, step = 30, startMin = 31, minSpan = 25)
        assertRange(30, 90, start = 100, end = 100, step = 30, startMin = 21, startMax = 59, endMin = 61)
        assertRange(20, 20, start = 0, end = 0, startMin = 20)
    }

    @Test
    fun nearestRange_startMinimumHandlesFullIntegerDomainAndInfeasibleExtremeConstraints() {
        assertRange(
            Int.MAX_VALUE, Int.MAX_VALUE, start = Int.MIN_VALUE, end = Int.MIN_VALUE,
            total = Int.MAX_VALUE, startMin = Int.MAX_VALUE,
        )
        assertRange(
            Int.MAX_VALUE - 1, Int.MAX_VALUE - 1, start = Int.MIN_VALUE, end = Int.MAX_VALUE,
            total = Int.MAX_VALUE, step = 2, startMin = Int.MAX_VALUE - 2,
        )
        assertThrows(IllegalArgumentException::class.java) {
            nearest(total = Int.MAX_VALUE, step = 2, startMin = Int.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            nearest(total = Int.MAX_VALUE, startMin = 1, minSpan = Int.MAX_VALUE)
        }
    }

    @Test
    fun nearestRange_handlesFullSignedInputRangeWithoutSquaredOverflow() {
        assertRange(0, Int.MAX_VALUE, start = Int.MIN_VALUE, end = Int.MAX_VALUE, total = Int.MAX_VALUE)
        assertRange(0, 0, start = Int.MAX_VALUE, end = Int.MIN_VALUE, total = Int.MAX_VALUE)
        assertRange(
            0, Int.MAX_VALUE, start = Int.MAX_VALUE, end = Int.MAX_VALUE,
            total = Int.MAX_VALUE, minSpan = Int.MAX_VALUE,
        )
        assertRange(
            0, Int.MAX_VALUE - 1, start = Int.MIN_VALUE, end = Int.MIN_VALUE,
            total = Int.MAX_VALUE, minSpan = Int.MAX_VALUE - 1,
        )
    }

    @Test
    fun nearestRange_handlesLargestStepAndNondivisibleUpperBound() {
        assertRange(
            0, 0, start = Int.MAX_VALUE / 2, end = Int.MAX_VALUE / 2,
            total = Int.MAX_VALUE, step = Int.MAX_VALUE,
        )
        assertRange(
            Int.MAX_VALUE, Int.MAX_VALUE, start = Int.MAX_VALUE / 2 + 1, end = Int.MAX_VALUE / 2 + 1,
            total = Int.MAX_VALUE, step = Int.MAX_VALUE,
        )
        assertRange(
            0, Int.MAX_VALUE, start = 0, end = 0, total = Int.MAX_VALUE, step = Int.MAX_VALUE,
            startMax = 0, endMin = 1,
        )
        assertRange(
            Int.MAX_VALUE - 1, Int.MAX_VALUE - 1, start = Int.MAX_VALUE, end = Int.MAX_VALUE,
            total = Int.MAX_VALUE, step = 2,
        )
    }

    @Test
    fun nearestRange_rejectsConstraintsWithNoLegalSelection() {
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 30, endMin = 91) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 30, minSpan = 91) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 101, endMin = 1) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 101, minSpan = 1) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 30, startMin = 91) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 30, startMin = 31, startMax = 59) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 30, startMin = 30, minSpan = 61) }
        assertThrows(IllegalArgumentException::class.java) {
            nearest(total = Int.MAX_VALUE, step = 2, minSpan = Int.MAX_VALUE)
        }
    }

    @Test
    fun nearestRange_rejectsInvalidConfigurationValues() {
        assertThrows(IllegalArgumentException::class.java) { nearest(total = 0) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = 0) }
        assertThrows(IllegalArgumentException::class.java) { nearest(step = -1) }
        assertThrows(IllegalArgumentException::class.java) { nearest(startMax = -2) }
        assertThrows(IllegalArgumentException::class.java) { nearest(startMin = -2) }
        assertThrows(IllegalArgumentException::class.java) { nearest(endMin = -2) }
        assertThrows(IllegalArgumentException::class.java) { nearest(minSpan = -1) }
    }

    private fun assertRange(
        expectedStart: Int,
        expectedEnd: Int,
        start: Int,
        end: Int,
        total: Int = 100,
        step: Int = 1,
        startMax: Int = -1,
        endMin: Int = -1,
        minSpan: Int = 0,
        startMin: Int = -1,
    ) {
        assertEquals(
            KitRangeSelection(expectedStart, expectedEnd),
            nearestKitRangeSelection(start, end, total, step, startMax, endMin, minSpan, startMin),
        )
    }

    private fun nearest(
        total: Int = 100,
        step: Int = 1,
        startMax: Int = -1,
        endMin: Int = -1,
        minSpan: Int = 0,
        startMin: Int = -1,
    ): KitRangeSelection = nearestKitRangeSelection(0, total, total, step, startMax, endMin, minSpan, startMin)
}
