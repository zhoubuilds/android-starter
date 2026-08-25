# Quill 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                       |
|-----------------|---------|--------------------------------|
| 2026-07-28      | whisper | 新增 Quill 接入、使用和排错说明 |

本文面向使用 Quill 的业务开发者。维护实现请阅读 [开发文档](development.md)，方案取舍请阅读 [设计文档](design.md)。

## 1. 适用范围

Quill 提供 Android `Log` 风格的日志入口、lazy message、可插拔 writer、默认 Logcat writer，以及日志框架自身的异常保护。

Quill 不提供文件日志、日志上传、crash 上报或自动业务上下文采集。

## 2. 配置依赖

starter 通过 `common` 统一暴露 Quill API：

```kotlin
// common/build.gradle.kts
dependencies {
    api(project(":quill"))
}
```

业务模块依赖 `common` 后即可使用 Quill。若不希望统一暴露，也可以由实际项目按模块直接依赖 `:quill`。

## 3. 注册 Writer

在应用启动阶段注册 writer：

```kotlin
class StarterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Quill.addWriter(
            LogcatQuillWriter(
                minimumLevel = if (BuildConfig.DEBUG) {
                    QuillLevel.DEBUG
                } else {
                    QuillLevel.WARN
                },
                defaultTag = "AndroidStarter",
            )
        )
    }
}
```

如果 release 环境希望完全避免日志消息构建，可以不注册 writer。没有 writer 时，`Quill.d { ... }` 直接返回 `0`，花括号中的代码不会执行。

## 4. 输出日志

```kotlin
private const val TAG: String = "UserRepository"

Quill.d(TAG) {
    "Load profile userId=$userId"
}

Quill.e(TAG, throwable) {
    "Load profile failed."
}

Quill.w(TAG, throwable)
```

无 tag 时由 writer 提供默认 tag：

```kotlin
Quill.i { "Application started." }
```

## 5. Lazy Message 规则

```text
没有 writer
  -> 不执行 messageSupplier

writer.isLoggable() 全部返回 false
  -> 不执行 messageSupplier

至少一个 writer 返回 true
  -> 执行一次 messageSupplier
  -> 分发给所有选中的 writer
```

因此可以安全地把较贵的调试信息放进花括号中。

## 6. Logcat 输出规则

`LogcatQuillWriter` 同时检查 `minimumLevel` 和 Android `Log.isLoggable(tag, priority)`。默认最低级别是 `VERBOSE`，但是否真正输出仍取决于 Android Logcat 的系统规则。

## 7. 自定义 Writer

```kotlin
class MemoryQuillWriter : QuillWriter {
    override fun isLoggable(level: QuillLevel, tag: String?): Boolean {
        return level.priority >= QuillLevel.INFO.priority
    }

    override fun write(
        level: QuillLevel,
        tag: String?,
        throwable: Throwable?,
        message: String,
    ): Int {
        return 1
    }
}
```

`isLoggable()` 决定是否构建消息；`write()` 处理已构建消息。Writer 抛出的 `Throwable` 会被 Quill 捕获，不会影响业务流程。

## 8. 排错

| 现象                         | 可能原因                               | 处理方式                                  |
|------------------------------|----------------------------------------|-------------------------------------------|
| 日志完全不输出               | 没有注册 writer                        | 在应用启动阶段调用 `Quill.addWriter(...)` |
| `DEBUG` 日志不输出           | Android `Log.isLoggable()` 返回 false  | 检查系统 log tag 设置                     |
| 花括号内代码没有执行         | 没有 writer 接收该日志                 | 检查 writer 注册和过滤条件                |
| 看到 message supplier failed | 消息构建函数抛出异常                   | 检查花括号内的字符串构建逻辑              |
| 重复添加 writer 返回 false   | 同一个 writer 实例已经注册             | 避免重复注册                              |
