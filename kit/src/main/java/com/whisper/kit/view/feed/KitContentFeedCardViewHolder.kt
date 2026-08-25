package com.whisper.kit.view.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.whisper.kit.R
import com.whisper.kit.databinding.KitItemContentFeedCardBinding

/**
 * 通用内容瀑布流卡片 ViewHolder.
 *
 * 只处理卡片视觉绑定、封面高度和点击分发; 业务跳转、默认文案和图片加载由调用方提供.
 *
 * @author 张梁
 * @since 2026/08/17
 */
class KitContentFeedCardViewHolder private constructor(
    private val binding: KitItemContentFeedCardBinding,
    private val onItemClick: (KitContentFeedCardUi) -> Unit,
    private val columnWidthProvider: () -> Int,
    private val imageLoader: KitContentFeedImageLoader,
) : RecyclerView.ViewHolder(binding.root) {

    /**
     * 绑定卡片数据.
     *
     * @param item 卡片展示数据.
     */
    fun bind(item: KitContentFeedCardUi) {
        binding.kitContentFeedTitle.text = item.title
        binding.kitContentFeedStats.text = item.statsText ?: ""
        binding.kitContentFeedStats.visibility = if (item.statsText.isNullOrBlank()) View.GONE else View.VISIBLE
        bindBadges(item)
        val imageView: ImageView = binding.kitContentFeedImage
        imageView.setTag(R.id.kit_content_feed_cover_bind_token, item.id)
        val coverLayout: FeedCoverLayout? = resolveCoverLayout(item)
        if (coverLayout != null) {
            applyCoverHeight(binding.kitContentFeedMediaContainer, coverLayout.targetHeightPx)
            imageLoader.load(imageView, item.imageUrl, coverLayout.coverWidthPx, coverLayout.targetHeightPx)
        } else {
            applyCoverHeight(binding.kitContentFeedMediaContainer, defaultCoverHeightPx(item))
            imageLoader.load(imageView, item.imageUrl, decodeWidth = null, decodeHeight = null)
            if (item.fixedCoverHeightDp == null) {
                applyCoverLayoutDeferred(item)
            }
        }
        binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    private fun bindBadges(item: KitContentFeedCardUi) {
        binding.kitContentFeedPlay.visibility = if (item.showPlayIcon) View.VISIBLE else View.GONE
        val durationText: CharSequence? = item.durationText?.takeIf { text: CharSequence ->
            text.isNotBlank()
        }
        binding.kitContentFeedDuration.text = durationText ?: ""
        binding.kitContentFeedDuration.visibility = if (durationText == null) View.GONE else View.VISIBLE

        val coverBadgeText: CharSequence? = item.coverBadgeText?.takeIf { text: CharSequence ->
            text.isNotBlank()
        }
        binding.kitContentFeedCoverBadge.text = coverBadgeText ?: ""
        binding.kitContentFeedCoverBadge.visibility = if (coverBadgeText == null) View.GONE else View.VISIBLE

        val tagText: CharSequence? = item.tagText?.takeIf { text: CharSequence ->
            text.isNotBlank()
        }
        binding.kitContentFeedTag.text = tagText ?: ""
        binding.kitContentFeedTag.visibility = if (tagText == null) View.GONE else View.VISIBLE
    }

    private fun resolveCoverLayout(item: KitContentFeedCardUi): FeedCoverLayout? {
        val coverWidthPx: Int = resolveCoverWidthPx()
        if (coverWidthPx <= 0) {
            return null
        }
        val fixedCoverHeightDp: Int? = item.fixedCoverHeightDp
        val targetHeightPx: Int = if (fixedCoverHeightDp != null) {
            dpToPx(fixedCoverHeightDp)
        } else {
            KitContentFeedCoverHeight.resolveCoverHeightPx(
                coverWidthPx = coverWidthPx,
                coverWidth = item.coverWidth,
                coverHeight = item.coverHeight,
            )
        }
        if (targetHeightPx <= 0) {
            return null
        }
        return FeedCoverLayout(
            coverWidthPx = coverWidthPx,
            targetHeightPx = targetHeightPx,
        )
    }

    private fun resolveCoverWidthPx(): Int {
        val knownColumnWidth: Int = columnWidthProvider()
        if (knownColumnWidth > 0) {
            return knownColumnWidth
        }
        val mediaContainer: View = binding.kitContentFeedMediaContainer
        return listOf(
            mediaContainer.width,
            mediaContainer.measuredWidth,
            binding.root.width,
            binding.root.measuredWidth,
        ).firstOrNull { width: Int -> width > 0 } ?: 0
    }

    private fun applyCoverLayoutDeferred(item: KitContentFeedCardUi) {
        val imageView: ImageView = binding.kitContentFeedImage
        imageView.post {
            val expectedId: String? = imageView.getTag(R.id.kit_content_feed_cover_bind_token) as? String
            if (expectedId != item.id || !imageView.isAttachedToWindow) {
                return@post
            }
            val coverLayout: FeedCoverLayout = resolveCoverLayout(item) ?: return@post
            applyCoverHeight(binding.kitContentFeedMediaContainer, coverLayout.targetHeightPx)
            imageLoader.load(imageView, item.imageUrl, coverLayout.coverWidthPx, coverLayout.targetHeightPx)
        }
    }

    private fun defaultCoverHeightPx(item: KitContentFeedCardUi): Int {
        return item.fixedCoverHeightDp?.let(::dpToPx) ?: dpToPx(DEFAULT_COVER_HEIGHT_DP)
    }

    private fun dpToPx(valueDp: Int): Int {
        return (valueDp * binding.root.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun applyCoverHeight(
        coverView: View,
        targetHeight: Int,
    ) {
        if (targetHeight <= 0) {
            return
        }
        val layoutParams: ViewGroup.LayoutParams = coverView.layoutParams
        if (layoutParams.height == targetHeight) {
            return
        }
        layoutParams.height = targetHeight
        coverView.layoutParams = layoutParams
    }

    /**
     * 创建通用内容卡片 ViewHolder.
     */
    companion object {

        private const val DEFAULT_COVER_HEIGHT_DP: Int = 147

        /**
         * 创建通用内容卡片 ViewHolder.
         *
         * @param parent item 所属父容器.
         * @param onItemClick item 点击回调.
         * @param columnWidthProvider 卡片列宽提供器, 单位 px.
         * @param imageLoader 图片加载器.
         * @return 通用内容卡片 ViewHolder.
         */
        fun create(
            parent: ViewGroup,
            onItemClick: (KitContentFeedCardUi) -> Unit,
            columnWidthProvider: () -> Int,
            imageLoader: KitContentFeedImageLoader,
        ): KitContentFeedCardViewHolder {
            return KitContentFeedCardViewHolder(
                binding = KitItemContentFeedCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
                onItemClick = onItemClick,
                columnWidthProvider = columnWidthProvider,
                imageLoader = imageLoader,
            )
        }
    }
}

private data class FeedCoverLayout(
    val coverWidthPx: Int,
    val targetHeightPx: Int,
)
