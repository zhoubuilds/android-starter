package com.whisper.aster.runtime

import com.whisper.aster.runtime.internal.RoutePathValidator
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Postcard 创建入口和运行时路径校验.
 *
 * @author whisper
 * @since 2026/07/23
 */
class PostcardTest {

    /**
     * 合法路径通过运行时格式校验.
     */
    @Test
    fun validRoutePathsPassValidation() {
        val paths: List<String> = listOf(
            "/feature/page",
            "/feature/page_1",
            "/feature/page/detail2"
        )

        paths.forEach { path: String ->
            assertNull(RoutePathValidator.validationError(path))
        }
    }

    /**
     * 未初始化时调用 Aster 优先抛出生命周期异常.
     */
    @Test
    fun buildBeforeInitializationThrows() {
        assertThrows(IllegalStateException::class.java) {
            Aster.build("/feature/page")
        }
        assertThrows(IllegalStateException::class.java) {
            Aster.build("/invalid")
        }
    }

    /**
     * 非法路径返回不可导航的 Postcard.
     */
    @Test
    fun invalidRoutePathsReturnSafePostcards() {
        val paths: List<String> = listOf(
            "",
            " ",
            "/",
            "/feature",
            "feature/page",
            "/Feature/page",
            "/feature/Page",
            "/feature/1page",
            "/feature/page/",
            "/feature//page",
            "/feature/page-name"
        )
        paths.forEach { path: String ->
            assertNotNull(RoutePathValidator.validationError(path))
            val postcard: Postcard = Postcard.create(path, valid = false)
            assertFalse(postcard.isRegistered())
            assertFalse(postcard.navigate())
        }
    }

    /**
     * Postcard 构造函数不会作为公共 JVM API 暴露.
     */
    @Test
    fun postcardConstructorIsPrivate() {
        val sourceConstructors = Postcard::class.java.declaredConstructors
            .filterNot { constructor -> constructor.isSynthetic }

        assertEquals(1, sourceConstructors.size)
        assertTrue(Modifier.isPrivate(sourceConstructors.single().modifiers))
    }
}
