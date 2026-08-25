package com.whisper.aster.runtime.internal

/**
 * 校验运行时路由路径.
 *
 * 使用与 Aster compiler 相同的通用路径格式, 但不校验具体模块的首段.
 *
 * @aegis 保护 Runtime 与 Compiler 一致的路由路径格式和校验结果语义.
 * @author whisper
 * @since 2026/07/23
 */
internal object RoutePathValidator {

    /**
     * 路由路径格式, 要求以斜杠开始并至少包含两个路径段.
     */
    private val ROUTE_PATH_PATTERN: Regex =
        Regex("^/[a-z][a-z0-9_]*(/[a-z][a-z0-9_]*)+$")

    /**
     * 返回路由路径格式错误说明.
     *
     * @param path 待校验的路由路径.
     * @return 格式合法时返回 null, 否则返回错误说明.
     */
    fun validationError(path: String): String? {
        if (ROUTE_PATH_PATTERN.matches(path)) {
            return null
        }
        return "Invalid route path '$path'. Expected at least two slash-separated segments; " +
            "each segment must start with [a-z] and contain only [a-z0-9_], " +
            "the path must start with '/' and must not end with '/'. " +
            "Expected the format '/<segment>/<page>'. Example: '/feature/page'."
    }
}
