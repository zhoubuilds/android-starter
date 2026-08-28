package com.whisper.architecture.ui.component

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 Architecture UI 组件不泄漏渲染依赖并封闭绑定不变量. */
class ArchitectureUiComponentContractTest {

    @Test
    fun constructor_doesNotRequireRenderingDependencies() {
        val constructorParameterCounts: List<Int> =
            ArchitectureUiComponent::class.java.declaredConstructors
                .map { constructor -> constructor.parameterCount }

        assertEquals(listOf(0), constructorParameterCounts)
    }

    @Test
    fun renderingCallbacks_areProtectedExtensionPoints() {
        val callbackNames: Set<String> = setOf(
            "onActiveOperationCountChanged",
            "handleNotice",
        )
        val callbacks: List<Method> = ArchitectureUiComponent::class.java.declaredMethods
            .filter { method: Method -> method.name in callbackNames }

        assertEquals(callbackNames, callbacks.map(Method::getName).toSet())
        callbacks.forEach { method: Method ->
            assertTrue(Modifier.isProtected(method.modifiers))
            assertTrue(Modifier.isAbstract(method.modifiers))
        }
    }

    @Test
    fun bind_isPublicAndFinal() {
        val bindMethod: Method = ArchitectureUiComponent::class.java.declaredMethods
            .single { method: Method -> method.name == "bind" }

        assertTrue(Modifier.isPublic(bindMethod.modifiers))
        assertTrue(Modifier.isFinal(bindMethod.modifiers))
    }
}
