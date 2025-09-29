package com.whisper.architecture.exception

data class BusinessException(
    val code: Int?,
    override val message: String?,
) : Exception()
