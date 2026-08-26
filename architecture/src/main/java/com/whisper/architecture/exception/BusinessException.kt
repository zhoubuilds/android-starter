package com.whisper.architecture.exception

/**
 * 表示服务端业务错误的异常包装.
 *
 * 该异常只承载服务端返回的错误信息, 不解释具体业务含义.
 *
 * @property message 业务错误信息.
 *
 * @aegis 保护异常类型的公开契约和只承载错误信息而不解释业务语义的边界.
 * @author whisper
 * @since 2026/07/24
 */
data class BusinessException(
    override val message: String?,
) : Exception(message)
