package com.whisper.aster.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.register
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 验证 Registry Manifest 索引生成.
 *
 * @author whisper
 * @since 2026/07/21
 */
class GenerateAsterManifestTaskTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证任务可以生成指向当前模块 Registry 的 Manifest.
     */
    @Test
    fun generatesRegistryManifest() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.root)
            .build()
        val manifestFile = temporaryFolder.newFile("AndroidManifest.xml")
        val task = project.tasks.register<GenerateAsterManifestTask>("generateManifest") {
            registryQualifiedName.set(
                "com.example.user.aster.generated.AsterGeneratedRegistry"
            )
            registryMetadataMarker.set("com.whisper.aster.registry")
            this.manifestFile.set(manifestFile)
        }.get()

        task.generate()

        val manifest = manifestFile.readText()
        assertTrue(
            manifest.contains(
                "android:name=\"com.example.user.aster.generated.AsterGeneratedRegistry\""
            )
        )
        assertTrue(
            manifest.contains(
                "android:value=\"com.whisper.aster.registry\""
            )
        )
    }
}
