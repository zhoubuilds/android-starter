package com.whisper.aster.runtime.annotation

/**
 * 声明一个可通过路径导航的 Activity.
 *
 * 目标必须是具体的 Activity 类. 路径必须以 `/` 开头且不能以 `/` 结尾, 至少包含两个路径段;
 * 每个路径段必须以小写字母开头, 其余字符只能使用小写字母, 数字或下划线. 第一个路径段必须与
 * 当前模块配置的 `aster.segment` 一致. 注解使用二进制保留策略供编译器处理, Runtime 不通过
 * 反射读取该注解.
 *
 * @param path Activity 的完整路由路径.
 * @aegis 保护注解目标, 二进制保留策略, 路径参数和已文档化校验规则.
 * @author whisper
 * @since 2026/07/23
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Route(val path: String)
