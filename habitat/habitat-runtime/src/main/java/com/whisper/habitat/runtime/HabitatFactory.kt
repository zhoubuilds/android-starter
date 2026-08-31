package com.whisper.habitat.runtime

import android.app.Application
import com.whisper.habitat.runtime.internal.LogcatErrorHandler
import com.whisper.habitat.runtime.internal.registry.ManifestRegistryLoader
import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import com.whisper.habitat.runtime.registry.HabitatRegistry
import kotlin.reflect.KClass

/**
 * Habitat Dao 工厂.
 *
 * 业务模块通过 Dao 类型获取 Dao 实例, 不感知 Dao 最终归属的 RoomDatabase.
 *
 * @aegis 保护初始化发布, 按类型获取 Dao, 未初始化失败和可恢复加载失败语义.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持限定 Dao 获取、唯一绑定回退和多绑定歧义降级.
 * @aegis-audit 2026-08-31 | whisper | 经授权使用 @Volatile 可空只读 Map 发布 Dao binding 快照.
 *
 * @author whisper
 * @since 2026/07/27
 */
object HabitatFactory {

    /**
     * Dao binding 快照.
     */
    @Volatile
    private var daoBindings: Map<KClass<*>, Map<String?, () -> Any>>? = null

    /**
     * 初始化锁.
     */
    private val initializeLock: Any = Any()

    /**
     * 初始化 Habitat 运行时.
     *
     * @param application 当前进程的 Application.
     */
    fun initialize(application: Application) {
        if (daoBindings != null) {
            return
        }
        synchronized(initializeLock) {
            if (daoBindings != null) {
                return
            }
            val providers: List<HabitatDaoProvider> = loadGeneratedProviders(application)
            installProviders(providers)
        }
    }

    /**
     * 按 Dao 类型获取实例.
     *
     * 只有一个绑定时执行其工厂; 存在多个绑定时记录 warning 并返回 null.
     *
     * @param daoClass Dao 类型.
     * @return Dao 实例, 找不到时返回 null.
     */
    fun <T : Any> get(daoClass: KClass<T>): T? {
        val daoBindings: Map<KClass<*>, Map<String?, () -> Any>> = requireInitializedBindings()
        val bindings: Map<String?, () -> Any> = daoBindings[daoClass] ?: run {
            LogcatErrorHandler.warning("Dao not found: ${daoClass.qualifiedName}.")
            return null
        }
        if (bindings.size > 1) {
            LogcatErrorHandler.warning(
                "Multiple Dao bindings found: dao=${daoClass.displayName()}, qualifiers=" +
                    "${bindings.keys.displayQualifiers()}. Specify a qualifier."
            )
            return null
        }
        val binding: Map.Entry<String?, () -> Any> = bindings.entries.singleOrNull() ?: run {
            LogcatErrorHandler.warning("Dao not found: ${daoClass.qualifiedName}.")
            return null
        }
        return createDao(
            daoClass = daoClass,
            qualifier = binding.key,
            daoFactory = binding.value,
        )
    }

    /**
     * 按 Dao 类型和限定符获取实例.
     *
     * 只执行完全匹配当前 Dao 类型和限定符的工厂.
     *
     * @param daoClass Dao 类型.
     * @param qualifier Dao 限定符.
     * @return Dao 实例, 找不到时返回 null.
     */
    fun <T : Any> get(
        daoClass: KClass<T>,
        qualifier: String,
    ): T? {
        val daoBindings: Map<KClass<*>, Map<String?, () -> Any>> = requireInitializedBindings()
        if (qualifier.isBlank()) {
            LogcatErrorHandler.warning(
                "Dao qualifier must not be blank: dao=${daoClass.displayName()}."
            )
            return null
        }
        val bindings: Map<String?, () -> Any> = daoBindings[daoClass] ?: run {
            LogcatErrorHandler.warning("Dao not found: ${daoClass.qualifiedName}.")
            return null
        }
        val daoFactory: (() -> Any) = bindings[qualifier] ?: run {
            LogcatErrorHandler.warning(
                "Dao binding not found: dao=${daoClass.displayName()}, qualifier=$qualifier, " +
                    "availableQualifiers=${bindings.keys.displayQualifiers()}."
            )
            return null
        }
        return createDao(
            daoClass = daoClass,
            qualifier = qualifier,
            daoFactory = daoFactory,
        )
    }

    private fun <T : Any> createDao(
        daoClass: KClass<T>,
        qualifier: String?,
        daoFactory: () -> Any,
    ): T? {
        val dao: Any = try {
            daoFactory()
        } catch (exception: RuntimeException) {
            LogcatErrorHandler.warning(
                "Dao factory failed: dao=${daoClass.displayName()}, qualifier=${qualifier.displayValue()}.",
                exception,
            )
            return null
        } catch (error: LinkageError) {
            LogcatErrorHandler.warning(
                "Dao factory could not be linked: dao=${daoClass.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}.",
                error,
            )
            return null
        }
        if (!daoClass.java.isInstance(dao)) {
            LogcatErrorHandler.warning(
                "Dao type mismatch: expected=${daoClass.displayName()}, actual=${dao::class.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}."
            )
            return null
        }
        return try {
            daoClass.java.cast(dao)
        } catch (exception: ClassCastException) {
            LogcatErrorHandler.warning(
                "Dao cast failed: expected=${daoClass.displayName()}, actual=${dao::class.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}.",
                exception,
            )
            null
        }
    }

    /**
     * 按 Dao 类型获取实例.
     *
     * @return Dao 实例, 找不到时返回 null.
     */
    inline fun <reified T : Any> get(): T? {
        return get(T::class)
    }

    /**
     * 按 Dao 类型和限定符获取实例.
     *
     * @param qualifier Dao 限定符.
     * @return Dao 实例, 找不到时返回 null.
     */
    inline fun <reified T : Any> get(qualifier: String): T? {
        return get(T::class, qualifier)
    }

    private fun loadGeneratedProviders(application: Application): List<HabitatDaoProvider> {
        val registries: List<HabitatRegistry> = loadGeneratedRegistries(application)
        return registries.flatMap { registry: HabitatRegistry ->
            loadRegistryProviders(registry)
        }
    }

    /**
     * 获取已发布的 Dao binding 快照.
     *
     * 如果其它线程正在初始化, 会等待同一把初始化锁释放后再读取快照, 避免初始化过程中的短暂空值被误判为未初始化.
     *
     * @return 已发布的 Dao binding 快照.
     * @exception IllegalStateException 从未调用初始化方法时抛出.
     */
    private fun requireInitializedBindings(): Map<KClass<*>, Map<String?, () -> Any>> {
        val currentBindings: Map<KClass<*>, Map<String?, () -> Any>>? = daoBindings
        if (currentBindings != null) {
            return currentBindings
        }
        val initializedBindings: Map<KClass<*>, Map<String?, () -> Any>>? = synchronized(initializeLock) {
            daoBindings
        }
        return checkNotNull(initializedBindings) {
            "HabitatFactory.initialize(application) must be called before getting DAOs."
        }
    }

    private fun loadGeneratedRegistries(application: Application): List<HabitatRegistry> {
        return try {
            ManifestRegistryLoader.load(
                application = application,
                warning = LogcatErrorHandler::warning,
            )
        } catch (exception: RuntimeException) {
            LogcatErrorHandler.warning(
                "Failed to load generated Habitat registries. An empty Dao registry will be installed.",
                exception,
            )
            emptyList()
        } catch (error: LinkageError) {
            LogcatErrorHandler.warning(
                "Generated Habitat registries could not be linked. An empty Dao registry will be installed.",
                error,
            )
            emptyList()
        }
    }

    private fun loadRegistryProviders(registry: HabitatRegistry): List<HabitatDaoProvider> {
        return try {
            registry.providers()
        } catch (exception: RuntimeException) {
            LogcatErrorHandler.warning(
                "Ignoring Habitat registry because its providers could not be loaded: " +
                    "${registry::class.qualifiedName}.",
                exception,
            )
            emptyList()
        } catch (error: LinkageError) {
            LogcatErrorHandler.warning(
                "Ignoring Habitat registry because its providers could not be linked: " +
                    "${registry::class.qualifiedName}.",
                error,
            )
            emptyList()
        }
    }

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val groupedFactoryMaps: Map<KClass<*>, List<Map<String?, () -> Any>>> = providers
            .flatMap { provider: HabitatDaoProvider ->
                loadProviderFactories(provider)
            }
            .groupBy(
                keySelector = { entry: Map.Entry<KClass<*>, Map<String?, () -> Any>> -> entry.key },
                valueTransform = { entry: Map.Entry<KClass<*>, Map<String?, () -> Any>> -> entry.value },
            )
        val installedBindings: Map<KClass<*>, Map<String?, () -> Any>> = groupedFactoryMaps
            .mapNotNull { entry: Map.Entry<KClass<*>, List<Map<String?, () -> Any>>> ->
                val bindings: Map<String?, () -> Any> = mergeDaoBindings(
                    daoClass = entry.key,
                    factoryMaps = entry.value,
                )
                if (bindings.isEmpty()) {
                    null
                } else {
                    entry.key to bindings
                }
            }
            .toMap()
        daoBindings = installedBindings
    }

    private fun mergeDaoBindings(
        daoClass: KClass<*>,
        factoryMaps: List<Map<String?, () -> Any>>,
    ): Map<String?, () -> Any> {
        val validEntries: List<Map.Entry<String?, () -> Any>> = factoryMaps
            .flatMap { factories: Map<String?, () -> Any> -> factories.entries }
            .filter { entry: Map.Entry<String?, () -> Any> ->
                val isValid: Boolean = entry.key?.isNotBlank() != false
                if (!isValid) {
                    LogcatErrorHandler.warning(
                        "Ignoring Dao binding with a blank qualifier: dao=${daoClass.displayName()}."
                    )
                }
                isValid
            }
        val groupedBindings: Map<String?, List<() -> Any>> = validEntries.groupBy(
            keySelector = { entry: Map.Entry<String?, () -> Any> -> entry.key },
            valueTransform = { entry: Map.Entry<String?, () -> Any> -> entry.value },
        )
        groupedBindings
            .filterValues { factories: List<() -> Any> -> factories.size > 1 }
            .keys
            .forEach { qualifier: String? ->
                LogcatErrorHandler.warning(
                    "Dao binding registered multiple times and will be ignored: " +
                        "dao=${daoClass.displayName()}, qualifier=${qualifier.displayValue()}."
                )
            }
        val bindings: Map<String?, () -> Any> = groupedBindings
            .filterValues { factories: List<() -> Any> -> factories.size == 1 }
            .mapValues { entry: Map.Entry<String?, List<() -> Any>> -> entry.value.single() }
        if (bindings.size > 1 && bindings.containsKey(null)) {
            LogcatErrorHandler.warning(
                "Dao has mixed qualified and unqualified bindings and will be ignored: " +
                    "dao=${daoClass.displayName()}, qualifiers=${bindings.keys.displayQualifiers()}."
            )
            return emptyMap()
        }
        return bindings
    }

    private fun loadProviderFactories(
        provider: HabitatDaoProvider,
    ): Set<Map.Entry<KClass<*>, Map<String?, () -> Any>>> {
        return try {
            provider.daoFactories.entries
        } catch (exception: RuntimeException) {
            LogcatErrorHandler.warning(
                "Ignoring Habitat Dao provider because its factories could not be loaded: " +
                    "${provider::class.qualifiedName}.",
                exception,
            )
            emptySet()
        } catch (error: LinkageError) {
            LogcatErrorHandler.warning(
                "Ignoring Habitat Dao provider because its factories could not be linked: " +
                    "${provider::class.qualifiedName}.",
                error,
            )
            emptySet()
        }
    }

    private fun KClass<*>.displayName(): String {
        return qualifiedName ?: toString()
    }

    private fun String?.displayValue(): String {
        return this ?: "<unqualified>"
    }

    private fun Set<String?>.displayQualifiers(): String {
        return sortedWith(compareBy<String?>({ it == null }, { it }))
            .joinToString(prefix = "[", postfix = "]") { qualifier: String? ->
                qualifier.displayValue()
            }
    }
}
