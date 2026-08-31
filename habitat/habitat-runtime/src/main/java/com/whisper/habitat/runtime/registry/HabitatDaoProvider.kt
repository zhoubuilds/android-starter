package com.whisper.habitat.runtime.registry

import kotlin.reflect.KClass

/**
 * Habitat Dao 提供者.
 *
 * 每个参与注册的 RoomDatabase 对应一个 Dao Provider.
 *
 * @aegis 保护生成 Provider 与 Runtime 之间的 Dao 工厂映射 ABI.
 * @aegis-audit 2026-08-31 | whisper | 经授权将 Dao 工厂 ABI 扩展为按可空限定符索引的延迟工厂表.
 *
 * @author whisper
 * @since 2026/07/27
 */
interface HabitatDaoProvider {

    /**
     * Dao 类型到限定 Dao 工厂的映射.
     *
     * 内层 `null` key 表示未显式声明限定符的唯一绑定.
     */
    val daoFactories: Map<KClass<*>, Map<String?, () -> Any>>
}
