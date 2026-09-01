package com.whisper.habitat.runtime.registry

/**
 * Habitat 生成注册入口契约.
 *
 * 最终唯一的 Habitat 装配模块生成一个 Registry, 负责注册该模块中所有参与 Habitat 的数据库 Dao Provider. 该接口是
 * compiler、Gradle Manifest 索引与 runtime 之间的 ABI, 不是面向业务代码的手写扩展点, 也不支持运行时追加 Provider.
 *
 * @aegis 保护生成 Registry 与 Runtime 之间的 Provider 列表 ABI.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确唯一生成入口和静态 Provider 列表边界.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确空 Provider 列表与 Registry ABI 失败的不同处理语义.
 *
 * @author whisper
 * @since 2026/07/27
 */
interface HabitatRegistry {

    /**
     * 返回所有数据库 Dao Provider.
     *
     * 空列表是合法的空注册表. 方法执行或链接失败表示生成 ABI 损坏, Runtime 初始化会直接失败.
     *
     * @return 数据库 Dao Provider 列表.
     */
    fun providers(): List<HabitatDaoProvider>
}
