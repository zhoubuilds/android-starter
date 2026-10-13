package com.whisper.feature.project

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 Project 对外提供的源码仓库入口.
 *
 * @author whisper
 * @since 2026/10/13
 */
class ProjectRepositoryCapabilityImplTest {

    @Test
    fun repositoryUrl_returnsStableHttpsAddress() {
        assertEquals(
            "https://github.com/zhoubuilds/android-starter",
            ProjectRepositoryCapabilityImpl().repositoryUrl,
        )
    }
}
