package com.whisper.architecture.ui.message

/**
 * 表示需要由 UI 展示的消息.
 *
 * @property message 消息内容.
 * @property importance 消息重要程度, 用于决定 Toast 或 Dialog 等展示方式.
 * @property tone 消息语义色调.
 *
 * @aegis 保护消息字段, 类型和构造契约.
 * @author whisper
 * @since 2026/07/24
 */
data class UiMessage(
    val message: CharSequence,
    val importance: UiMessageImportance,
    val tone: UiMessageTone,
)
