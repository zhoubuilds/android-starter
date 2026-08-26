package com.whisper.architecture.model.ui.notice

/**
 * 表示需要由 UI 展示的通知.
 *
 * @property content 通知内容.
 * @property importance 通知重要程度, 用于决定 Toast 或 Dialog 等展示方式.
 * @property tone 通知语义色调.
 *
 * @aegis 保护通知字段, 类型和构造契约.
 * @author whisper
 * @since 2026/07/24
 */
data class NoticeUi(
    val content: CharSequence,
    val importance: NoticeImportance,
    val tone: NoticeTone,
)
