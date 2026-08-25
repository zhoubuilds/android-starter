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
 * @aegis 保护 Manifest metadata 前缀, 类名取值, 排序去重和加载失败边界.
 * @author whisper
 * @since 2026/07/22
 */
internal object ManifestRegistryLoader {

    /**
     * Registry Manifest metadata key 的固定前缀.
     */
    private const val REGISTRY_METADATA_PREFIX: String = "com.whisper.aster.runtime.registry."

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
     * metadata name 只用于匹配保留前缀, value 独立作为候选注册器类名校验.
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
        val registryMetadataNames: List<String> = metadataEntries.keys
            .filter { it.startsWith(REGISTRY_METADATA_PREFIX) }
            .sorted()
        if (registryMetadataNames.isEmpty()) {
            warning(
                "No Aster registry metadata was found. " +
                    "Initialization will continue with empty registries.",
                null
            )
            return emptyList()
        }

        val registryClassNames: List<String> = registryMetadataNames.mapNotNull {
            val value: Any? = metadataEntries[it]
            val className: String? = value as? String
            if (className.isNullOrBlank()) {
                warning(
                    "Ignoring manifest metadata '$it' because its value must be a non-blank " +
                        "String containing an Aster registry class name. The metadata prefix " +
                        "'$REGISTRY_METADATA_PREFIX' is reserved for Aster registries.",
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
                "Ignoring manifest metadata value '$className' because the referenced class " +
                    "could not be found. The metadata prefix '$REGISTRY_METADATA_PREFIX' is " +
                    "reserved for Aster registries.",
                exception
            )
            return null
        }

        if (!AsterRegistryInstaller::class.java.isAssignableFrom(registryClass)) {
            warning(
                "Ignoring manifest metadata value '$className' because it does not implement " +
                    "${AsterRegistryInstaller::class.java.name}. The metadata prefix " +
                    "'$REGISTRY_METADATA_PREFIX' is reserved for Aster registries.",
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
            .filter { it.startsWith(REGISTRY_METADATA_PREFIX) }
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
