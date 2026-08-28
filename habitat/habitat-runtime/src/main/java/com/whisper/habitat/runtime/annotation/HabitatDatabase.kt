package com.whisper.habitat.runtime.annotation

/**
 * 标记参与 Habitat Dao 注册的 RoomDatabase.
 *
 * 只有标记该注解的数据库会被 Habitat 编译器扫描并注册到 [HabitatFactory].
 *
 * @aegis 保护注解目标, 源码保留策略和数据库参与注册的语义.
 *
 * @author whisper
 * @since 2026/07/27
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class HabitatDatabase
