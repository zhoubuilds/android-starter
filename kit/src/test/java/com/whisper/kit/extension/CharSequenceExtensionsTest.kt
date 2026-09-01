package com.whisper.kit.extension

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
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
import org.robolectric.annotation.Config

/**
 * 验证 CharSequence 样式和点击扩展.
 *
 * @author whisper
 * @since 2026/08/13
 */
@RunWith(RobolectricTestRunner::class)
class CharSequenceExtensionsTest {

    @Test
    fun absoluteSize_nonEmptyText_appliesPixelSizeToWholeText() {
        val result: Spanned = "标题".absoluteSize(36) as Spanned
        val span: MetricAffectingSpan = result.getSpans(
            0,
            result.length,
            MetricAffectingSpan::class.java,
        ).single()
        val paint: TextPaint = result.resolveSizePaint(initialSize = 20f)

        assertEquals(36f, paint.textSize)
        assertEquals(0, result.getSpanStart(span))
        assertEquals(result.length, result.getSpanEnd(span))
    }

    @Test
    fun relativeSize_nonEmptyText_appliesProportionToWholeText() {
        val result: Spanned = "单位".relativeSize(0.8f) as Spanned
        val paint: TextPaint = result.resolveSizePaint(initialSize = 20f)

        assertEquals(16f, paint.textSize)
    }

    @Test
    fun absoluteSizeAndRelativeSize_bothOrdersUseAbsoluteSizeAsBase() {
        val absoluteThenRelative: TextPaint = "标题"
            .absoluteSize(40)
            .relativeSize(1.2f)
            .resolveSizePaint(initialSize = 20f)
        val relativeThenAbsolute: TextPaint = "标题"
            .relativeSize(1.2f)
            .absoluteSize(40)
            .resolveSizePaint(initialSize = 20f)

        assertEquals(48f, absoluteThenRelative.textSize)
        assertEquals(48f, relativeThenAbsolute.textSize)
    }

    @Test
    fun size_repeatedSetting_usesLastValueForEachSizeType() {
        val result: Spanned = "标题"
            .absoluteSize(30)
            .absoluteSize(40)
            .relativeSize(0.8f)
            .relativeSize(1.2f) as Spanned
        val paint: TextPaint = result.resolveSizePaint(initialSize = 20f)

        assertEquals(1, result.getSpans(0, result.length, MetricAffectingSpan::class.java).size)
        assertEquals(48f, paint.textSize)
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
    fun color_repeatedSetting_keepsOnlyLastSpan() {
        val result: Spanned = "协议"
            .color(Color.RED)
            .color(Color.BLUE) as Spanned
        val spans: Array<ForegroundColorSpan> = result.getSpans(
            0,
            result.length,
            ForegroundColorSpan::class.java,
        )

        assertEquals(1, spans.size)
        assertEquals(Color.BLUE, spans.single().foregroundColor)
    }

    @Test
    fun textStyle_boldThenItalic_combinesStyles() {
        val paint: TextPaint = "强调"
            .textStyle(Typeface.BOLD)
            .textStyle(Typeface.ITALIC)
            .resolveFontPaint()

        assertEquals(Typeface.BOLD_ITALIC, paint.typeface?.style)
    }

    @Test
    fun textStyle_boldThenNormal_resetsStyle() {
        val paint: TextPaint = "强调"
            .textStyle(Typeface.BOLD)
            .textStyle(Typeface.NORMAL)
            .resolveFontPaint()

        assertEquals(Typeface.NORMAL, paint.typeface?.style)
        assertFalse(paint.isFakeBoldText)
        assertEquals(0f, paint.textSkewX)
    }

    @Test
    fun textStyle_normalThenBold_appliesBoldStyle() {
        val paint: TextPaint = "强调"
            .textStyle(Typeface.NORMAL)
            .textStyle(Typeface.BOLD)
            .resolveFontPaint()

        assertEquals(Typeface.BOLD, paint.typeface?.style)
    }

    @Test
    fun textStyle_invalidValue_throwsIllegalArgumentException() {
        assertIllegalArgument { "强调".textStyle(-1) }
        assertIllegalArgument { "强调".textStyle(Typeface.BOLD_ITALIC + 1) }
    }

    @Test
    @Config(sdk = [27])
    fun typefaceAndTextStyle_api27_bothOrdersProduceSameFont() {
        assertTypefaceAndStyleComposeInBothOrders()
    }

    @Test
    @Config(sdk = [28])
    fun typefaceAndTextStyle_api28_bothOrdersProduceSameFont() {
        assertTypefaceAndStyleComposeInBothOrders()
    }

    @Test
    fun typeface_repeatedSetting_usesLastTypefaceAndPreservesStyle() {
        val result: Spanned = "金额"
            .typeface(Typeface.SERIF)
            .textStyle(Typeface.BOLD)
            .typeface(Typeface.MONOSPACE) as Spanned
        val paint: TextPaint = result.resolveFontPaint()

        assertEquals(1, result.getSpans(0, result.length, MetricAffectingSpan::class.java).size)
        assertEquals(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), paint.typeface)
    }

    @Test
    fun underlineAndStrikethrough_repeatedSetting_keepsOneSpanOfEachType() {
        val result: Spanned = "状态"
            .underline()
            .underline()
            .strikethrough()
            .strikethrough() as Spanned

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
    fun underlineThenOnClick_withoutUnderline_lastClickSettingDisablesUnderline() {
        val paint: TextPaint = "协议"
            .underline()
            .onClick(underline = false) { }
            .resolveDrawPaint()

        assertFalse(paint.isUnderlineText)
    }

    @Test
    fun onClickWithoutUnderlineThenUnderline_lastUnderlineSettingEnablesUnderlineAndPreservesClick() {
        var clickCount = 0
        val result: Spanned = "协议"
            .onClick(underline = false) { clickCount++ }
            .underline() as Spanned
        val paint: TextPaint = result.resolveDrawPaint()
        val clickableSpan: ClickableSpan = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).single()

        clickableSpan.onClick(View(RuntimeEnvironment.getApplication()))

        assertTrue(paint.isUnderlineText)
        assertEquals(1, clickCount)
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
    fun onClick_repeatedSetting_usesLastActionAndUnderlineSetting() {
        var firstClickCount = 0
        var secondClickCount = 0
        val result: Spanned = "协议"
            .onClick(underline = true) { firstClickCount++ }
            .onClick(underline = false) { secondClickCount++ } as Spanned
        val clickableSpans: Array<ClickableSpan> = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        )
        val drawState = TextPaint().apply {
            isUnderlineText = true
        }

        clickableSpans.single().onClick(View(RuntimeEnvironment.getApplication()))
        clickableSpans.single().updateDrawState(drawState)

        assertEquals(0, firstClickCount)
        assertEquals(1, secondClickCount)
        assertFalse(drawState.isUnderlineText)
    }

    @Test
    fun onClick_sourceContainsPartialClickableSpan_replacesItWithWholeTextAction() {
        var partialClickCount = 0
        var wholeTextClickCount = 0
        val source = SpannableString("用户协议").apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        partialClickCount++
                    }
                },
                2,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val result: Spanned = source.onClick { wholeTextClickCount++ } as Spanned
        val clickableSpan: ClickableSpan = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).single()

        clickableSpan.onClick(View(RuntimeEnvironment.getApplication()))

        assertEquals(0, partialClickCount)
        assertEquals(1, wholeTextClickCount)
        assertEquals(0, result.getSpanStart(clickableSpan))
        assertEquals(result.length, result.getSpanEnd(clickableSpan))
    }

    @Test
    fun onClick_twoSeparateTextRegions_preservesBothActions() {
        var agreementClickCount = 0
        var privacyClickCount = 0
        val result: Spanned = SpannableStringBuilder()
            .append("查看")
            .append("用户协议".onClick { agreementClickCount++ })
            .append("和")
            .append("隐私政策".onClick { privacyClickCount++ })
        val clickableSpans: List<ClickableSpan> = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).sortedBy(result::getSpanStart)
        val view = View(RuntimeEnvironment.getApplication())

        assertEquals(2, clickableSpans.size)

        clickableSpans[0].onClick(view)
        clickableSpans[1].onClick(view)

        assertEquals(1, agreementClickCount)
        assertEquals(1, privacyClickCount)
        assertTrue(result.getSpanEnd(clickableSpans[0]) <= result.getSpanStart(clickableSpans[1]))
    }

    @Test
    fun extensions_fullChain_appliesFinalRenderingAndClickBehavior() {
        var clickedView: View? = null
        val result: Spanned = "协议"
            .absoluteSize(32)
            .relativeSize(1.25f)
            .color(Color.RED)
            .textStyle(Typeface.BOLD)
            .typeface(Typeface.MONOSPACE)
            .onClick(underline = false) { view: View -> clickedView = view }
            .underline()
            .strikethrough() as Spanned
        val paint: TextPaint = result.resolveDrawPaint(initialSize = 20f)
        val clickableSpan: ClickableSpan = result.getSpans(
            0,
            result.length,
            ClickableSpan::class.java,
        ).single()
        val characterStyles: Array<CharacterStyle> = result.getSpans(
            0,
            result.length,
            CharacterStyle::class.java,
        )
        val view = View(RuntimeEnvironment.getApplication())

        clickableSpan.onClick(view)

        assertEquals("协议", result.toString())
        assertEquals(40f, paint.textSize)
        assertEquals(Color.RED, paint.color)
        assertEquals(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), paint.typeface)
        assertTrue(paint.isUnderlineText)
        assertTrue(paint.isStrikeThruText)
        assertSame(view, clickedView)
        assertEquals(
            2,
            result.getSpans(0, result.length, MetricAffectingSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, ForegroundColorSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, ClickableSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, UnderlineSpan::class.java).size,
        )
        assertEquals(
            1,
            result.getSpans(0, result.length, StrikethroughSpan::class.java).size,
        )
        assertTrue(
            characterStyles.all { span: CharacterStyle ->
                result.getSpanStart(span) == 0 && result.getSpanEnd(span) == result.length
            }
        )
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

    private fun assertIllegalArgument(action: () -> Unit) {
        try {
            action()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun assertTypefaceAndStyleComposeInBothOrders() {
        val styleThenTypeface: TextPaint = "金额"
            .textStyle(Typeface.BOLD)
            .typeface(Typeface.MONOSPACE)
            .resolveFontPaint()
        val typefaceThenStyle: TextPaint = "金额"
            .typeface(Typeface.MONOSPACE)
            .textStyle(Typeface.BOLD)
            .resolveFontPaint()
        val expectedTypeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        assertEquals(expectedTypeface, styleThenTypeface.typeface)
        assertEquals(expectedTypeface, typefaceThenStyle.typeface)
    }

    private fun CharSequence.resolveFontPaint(): TextPaint {
        val paint = TextPaint().apply {
            typeface = Typeface.DEFAULT
        }
        val spanned: Spanned = this as Spanned
        spanned.getSpans(
            0,
            spanned.length,
            MetricAffectingSpan::class.java,
        ).forEach { span: MetricAffectingSpan ->
            span.updateMeasureState(paint)
        }
        return paint
    }

    private fun CharSequence.resolveDrawPaint(initialSize: Float = 16f): TextPaint {
        val paint = TextPaint().apply {
            textSize = initialSize
            typeface = Typeface.DEFAULT
            color = Color.BLACK
            isUnderlineText = false
            isStrikeThruText = false
        }
        val spanned: Spanned = this as Spanned
        spanned.getSpans(
            0,
            spanned.length,
            CharacterStyle::class.java,
        ).forEach { span: CharacterStyle ->
            span.updateDrawState(paint)
        }
        return paint
    }

    private fun CharSequence.resolveSizePaint(initialSize: Float): TextPaint {
        val paint = TextPaint().apply {
            textSize = initialSize
        }
        val spanned: Spanned = this as Spanned
        spanned.getSpans(
            0,
            spanned.length,
            MetricAffectingSpan::class.java,
        ).forEach { span: MetricAffectingSpan ->
            span.updateMeasureState(paint)
        }
        return paint
    }
}
