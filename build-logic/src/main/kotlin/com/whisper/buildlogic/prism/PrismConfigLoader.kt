package com.whisper.buildlogic.prism

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Prism 配置文件加载器.
 *
 * 负责选择根工程配置文件、读取文本并输出配置缺省提示. TOML 结构解析由 [PrismConfigParser] 负责.
 *
 * @aegis 保护 `prism.appConfig.file`, 根工程默认路径, 显式路径不回退和空配置提示语义.
 *
 * @author whisper
 * @since 2026/09/01
 */
internal class PrismConfigLoader(
    private val parser: PrismConfigParser = PrismConfigParser()
) {

    /**
     * 加载当前项目选中的应用配置.
     *
     * @param target 当前 Gradle 项目
     * @return 应用配置
     * @exception GradleException 配置路径或配置内容不合法时抛出
     */
    fun load(target: Project): AppConfig {
        val configFilePath: String = target.providers
            .gradleProperty(CONFIG_FILE_PROPERTY)
            .getOrElse(DEFAULT_CONFIG_FILE_PATH)
            .trim()
        if (configFilePath.isEmpty()) {
            throw GradleException("Gradle property '$CONFIG_FILE_PROPERTY' must not be blank.")
        }

        val file: File = target.rootProject.file(configFilePath)
        if (!file.isFile) {
            throw GradleException(
                "App config file does not exist: ${file.path}. " +
                    "Set '$CONFIG_FILE_PROPERTY' to an existing file. " +
                    "When the property is omitted, the fallback path is '$DEFAULT_CONFIG_FILE_PATH'."
            )
        }

        val source: String = file.readText()
        if (source.isBlank()) {
            logConfigNotice(
                target = target,
                message = "App config is empty at ${file.path}. " +
                    "No exports, default config or environments will be applied."
            )
            return AppConfig.EMPTY
        }

        val config: AppConfig = parser.parse(
            source = source,
            sourcePath = file.path
        )
        if (config.environments.isEmpty()) {
            logConfigNotice(
                target = target,
                message = "No environments configured in ${file.path}. " +
                    "Shared configuration will be applied without creating environment productFlavors."
            )
        }
        return config
    }

    private fun logConfigNotice(target: Project, message: String) {
        target.logger.lifecycle(
            """
            |
            |[com.whisper.prism]
            |$message
            """.trimMargin()
        )
    }

    private companion object {
        private const val DEFAULT_CONFIG_FILE_PATH: String = "app-config.toml"
        private const val CONFIG_FILE_PROPERTY: String = "prism.appConfig.file"
    }
}
