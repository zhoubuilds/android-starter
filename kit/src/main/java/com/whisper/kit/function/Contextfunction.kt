package com.whisper.kit.function

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 判断当前 Context 或其包装链中是否包含 Activity Context.
 *
 * Android 的 ContextThemeWrapper、ContextWrapper 等实现可能包装实际的 Activity Context,
 * 因此不能只使用 `this is Activity` 判断. 该方法沿 [ContextWrapper.baseContext] 链查找,
 * 并使用对象身份记录已访问的 Context, 防止自定义 ContextWrapper 形成循环.
 *
 * @return 当前 Context 或其包装链包含 Activity 时返回 true.
 */
fun Context.isActivityContext(): Boolean {
    val visitedContexts: MutableSet<Context> =
        Collections.newSetFromMap(IdentityHashMap<Context, Boolean>())
    var currentContext: Context? = this

    while (currentContext != null && visitedContexts.add(currentContext)) {
        if (currentContext is Activity) {
            return true
        }
        val contextWrapper: ContextWrapper = currentContext as? ContextWrapper
            ?: return false
        currentContext = contextWrapper.baseContext
    }
    return false
}
