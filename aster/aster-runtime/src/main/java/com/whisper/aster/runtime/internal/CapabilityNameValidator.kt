package com.whisper.aster.runtime.internal

/**
 * 校验运行时能力名.
 *
 * 使用与 Aster compiler 相同的通用能力名格式, 但不校验具体模块的首段.
 *
 * @aegis 保护 Runtime 与 Compiler 一致的能力名称格式和校验结果语义.
 *
 * @author whisper
 * @since 2026/07/23
 */
internal object CapabilityNameValidator {

    /**
     * 能力名称格式, 要求至少包含两个点号分隔的段.
     */
    private val CAPABILITY_NAME_PATTERN: Regex = Regex(
        "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"
    )

    /**
     * 返回能力名格式错误说明.
     *
     * @param name 待校验的能力名.
     * @return 格式合法时返回 null, 否则返回错误说明.
     */
    fun validationError(name: String): String? {
        if (CAPABILITY_NAME_PATTERN.matches(name)) {
            return null
        }
        return "Invalid capability name '$name'. " +
            "Expected at least two dot-separated segments; each segment must start with " +
            "[a-z] and contain only [a-z0-9_]. Example: 'feature.capability'."
    }
}
