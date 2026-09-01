package com.whisper.aster.runtime

import android.app.Application
import com.whisper.aster.runtime.internal.registry.CapabilityDescriptor
import com.whisper.aster.runtime.internal.registry.CapabilityRegistry
import com.whisper.aster.runtime.internal.registry.RegistryState
import com.whisper.aster.runtime.internal.registry.RouteRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * 验证 Aster 公开能力类型解析契约.
 *
 * @author whisper
 * @since 2026/09/01
 */
class AsterCapabilityResolutionTest {

    private lateinit var registryStateField: Field

    @Before
    fun setUp() {
        registryStateField = Aster::class.java.getDeclaredField("registryState").also { field: Field ->
            field.isAccessible = true
            field.set(Aster, null)
        }
    }

    @After
    fun tearDown() {
        registryStateField.set(Aster, null)
    }

    @Test
    fun resolveByNameReturnsTypedImplementation() {
        installCapabilities(
            CapabilityDescriptor(
                name = "test.unique",
                implClass = UniqueTestCapability::class.java,
                singleton = true
            )
        )

        val capability: TestContract? = Aster.resolve<TestContract>("test.unique")

        assertTrue(capability is UniqueTestCapability)
    }

    @Test
    fun resolveByNameFailsWithActionableDetailsWhenImplementationTypeMismatches() {
        installCapabilities(
            CapabilityDescriptor(
                name = "test.mismatch",
                implClass = MismatchedTestCapability::class.java,
                singleton = true
            )
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            Aster.resolve<TestContract>("test.mismatch")
        }

        assertEquals(
            "Capability type mismatch for name 'test.mismatch': requested type " +
                "'${TestContract::class.java.name}', but registered implementation type is " +
                "'${MismatchedTestCapability::class.java.name}'. Check the capability name " +
                "constant and requested contract, or use Aster.resolveCapability(name) only when " +
                "dynamic type handling is intentional.",
            exception.message
        )
    }

    @Test
    fun resolveCapabilityByNameReturnsDynamicImplementation() {
        installCapabilities(
            CapabilityDescriptor(
                name = "test.dynamic",
                implClass = UniqueTestCapability::class.java,
                singleton = true
            )
        )

        val capability: Capability? = Aster.resolveCapability("test.dynamic")

        assertTrue(capability is UniqueTestCapability)
    }

    @Test
    fun resolveByTypeReturnsUniqueImplementation() {
        installCapabilities(
            CapabilityDescriptor(
                name = "test.unique",
                implClass = UniqueTestCapability::class.java,
                singleton = true
            )
        )

        val capability: TestContract? = Aster.resolve(TestContract::class.java)

        assertTrue(capability is UniqueTestCapability)
    }

    @Test
    fun resolveByTypeFailsWithActionableDetailsWhenImplementationsAreAmbiguous() {
        installCapabilities(
            CapabilityDescriptor(
                name = "test.zeta",
                implClass = OtherTestCapability::class.java,
                singleton = true
            ),
            CapabilityDescriptor(
                name = "test.alpha",
                implClass = AmbiguousTestCapability::class.java,
                singleton = true
            )
        )

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            Aster.resolve(TestContract::class.java)
        }

        assertEquals(
            "Ambiguous capability resolution for type '${TestContract::class.java.name}': " +
                "expected at most one implementation but found 2. Matching capability names " +
                "ordered by name: ['test.alpha', 'test.zeta']. Use the type-safe named resolve " +
                "API to select one explicitly, or use resolveAll to retrieve all implementations.",
            exception.message
        )
    }

    private fun installCapabilities(
        vararg descriptors: CapabilityDescriptor
    ) {
        val application: Application = Application()
        val descriptorsByName: Map<String, CapabilityDescriptor> =
            descriptors.associateBy(CapabilityDescriptor::name)
        registryStateField.set(
            Aster,
            RegistryState(
                application = application,
                routeRegistry = RouteRegistry(emptyMap()),
                capabilityRegistry = CapabilityRegistry(application, descriptorsByName)
            )
        )
    }

    interface TestContract : Capability

    interface MismatchedContract : Capability

    class UniqueTestCapability : TestContract {

        override fun initialize(application: Application) = Unit
    }

    class OtherTestCapability : TestContract {

        override fun initialize(application: Application) = Unit
    }

    class AmbiguousTestCapability : TestContract {

        init {
            throw AssertionError("Ambiguous capability must not be instantiated.")
        }

        override fun initialize(application: Application) = Unit
    }

    class MismatchedTestCapability : MismatchedContract {

        init {
            throw AssertionError("Mismatched capability must not be instantiated.")
        }

        override fun initialize(application: Application) = Unit
    }
}
