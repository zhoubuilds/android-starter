package com.whisper.habitat.runtime.annotation

import com.whisper.habitat.runtime.HabitatFactory

/**
 * 标记参与 Habitat Dao 注册的 RoomDatabase.
 *
 * 只有标记该注解的数据库会被 Habitat 编译器扫描并注册到 [HabitatFactory]. 注解只声明 Dao 参与静态注册, 不自动维护
 * Room Entity、schema、migration 或数据库生命周期. 所有参与数据库必须位于最终唯一的 Habitat 装配模块中.
 *
 * @aegis 保护注解目标, 源码保留策略和数据库参与注册的语义.
 * @aegis-audit 2026-09-01 | whisper | 经授权明确静态注册、Room 职责和唯一装配模块边界.
 *
 * @author whisper
 * @since 2026/07/27
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class HabitatDatabase
