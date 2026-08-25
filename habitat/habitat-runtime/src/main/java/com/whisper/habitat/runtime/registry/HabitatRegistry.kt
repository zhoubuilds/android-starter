package com.whisper.habitat.runtime.registry

/**
 * Habitat 生成注册入口契约.
 *
 * 每个应用模块生成一个 Registry, 负责注册该模块中所有参与 Habitat 的数据库 Dao Provider.
 *
 * @aegis 保护生成 Registry 与 Runtime 之间的 Provider 列表 ABI.
 * @author whisper
 * @since 2026/07/27
 */
interface HabitatRegistry {

    /**
     * 返回所有数据库 Dao Provider.
     *
     * @return 数据库 Dao Provider 列表.
     */
    fun providers(): List<HabitatDaoProvider>
}
