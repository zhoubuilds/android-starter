package com.whisper.aster.runtime.internal.registry

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.whisper.aster.runtime.internal.LogcatErrorHandler
import com.whisper.aster.runtime.registry.AsterRegistryInstaller

/**
 * 从 Application Manifest 加载 Aster 模块注册器.
 *
 * 负责读取 Registry metadata、整理注册器类名并反射创建符合协议的安装器实例.
 *
 * @aegis 保护 Manifest metadata 标记, 类名取值, 排序去重和加载失败边界.
 * @aegis-audit 2026-08-31 | whisper | 经授权将固定标记作为 value, Registry 类名改由 name 提供.
 *
 * @author whisper
 * @since 2026/07/22
 */
internal object ManifestRegistryLoader {

    /**
     * Registry Manifest metadata 的固定发现标记.
     */
    private const val REGISTRY_METADATA_MARKER: String = "com.whisper.aster.registry"

    /**
     * 加载当前应用中全部 Aster 模块注册器.
     *
     * @param application 当前进程的 Application.
     * @return 按类名排序且去重后的注册器实例, 未发现注册器时返回空列表.
     * @exception IllegalStateException Manifest 查询失败或已确认的注册器无法创建时抛出.
     */
    fun load(application: Application): List<AsterRegistryInstaller> {
        val metadata: Bundle? = readMetadata(application)
        val metadataEntries: Map<String, Any?> = readMetadataEntries(metadata)
        return load(
            metadataEntries = metadataEntries,
            classLoader = application.classLoader,
            warning = LogcatErrorHandler::warning
        )
    }

    /**
     * 从 Manifest metadata 候选项中加载注册器.
     *
     * metadata value 用于匹配固定发现标记, name 作为候选注册器类名校验.
     *
     * @param metadataEntries Manifest metadata 条目.
     * @param classLoader 当前应用的 ClassLoader.
     * @param warning 可恢复的候选项错误输出入口.
     * @return 按类名排序且去重后的注册器实例.
     * @exception IllegalStateException 已确认的注册器无法创建时抛出.
     */
    internal fun load(
        metadataEntries: Map<String, Any?>,
        classLoader: ClassLoader,
        warning: (message: String, cause: Throwable?) -> Unit
    ): List<AsterRegistryInstaller> {
        val registryMetadataNames: List<String> = metadataEntries.entries
            .filter { entry: Map.Entry<String, Any?> ->
                entry.value == REGISTRY_METADATA_MARKER
            }
            .map { entry: Map.Entry<String, Any?> -> entry.key }
            .sorted()
        if (registryMetadataNames.isEmpty()) {
            warning(
                "No Aster registry metadata was found. " +
                    "Initialization will continue with empty registries.",
                null
            )
            return emptyList()
        }

        val registryClassNames: List<String> = registryMetadataNames.mapNotNull { className ->
            if (className.isBlank()) {
                warning(
                    "Ignoring manifest metadata marked with '$REGISTRY_METADATA_MARKER' because " +
                        "its name must contain a non-blank Aster registry class name.",
                    null
                )
                null
            } else {
                className
            }
        }.distinct().sorted()

        return registryClassNames.mapNotNull { className: String ->
            createInstaller(classLoader, className, warning)
        }
    }

    private fun readMetadata(application: Application): Bundle? {
        return try {
            readApplicationMetadata(application)
        } catch (exception: PackageManager.NameNotFoundException) {
            throw IllegalStateException(
                "Failed to read generated aster registry metadata.",
                exception
            )
        }
    }

    private fun createInstaller(
        classLoader: ClassLoader,
        className: String,
        warning: (message: String, cause: Throwable?) -> Unit
    ): AsterRegistryInstaller? {
        val registryClass: Class<*> = try {
            Class.forName(className, false, classLoader)
        } catch (exception: ClassNotFoundException) {
            warning(
                "Ignoring manifest metadata name '$className' marked with " +
                    "'$REGISTRY_METADATA_MARKER' because the referenced class could not be found.",
                exception
            )
            return null
        }

        if (!AsterRegistryInstaller::class.java.isAssignableFrom(registryClass)) {
            warning(
                "Ignoring manifest metadata name '$className' marked with " +
                    "'$REGISTRY_METADATA_MARKER' because it does not implement " +
                    "${AsterRegistryInstaller::class.java.name}.",
                null
            )
            return null
        }

        val installerClass: Class<out AsterRegistryInstaller> =
            registryClass.asSubclass(AsterRegistryInstaller::class.java)
        return try {
            installerClass.getConstructor().newInstance()
        } catch (exception: ReflectiveOperationException) {
            throw IllegalStateException(
                "Failed to create generated aster registry: $className.",
                exception
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun readMetadataEntries(metadata: Bundle?): Map<String, Any?> {
        if (metadata == null) {
            return emptyMap()
        }
        return metadata.keySet()
            .associateWith { metadata.get(it) }
    }

    @Suppress("DEPRECATION")
    private fun readApplicationMetadata(application: Application): Bundle? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.getApplicationInfo(
                application.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            ).metaData
        } else {
            application.packageManager.getApplicationInfo(
                application.packageName,
                PackageManager.GET_META_DATA
            ).metaData
        }
    }
}
