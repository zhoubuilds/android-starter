package com.whisper.feature.project.api

import com.whisper.aster.runtime.Capability

/**
 * 提供当前 Project 对应的公开源码仓库地址.
 *
 * 地址必须是可交给外部浏览器打开的绝对 HTTPS URL, 不依赖 Project 页的网络或缓存状态.
 * 最终应用依赖图中应只有一个实现, 调用方通过 Aster 按契约类型解析.
 *
 * @author whisper
 * @since 2026/10/13
 */
interface ProjectRepositoryCapability : Capability {

    /**
     * 当前 Project 对应的公开源码仓库地址.
     */
    val repositoryUrl: String
}
