package com.whisper.habitat.runtime

import android.app.Application
import com.whisper.habitat.runtime.internal.LogcatErrorHandler
import com.whisper.habitat.runtime.internal.registry.ManifestRegistryLoader
import com.whisper.habitat.runtime.registry.HabitatDaoProvider
import com.whisper.habitat.runtime.registry.HabitatRegistry
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass

/**
 * Habitat Dao 工厂.
 *
 * 业务模块通过 Dao 类型获取 Dao 实例, 不感知 Dao 最终归属的 RoomDatabase. HabitatFactory 只安装编译期生成的静态
 * Dao binding, 不负责创建 RoomDatabase、缓存 Dao 或在动态模块安装后追加 binding.
 *
 * @aegis 保护初始化发布, 按类型获取 Dao, 未初始化失败和运行时错误处理语义.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持限定 Dao 获取、唯一绑定回退和多绑定歧义降级.
 * @aegis-audit 2026-08-31 | whisper | 经授权使用 @Volatile 可空只读 Map 发布 Dao binding 快照.
 * @aegis-audit 2026-09-01 | whisper | 经授权覆盖全部 Exception 工厂失败并明确静态注册与初始化边界.
 * @aegis-audit 2026-09-01 | whisper | 经授权确保 Dao 工厂的 CancellationException 原样传播.
 * @aegis-audit 2026-09-01 | whisper | 经授权区分合法查找未命中与 Registry、Provider、Dao 工厂完整性失败.
 * @aegis-audit 2026-09-01 | whisper | 经授权允许 Registry 类缺失时提示 compiler 配置并安装空 binding 快照.
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
     * 每个进程只安装第一次完成加载的 binding 快照, 后续调用保持幂等. Manifest metadata 或其指向的 Registry 类缺失时安装
     * 空快照, 后续调用不会自动重试; 已找到 Registry 的链接、类型、构造或 binding 完整性校验失败时不发布快照并直接抛出
     * 异常. 本方法只安装延迟工厂, 不访问或初始化 RoomDatabase; 调用方需要在第一次成功命中 [get] 前准备好数据库实例.
     *
     * @param application 当前进程的 Application.
     * @exception IllegalStateException Registry、Provider 或 Dao binding 完整性校验失败时抛出.
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
     * 必须先调用 [initialize]. 只有一个绑定时执行其延迟工厂; 存在多个绑定时记录 warning 并返回 `null`, 不推断默认
     * binding. 数据库实例必须在工厂执行前可用.
     *
     * @param daoClass Dao 类型.
     * @return Dao 实例; 未注册或多绑定歧义时返回 `null`.
     * @exception CancellationException Dao 工厂通过取消异常终止时原样抛出.
     * @exception IllegalStateException 尚未调用 [initialize]、Dao 工厂执行失败或返回类型不匹配时抛出.
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
     * 必须先调用 [initialize]. qualifier 按区分大小写的原始字符串精确匹配, 不执行 trim 或其它规范化. 只执行完全匹配
     * 当前 Dao 类型和 qualifier 的延迟工厂, 数据库实例必须在工厂执行前可用.
     *
     * @param daoClass Dao 类型.
     * @param qualifier 非空白的稳定 Dao 限定符.
     * @return Dao 实例; Dao 或 qualifier 不匹配时返回 `null`.
     * @exception IllegalArgumentException qualifier 为空白时抛出.
     * @exception CancellationException Dao 工厂通过取消异常终止时原样抛出.
     * @exception IllegalStateException 尚未调用 [initialize]、Dao 工厂执行失败或返回类型不匹配时抛出.
     */
    fun <T : Any> get(
        daoClass: KClass<T>,
        qualifier: String,
    ): T? {
        require(qualifier.isNotBlank()) {
            "Dao qualifier must not be blank: dao=${daoClass.displayName()}."
        }
        val daoBindings: Map<KClass<*>, Map<String?, () -> Any>> = requireInitializedBindings()
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
    ): T {
        val dao: Any = try {
            daoFactory()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Dao factory failed: dao=${daoClass.displayName()}, qualifier=${qualifier.displayValue()}.",
                exception,
            )
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Dao factory could not be linked: dao=${daoClass.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}.",
                error,
            )
        }
        if (!daoClass.java.isInstance(dao)) {
            throw IllegalStateException(
                "Dao type mismatch: expected=${daoClass.displayName()}, actual=${dao::class.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}."
            )
        }
        return try {
            daoClass.java.cast(dao)
        } catch (exception: ClassCastException) {
            throw IllegalStateException(
                "Dao cast failed: expected=${daoClass.displayName()}, actual=${dao::class.displayName()}, " +
                    "qualifier=${qualifier.displayValue()}.",
                exception,
            )
        }
    }

    /**
     * 按 Dao 类型获取实例.
     *
     * 行为边界与 [get] 的 `KClass` 重载一致.
     *
     * @return Dao 实例, 未注册或多绑定歧义时返回 `null`.
     * @exception CancellationException Dao 工厂通过取消异常终止时原样抛出.
     * @exception IllegalStateException 尚未调用 [initialize]、Dao 工厂执行失败或返回类型不匹配时抛出.
     */
    inline fun <reified T : Any> get(): T? {
        return get(T::class)
    }

    /**
     * 按 Dao 类型和限定符获取实例.
     *
     * 行为边界与 [get] 的 `KClass` 和 qualifier 重载一致.
     *
     * @param qualifier 非空白的稳定 Dao 限定符.
     * @return Dao 实例, Dao 或 qualifier 不匹配时返回 `null`.
     * @exception IllegalArgumentException qualifier 为空白时抛出.
     * @exception CancellationException Dao 工厂通过取消异常终止时原样抛出.
     * @exception IllegalStateException 尚未调用 [initialize]、Dao 工厂执行失败或返回类型不匹配时抛出.
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
        return ManifestRegistryLoader.load(
            application = application,
            warning = LogcatErrorHandler::warning,
        )
    }

    private fun loadRegistryProviders(registry: HabitatRegistry): List<HabitatDaoProvider> {
        return try {
            registry.providers()
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "Failed to load Habitat Registry providers: registry=${registry::class.displayName()}.",
                exception,
            )
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Failed to link Habitat Registry providers: registry=${registry::class.displayName()}.",
                error,
            )
        }
    }

    private fun installProviders(providers: List<HabitatDaoProvider>) {
        val groupedBindings: Map<KClass<*>, List<ProviderDaoBinding>> = providers
            .flatMap { provider: HabitatDaoProvider ->
                loadProviderBindings(provider)
            }
            .groupBy { binding: ProviderDaoBinding -> binding.daoClass }
        val installedBindings: Map<KClass<*>, Map<String?, () -> Any>> = groupedBindings
            .mapValues { entry: Map.Entry<KClass<*>, List<ProviderDaoBinding>> ->
                mergeDaoBindings(
                    daoClass = entry.key,
                    bindings = entry.value,
                )
            }
        daoBindings = installedBindings
    }

    private fun mergeDaoBindings(
        daoClass: KClass<*>,
        bindings: List<ProviderDaoBinding>,
    ): Map<String?, () -> Any> {
        val blankBindings: List<ProviderDaoBinding> = bindings.filter { binding: ProviderDaoBinding ->
            binding.qualifier?.isBlank() == true
        }
        check(blankBindings.isEmpty()) {
            "Dao binding qualifier must not be blank: dao=${daoClass.displayName()}, " +
                "providers=${blankBindings.providerNames()}."
        }
        val groupedBindings: Map<String?, List<ProviderDaoBinding>> = bindings.groupBy { binding ->
            binding.qualifier
        }
        val duplicateBindings: Map.Entry<String?, List<ProviderDaoBinding>>? = groupedBindings.entries
            .firstOrNull { entry: Map.Entry<String?, List<ProviderDaoBinding>> -> entry.value.size > 1 }
        check(duplicateBindings == null) {
            "Dao binding registered multiple times: dao=${daoClass.displayName()}, " +
                "qualifier=${duplicateBindings?.key.displayValue()}, " +
                "providers=${duplicateBindings?.value.orEmpty().providerNames()}."
        }
        check(groupedBindings.size <= 1 || !groupedBindings.containsKey(null)) {
            "Dao has mixed qualified and unqualified bindings: dao=${daoClass.displayName()}, " +
                "qualifiers=${groupedBindings.keys.displayQualifiers()}, providers=${bindings.providerNames()}."
        }
        return groupedBindings.mapValues { entry: Map.Entry<String?, List<ProviderDaoBinding>> ->
            entry.value.single().daoFactory
        }
    }

    private fun loadProviderBindings(
        provider: HabitatDaoProvider,
    ): List<ProviderDaoBinding> {
        val providerName: String = provider::class.displayName()
        return try {
            provider.daoFactories.flatMap { entry: Map.Entry<KClass<*>, Map<String?, () -> Any>> ->
                entry.value.map { factoryEntry: Map.Entry<String?, () -> Any> ->
                    ProviderDaoBinding(
                        daoClass = entry.key,
                        qualifier = factoryEntry.key,
                        daoFactory = factoryEntry.value,
                        providerName = providerName,
                    )
                }
            }
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "Failed to load Habitat Dao Provider bindings: provider=$providerName.",
                exception,
            )
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "Failed to link Habitat Dao Provider bindings: provider=$providerName.",
                error,
            )
        }
    }

    private fun List<ProviderDaoBinding>.providerNames(): String {
        return map { binding: ProviderDaoBinding -> binding.providerName }
            .distinct()
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
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

    private data class ProviderDaoBinding(
        val daoClass: KClass<*>,
        val qualifier: String?,
        val daoFactory: () -> Any,
        val providerName: String,
    )
}
