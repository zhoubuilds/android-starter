package com.whisper.architecture.model.ui.notice

/**
 * 表示 UI 通知的重要程度.
 *
 * 重要程度可用于区分 Toast, Snackbar 或 Dialog 等展示方式.
 *
 * @aegis 保护重要程度枚举成员和等级语义.
 * @author whisper
 * @since 2026/07/24
 */
enum class NoticeImportance {
    LOW,
    MEDIUM,
    HIGH,
}
