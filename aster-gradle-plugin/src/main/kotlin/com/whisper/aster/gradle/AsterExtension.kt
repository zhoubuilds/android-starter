package com.whisper.aster.gradle

import org.gradle.api.GradleException

/**
 * Aster 业务模块配置.
 *
 * 每个使用 Aster 的 Android 模块都生成一个自己的 Registry. `segment`
 * 用于约束当前模块的路由和能力首段, 并且在同一个 Gradle Build 的源码模块中必须唯一.
 * Android namespace 由 AGP 保证在当前构建图中唯一.
 *
 * @aegis 保护 `aster.segment` DSL, 格式校验, 最终冻结和配置顺序兼容语义.
 *
 * @author whisper
 * @since 2026/07/21
 */
open class AsterExtension {

    /**
     * 当前模块使用的路由和能力首段, 不包含斜杠或点号.
     */
    var segment: String? = null
        set(value) {
            if (segmentFinalized && value != field) {
                throw GradleException(
                    "Cannot change aster.segment after Android DSL finalization. " +
                        "Current value: '${field.orEmpty()}'. Requested value: " +
                        "'${value.orEmpty()}'."
                )
            }
            if (value != null && !SEGMENT_PATTERN.matches(value)) {
                throw GradleException(
                    "Invalid aster.segment '$value'. Expected exactly one segment matching " +
                        "[a-z][a-z0-9_]*, without '/' or '.'. Example: 'feature'."
                )
            }
            if (segmentFinalized) {
                return
            }
            field = value
            value?.let { segmentValue: String ->
                segmentListener?.invoke(segmentValue)
            }
    }

    private var segmentListener: ((String) -> Unit)? = null

    /**
     * segment 是否已经完成最终校验.
     */
    private var segmentFinalized: Boolean = false

    /**
     * 绑定 KSP 参数同步回调, 并立即同步已经配置的 segment.
     *
     * 该方法用于兼容 Gradle 脚本中插件配置顺序早于或晚于 aster DSL 的情况.
     *
     * @param listener 接收当前模块 segment 的 KSP 参数回调.
     */
    internal fun bindKsp(listener: (String) -> Unit) {
        segmentListener = listener
        segment?.let { value: String -> listener(value) }
    }

    /**
     * 校验并冻结当前模块最终使用的 segment.
     *
     * @param projectPath 当前 Gradle 模块路径.
     * @return 当前模块最终使用的 segment.
     * @exception GradleException segment 未配置时抛出.
     */
    internal fun finalizeSegment(projectPath: String): String {
        val value: String = segment ?: throw GradleException(
            "Aster requires the aster.segment configuration in module " +
                "$projectPath. Add a top-level aster block in the module " +
                "build.gradle.kts, alongside plugins, android, and dependencies. " +
                "The value must be one lowercase segment, for example 'feature':\n\n" +
                "aster {\n" +
                "    segment = \"feature\"\n" +
                "}\n"
        )
        segmentFinalized = true
        return value
    }

    private companion object {
        private val SEGMENT_PATTERN: Regex = Regex("^[a-z][a-z0-9_]*$")
    }
}
