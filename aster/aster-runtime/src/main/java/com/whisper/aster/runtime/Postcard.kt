package com.whisper.aster.runtime

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.os.PersistableBundle
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat
import java.io.Serializable
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 一次 Activity 路由请求.
 *
 * 保存目标路由, Intent 参数和启动选项, 并负责构建或发起 Activity 导航.
 *
 * @aegis 保护公开 API, Intent 快照, flags/options 和各导航入口的行为语义.
 * @author whisper
 * @since 2026/07/22
 */
class Postcard private constructor(
    private val path: String,
    private val valid: Boolean
) {

    private val extras: Bundle = Bundle()
    private var flags: Int = 0
    private var options: ActivityOptionsCompat? = null

    /**
     * 判断当前路径是否已注册.
     *
     * 该方法只检查路径是否存在于路由表中, 不校验目标 Activity 的结构是否合法.
     *
     * @return 当前路径已注册时返回 true.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun isRegistered(): Boolean {
        if (!valid) {
            return false
        }
        return Aster.containsRoute(path)
    }

    /**
     * 构建当前路由的 Intent.
     *
     * 该方法每次调用都会创建新的 Intent 快照, 不根据 Context 自动添加任务栈 Flag,
     * 也不将 Activity 启动选项写入 Intent.
     *
     * @param context 用于构建显式 Intent 的 Context, 必须属于当前 Aster 初始化的应用.
     * @return 目标 Intent, 路径未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun createIntent(context: Context): Intent? {
        if (!valid) {
            return null
        }
        val activityClass: Class<out Activity>? = Aster.findRoute(path)
        if (activityClass == null) {
            Aster.reportError("Route not found: $path")
            return null
        }

        return Intent(context, activityClass).also {
            if (!extras.isEmpty) {
                it.putExtras(extras)
            }
            if (flags != 0) {
                it.addFlags(flags)
            }
        }
    }

    /**
     * 使用 Aster 持有的 Application 发起导航.
     *
     * 由于 Application 不是 Activity Context, 启动时会自动添加 `FLAG_ACTIVITY_NEW_TASK`.
     *
     * @return 成功提交 Activity 启动请求时返回 true.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     * @exception ActivityNotFoundException Android 无法启动目标 Activity 时抛出.
     * @exception SecurityException Android 拒绝启动目标 Activity 时抛出.
     */
    fun navigate(): Boolean {
        if (!valid) {
            return false
        }
        return navigate(Aster.application)
    }

    /**
     * 使用指定 Context 发起导航.
     *
     * 非 Activity Context 启动时会自动添加 `FLAG_ACTIVITY_NEW_TASK`.
     *
     * @param context 用于启动 Activity 的 Context, 必须属于当前 Aster 初始化的应用.
     * @return 成功提交 Activity 启动请求时返回 true.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     * @exception ActivityNotFoundException Android 无法启动目标 Activity 时抛出.
     * @exception SecurityException Android 拒绝启动目标 Activity 时抛出.
     */
    fun navigate(context: Context): Boolean {
        val intent: Intent = createIntent(context) ?: return false
        if (!context.containsActivity()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent, options?.toBundle())
        return true
    }

    /**
     * 使用 Activity Result Launcher 发起导航.
     *
     * 该方法不会自动添加 `FLAG_ACTIVITY_NEW_TASK`.
     *
     * @param launcher 已注册的 Activity Result Launcher.
     * @return 成功提交 Activity Result 启动请求时返回 true.
     * @exception IllegalStateException Aster 尚未初始化或 Launcher 生命周期状态不合法时抛出.
     * @exception ActivityNotFoundException Android 无法启动目标 Activity 时抛出.
     * @exception SecurityException Android 拒绝启动目标 Activity 时抛出.
     */
    fun launch(launcher: ActivityResultLauncher<Intent>): Boolean {
        if (!valid) {
            return false
        }
        return launch(Aster.application, launcher)
    }

    /**
     * 使用指定 Context 构建 Intent, 并通过 Activity Result Launcher 发起导航.
     *
     * @param context 用于构建显式 Intent 的 Context, 必须属于当前 Aster 初始化的应用.
     * @param launcher 已注册的 Activity Result Launcher.
     * @return 成功提交 Activity Result 启动请求时返回 true.
     * @exception IllegalStateException Aster 尚未初始化或 Launcher 生命周期状态不合法时抛出.
     * @exception ActivityNotFoundException Android 无法启动目标 Activity 时抛出.
     * @exception SecurityException Android 拒绝启动目标 Activity 时抛出.
     */
    fun launch(
        context: Context,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        val intent: Intent = createIntent(context) ?: return false
        if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) {
            Aster.reportError(
                "Cannot launch route for result with FLAG_ACTIVITY_NEW_TASK: $path"
            )
            return false
        }
        launcher.launch(intent, options)
        return true
    }

    fun putAll(bundle: Bundle): Postcard = apply {
        extras.putAll(bundle)
    }

    fun putAll(bundle: PersistableBundle): Postcard = apply {
        extras.putAll(bundle)
    }

    fun putBoolean(key: String?, value: Boolean): Postcard = apply {
        extras.putBoolean(key, value)
    }

    fun putByte(key: String?, value: Byte): Postcard = apply {
        extras.putByte(key, value)
    }

    fun putChar(key: String?, value: Char): Postcard = apply {
        extras.putChar(key, value)
    }

    fun putShort(key: String?, value: Short): Postcard = apply {
        extras.putShort(key, value)
    }

    fun putInt(key: String?, value: Int): Postcard = apply {
        extras.putInt(key, value)
    }

    fun putLong(key: String?, value: Long): Postcard = apply {
        extras.putLong(key, value)
    }

    fun putFloat(key: String?, value: Float): Postcard = apply {
        extras.putFloat(key, value)
    }

    fun putDouble(key: String?, value: Double): Postcard = apply {
        extras.putDouble(key, value)
    }

    fun putString(key: String?, value: String?): Postcard = apply {
        extras.putString(key, value)
    }

    fun putCharSequence(key: String?, value: CharSequence?): Postcard = apply {
        extras.putCharSequence(key, value)
    }

    fun putParcelable(key: String?, value: Parcelable?): Postcard = apply {
        extras.putParcelable(key, value)
    }

    fun putSize(key: String?, value: Size?): Postcard = apply {
        extras.putSize(key, value)
    }

    fun putSizeF(key: String?, value: SizeF?): Postcard = apply {
        extras.putSizeF(key, value)
    }

    fun putParcelableArray(key: String?, value: Array<out Parcelable>?): Postcard = apply {
        extras.putParcelableArray(key, value)
    }

    fun putParcelableArrayList(
        key: String?,
        value: ArrayList<out Parcelable>?
    ): Postcard = apply {
        extras.putParcelableArrayList(key, value)
    }

    fun putSparseParcelableArray(
        key: String?,
        value: SparseArray<out Parcelable>?
    ): Postcard = apply {
        extras.putSparseParcelableArray(key, value)
    }

    fun putIntegerArrayList(key: String?, value: ArrayList<Int>?): Postcard = apply {
        extras.putIntegerArrayList(key, value)
    }

    fun putStringArrayList(key: String?, value: ArrayList<String>?): Postcard = apply {
        extras.putStringArrayList(key, value)
    }

    fun putCharSequenceArrayList(
        key: String?,
        value: ArrayList<CharSequence>?
    ): Postcard = apply {
        extras.putCharSequenceArrayList(key, value)
    }

    fun putSerializable(key: String?, value: Serializable?): Postcard = apply {
        extras.putSerializable(key, value)
    }

    fun putBooleanArray(key: String?, value: BooleanArray?): Postcard = apply {
        extras.putBooleanArray(key, value)
    }

    fun putByteArray(key: String?, value: ByteArray?): Postcard = apply {
        extras.putByteArray(key, value)
    }

    fun putShortArray(key: String?, value: ShortArray?): Postcard = apply {
        extras.putShortArray(key, value)
    }

    fun putCharArray(key: String?, value: CharArray?): Postcard = apply {
        extras.putCharArray(key, value)
    }

    fun putIntArray(key: String?, value: IntArray?): Postcard = apply {
        extras.putIntArray(key, value)
    }

    fun putLongArray(key: String?, value: LongArray?): Postcard = apply {
        extras.putLongArray(key, value)
    }

    fun putFloatArray(key: String?, value: FloatArray?): Postcard = apply {
        extras.putFloatArray(key, value)
    }

    fun putDoubleArray(key: String?, value: DoubleArray?): Postcard = apply {
        extras.putDoubleArray(key, value)
    }

    fun putStringArray(key: String?, value: Array<String>?): Postcard = apply {
        extras.putStringArray(key, value)
    }

    fun putCharSequenceArray(key: String?, value: Array<CharSequence>?): Postcard = apply {
        extras.putCharSequenceArray(key, value)
    }

    fun putBundle(key: String?, value: Bundle?): Postcard = apply {
        extras.putBundle(key, value)
    }

    fun putBinder(key: String?, value: IBinder?): Postcard = apply {
        extras.putBinder(key, value)
    }

    fun addFlags(flags: Int): Postcard = apply {
        this.flags = this.flags or flags
    }

    fun setFlags(flags: Int): Postcard = apply {
        this.flags = flags
    }

    /**
     * 设置 Activity 启动选项.
     *
     * 该选项同时用于 [navigate] 和 [launch] 启动链路.
     *
     * @param options Activity 启动选项, null 表示清除已设置选项.
     * @return 当前路由请求.
     */
    fun options(options: ActivityOptionsCompat?): Postcard = apply {
        this.options = options
    }

    /**
     * 判断 Context 及其包装链中是否包含 Activity.
     *
     * @return 包装链中包含 Activity 时返回 true.
     */
    private fun Context.containsActivity(): Boolean {
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

    internal companion object {

        @JvmSynthetic
        internal fun create(path: String, valid: Boolean): Postcard {
            return Postcard(path, valid)
        }
    }
}
