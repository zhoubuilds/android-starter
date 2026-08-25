package com.whisper.aster.compiler.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

/**
 * 编译期路由条目.
 *
 * @author whisper
 * @since 2026/07/22
 */
internal data class RouteEntry(
    /**
     * 路由路径.
     */
    val path: String,
    /**
     * Activity 的全限定类名.
     */
    val className: String,
    /**
     * Activity 的 KotlinPoet 类型引用.
     */
    val targetType: ClassName,
    /**
     * 声明该路由的源文件, 用于 KSP 增量处理依赖.
     */
    val sourceFile: KSFile?
)

/**
 * 编译期能力条目.
 *
 * @author whisper
 * @since 2026/07/22
 */
internal data class CapabilityEntry(
    /**
     * 能力的唯一名称.
     */
    val name: String,
    /**
     * 能力实现类的全限定类名.
     */
    val className: String,
    /**
     * 是否复用同一个能力实例.
     */
    val singleton: Boolean,
    /**
     * 能力实现类的 KotlinPoet 类型引用.
     */
    val targetType: ClassName,
    /**
     * 声明该能力的源文件, 用于 KSP 增量处理依赖.
     */
    val sourceFile: KSFile?
)
