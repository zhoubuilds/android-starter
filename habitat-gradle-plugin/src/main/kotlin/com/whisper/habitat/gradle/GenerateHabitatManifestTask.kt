package com.whisper.habitat.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 生成当前 app Habitat Registry 的 Android Manifest 索引.
 *
 * Manifest 由 AGP 接入当前 Variant, 最终由 runtime 从 ApplicationInfo metadata 读取.
 *
 * @aegis 保护任务输入/输出契约和生成 Manifest metadata 的 XML 协议.
 * @author whisper
 * @since 2026/07/28
 */
abstract class GenerateHabitatManifestTask : DefaultTask() {

    /**
     * Registry 在最终 ApplicationInfo.metaData 中使用的索引名称.
     */
    @get:Input
    abstract val registryMetadataName: Property<String>

    /**
     * 当前 app Registry 的全限定类名.
     */
    @get:Input
    abstract val registryQualifiedName: Property<String>

    /**
     * 生成的 Manifest 文件.
     */
    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    /**
     * 写入当前 app Registry 的 Manifest 索引.
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
                        android:name="${registryMetadataName.get().escapeXmlAttribute()}"
                        android:value="${registryQualifiedName.get().escapeXmlAttribute()}" />
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
