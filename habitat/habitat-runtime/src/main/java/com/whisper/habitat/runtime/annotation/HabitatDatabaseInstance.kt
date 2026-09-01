package com.whisper.habitat.runtime.annotation

/**
 * 标记 Habitat 生成代码可访问的数据库实例入口.
 *
 * 该注解应标记在 RoomDatabase 伴生对象中对同模块生成代码可见的非空普通属性或无参函数上, 返回当前数据库类型.
 * Habitat 初始化时只保存调用该入口的延迟函数; 每次成功获取 Dao 时才读取入口并调用 Dao accessor. Habitat 不创建、缓存或关闭
 * 数据库实例, 调用方负责在首次 Dao 获取前完成数据库初始化并保证实例发布和生命周期的线程安全.
 *
 * @aegis 保护注解目标, 源码保留策略和数据库实例入口约束.
 * @aegis-audit 2026-08-31 | whisper | 经授权明确实例入口可见性和不支持的声明形态.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确延迟调用、实例生命周期和线程安全责任边界.
 *
 * @author whisper
 * @since 2026/07/27
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HabitatDatabaseInstance
