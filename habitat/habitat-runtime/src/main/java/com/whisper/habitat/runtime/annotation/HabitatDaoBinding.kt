package com.whisper.habitat.runtime.annotation

/**
 * 为 Habitat Dao accessor 声明限定符.
 *
 * 该注解只能标记参与 [HabitatDatabase] 数据库继承链的抽象、无参、非空 Dao accessor. 同一个 Dao 类型由不同 RoomDatabase
 * 提供时, 每个最终 accessor 都必须声明非空白且互不重复的限定符. Room 不支持在同一个数据库中声明或继承多个返回同一 Dao
 * 类型的 accessor.
 *
 * qualifier 是当前 Dao 类型范围内区分大小写的精确 key, Habitat 不执行 trim 或其它规范化. 应使用稳定、非敏感的语义常量,
 * 不应使用数据库类名、用户输入或凭据. 子类 override accessor 时需要在最派生声明上重新标记该注解.
 *
 * @property value 当前 Dao 类型范围内的限定符.
 *
 * @aegis 保护注解目标, 保留策略和 Dao 限定绑定语义.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持同一 Dao 类型按限定符绑定到多个数据库.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确注解目标、跨数据库范围和 qualifier 精确匹配边界.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确继承链注解目标与最派生声明约束.
 *
 * @author whisper
 * @since 2026/08/31
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class HabitatDaoBinding(
    val value: String,
)
