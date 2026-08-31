package com.whisper.habitat.runtime.annotation

/**
 * 为 Habitat Dao accessor 声明限定符.
 *
 * 同一个 Dao 类型由多个 RoomDatabase 提供时, 每个 accessor 都必须声明非空白且互不重复的限定符.
 *
 * @property value 当前 Dao 类型范围内的限定符.
 *
 * @aegis 保护注解目标, 保留策略和 Dao 限定绑定语义.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持同一 Dao 类型按限定符绑定到多个数据库.
 *
 * @author whisper
 * @since 2026/08/31
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class HabitatDaoBinding(
    val value: String,
)
