package com.whisper.habitat.runtime.registry

import kotlin.reflect.KClass

/**
 * Habitat Dao 提供者.
 *
 * 每个参与注册的 RoomDatabase 对应一个 Dao Provider.
 *
 * @aegis 保护生成 Provider 与 Runtime 之间的 Dao 工厂映射 ABI.
 *
 * @author whisper
 * @since 2026/07/27
 */
interface HabitatDaoProvider {

    /**
     * Dao 类型到 Dao 工厂的映射.
     */
    val daoFactories: Map<KClass<*>, () -> Any>
}
