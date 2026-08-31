package com.whisper.aster.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 生成当前模块 Registry 的 Android Manifest 索引.
 *
 * Manifest 由 AGP 接入当前 Variant, 后续由 Android Manifest Merger 收集当前 APK
 * 或 AAR 实际包含的所有模块 Registry.
 *
 * @aegis 保护任务输入/输出契约和生成 Manifest metadata 的 XML 协议.
 * @aegis-audit 2026-08-31 | whisper | 经授权将 Registry 类名作为 metadata name, 固定标记作为 value.
 *
 * @author whisper
 * @since 2026/07/21
 */
abstract class GenerateAsterManifestTask : DefaultTask() {

    /**
     * 当前模块 Registry 的全限定类名.
     */
    @get:Input
    abstract val registryQualifiedName: Property<String>

    /**
     * Registry 在最终 ApplicationInfo.metaData 中使用的固定发现标记.
     */
    @get:Input
    abstract val registryMetadataMarker: Property<String>

    /**
     * 生成的 Manifest 文件.
     */
    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    /**
     * 写入当前模块 Registry 的 Manifest 索引.
     */
    @TaskAction
    fun generate() {
        val outputFile: File = manifestFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <meta-data
                        android:name="${registryQualifiedName.get().escapeXmlAttribute()}"
                        android:value="${registryMetadataMarker.get().escapeXmlAttribute()}" />
                </application>
            </manifest>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun String.escapeXmlAttribute(): String {
        return replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
    }
}
