package com.whisper.aster.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

/**
 * TestKit 使用的最小 KSP host plugin.
 *
 * 只提供真实 KspExtension 和参数存储, 不执行 KSP 的 Android source set 集成, 用于隔离
 * Aster 插件自身的参数传递测试.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class FakeKspPlugin : Plugin<Project> {

    /**
     * 向测试工程注册 KSP 公开扩展.
     *
     * @param project 测试工程.
     */
    override fun apply(project: Project) {
        project.extensions.add("ksp", FakeKspExtension(project))
    }
}

/**
 * 用于测试的 KSP Gradle 扩展实现.
 *
 * @param project 承载该扩展的 Gradle 工程.
 * @author whisper
 * @since 2026/07/21
 */
private class FakeKspExtension(project: Project) : KspExtension(project) {

    override val useKsp2: Property<Boolean> = project.objects.property(Boolean::class.java)

    override val excludedSources: ConfigurableFileCollection = project.files()
}
