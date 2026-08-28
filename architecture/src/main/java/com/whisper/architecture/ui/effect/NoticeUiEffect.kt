package com.whisper.architecture.ui.effect

import com.whisper.architecture.model.ui.notice.NoticeUiModel
import kotlinx.coroutines.flow.SharedFlow

/**
 * 提供页面一次性通知 Effect.
 *
 * `NoticeUiModel` 只描述通知的 UI 渲染信息, 该契约负责表达一次性消费语义.
 *
 * @aegis 保护通知通过 `SharedFlow` 暴露的一次性 Effect 契约.
 * @aegis-audit 2026-08-26 | whisper | 将页面通知从混合容器拆分为独立 SharedFlow Effect.
 *
 * @author whisper
 * @since 2026/08/26
 */
interface NoticeUiEffect {

    /**
     * 页面一次性通知 Effect 流.
     */
    val noticeUiEffectFlow: SharedFlow<NoticeUiModel>

}
