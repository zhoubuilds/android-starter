package com.whisper.kit.extension

import android.os.Build
import android.view.View
import androidx.annotation.MainThread
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.whisper.kit.R

/**
 * 将实时窗口 Insets 添加到指定物理边的初始 padding, 并向子 View 分发扣除已避让区域后的 Insets.
 *
 * 默认避让系统栏与刘海, 不自动处理 IME; 输入页面可以在 [typeMask] 中加入 [WindowInsetsCompat.Type.ime].
 * 多个类型在同一边取最大值, 不把键盘与导航栏高度相加. 未选择的边保留初始 padding, 对应 Insets 继续向下分发.
 * 需要处理 IME 的 Activity 应启用 adjustResize, 页面另行提供必要的滚动能力以露出输入区域.
 *
 * 本方法占用目标 View 的 OnApplyWindowInsetsListener. 首次安装时保存 padding 基线, 重复安装复用同一基线,
 * 因此应在 XML 或程序设置好页面 padding 后调用, 安装后不要再由其它代码修改该 View 的 padding 或覆盖监听器.
 * 左右参数表示物理边, 初始 padding 按安装时的布局方向解析. 不改变窗口模式、系统栏外观或子 View 的布局策略.
 * API 28 不能按边重建 DisplayCutout, 因此同一刘海应由单层完整避让, 不拆分给多个嵌套 View.
 *
 * @param typeMask 需要避让的 WindowInsetsCompat.Type 位组合.
 * @param left 是否避让左侧 Insets.
 * @param top 是否避让顶部 Insets.
 * @param right 是否避让右侧 Insets.
 * @param bottom 是否避让底部 Insets; 由底部导航组件处理时可关闭.
 * @author whisper
 * @since 2026/09/17
 */
@MainThread
fun View.applyWindowInsetsPadding(
    typeMask: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
) {
    val initialPadding: Insets = getTag(R.id.kit_window_insets_initial_padding) as? Insets
        ?: Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom).also { padding ->
            setTag(R.id.kit_window_insets_initial_padding, padding)
        }
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val requestedInsets: Insets = insets.paddingInsets(typeMask)
        val appliedInsets: Insets = Insets.of(
            if (left) requestedInsets.left else 0,
            if (top) requestedInsets.top else 0,
            if (right) requestedInsets.right else 0,
            if (bottom) requestedInsets.bottom else 0,
        )
        view.setPadding(
            initialPadding.left + appliedInsets.left,
            initialPadding.top + appliedInsets.top,
            initialPadding.right + appliedInsets.right,
            initialPadding.bottom + appliedInsets.bottom,
        )
        insets.remainingAfterPadding(appliedInsets)
    }
    doOnAttach { view -> ViewCompat.requestApplyInsets(view) }
}

/**
 * 读取当前分发剩余的安全区, 避免子 View 再次处理父 View 已经避让的刘海.
 *
 * API 28-29 的 getInsets(displayCutout) 会优先回读根窗口, 因此这些版本直接读取当前 DisplayCutout,
 * 再与其它请求类型逐边取最大值.
 */
private fun WindowInsetsCompat.paddingInsets(typeMask: Int): Insets {
    val cutoutType: Int = WindowInsetsCompat.Type.displayCutout()
    if (Build.VERSION.SDK_INT !in 28..29 || typeMask and cutoutType == 0) {
        return getInsets(typeMask)
    }
    val cutout = displayCutout
    val cutoutInsets: Insets = if (cutout == null) {
        Insets.NONE
    } else {
        Insets.of(cutout.safeInsetLeft, cutout.safeInsetTop, cutout.safeInsetRight, cutout.safeInsetBottom)
    }
    return Insets.max(getInsets(typeMask and cutoutType.inv()), cutoutInsets)
}

/**
 * 按实际增加的 padding 扣减各类 Insets, 不扣减 View 原有的设计间距.
 *
 * 扣减表示子内容已经远离窗口边缘的距离, 因此也作用于未列入 typeMask 的类型, 例如留给子 View 处理的 IME.
 * API 28 的 inset 不扣除刘海, 仅在所有安全边均已避让时整体消费; 更高版本交给平台逐边扣减.
 */
private fun WindowInsetsCompat.remainingAfterPadding(appliedInsets: Insets): WindowInsetsCompat {
    val remaining: WindowInsetsCompat = inset(appliedInsets)
    val cutout = displayCutout
    if (Build.VERSION.SDK_INT == 28 && cutout != null &&
        appliedInsets.left >= cutout.safeInsetLeft && appliedInsets.top >= cutout.safeInsetTop &&
        appliedInsets.right >= cutout.safeInsetRight && appliedInsets.bottom >= cutout.safeInsetBottom
    ) {
        @Suppress("DEPRECATION") // API 28 没有按边修改 DisplayCutout 的替代 API.
        return remaining.consumeDisplayCutout()
    }
    return remaining
}
