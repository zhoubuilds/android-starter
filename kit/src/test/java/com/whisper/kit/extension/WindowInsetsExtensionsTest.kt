package com.whisper.kit.extension

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * 通过真实 View Insets 分发验证 padding 基线、逐边消费及父子布局的避让协作.
 *
 * @author whisper
 * @since 2026/09/17
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34])
class WindowInsetsExtensionsTest {

    @Test
    fun applyWindowInsetsPadding_whenInsetsChange_preservesInitialPaddingWithoutAccumulating() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            setPadding(3, 5, 7, 11)
            applyWindowInsetsPadding()
        }
        val firstInsets: WindowInsetsCompat = systemBarInsets(9, 24, 13, 32)

        ViewCompat.dispatchApplyWindowInsets(view, firstInsets)
        assertPadding(view, Insets.of(12, 29, 20, 43))

        ViewCompat.dispatchApplyWindowInsets(view, firstInsets)
        assertPadding(view, Insets.of(12, 29, 20, 43))

        ViewCompat.dispatchApplyWindowInsets(view, systemBarInsets(4, 18, 6, 20))
        assertPadding(view, Insets.of(7, 23, 13, 31))

        ViewCompat.dispatchApplyWindowInsets(view, systemBarInsets(0, 0, 0, 0))
        assertPadding(view, Insets.of(3, 5, 7, 11))
    }

    @Test
    fun applyWindowInsetsPadding_whenInstalledAgain_reusesOriginalPaddingAndChangesSelectedEdges() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            setPadding(3, 5, 7, 11)
            applyWindowInsetsPadding()
        }
        val insets: WindowInsetsCompat = systemBarInsets(9, 24, 13, 32)
        ViewCompat.dispatchApplyWindowInsets(view, insets)

        view.applyWindowInsetsPadding(left = false, top = false, bottom = false)
        val remainingInsets: WindowInsetsCompat = ViewCompat.dispatchApplyWindowInsets(view, insets)

        assertPadding(view, Insets.of(3, 5, 20, 11))
        assertEquals(
            Insets.of(9, 24, 0, 32),
            remainingInsets.getInsets(WindowInsetsCompat.Type.systemBars()),
        )

        view.applyWindowInsetsPadding(left = false, top = false, bottom = false)
        ViewCompat.dispatchApplyWindowInsets(view, insets)
        assertPadding(view, Insets.of(3, 5, 20, 11))
    }

    @Test
    fun applyWindowInsetsPadding_whenParentHandlesTopAndSides_childrenReceiveOnlyBottom() {
        val parent = FrameLayout(RuntimeEnvironment.getApplication()).apply {
            setPadding(3, 5, 7, 11)
            applyWindowInsetsPadding(bottom = false)
        }
        val child = FrameLayout(parent.context)
        val grandchild = View(parent.context).apply {
            setPadding(1, 2, 3, 4)
            applyWindowInsetsPadding()
        }
        parent.addView(child)
        child.addView(grandchild)
        var childInsets: WindowInsetsCompat? = null
        ViewCompat.setOnApplyWindowInsetsListener(child) { _, insets ->
            childInsets = insets
            insets
        }

        val insets: WindowInsetsCompat = systemBarInsets(9, 24, 13, 32)
        assertFalse("Parent dispatch requires unconsumed framework insets", insets.isConsumed)
        ViewCompat.dispatchApplyWindowInsets(parent, insets)

        assertPadding(parent, Insets.of(12, 29, 20, 11))
        assertNotNull(childInsets)
        assertEquals(
            Insets.of(0, 0, 0, 32),
            requireNotNull(childInsets).getInsets(WindowInsetsCompat.Type.systemBars()),
        )
        assertPadding(grandchild, Insets.of(1, 2, 3, 36))
    }

    @Test
    fun applyWindowInsetsPadding_whenLayoutIsRtl_leftAndRightRemainPhysicalEdges() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.applicationInfo.flags =
            application.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = View(application).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPaddingRelative(7, 5, 3, 11)
        }
        assertEquals(View.LAYOUT_DIRECTION_RTL, view.layoutDirection)
        assertPadding(view, Insets.of(3, 5, 7, 11))
        view.applyWindowInsetsPadding(top = false, right = false, bottom = false)

        val remainingInsets: WindowInsetsCompat = ViewCompat.dispatchApplyWindowInsets(
            view,
            systemBarInsets(9, 24, 13, 32),
        )

        assertPadding(view, Insets.of(12, 5, 7, 11))
        assertEquals(7, view.paddingStart)
        assertEquals(12, view.paddingEnd)
        assertEquals(
            Insets.of(0, 24, 13, 32),
            remainingInsets.getInsets(WindowInsetsCompat.Type.systemBars()),
        )
    }

    @Test
    @Config(sdk = [34])
    fun applyWindowInsetsPadding_whenCutoutOverlapsSystemBars_usesMaximumOnEachEdge() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            setPadding(3, 5, 7, 11)
            applyWindowInsetsPadding()
        }
        val cutout = DisplayCutoutCompat(
            Insets.of(17, 40, 5, 20),
            Rect(0, 100, 17, 140),
            Rect(100, 0, 140, 40),
            Rect(295, 100, 300, 140),
            Rect(100, 580, 140, 600),
            Insets.NONE,
        )
        val insets: WindowInsetsCompat = WindowInsetsCompat.Builder(systemBarInsets(9, 24, 13, 32))
            .setDisplayCutout(cutout)
            .build()

        val remainingInsets: WindowInsetsCompat = ViewCompat.dispatchApplyWindowInsets(view, insets)

        assertPadding(view, Insets.of(20, 45, 20, 43))
        assertEquals(
            Insets.NONE,
            remainingInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            ),
        )
    }

    @Test
    @Config(sdk = [28, 29])
    fun applyWindowInsetsPadding_whenLegacyParentHandlesCutout_attachedChildDoesNotReadItAgain() {
        val originalInsets: WindowInsets = legacyCutoutWindowInsets()
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity: Activity = controller.get()
            val parent = object : FrameLayout(activity) {
                override fun getRootWindowInsets(): WindowInsets = originalInsets
            }.apply {
                setPadding(3, 5, 7, 11)
            }
            val child = object : View(activity) {
                override fun getRootWindowInsets(): WindowInsets = originalInsets
            }.apply {
                setPadding(1, 2, 3, 4)
            }
            parent.addView(child)
            activity.setContentView(parent)
            controller.visible()
            assertTrue(parent.isAttachedToWindow)
            assertTrue(child.isAttachedToWindow)
            parent.applyWindowInsetsPadding(bottom = false)
            child.applyWindowInsetsPadding()

            ViewCompat.dispatchApplyWindowInsets(
                parent,
                WindowInsetsCompat.toWindowInsetsCompat(originalInsets, parent),
            )

            assertPadding(parent, Insets.of(3, 45, 7, 11))
            assertPadding(child, Insets.of(1, 2, 3, 36))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [34])
    fun applyWindowInsetsPadding_whenImeAppearsAndHides_usesMaximumBottomAndRestoresNavigationInset() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            setPadding(3, 5, 7, 11)
            applyWindowInsetsPadding(
                typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
        }
        val bars: WindowInsetsCompat = systemBarInsets(0, 24, 0, 32)
        val visibleIme: WindowInsetsCompat = WindowInsetsCompat.Builder(bars)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 240))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()

        val remainingInsets: WindowInsetsCompat = ViewCompat.dispatchApplyWindowInsets(view, visibleIme)

        assertPadding(view, Insets.of(3, 29, 7, 251))
        assertEquals(Insets.NONE, remainingInsets.getInsets(WindowInsetsCompat.Type.ime()))
        assertEquals(Insets.NONE, remainingInsets.getInsets(WindowInsetsCompat.Type.navigationBars()))

        val hiddenIme: WindowInsetsCompat = WindowInsetsCompat.Builder(bars)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, hiddenIme)
        assertPadding(view, Insets.of(3, 29, 7, 43))
    }

    @Test
    @Config(sdk = [34])
    fun applyWindowInsetsPadding_whenImeIsNotRequested_preservesItsRemainingAreaForChildren() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            applyWindowInsetsPadding()
        }
        val insets: WindowInsetsCompat = WindowInsetsCompat.Builder(systemBarInsets(0, 24, 0, 32))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 240))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()

        val remainingInsets: WindowInsetsCompat = ViewCompat.dispatchApplyWindowInsets(view, insets)

        assertPadding(view, Insets.of(0, 24, 0, 32))
        assertEquals(
            Insets.of(0, 0, 0, 208),
            remainingInsets.getInsets(WindowInsetsCompat.Type.ime()),
        )
    }

    /**
     * 构造未消费的系统栏输入, 使测试经过平台真实的父子 View 分发.
     *
     * API 24 的 Compat Builder 从 CONSUMED 复制且不清除消费标记, 因此仅该版本使用 Robolectric
     * 构造平台 Insets; 否则即使尺寸非零, ViewGroup 也会直接跳过子级分发.
     */
    private fun systemBarInsets(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): WindowInsetsCompat {
        if (Build.VERSION.SDK_INT == 24) {
            val platformInsets: WindowInsets = ReflectionHelpers.callConstructor(
                WindowInsets::class.java,
                ClassParameter.from(Rect::class.java, Rect(left, top, right, bottom)),
            )
            return WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        }
        return WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, bottom))
            .setVisible(WindowInsetsCompat.Type.systemBars(), true)
            .build()
    }

    /**
     * 固定 SDK 28 的 fixture 用 Robolectric 构造平台收到的刘海数据, 不访问被测工具的内部状态.
     * API 28 尚无公开 WindowInsets Builder, Compat Builder 的 setDisplayCutout 也不生效;
     * API 29 分支直接使用平台公开 Builder, 两种输入随后都走真实 ViewCompat 分发.
     */
    private fun legacyCutoutWindowInsets(): WindowInsets {
        val safeInsets = Rect(0, 40, 0, 0)
        val boundingRects = listOf(Rect(100, 0, 140, 40))
        if (Build.VERSION.SDK_INT == 29) {
            @Suppress("DEPRECATION") // API 29 的根窗口使用 stableInsets 区分导航栏与 IME.
            val insets: WindowInsetsCompat = WindowInsetsCompat.Builder(systemBarInsets(0, 24, 0, 32))
                .setStableInsets(Insets.of(0, 24, 0, 32))
                .setDisplayCutout(DisplayCutoutCompat(safeInsets, boundingRects))
                .build()
            return requireNotNull(insets.toWindowInsets())
        }
        check(Build.VERSION.SDK_INT == 28) { "The legacy fixture requires API 28 or 29" }
        @Suppress("DEPRECATION") // 该夹具固定为 API 28, 当时尚无接收 Insets 的 DisplayCutout 构造器.
        val cutout = DisplayCutout(safeInsets, boundingRects)
        return ReflectionHelpers.callConstructor(
            WindowInsets::class.java,
            ClassParameter.from(Rect::class.java, Rect(0, 24, 0, 32)),
            ClassParameter.from(Rect::class.java, null),
            ClassParameter.from(Rect::class.java, Rect(0, 24, 0, 32)),
            ClassParameter.from(java.lang.Boolean.TYPE, false),
            ClassParameter.from(java.lang.Boolean.TYPE, false),
            ClassParameter.from(DisplayCutout::class.java, cutout),
        )
    }

    private fun assertPadding(view: View, expected: Insets) {
        assertEquals("Unexpected left padding", expected.left, view.paddingLeft)
        assertEquals("Unexpected top padding", expected.top, view.paddingTop)
        assertEquals("Unexpected right padding", expected.right, view.paddingRight)
        assertEquals("Unexpected bottom padding", expected.bottom, view.paddingBottom)
    }
}
