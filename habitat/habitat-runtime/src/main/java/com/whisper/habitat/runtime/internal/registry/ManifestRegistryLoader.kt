package com.whisper.habitat.runtime.internal.registry

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.whisper.habitat.runtime.registry.HabitatRegistry

/**
 * 从 Application Manifest 加载 Habitat Registry.
 *
 * 负责读取 Habitat 插件写入的 Manifest metadata, 并反射创建符合协议的 Registry.
 *
 * @aegis 保护固定 metadata 名称, Registry 类名取值和加载失败语义.
 * @aegis-audit 2026-09-01 | whisper | 经授权保留 metadata 缺失降级并让已声明 Registry 的完整性错误直接失败.
 * @aegis-audit 2026-09-01 | whisper | 经授权允许未接入 compiler 时 Registry 类缺失并通过 warning 降级为空注册表.
 *
 * @author whisper
 * @since 2026/07/28
 */
internal object ManifestRegistryLoader {

    /**
     * Registry Manifest metadata key.
     */
    private const val REGISTRY_METADATA_NAME: String = "com.whisper.habitat.registry"

    /**
     * 加载当前应用中的 Habitat Registry.
     *
     * @param application 当前进程的 Application.
     * @param warning metadata 缺失的 warning 日志输出入口.
     * @return 当前应用 Registry 实例列表, metadata 或 Registry 类缺失时返回空列表.
     * @exception IllegalStateException metadata 读取失败, 或已找到的 Registry 无法链接、校验或构造时抛出.
     */
    fun load(
        application: Application,
        warning: (String, Throwable?) -> Unit,
    ): List<HabitatRegistry> {
        val metadata: Bundle? = readMetadata(application)
        if (metadata == null || !metadata.containsKey(REGISTRY_METADATA_NAME)) {
            warning(
                "No Habitat registry metadata was found. An empty Dao registry will be installed.",
                null,
            )
            return emptyList()
        }
        val registryClassName: String? = try {
            metadata.getString(REGISTRY_METADATA_NAME)
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "Failed to read Habitat registry metadata value: " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        }
        check(!registryClassName.isNullOrBlank()) {
            "Habitat registry metadata must contain a non-blank Registry class name: " +
                "metadataName=$REGISTRY_METADATA_NAME."
        }
        return listOfNotNull(
            createRegistry(
                classLoader = application.classLoader,
                className = registryClassName,
                warning = warning,
            )
        )
    }

    private fun readMetadata(
        application: Application,
    ): Bundle? {
        return try {
            readApplicationMetadata(application)
        } catch (exception: PackageManager.NameNotFoundException) {
            throw IllegalStateException(
                "Failed to read Habitat registry metadata: package=${application.packageName}, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "Failed to read Habitat registry metadata: package=${application.packageName}, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Failed to link Habitat registry metadata API: package=${application.packageName}, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                error,
            )
        }
    }

    private fun createRegistry(
        classLoader: ClassLoader,
        className: String,
        warning: (String, Throwable?) -> Unit,
    ): HabitatRegistry? {
        val registryClass: Class<*> = try {
            Class.forName(className, false, classLoader)
        } catch (exception: ClassNotFoundException) {
            warning(
                "Generated Habitat Registry class was not found: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME. An empty Dao registry will be installed. " +
                    "If automatic Dao registration is expected, verify that habitat-compiler is configured " +
                    "for this variant.",
                exception,
            )
            return null
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Failed to link generated Habitat Registry: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                error,
            )
        } catch (exception: SecurityException) {
            throw IllegalStateException(
                "Failed to access generated Habitat Registry: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        }

        val registryType: Class<out HabitatRegistry> = try {
            registryClass.asSubclass(HabitatRegistry::class.java)
        } catch (exception: ClassCastException) {
            throw IllegalStateException(
                "Generated Habitat Registry has an incompatible type: registry=$className, " +
                    "expected=${HabitatRegistry::class.java.name}, metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        }

        return try {
            registryType.getDeclaredConstructor().newInstance()
        } catch (exception: ReflectiveOperationException) {
            throw IllegalStateException(
                "Failed to create generated Habitat Registry: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        } catch (exception: SecurityException) {
            throw IllegalStateException(
                "Failed to access generated Habitat Registry constructor: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                exception,
            )
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Failed to link generated Habitat Registry constructor: registry=$className, " +
                    "metadataName=$REGISTRY_METADATA_NAME.",
                error,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun readApplicationMetadata(application: Application): Bundle? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.getApplicationInfo(
                application.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            ).metaData
        } else {
            application.packageManager.getApplicationInfo(
                application.packageName,
                PackageManager.GET_META_DATA,
            ).metaData
        }
    }
}
