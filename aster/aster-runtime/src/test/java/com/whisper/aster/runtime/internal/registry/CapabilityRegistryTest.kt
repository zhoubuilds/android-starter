package com.whisper.aster.runtime.internal.registry

import android.app.Application
import com.whisper.aster.runtime.Capability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 验证 CapabilityRegistry 的实例化和类型查询行为.
 *
 * @author whisper
 * @since 2026/07/21
 */
class CapabilityRegistryTest {

    private lateinit var application: Application

    /**
     * 准备每个测试使用的能力上下文.
     */
    @Before
    fun setUp() {
        application = Application()
    }

    /**
     * 单例能力只创建一次, 且初始化时收到当前 Application.
     */
    @Test
    fun singletonCapabilityIsCachedAndInitializedOnce() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.singleton",
                implClass = SingletonTestCapability::class.java,
                singleton = true
            )
        )

        val first: SingletonTestCapability =
            registry.get("test.singleton") as SingletonTestCapability
        val second: SingletonTestCapability =
            registry.get("test.singleton") as SingletonTestCapability

        assertSame(first, second)
        assertSame(application, first.initializedApplication)
        assertEquals(1, first.initializeCount)
    }

    /**
     * 非单例能力每次获取都创建并初始化一个新实例.
     */
    @Test
    fun transientCapabilityCreatesAndInitializesEachInstance() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.transient",
                implClass = TransientTestCapability::class.java,
                singleton = false
            )
        )

        val first: TransientTestCapability =
            registry.get("test.transient") as TransientTestCapability
        val second: TransientTestCapability =
            registry.get("test.transient") as TransientTestCapability

        assertNotSame(first, second)
        assertSame(application, first.initializedApplication)
        assertSame(application, second.initializedApplication)
        assertEquals(1, first.initializeCount)
        assertEquals(1, second.initializeCount)
    }

    /**
     * Runtime 可以通过公开 secondary 无参构造和全默认参数构造创建能力.
     */
    @Test
    fun supportedNoArgConstructorFormsCanBeInstantiated() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.secondary",
                implClass = SecondaryConstructorCapability::class.java,
                singleton = true
            ),
            CapabilityDescriptor(
                name = "test.defaults",
                implClass = DefaultParameterCapability::class.java,
                singleton = true
            )
        )

        val secondary: SecondaryConstructorCapability =
            registry.get("test.secondary") as SecondaryConstructorCapability
        val defaults: DefaultParameterCapability =
            registry.get("test.defaults") as DefaultParameterCapability

        assertEquals("secondary", secondary.value)
        assertEquals("default", defaults.value)
        assertSame(application, secondary.initializedApplication)
        assertSame(application, defaults.initializedApplication)
    }

    /**
     * 类型查询可以返回多个实现, 且结果按能力名稳定排序.
     */
    @Test
    fun typeQueryReturnsAllImplementationsInNameOrder() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.zeta",
                implClass = SingletonTestCapability::class.java,
                singleton = true
            ),
            CapabilityDescriptor(
                name = "test.alpha",
                implClass = TransientTestCapability::class.java,
                singleton = false
            )
        )

        val capabilities: List<TestContract> = registry.get(TestContract::class.java)

        assertEquals(2, capabilities.size)
        assertEquals(TransientTestCapability::class.java, capabilities[0]::class.java)
        assertEquals(SingletonTestCapability::class.java, capabilities[1]::class.java)
    }

    /**
     * 单个类型查询返回按能力名排序的第一个实现和完整匹配数量.
     */
    @Test
    fun singleTypeResolutionReturnsFirstImplementationAndMatchCount() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.zeta",
                implClass = SingletonTestCapability::class.java,
                singleton = true
            ),
            CapabilityDescriptor(
                name = "test.alpha",
                implClass = TransientTestCapability::class.java,
                singleton = false
            )
        )

        val resolution: CapabilityRegistry.Resolution<TestContract> =
            registry.resolveFirst(TestContract::class.java)

        assertEquals(2, resolution.implementationCount)
        assertEquals("test.alpha", resolution.selectedName)
        assertEquals(TransientTestCapability::class.java, resolution.capability?.javaClass)
    }

    /**
     * 类型没有匹配实现时返回合法的空解析结果.
     */
    @Test
    fun singleTypeResolutionReturnsEmptyResultWhenNoImplementationMatches() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.unmatched",
                implClass = UnmatchedConstructorCapability::class.java,
                singleton = true
            )
        )

        val resolution: CapabilityRegistry.Resolution<TestContract> =
            registry.resolveFirst(TestContract::class.java)

        assertNull(resolution.capability)
        assertEquals(0, resolution.implementationCount)
        assertNull(resolution.selectedName)
    }

    /**
     * Capability 构造函数抛出的业务异常会从反射包装中解包并原样传播.
     */
    @Test
    fun constructorFailureIsPropagatedWithoutReflectionWrapper() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                name = "test.throwing-constructor",
                implClass = ThrowingConstructorCapability::class.java,
                singleton = true
            )
        )

        val exception: ConstructorFailureException = assertThrows(
            ConstructorFailureException::class.java
        ) {
            registry.get("test.throwing-constructor")
        }

        assertEquals("Capability constructor failed.", exception.message)
    }

    /**
     * 非法 Capability 目标只在首次获取时抛出异常.
     */
    @Test
    fun invalidCapabilityTargetsFailWhenResolved() {
        @Suppress("UNCHECKED_CAST")
        val wrongType: Class<out Capability> = String::class.java as Class<out Capability>
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor("test.wrong", wrongType, true),
            CapabilityDescriptor("test.abstract", AbstractTestCapability::class.java, true),
            CapabilityDescriptor("test.private", PrivateTestCapability::class.java, true),
            CapabilityDescriptor(
                "test.constructor",
                ConstructorTestCapability::class.java,
                true
            )
        )

        assertThrows(IllegalStateException::class.java) {
            registry.get("test.wrong")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.get("test.abstract")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.get("test.private")
        }
        assertThrows(IllegalStateException::class.java) {
            registry.get("test.constructor")
        }
    }

    /**
     * 类型查询会检查扫描到的全部 Capability 映射.
     */
    @Test
    fun typeQueryValidatesCapabilityMappings() {
        @Suppress("UNCHECKED_CAST")
        val wrongType: Class<out Capability> = String::class.java as Class<out Capability>
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor("test.wrong", wrongType, true)
        )

        assertThrows(IllegalStateException::class.java) {
            registry.get(TestContract::class.java)
        }
    }

    /**
     * 类型查询不会检查或实例化不匹配的合法 Capability.
     */
    @Test
    fun typeQuerySkipsUnmatchedCapabilityWithoutConstructorValidation() {
        val registry: CapabilityRegistry = createRegistry(
            CapabilityDescriptor(
                "test.unmatched",
                UnmatchedConstructorCapability::class.java,
                true
            )
        )

        val capabilities: List<TestContract> = registry.get(TestContract::class.java)

        assertTrue(capabilities.isEmpty())
    }

    private fun createRegistry(
        vararg descriptors: CapabilityDescriptor
    ): CapabilityRegistry {
        val descriptorsByName: Map<String, CapabilityDescriptor> =
            descriptors.associateBy(CapabilityDescriptor::name)
        return CapabilityRegistry(application, descriptorsByName)
    }

    /**
     * 能力契约用于验证同一接口支持多个实现.
     */
    interface TestContract : Capability

    /**
     * 测试单例能力实现.
     *
     * @author whisper
     * @since 2026/07/21
     */
    class SingletonTestCapability : TestContract {

        var initializedApplication: Application? = null
        var initializeCount: Int = 0

        override fun initialize(application: Application) {
            initializedApplication = application
            initializeCount += 1
        }
    }

    /**
     * 测试非单例能力实现.
     *
     * @author whisper
     * @since 2026/07/21
     */
    class TransientTestCapability : TestContract {

        var initializedApplication: Application? = null
        var initializeCount: Int = 0

        override fun initialize(application: Application) {
            initializedApplication = application
            initializeCount += 1
        }
    }

    /**
     * 通过公开 secondary 无参构造创建的测试能力.
     */
    class SecondaryConstructorCapability private constructor(
        val value: String
    ) : TestContract {

        var initializedApplication: Application? = null

        constructor() : this("secondary")

        override fun initialize(application: Application) {
            initializedApplication = application
        }
    }

    /**
     * 通过全默认参数构造生成 JVM 无参构造的测试能力.
     */
    class DefaultParameterCapability(
        val value: String = "default"
    ) : TestContract {

        var initializedApplication: Application? = null

        override fun initialize(application: Application) {
            initializedApplication = application
        }
    }

    /**
     * 抽象 Capability 测试目标.
     */
    abstract class AbstractTestCapability : Capability

    /**
     * 非公开 Capability 测试目标.
     */
    private class PrivateTestCapability : Capability {

        override fun initialize(application: Application) = Unit
    }

    /**
     * 没有公开无参构造函数的 Capability 测试目标.
     */
    class ConstructorTestCapability(
        private val value: String
    ) : Capability {

        override fun initialize(application: Application) = Unit
    }

    /**
     * 与 TestContract 不匹配且没有无参构造函数的合法 Capability.
     */
    class UnmatchedConstructorCapability(
        private val value: String
    ) : Capability {

        override fun initialize(application: Application) = Unit
    }

    /**
     * 构造时抛出业务异常的 Capability 测试目标.
     */
    class ThrowingConstructorCapability : Capability {

        init {
            throw ConstructorFailureException("Capability constructor failed.")
        }

        override fun initialize(application: Application) = Unit
    }

    /**
     * Capability 构造失败测试异常.
     */
    class ConstructorFailureException(message: String) : IllegalStateException(message)
}
