package com.whisper.aster.runtime.internal.registry

import com.whisper.aster.runtime.registry.AsterRegistrar
import com.whisper.aster.runtime.registry.AsterRegistryInstaller
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 验证 Manifest Registry 候选项解析和反射边界.
 *
 * @author whisper
 * @since 2026/07/23
 */
class ManifestRegistryLoaderTest {

    /**
     * 无效候选项只输出警告并被忽略.
     */
    @Test
    fun invalidCandidatesWarnAndAreIgnored() {
        val warningMessages: MutableList<String> = mutableListOf()
        val warningCauses: MutableList<Throwable?> = mutableListOf()
        val metadataEntries: Map<String, Any?> = mapOf(
            "unrelated.metadata" to "missing.UnrelatedClass",
            "legacy.RegistryInstaller" to "com.whisper.aster.runtime.registry.legacy",
            "" to REGISTRY_METADATA_MARKER,
            "missing.RegistryInstaller" to REGISTRY_METADATA_MARKER,
            String::class.java.name to REGISTRY_METADATA_MARKER
        )

        val installers: List<AsterRegistryInstaller> = ManifestRegistryLoader.load(
            metadataEntries = metadataEntries,
            classLoader = requireNotNull(javaClass.classLoader),
            warning = { message, cause ->
                warningMessages += message
                warningCauses += cause
            }
        )

        assertTrue(installers.isEmpty())
        assertEquals(3, warningMessages.size)
        assertTrue(warningMessages.all { it.contains(REGISTRY_METADATA_MARKER) })
        assertTrue(warningCauses.any { it is ClassNotFoundException })
    }

    /**
     * metadata value 匹配固定标记时, name 作为 Registry 类名加载.
     */
    @Test
    fun registryMetadataMarkerLoadsRegistryNamedByKey() {
        val metadataEntries: Map<String, Any?> = mapOf(
            TestRegistryInstaller::class.java.name to REGISTRY_METADATA_MARKER
        )

        val installers: List<AsterRegistryInstaller> = ManifestRegistryLoader.load(
            metadataEntries = metadataEntries,
            classLoader = requireNotNull(javaClass.classLoader),
            warning = { _, _ -> }
        )

        assertEquals(1, installers.size)
        assertTrue(installers.single() is TestRegistryInstaller)
    }

    /**
     * 多个合法 Registry 按 metadata name 稳定排序.
     */
    @Test
    fun validRegistryNamesAreSorted() {
        val metadataEntries: Map<String, Any?> = mapOf(
            ZetaRegistryInstaller::class.java.name to REGISTRY_METADATA_MARKER,
            AlphaRegistryInstaller::class.java.name to REGISTRY_METADATA_MARKER
        )

        val installers: List<AsterRegistryInstaller> = ManifestRegistryLoader.load(
            metadataEntries = metadataEntries,
            classLoader = requireNotNull(javaClass.classLoader),
            warning = { _, _ -> }
        )

        assertEquals(
            listOf(AlphaRegistryInstaller::class.java, ZetaRegistryInstaller::class.java),
            installers.map { it.javaClass }
        )
    }

    /**
     * 已确认 Registry 的构造失败会保留反射原因并中断加载.
     */
    @Test
    fun confirmedRegistryConstructionFailureIsFatal() {
        val metadataEntries: Map<String, Any?> = mapOf(
            ThrowingRegistryInstaller::class.java.name to REGISTRY_METADATA_MARKER
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            ManifestRegistryLoader.load(
                metadataEntries = metadataEntries,
                classLoader = requireNotNull(javaClass.classLoader),
                warning = { _, _ -> }
            )
        }

        assertTrue(exception.cause is InvocationTargetException)
    }

    /**
     * 测试 Registry Installer.
     */
    class TestRegistryInstaller : AsterRegistryInstaller {

        override fun install(registrar: AsterRegistrar) = Unit
    }

    /**
     * 类名排序靠前的测试 Registry Installer.
     */
    class AlphaRegistryInstaller : AsterRegistryInstaller {

        override fun install(registrar: AsterRegistrar) = Unit
    }

    /**
     * 类名排序靠后的测试 Registry Installer.
     */
    class ZetaRegistryInstaller : AsterRegistryInstaller {

        override fun install(registrar: AsterRegistrar) = Unit
    }

    /**
     * 构造阶段失败的测试 Registry Installer.
     */
    class ThrowingRegistryInstaller : AsterRegistryInstaller {

        init {
            throw IllegalStateException("Registry constructor failure.")
        }

        override fun install(registrar: AsterRegistrar) = Unit
    }

    private companion object {

        /**
         * Registry Manifest metadata 的固定发现标记.
         */
        private const val REGISTRY_METADATA_MARKER: String = "com.whisper.aster.registry"
    }
}
