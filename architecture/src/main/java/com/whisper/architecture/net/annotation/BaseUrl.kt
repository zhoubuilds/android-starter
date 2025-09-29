package com.whisper.architecture.net.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class BaseUrl(val value: String)
