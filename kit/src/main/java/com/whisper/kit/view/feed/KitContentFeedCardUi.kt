package com.whisper.kit.view.feed

/**
 * 通用内容瀑布流卡片展示数据.
 *
 * 该模型只描述卡片视觉状态, 不承载业务内容类型、跳转或默认文案规则.
 *
 * @author 张梁
 * @since 2026/08/17
 */
data class KitContentFeedCardUi(

    /**
     * 卡片稳定 ID.
     */
    val id: String,

    /**
     * 标题文案.
     */
    val title: CharSequence,

    /**
     * 封面图片数据源.
     */
    val imageUrl: Any?,

    /**
     * 封面原始宽度.
     */
    val coverWidth: Int?,

    /**
     * 封面原始高度.
     */
    val coverHeight: Int?,

    /**
     * 是否展示播放图标.
     */
    val showPlayIcon: Boolean,

    /**
     * 视频时长文案.
     */
    val durationText: CharSequence?,

    /**
     * 封面右下角标文案.
     */
    val coverBadgeText: CharSequence?,

    /**
     * 标题下方标签文案.
     */
    val tagText: CharSequence?,

    /**
     * 统计文案.
     */
    val statsText: CharSequence?,

    /**
     * 固定封面高度, 单位 dp; 为 null 时按封面宽高比计算.
     */
    val fixedCoverHeightDp: Int? = null,

    /**
     * 调用方原始业务数据.
     */
    val payload: Any? = null,
)
