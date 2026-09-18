package com.whisper.feature.project.data.network

import com.whisper.architecture.network.annotation.ApplicationInterceptors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.http.Headers

/**
 * 验证 GitHub API 的接口级网络能力声明.
 *
 * @author whisper
 * @since 2026/09/15
 */
class GithubProjectApiTest {

    @Test
    fun apiDeclaresGithubRoutingAndSystemParametersInterceptors() {
        val annotation: ApplicationInterceptors? =
            GithubProjectApi::class.java.getAnnotation(ApplicationInterceptors::class.java)

        assertEquals(
            listOf(
                GithubEndpointRoutingInterceptor::class,
                GithubSystemParametersInterceptor::class,
            ),
            annotation?.value?.toList(),
        )
    }

    @Test
    fun getProjectDoesNotDeclareMethodLevelHeaders() {
        val getProjectMethod = GithubProjectApi::class.java.declaredMethods
            .single { method -> method.name == "getProject" }

        assertNull(getProjectMethod.getAnnotation(Headers::class.java))
    }
}
