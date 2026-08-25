package com.whisper.kit.view.feed

import android.widget.ImageView

/**
 * 通用内容卡片封面图片加载器.
 *
 * 由调用方接入具体图片库, 避免 kit 依赖应用图片加载约定.
 *
 * @author 张梁
 * @since 2026/08/17
 */
fun interface KitContentFeedImageLoader {

    /**
     * 加载封面图片.
     *
     * @param imageView 目标图片 View.
     * @param model 图片数据源.
     * @param decodeWidth 解码宽度, 单位 px; 为 null 时由图片库自行决定.
     * @param decodeHeight 解码高度, 单位 px; 为 null 时由图片库自行决定.
     */
    fun load(
        imageView: ImageView,
        model: Any?,
        decodeWidth: Int?,
        decodeHeight: Int?,
    )
}
