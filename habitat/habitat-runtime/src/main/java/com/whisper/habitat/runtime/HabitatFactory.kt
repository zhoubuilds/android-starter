package com.whisper.habitat.runtime

import android.app.Application
import com.whisper.habitat.runtime.internal.LogcatErrorHandler
import com.whisper.habitat.runtime.internal.registry.ManifestRegistryLoader
import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import com.whisper.habitat.runtime.registry.HabitatRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * Habitat Dao 工厂.
 *
 * 业务模块通过 Dao 类型获取 Dao 实例, 不感知 Dao 最终归属的 RoomDatabase.
 *
 * @aegis 保护初始化发布, 按类型获取 Dao, 未初始化失败和可恢复加载失败语义.
 *
 * @author whisper
 * @since 2026/07/27
 */
object HabitatFactory {

    /**
     * 当前 Dao Provider 状态.
     */
    private val stateReference: AtomicReference<FactoryState?> = AtomicReference(null)

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
        if (stateReference.get() != null) {
            return
        }
        synchronized(initializeLock) {
            if (stateReference.get() != null) {
                return
            }
            val providers: List<HabitatDaoProvider> = loadGeneratedProviders(application)
            installProviders(providers)
        }
    }

    /**
     * 按 Dao 类型获取实例.
     *
     * @param daoClass Dao 类型.
     * @return Dao 实例, 找不到时返回 null.
     */
    fun <T : Any> get(daoClass: KClass<T>): T? {
        val state: FactoryState = requireInitializedState()
        val daoFactory: (() -> Any)? = state.daoFactories[daoClass]
        if (daoFactory == null) {
            LogcatErrorHandler.warning("Dao not found: ${daoClass.qualifiedName}.")
            return null
        }
        val dao: Any = try {
            daoFactory()
        } catch (exception: RuntimeException) {
            LogcatErrorHandler.warning(
                "Dao factory failed: ${daoClass.qualifiedName}.",
                exception,
            )
            return null
        } catch (error: LinkageError) {
            LogcatErrorHandler.warning(
                "Dao factory could not be linked: ${daoClass.qualifiedName}.",
                error,
            )
            return null
        }
        if (!daoClass.java.isInstance(dao)) {
            LogcatErrorHandler.warning(
                "Dao type mismatch. Expected: ${daoClass.qualifiedName}, actual: " +
                    "${dao::class.qualifiedName}."
            )
            return null
        }
        return try {
            daoClass.java.cast(dao)
        } catch (exception: ClassCastException) {
            LogcatErrorHandler.warning(
                "Dao cast failed. Expected: ${daoClass.qualifiedName}, actual: " +
                    "${dao::class.qualifiedName}.",
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

    private fun loadGeneratedProviders(application: Application): List<HabitatDaoProvider> {
        val registries: List<HabitatRegistry> = loadGeneratedRegistries(application)
        return registries.flatMap { registry: HabitatRegistry ->
            loadRegistryProviders(registry)
        }
    }

    /**
     * 获取已发布的 Provider 状态.
     *
     * 如果其它线程正在初始化, 会等待同一把初始化锁释放后再读取状态, 避免初始化过程中的短暂空值被误判为未初始化.
     *
     * @return 已发布的 Provider 状态.
     * @exception IllegalStateException 从未调用初始化方法时抛出.
     */
    private fun requireInitializedState(): FactoryState {
        val currentState: FactoryState? = stateReference.get()
        if (currentState != null) {
            return currentState
        }
        val initializedState: FactoryState? = synchronized(initializeLock) {
            stateReference.get()
        }
        return checkNotNull(initializedState) {
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
        val groupedFactories: Map<KClass<*>, List<() -> Any>> = providers
            .flatMap { provider: HabitatDaoProvider ->
                loadProviderFactories(provider)
            }
            .groupBy(
                keySelector = { entry: Map.Entry<KClass<*>, () -> Any> -> entry.key },
                valueTransform = { entry: Map.Entry<KClass<*>, () -> Any> -> entry.value },
            )
        val duplicateDaoNames: String = groupedFactories
            .filterValues { factories: List<() -> Any> -> factories.size > 1 }
            .keys
            .joinToString { daoClass: KClass<*> ->
                daoClass.qualifiedName ?: daoClass.toString()
            }
        if (duplicateDaoNames.isNotEmpty()) {
            LogcatErrorHandler.warning(
                "Dao registered in multiple Habitat databases and will be ignored: $duplicateDaoNames."
            )
        }
        val daoFactories: Map<KClass<*>, () -> Any> = groupedFactories
            .filterValues { factories: List<() -> Any> -> factories.size == 1 }
            .mapValues { entry: Map.Entry<KClass<*>, List<() -> Any>> ->
                entry.value.first()
            }
        stateReference.set(FactoryState(daoFactories = daoFactories))
    }

    private fun loadProviderFactories(provider: HabitatDaoProvider): Set<Map.Entry<KClass<*>, () -> Any>> {
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

    /**
     * Dao Provider 状态.
     *
     * @property daoFactories 已安装的 Dao 工厂表.
     */
    private data class FactoryState(
        val daoFactories: Map<KClass<*>, () -> Any>,
    )
}
