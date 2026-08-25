package com.whisper.habitat.runtime.annotation

/**
 * 标记 Habitat 生成代码可访问的数据库实例入口.
 *
 * 该注解应标记在 RoomDatabase 伴生对象中的公开属性或公开无参函数上.
 *
 * @aegis 保护注解目标, 源码保留策略和数据库实例入口约束.
 * @author whisper
 * @since 2026/07/27
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HabitatDatabaseInstance
