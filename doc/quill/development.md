# Quill 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                         |
|-----------------|---------|----------------------------------|
| 2026-08-20      | whisper | 标记稳定日志契约的 Aegis 保护范围 |
| 2026-07-28      | whisper | 新增 Quill 源码结构和维护说明     |

本文面向 Quill 维护者。设计取舍见 [设计文档](design.md)，业务接入见 [使用文档](usage.md)。

## 1. 模块总览

```text
quill/
|- build.gradle.kts
|- consumer-rules.keep
`- src/
   |- main/java/com/whisper/quill/
   |  |- Quill.kt
   |  |- QuillLevel.kt
   |  |- QuillWriter.kt
   |  `- LogcatQuillWriter.kt
   `- test/java/com/whisper/quill/
      |- QuillTest.kt
      `- LogcatQuillWriterTest.kt
```

Quill 是普通 Android library，不依赖 starter 的业务或架构模块。

## 2. 公开 API

```kotlin
Quill.addWriter(writer)
Quill.removeWriter(writer)
Quill.clearWriters()
Quill.isLoggable(level, tag)

Quill.v { "message" }
Quill.d("Tag") { "message" }
Quill.i("Tag", throwable) { "message" }
Quill.w("Tag", throwable)
Quill.e(throwable) { "message" }
Quill.wtf("Tag") { "message" }
Quill.log(level, tag, throwable) { "message" }
```

公开日志消息参数使用 `() -> String`。`@PublishedApi internal` 方法只服务于 inline API，不是业务调用入口。

## 3. 执行链路

```text
Quill.d(tag) { ... }
  -> selectWriters(level, tag)
  -> writer.isLoggable(level, tag)
  -> messageSupplier()
  -> publish(...)
  -> writer.write(level, tag, throwable, message)
```

只有 `selectWriters()` 得到非空列表时才执行 `messageSupplier()`。

## 4. Writer 契约

| 阶段           | 维护要求                                                                 |
|----------------|--------------------------------------------------------------------------|
| `isLoggable()` | 完成输出条件判断；需要避免消息构建时必须在这里返回 `false`                |
| `write()`      | 直接处理日志，不应再次因为级别或 tag 主动丢弃日志                         |
| 返回值         | 正数表示至少一次写入成功，`0` 表示未写入                                  |
| 异常           | Quill 会隔离 writer 异常，但 writer 自身仍应保持健壮                       |

`publish()` 遍历所有选中的 writer，返回第一个正数结果，不对多个结果求和。

## 5. Logcat Writer

`LogcatQuillWriter.isLoggable()` 先判断 `minimumLevel`，再调用 `Log.isLoggable()`。`write()` 使用默认 tag 补齐空 tag，追加异常堆栈，按 4000 字符分段，并保证空消息也调用一次 `Log.println()`。

## 6. 测试

```bash
./gradlew :quill:testDebugUnitTest :quill:lintDebug
```

测试覆盖 lazy 分发、writer 管理、消息构建失败、writer 异常隔离、最低级别、Logcat 判断、长消息分段、异常拼接和空消息输出。

## 7. Aegis 保护范围

受保护类型为 `Quill`、`QuillLevel`、`QuillWriter` 和 `LogcatQuillWriter`，范围包括公开 API、lazy message、writer 选择与故障隔离、日志级别映射和 Logcat 写入契约。
