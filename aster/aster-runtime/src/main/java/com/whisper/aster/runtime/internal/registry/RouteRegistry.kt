package com.whisper.aster.runtime.internal.registry

import android.app.Activity
import java.lang.reflect.Modifier

/**
 * 提供已冻结 Activity 路由的只读查询.
 *
 * @aegis 保护只读路由快照和目标 Activity 的运行时防御性校验语义.
 *
 * @author whisper
 * @since 2026/07/20
 */
internal class RouteRegistry(
    routes: Map<String, Class<out Activity>>
) {

    private val routes: Map<String, Class<out Activity>> = routes.toMap()

    fun contains(path: String): Boolean {
        return routes.containsKey(path)
    }

    fun find(path: String): Class<out Activity>? {
        val activityClass: Class<out Activity> = routes[path] ?: return null
        return validateTarget(path, activityClass)
    }

    private fun validateTarget(
        path: String,
        activityClass: Class<out Activity>
    ): Class<out Activity> {
        if (!Activity::class.java.isAssignableFrom(activityClass)) {
            throw IllegalStateException(
                "Invalid route target for path '$path': '${activityClass.name}' must extend " +
                    "android.app.Activity."
            )
        }
        if (!Modifier.isPublic(activityClass.modifiers)) {
            throw IllegalStateException(
                "Invalid route target for path '$path': '${activityClass.name}' must be public."
            )
        }
        if (Modifier.isAbstract(activityClass.modifiers)) {
            throw IllegalStateException(
                "Invalid route target for path '$path': '${activityClass.name}' must be a " +
                    "concrete Activity class."
            )
        }
        if (activityClass.isMemberClass && !Modifier.isStatic(activityClass.modifiers)) {
            throw IllegalStateException(
                "Invalid route target for path '$path': '${activityClass.name}' must not be a " +
                    "non-static inner class."
            )
        }
        return activityClass
    }
}
