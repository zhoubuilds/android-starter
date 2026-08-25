package com.whisper.habitat.gradle

import java.io.File
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 验证 Habitat Registry Manifest 索引生成.
 *
 * @author whisper
 * @since 2026/07/28
 */
class GenerateHabitatManifestTaskTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * 验证任务可以生成固定 metadata name 和当前模块 Registry value.
     */
    @Test
    fun generatesRegistryManifest() {
        val manifestFile: File = temporaryFolder.newFile("AndroidManifest.xml")
        val task: GenerateHabitatManifestTask = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.root)
            .build()
            .tasks
            .register<GenerateHabitatManifestTask>("generateManifest") {
                registryMetadataName.set("com.whisper.habitat.registry")
                registryQualifiedName.set("com.example.habitat.generated.GeneratedHabitatRegistry")
                this.manifestFile.set(manifestFile)
            }
            .get()

        task.generate()

        val manifest: String = manifestFile.readText()
        assertTrue(manifest.contains("com.whisper.habitat.registry"))
        assertTrue(manifest.contains("com.example.habitat.generated.GeneratedHabitatRegistry"))
    }
}
