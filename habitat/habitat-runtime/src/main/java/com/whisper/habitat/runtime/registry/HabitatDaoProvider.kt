package com.whisper.habitat.runtime.registry

import kotlin.reflect.KClass

/**
 * Habitat Dao 提供者.
 *
 * 每个参与注册的 RoomDatabase 对应一个由 Habitat compiler 生成的 Dao Provider. 该接口是生成代码与 runtime 之间的 ABI,
 * 不是面向业务代码的手写扩展点.
 *
 * @aegis 保护生成 Provider 与 Runtime 之间的 Dao 工厂映射 ABI.
 * @aegis-audit 2026-08-31 | whisper | 经授权将 Dao 工厂 ABI 扩展为按可空限定符索引的延迟工厂表.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确 compiler 专用 ABI 和延迟工厂边界.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确非法生成 binding 和工厂执行失败的 fail-fast 边界.
 *
 * @author whisper
 * @since 2026/07/27
 */
interface HabitatDaoProvider {

    /**
     * Dao 类型到限定 Dao 工厂的映射.
     *
     * 内层 `null` key 只表示未显式声明 qualifier 的唯一绑定. 工厂必须延迟返回声明的 Dao 类型, 不应在 Map 构建期间读取
     * 数据库实例. Compiler 必须在生成前拒绝空白、重复或混用限定与非限定 binding; Runtime 初始化时再次校验生成 ABI,
     * 发现非法结构会直接失败. Runtime 只复制 binding, 不会执行或缓存工厂返回值; 工厂执行失败或返回错误类型时不会转换为
     * 查找未命中的 `null`.
     */
    val daoFactories: Map<KClass<*>, Map<String?, () -> Any>>
}
