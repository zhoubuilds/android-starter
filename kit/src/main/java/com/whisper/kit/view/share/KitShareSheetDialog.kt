package com.whisper.kit.view.share

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.isVisible
import com.whisper.kit.R
import com.whisper.kit.databinding.KitDialogShareSheetBinding
import com.whisper.kit.databinding.KitItemShareSheetActionBinding

/**
 * 通用分享底部面板.
 *
 * 只负责底部弹窗外壳、分享入口排列和点击分发, 分享渠道语义、图标、文案和实际分享动作由调用方提供.
 *
 * @author 张梁
 * @since 2026/08/17
 */
class KitShareSheetDialog private constructor(
    context: Context,
) : AppCompatDialog(context, R.style.KitWidget_Dialog_ShareSheet) {

    private val binding: KitDialogShareSheetBinding by lazy {
        KitDialogShareSheetBinding.inflate(layoutInflater)
    }

    private var titleText: CharSequence? = null
    private var cancelText: CharSequence? = null
    private var dismissOnActionClick: Boolean = true
    private var actions: List<ShareAction> = emptyList()
    private var onActionClickListener: OnShareActionClickListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        renderContent()
        setViewListeners()
        applyWindowAttributes()
    }

    private fun renderContent() {
        binding.kitShareSheetTitle.text = titleText
        binding.kitShareSheetTitle.isVisible = titleText != null
        binding.kitShareSheetCancel.text = cancelText ?: context.getString(android.R.string.cancel)
        binding.kitShareSheetActions.removeAllViews()
        actions.forEach { action: ShareAction ->
            binding.kitShareSheetActions.addView(createActionView(action))
        }
    }

    private fun createActionView(action: ShareAction): View {
        val itemBinding: KitItemShareSheetActionBinding =
            KitItemShareSheetActionBinding.inflate(layoutInflater, binding.kitShareSheetActions, false)
        itemBinding.kitShareSheetActionIcon.setImageResource(action.iconRes)
        itemBinding.kitShareSheetActionTitle.text = action.title
        itemBinding.root.isEnabled = action.enabled
        itemBinding.root.alpha = if (action.enabled) {
            1f
        } else {
            DISABLED_ACTION_ALPHA
        }
        itemBinding.root.setOnClickListener {
            if (!action.enabled) {
                return@setOnClickListener
            }
            onActionClickListener?.onShareActionClick(this, action)
            if (dismissOnActionClick) {
                dismiss()
            }
        }
        return itemBinding.root
    }

    private fun setViewListeners() {
        binding.kitShareSheetCancel.setOnClickListener {
            cancel()
        }
    }

    private fun applyWindowAttributes() {
        val targetWindow: Window = window ?: return
        targetWindow.setGravity(Gravity.BOTTOM)
        targetWindow.decorView.setPadding(0, 0, 0, 0)
        targetWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * 分享入口点击监听器.
     */
    fun interface OnShareActionClickListener {

        /**
         * 分享入口点击.
         */
        fun onShareActionClick(dialog: KitShareSheetDialog, action: ShareAction)
    }

    /**
     * 分享入口 UI 数据.
     *
     * @property id 调用方自定义入口标识.
     * @property title 入口展示文案.
     * @property iconRes 入口图标资源.
     * @property enabled 入口是否可点.
     * @property payload 调用方自定义数据.
     */
    data class ShareAction(
        val id: String,
        val title: CharSequence,
        @DrawableRes
        val iconRes: Int,
        val enabled: Boolean = true,
        val payload: Any? = null,
    )

    /**
     * 通用分享底部面板构建器.
     */
    class Builder(
        private val context: Context,
    ) {

        private var titleText: CharSequence? = null
        private var cancelText: CharSequence? = null
        private var dismissOnActionClick: Boolean = true
        private val actions: MutableList<ShareAction> = mutableListOf()
        private var onActionClickListener: OnShareActionClickListener? = null

        /**
         * 设置标题.
         */
        fun setTitle(title: CharSequence?): Builder = apply {
            titleText = title
        }

        /**
         * 设置取消按钮文案.
         */
        fun setCancelText(text: CharSequence?): Builder = apply {
            cancelText = text
        }

        /**
         * 设置点击分享入口后是否自动关闭面板.
         */
        fun setDismissOnActionClick(dismiss: Boolean): Builder = apply {
            dismissOnActionClick = dismiss
        }

        /**
         * 新增分享入口.
         */
        fun addAction(action: ShareAction): Builder = apply {
            actions += action
        }

        /**
         * 设置分享入口点击监听.
         */
        fun setOnActionClickListener(listener: OnShareActionClickListener?): Builder = apply {
            onActionClickListener = listener
        }

        /**
         * 构建分享面板.
         */
        fun build(): KitShareSheetDialog {
            return KitShareSheetDialog(context).also { dialog: KitShareSheetDialog ->
                dialog.titleText = titleText
                dialog.cancelText = cancelText
                dialog.dismissOnActionClick = dismissOnActionClick
                dialog.actions = actions.toList()
                dialog.onActionClickListener = onActionClickListener
            }
        }

        /**
         * 构建并展示分享面板.
         */
        fun show(): KitShareSheetDialog {
            return build().also { dialog: KitShareSheetDialog ->
                dialog.show()
            }
        }
    }

    private companion object {

        private const val DISABLED_ACTION_ALPHA: Float = 0.4f
    }
}
