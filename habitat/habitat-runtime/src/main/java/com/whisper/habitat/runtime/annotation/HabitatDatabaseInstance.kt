package com.whisper.habitat.runtime.annotation

/**
 * 标记 Habitat 生成代码可访问的数据库实例入口.
 *
 * 该注解应标记在 RoomDatabase 伴生对象中对同模块生成代码可见的普通属性或无参函数上.
 *
 * @aegis 保护注解目标, 源码保留策略和数据库实例入口约束.
 * @aegis-audit 2026-08-31 | whisper | 经授权明确实例入口可见性和不支持的声明形态.
 *
 * @author whisper
 * @since 2026/07/27
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HabitatDatabaseInstance
