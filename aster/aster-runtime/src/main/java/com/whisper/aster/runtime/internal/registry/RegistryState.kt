package com.whisper.aster.runtime.internal.registry

import android.app.Application

/**
 * Aster 完成初始化后发布的只读 Registry 状态.
 *
 * @author whisper
 * @since 2026/07/23
 */
internal class RegistryState(
    val application: Application,
    val routeRegistry: RouteRegistry,
    val capabilityRegistry: CapabilityRegistry
)
