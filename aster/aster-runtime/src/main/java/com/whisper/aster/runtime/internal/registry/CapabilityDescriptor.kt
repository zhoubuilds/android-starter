package com.whisper.aster.runtime.internal.registry

import com.whisper.aster.runtime.Capability

/**
 * 能力注册元数据.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal data class CapabilityDescriptor(
    val name: String,
    val implClass: Class<out Capability>,
    val singleton: Boolean
)
