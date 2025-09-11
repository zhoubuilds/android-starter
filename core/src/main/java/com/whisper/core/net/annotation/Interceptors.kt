package com.whisper.core.net.annotation

import okhttp3.Interceptor
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Interceptors(val value: Array<KClass<out Interceptor>>)