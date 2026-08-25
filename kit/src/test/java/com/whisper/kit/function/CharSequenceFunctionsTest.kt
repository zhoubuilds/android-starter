package com.whisper.kit.function

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 验证 CharSequence 样式和点击扩展.
 *
 * @author whisper
 * @since 2026/08/13
 */
@RunWith(RobolectricTestRunner::class)
class CharSequenceFunctionsTest {

    @Test
    fun absoluteSize_nonEmptyText_appliesPixelSizeToWholeText() {
        val result: Spanned = "标题".absoluteSize(36) as Spanned
        val span: AbsoluteSizeSpan = result.getSpans(
            0,
            result.length,
            AbsoluteSizeSpan::class.java,
        ).single()

        assertEquals(36, span.size)
        assertEquals(0, result.getSpanStart(span))
        assertEquals(result.length, result.getSpanEnd(span))
    }

    @Test
    fun relativeSize_nonEmptyText_appliesProportionToWholeText() {
        val result: Spanned = "单位".relativeSize(0.8f) as Spanned
        val span: RelativeSizeSpan = result.getSpans(
            0,
            result.length,
            RelativeSizeSpan::class.java,
        ).single()

        assertEquals(0.8f, span.sizeChange)
    }

    @Test
    fun color_nonEmptyText_appliesColorToWholeText() {
        val result: Spanned = "协议".color(Color.RED) as Spanned
        val spans: Array<ForegroundColorSpan> = result.getSpans(
            0,
            result.length,
            ForegroundColorSpan::class.java,
        )

        assertEquals("协议", result.toString())
        assertEquals(1, spans.size)
        assertEquals(Color.RED, spans.single().foregroundColor)
        assertEquals(0, result.getSpanStart(spans.single()))
        assertEquals(result.length, result.getSpanEnd(spans.single()))
    }

    @Test
    fun color_spannedText_preservesExistingSpans() {
        val source: Spanned = "协议".onClick { } as Spanned
        val result: Spanned = source.color(Color.RED) as Spanned

        assertEquals(
            1,
            result.getSpans(0, result.length, ClickableSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, ForegroundColorSpan::class.java).size,
        )
    }

    @Test
    fun textStyle_nonEmptyText_appliesTypefaceStyle() {
        val result: Spanned = "强调".textStyle(Typeface.BOLD_ITALIC) as Spanned
        val span: StyleSpan = result.getSpans(
            0,
            result.length,
            StyleSpan::class.java,
        ).single()

        assertEquals(Typeface.BOLD_ITALIC, span.style)
    }

    @Test
    fun typeface_nonEmptyText_appliesMetricAffectingSpan() {
        val result: Spanned = "金额".typeface(Typeface.MONOSPACE) as Spanned

        assertEquals(
            1,
            result.getSpans(0, result.length, MetricAffectingSpan::class.java).size,
        )
    }

    @Test
    fun underlineAndStrikethrough_chained_preserveBothSpans() {
        val result: Spanned = "状态".underline().strikethrough() as Spanned

        assertEquals(
            1,
            result.getSpans(0, result.length, UnderlineSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, StrikethroughSpan::class.java).size,
        )
    }

    @Test
    fun size_invalidValue_throwsIllegalArgumentException() {
        assertIllegalArgument { "标题".absoluteSize(0) }
        assertIllegalArgument { "单位".relativeSize(0f) }
        assertIllegalArgument { "单位".relativeSize(Float.POSITIVE_INFINITY) }
    }

    @Test
    fun onClick_clickableSpan_invokesActionWithoutChangingColor() {
        var clickedView: View? = null
        val result: Spanned = "协议".onClick { view: View ->
            clickedView = view
        } as Spanned
        val clickableSpan: ClickableSpan = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).single()
        val view = View(RuntimeEnvironment.getApplication())
        val drawState = TextPaint().apply {
            color = Color.GREEN
            isUnderlineText = true
        }

        clickableSpan.onClick(view)
        clickableSpan.updateDrawState(drawState)

        assertSame(view, clickedView)
        assertEquals(Color.GREEN, drawState.color)
        assertFalse(drawState.isUnderlineText)
    }

    @Test
    fun onClick_underlineEnabled_drawsUnderline() {
        val result: Spanned = "协议".onClick(underline = true) { } as Spanned
        val clickableSpan: ClickableSpan = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).single()
        val drawState = TextPaint().apply {
            isUnderlineText = false
        }

        clickableSpan.updateDrawState(drawState)

        assertTrue(drawState.isUnderlineText)
    }

    @Test
    fun extensions_emptyText_returnOriginalText() {
        val source: CharSequence = ""

        assertSame(source, source.absoluteSize(16))
        assertSame(source, source.relativeSize(1.2f))
        assertSame(source, source.color(Color.RED))
        assertSame(source, source.textStyle(Typeface.BOLD))
        assertSame(source, source.typeface(Typeface.DEFAULT))
        assertSame(source, source.underline())
        assertSame(source, source.strikethrough())
        assertSame(source, source.onClick { })
    }

    @Suppress("UNUSED_VARIABLE")
    private fun compileChainedExtensions() {
        val text: CharSequence = "协议"
            .absoluteSize(32)
            .relativeSize(1.1f)
            .color(Color.RED)
            .textStyle(Typeface.BOLD)
            .typeface(Typeface.DEFAULT)
            .underline()
            .strikethrough()
            .onClick { _: View -> }
    }

    private fun assertIllegalArgument(action: () -> Unit) {
        try {
            action()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
