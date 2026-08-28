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
 * @aegis 保护固定 metadata 名称, Registry 类名取值和可恢复加载失败语义.
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
     * @param warning 可恢复错误的 warning 日志输出入口.
     * @return 当前应用 Registry 实例列表, metadata 缺失或不可用时返回空列表.
     */
    fun load(
        application: Application,
        warning: (String, Throwable?) -> Unit,
    ): List<HabitatRegistry> {
        val metadata: Bundle? = readMetadata(application, warning)
        val registryClassName: String? = metadata?.getString(REGISTRY_METADATA_NAME)
        if (registryClassName.isNullOrBlank()) {
            warning(
                "No Habitat registry metadata was found. An empty Dao registry will be installed.",
                null,
            )
            return emptyList()
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
        warning: (String, Throwable?) -> Unit,
    ): Bundle? {
        return try {
            readApplicationMetadata(application)
        } catch (exception: PackageManager.NameNotFoundException) {
            warning(
                "Failed to read generated habitat registry metadata.",
                exception,
            )
            null
        } catch (exception: RuntimeException) {
            warning(
                "Failed to read generated habitat registry metadata.",
                exception,
            )
            null
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
                "Ignoring manifest metadata value '$className' because the referenced class " +
                    "could not be found. The metadata name '$REGISTRY_METADATA_NAME' is " +
                    "reserved for the Habitat registry.",
                exception,
            )
            return null
        } catch (error: LinkageError) {
            warning(
                "Ignoring manifest metadata value '$className' because the referenced class " +
                    "could not be loaded: ${error.javaClass.name}: ${error.message}.",
                error,
            )
            return null
        }

        val registryType: Class<out HabitatRegistry> = try {
            registryClass.asSubclass(HabitatRegistry::class.java)
        } catch (exception: ClassCastException) {
            warning(
                "Ignoring manifest metadata value '$className' because it does not implement " +
                    "${HabitatRegistry::class.java.name}. The metadata name " +
                    "'$REGISTRY_METADATA_NAME' is reserved for the Habitat registry.",
                exception,
            )
            return null
        }

        return try {
            registryType.getDeclaredConstructor().newInstance()
        } catch (exception: ReflectiveOperationException) {
            warning(
                "Ignoring manifest metadata value '$className' because the registry could not " +
                    "be created: ${exception.javaClass.name}: ${exception.message}.",
                exception,
            )
            null
        } catch (exception: SecurityException) {
            warning(
                "Ignoring manifest metadata value '$className' because the registry constructor " +
                    "could not be accessed: ${exception.javaClass.name}: ${exception.message}.",
                exception,
            )
            null
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
