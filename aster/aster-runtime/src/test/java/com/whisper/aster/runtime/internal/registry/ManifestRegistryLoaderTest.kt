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
            "$REGISTRY_METADATA_PREFIX.null" to null,
            "$REGISTRY_METADATA_PREFIX.number" to 1,
            "$REGISTRY_METADATA_PREFIX.blank" to " ",
            "$REGISTRY_METADATA_PREFIX.missing" to "missing.RegistryInstaller",
            "$REGISTRY_METADATA_PREFIX.wrongType" to String::class.java.name
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
        assertEquals(5, warningMessages.size)
        assertTrue(warningMessages.all { it.contains("reserved for Aster registries") })
        assertTrue(warningCauses.any { it is ClassNotFoundException })
    }

    /**
     * metadata name 只需要保留前缀, 不需要与 Registry 类名对应.
     */
    @Test
    fun reservedMetadataNameLoadsIndependentRegistryValue() {
        val metadataEntries: Map<String, Any?> = mapOf(
            "${REGISTRY_METADATA_PREFIX}custom.entry" to TestRegistryInstaller::class.java.name
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
     * 多个合法 Registry 按类名稳定排序, 重复 value 只实例化一次.
     */
    @Test
    fun validRegistryValuesAreSortedAndDeduplicated() {
        val metadataEntries: Map<String, Any?> = mapOf(
            "${REGISTRY_METADATA_PREFIX}zeta" to ZetaRegistryInstaller::class.java.name,
            "${REGISTRY_METADATA_PREFIX}alpha" to AlphaRegistryInstaller::class.java.name,
            "${REGISTRY_METADATA_PREFIX}duplicate" to ZetaRegistryInstaller::class.java.name
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
            "${REGISTRY_METADATA_PREFIX}throwing" to
                ThrowingRegistryInstaller::class.java.name
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
         * Registry Manifest metadata key 的固定前缀.
         */
        private const val REGISTRY_METADATA_PREFIX: String = "com.whisper.aster.runtime.registry."
    }
}
