# Aster 使用文档

## 修订记录

| 修订时间（CST）  | 修订人    | 修订说明                                                                                  |
|------------------|-----------|-------------------------------------------------------------------------------------------|
| 2026-07-29       | whisper   | 补充路由跨模块暴露边界                                                                    |
| 2026-07-24 18:40 | whisper   | 同步当前已验证依赖基线为 AGP 9.2.1、Gradle 9.6.1、Kotlin 2.4.10、KSP 2.3.10、API 37 和 JVM 17 |
| 2026-07-23 19:35 | whisper   | 补充 R8/ProGuard consumer rules、手动配置场景及 release 验证方法                          |
| 2026-07-23 19:26 | whisper   | 补充已验证依赖基线及构建工具版本说明                                                      |
| 2026-07-23 18:36 | whisper   | 归并 Aster 接入、使用限制、错误处理、排错方法及接入检查项                                 |

本文面向使用 Aster 的业务开发者，说明接入、路由、能力发现、限制和排错方式。维护实现请阅读 [开发文档](development.md)，方案取舍请阅读 [设计文档](design.md)。

## 1. 适用范围

Aster 提供：

* 基于字符串路径的 Activity 路由。
* 基于唯一名称或接口类型的 Capability 发现。
* application/library 模块的自动 Registry 生成和 Manifest 注册。

Aster 不提供 Fragment 路由、Deep Link、拦截器、自动参数注入或依赖注入。

当前支持且已验证的精确组合如下，外部版本信息核对于 2026-07-24：

| 维度 | 支持基线 | 使用说明 |
| --- | --- | --- |
| AGP | `9.2.1` | 最大支持 API `37.0`；当前 IDE 支持上限为 `9.2.1`，不要升到 `9.3.x` |
| Gradle | `9.6.1` | 高于 AGP 9.2 的最低要求 |
| Android Kotlin | AGP 9.2 built-in Kotlin，当前解析 KGP `2.2.10` | Android 模块不要应用 `org.jetbrains.kotlin.android` |
| JVM 模块 Kotlin | KGP `2.4.10` | 用于 `aster-compiler` 等 JVM 模块，不会覆盖 built-in Kotlin |
| KSP | `2.3.10` | 其它 KSP 版本尚未验证 |
| Gradle 运行 JDK | daemon `21`，wrapper launcher `17` | 仓库通过 daemon criteria 固定 JDK 21；两者都与 JVM target 不同 |
| compileSdk / targetSdk | Android `37` / `37` | compileSdk 等于 AGP 9.2 的上限 |
| minSdk | `24` | `aster-runtime` 的最低要求，低于 24 不支持 |
| Java/Kotlin JVM target | `17` | 控制产物字节码，不代表可以用 JDK 17 运行 Gradle daemon |

这是一组整体基线，不能单独替换其中一个版本后继续视为已支持。当前仅确认本仓库精确组合可构建。其它组合的候选通道、上游约束和验证规则见[开发文档的依赖兼容矩阵](development.md#8-依赖兼容矩阵)。

AGP `8.7.3` 只保留为候选兼容通道，不代表已经声明或完整验证的最低支持版本。

## 2. 接入模块类型

只有需要声明 `@Route` 或 `@Capable` 的 Android application/library 模块需要应用 Aster Gradle 插件和 KSP processor。

只声明能力接口、路由常量或参数常量的 `api` 模块只需要依赖 `aster-runtime`，不需要应用 Aster 插件。

推荐结构：

```text
feature-user-api
  |- UserSessionCapability
  |- UserRoutes
  `- UserRouteExtras

feature-user-impl
  |- UserSessionCapabilityImpl
  `- LoginActivity
```

调用方依赖 `feature-user-api`，不直接引用 `feature-user-impl` 中的实现类。

### 2.1 路由和能力暴露边界

Aster 面向跨模块路由和能力发现, 不要求模块内部跳转也走路由。业务模块的 API 模块只暴露跨模块必要契约:

* 其它模块或 app 需要直接进入的页面路由常量。
* 其它模块需要调用的 Capability 或服务契约。
* 调用方必须知道的参数常量和轻量契约模型。

模块内部页面跳转优先使用普通 Android 机制, 例如显式 `Intent`、Navigation、Fragment transaction 或本模块内部 navigator。
不要为了模块内部复用, 将所有页面路径都注册并暴露到 API 模块。已经暴露给外部的路由和 Capability 应按公开 API 维护稳定性。

## 3. 配置 Gradle 插件

当前仓库通过 Included Build 提供插件：

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("aster-gradle-plugin")
}
```

在需要生成 Registry 的模块中应用插件。插件顺序必须是 Android、KSP、Aster：

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("com.whisper.aster")
}
```

application 模块使用 `com.android.application`，其余配置相同。

声明模块 segment：

```kotlin
aster {
    segment = "user"
}
```

segment 规则：

* 以小写字母开头。
* 只包含小写字母、数字和下划线。
* 不包含 `/` 或 `.`。
* 同一 Gradle Build 中已配置的 Aster 源码模块必须唯一。

## 4. 配置依赖

### 4.1 API 模块

能力接口需要继承 `Capability`，因此 API 模块应向使用方暴露 runtime：

```kotlin
dependencies {
    api(project(":aster:aster-runtime"))
}
```

### 4.2 实现模块和 app

```kotlin
dependencies {
    implementation(project(":aster:aster-runtime"))
    ksp(project(":aster:aster-compiler"))
}
```

当前插件只检查 KSP plugin 是否存在，不会自动添加或验证 `aster-compiler`。遗漏 processor 时构建可能成功，但运行时 Registry 会缺失，因此必须显式检查每个生产 Variant 的 KSP 依赖。

### 4.3 androidTest 注意事项

当前全局 `ksp(...)` 依赖也可能应用到 `androidTest` compilation，使测试 APK 生成与 app 相同全限定名的 Registry，并遮蔽目标 APK Registry。

如果模块需要可靠运行 instrumentation tests，当前建议只向生产 Variant 添加 processor：

```kotlin
dependencies {
    implementation(project(":aster:aster-runtime"))
    add("kspDebug", project(":aster:aster-compiler"))
    add("kspRelease", project(":aster:aster-compiler"))
}
```

有 product flavor 或自定义 build type 时，需要为每个实际生产 Variant 配置对应的 KSP configuration。当前不支持在 `androidTest` 源集中声明 `@Route` 或 `@Capable`。

### 4.4 配置混淆规则

`aster-runtime` 已通过 `consumerProguardFiles` 将规则打包进 AAR 的 `proguard.txt`。正常使用 project dependency 或完整 AAR 依赖时，AGP 会自动将规则合入 app 的 R8 配置，业务模块不需要重复添加。

需要审计规则，或发布流程只复制 `classes.jar`、重新打包 AAR 时丢失 `proguard.txt`、使用自定义发布流程未携带 consumer rules 时，应在最终 application 的混淆文件中补充以下完整规则。规则源文件见 [consumer-rules.keep](../../aster/aster-runtime/consumer-rules.keep)。

```proguard
-keep,allowoptimization class * implements com.whisper.aster.runtime.registry.AsterRegistryInstaller {
    public <init>();
    public void install(com.whisper.aster.runtime.registry.AsterRegistrar);
}

-keep,allowoptimization,allowobfuscation @com.whisper.aster.runtime.annotation.Capable class * {
    public <init>();
}
```

两条规则的作用不同：

* `AsterRegistryInstaller` 实现类通过 Manifest 中的字符串类名反射加载，因此必须保留类名、public 无参构造和 `install()`；方法体允许优化。
* `@Capable` 实现类由生成代码通过 class literal 引用，允许混淆和优化，但 Runtime 反射实例化要求保留 public 无参构造。

不要使用 `-keep class com.whisper.aster.runtime.** { *; }` 保留整个 Aster 包，这会阻止无关代码优化，也不能替代对业务 `@Capable` 实现构造函数的保护。

release 开启 R8 后至少检查：

1. runtime AAR 中存在 `proguard.txt`，或 application 混淆文件包含上述规则。
2. R8 merged configuration 中包含两条规则。
3. 最终产物中 `AsterGeneratedRegistry` 全限定名未改变。
4. 初始化、路由和 Capability 解析在压缩后的 release 包中正常工作。

## 5. 初始化

在自定义 `Application` 中初始化一次：

```kotlin
class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Aster.initialize(this)
    }
}
```

并在 Manifest 中声明：

```xml
<application
    android:name=".AppApplication" />
```

要求：

* 在任何路由或能力 API 之前完成初始化。
* 同一进程应使用同一个 `Application` 实例。
* 同一实例重复初始化只记录 warning；不同实例重复初始化会抛异常。

## 6. 声明和使用路由

### 6.1 在 API 模块声明契约

```kotlin
object UserRoutes {
    const val LOGIN: String = "/user/login"
    const val EXTRA_SOURCE: String = "source"
}
```

### 6.2 在实现模块声明 Activity

```kotlin
@Route(UserRoutes.LOGIN)
class LoginActivity : AppCompatActivity()
```

同时需要按普通 Android 规则在 Manifest 中声明 Activity。Aster 只注册路由映射，不自动添加 Android 组件。

路由约束：

* 目标必须是命名的普通 Activity class。
* 不能是 abstract、inner class、object 或 companion object。
* 路径格式为 `/<segment>/<page>`，可以继续增加更多段。
* 首段必须等于当前模块 `aster.segment`。
* 同一个路径不能对应多个 Activity。

### 6.3 构建和导航

```kotlin
val postcard: Postcard = Aster.build(UserRoutes.LOGIN)
    .putString(UserRoutes.EXTRA_SOURCE, "profile")

val registered: Boolean = postcard.isRegistered()
val navigated: Boolean = postcard.navigate(context)
```

`navigate()` 使用 Aster 持有的 Application。使用非 Activity Context 导航时会自动添加 `Intent.FLAG_ACTIVITY_NEW_TASK`。

### 6.4 创建 Intent

```kotlin
val intent: Intent? = Aster.build(UserRoutes.LOGIN)
    .putString(UserRoutes.EXTRA_SOURCE, "profile")
    .createIntent(context)
```

每次调用都会创建新的 Intent，不会因为 Context 类型自动添加 flag。调用方负责后续启动行为。

传入的 Context 必须属于当前 Aster 初始化的应用。

### 6.5 Activity Result

```kotlin
val launched: Boolean = Aster.build(UserRoutes.LOGIN)
    .putString(UserRoutes.EXTRA_SOURCE, "profile")
    .launch(launcher)
```

`launch()` 不添加 `NEW_TASK`。如果调用方通过 `setFlags()` 或 `addFlags()` 显式携带 `NEW_TASK`，Aster 会记录 error 并返回 `false`。

### 6.6 flags 和 options

```kotlin
val options: ActivityOptionsCompat = ActivityOptionsCompat.makeBasic()

Aster.build(UserRoutes.LOGIN)
    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    .options(options)
    .navigate(context)
```

* `setFlags()` 替换已设置的 flags。
* `addFlags()` 在现有 flags 上追加。
* `options()` 同时用于 `navigate()` 和 `launch()`。

## 7. 声明和使用 Capability

### 7.1 在 API 模块声明契约

```kotlin
object UserCapabilities {
    const val SESSION: String = "user.account.session"
}

interface UserSessionCapability : Capability {
    fun isLoggedIn(): Boolean
}
```

### 7.2 在实现模块声明能力

```kotlin
@Capable(
    name = UserCapabilities.SESSION,
    singleton = true
)
class UserSessionCapabilityImpl : UserSessionCapability {

    override fun initialize(application: Application) {
        // 只初始化当前能力自身状态.
    }

    override fun isLoggedIn(): Boolean = false
}
```

能力实现约束：

* 必须是命名的普通、非 abstract、非 inner class。
* 必须实现 `Capability`。
* 必须提供 JVM public 无参构造。
* Kotlin `object` 和 companion object 不支持。
* name 至少包含两个点分段，首段等于模块 segment。
* name 全局唯一。
* 构造函数和 `initialize()` 不得解析其它 Capability。

### 7.3 按名称精确获取

```kotlin
val capability: UserSessionCapability? =
    Aster.resolve(UserCapabilities.SESSION) as? UserSessionCapability
```

名称未注册或格式非法时返回 `null`。

### 7.4 按类型获取第一个实现

```kotlin
val capability: UserSessionCapability? =
    Aster.resolve(UserSessionCapability::class.java)
```

如果存在多个实现，Aster 按能力名字典序返回第一个，并记录 warning。只有明确接受这种选择规则时才使用该 API。

### 7.5 按类型获取全部实现

```kotlin
val capabilities: List<UserSessionCapability> =
    Aster.resolveAll<UserSessionCapability>()
```

返回结果按能力名字典序排序。没有实现时返回空列表。

### 7.6 单例语义

* `singleton = true`：第一次解析时构造并初始化，成功后按能力名缓存。
* `singleton = false`：每次解析都创建并初始化新实例。
* 构造或初始化失败时异常原样向上传播，失败实例不会缓存。

## 8. 错误和返回值

| 场景 | 行为 |
| --- | --- |
| 未调用 `Aster.initialize()` | 抛出 `IllegalStateException` |
| 路由路径格式非法 | 记录 error，返回不可导航 Postcard |
| 路由未注册 | `createIntent()` 返回 `null`，导航返回 `false` |
| 能力名格式非法或未注册 | 记录 error，返回 `null` 或 `false` |
| 类型查询没有实现 | 单个查询返回 `null`，全部查询返回空列表 |
| Activity Result 携带 `NEW_TASK` | 记录 error，返回 `false` |
| Registry 路由或能力冲突 | 初始化失败并抛异常 |
| Activity 未在 Manifest 声明 | Android 启动异常原样传播 |
| 权限或 Launcher 生命周期错误 | 外部异常原样传播 |
| Capability 构造或初始化失败 | 业务异常原样传播 |

Aster 日志 tag 为 `Aster`，日志和异常信息使用英文。

## 9. 排错

### 9.1 插件提示只支持 Android 模块

典型信息：

```text
com.whisper.aster can only be applied to Android application or library modules
```

检查：

* 当前模块是否为 application 或 library。
* Android plugin 是否写在 Aster plugin 之前。
* 不要在纯 JVM 或仅声明 API 的模块应用 Aster plugin。

### 9.2 segment 缺失或非法

检查模块是否存在顶层配置：

```kotlin
aster {
    segment = "user"
}
```

如果提示 duplicate segment，搜索报错中列出的模块路径，为其中一个模块分配新的稳定 segment，并同步修改该模块的路由和能力名称首段。

### 9.3 缺少 KSP

确认模块 plugins 中存在：

```kotlin
alias(libs.plugins.ksp)
id("com.whisper.aster")
```

只应用 KSP plugin 还不够，dependencies 中必须将 `aster-compiler` 加入对应生产 Variant。

### 9.4 Registry class 找不到

典型日志：

```text
Ignoring manifest metadata value '...' because the referenced class could not be found
```

按顺序检查：

1. 对应 Variant 是否配置 `aster-compiler`。
2. 是否生成 `AsterGeneratedRegistry.kt`。
3. module Manifest 是否生成 metadata。
4. library AAR 是否同时包含 Registry class 和 metadata。
5. app 最终 merged manifest 是否包含该 metadata。
6. release R8 是否合入 `aster-runtime` consumer rules。
7. plugin、compiler、runtime 是否来自兼容版本。

### 9.5 路由或能力未找到

检查生成源码：

```text
<module>/build/generated/ksp/<variant>/kotlin/
```

确认 `AsterGeneratedRegistry.install()` 中存在目标注册语句。然后检查最终 Manifest metadata 和当前 APK 实际依赖的 AAR Variant。

如果 debug 正常、release 失败，重点检查：

* release 是否运行对应 KSP task。
* `matchingFallbacks` 最终选择了哪个 library Variant。
* release AAR 中是否包含 Registry。
* R8 merged configuration 是否包含 runtime consumer rules。

### 9.6 instrumentation 中 app 路由缺失

检查是否生成了：

```text
build/generated/ksp/debugAndroidTest/.../AsterGeneratedRegistry.kt
```

如果它与 debug Registry 同名，应移除 androidTest compilation 的 Aster processor，只为生产 Variant 配置 compiler。当前不要在 androidTest 源集中声明 Aster 注解。

### 9.7 AGP/KSP 兼容错误

插件会在可识别的链接错误中输出当前验证组合和原始错误。检查：

* AGP、Kotlin、KSP 是否采用兼容组合。
* Aster plugin 编译基线是否覆盖目标 API。
* 是否同时存在多个版本的 KSP Gradle plugin。

不要通过添加 AGP/KSP 内部实现依赖绕过错误。

## 10. 使用限制

* 只支持 Android application 和 library，不支持纯 JVM、dynamic-feature 或其它 Android plugin 类型。
* Activity 必须由业务 Manifest 正常声明。
* 外部 AAR 的 segment 不参与宿主源码 Build 的唯一性检查。
* Configuration on Demand 和 Isolated Projects 下，未配置模块可能不参与 segment 检查。
* 当前没有跨版本 Registry 协议协商，plugin、compiler、runtime 应保持同一发布版本。
* 当前插件不会自动添加 compiler，也不会验证 processor 是否真正生成 class。
* 当前 androidTest Registry 身份未隔离。
* Capability 初始化依赖不受支持。
* 手写 `AsterRegistryInstaller` 必须提供 public 无参构造，并遵守全局名称唯一性。

## 11. 接入检查表

提交接入变更前确认：

* Android、KSP、Aster plugin 顺序正确。
* segment 合法且在当前源码 Build 中唯一。
* runtime 和 compiler 已加入正确配置。
* 路由和能力名称首段与 segment 一致。
* Activity 已在业务 Manifest 声明。
* Application 已调用 `Aster.initialize()`。
* debug 和 release 均生成 Registry 与 Manifest。
* instrumentation 没有生成同名空 Registry。
* release 开启压缩时确认 runtime consumer rules 已合入，并完成 Registry 与 Capability 验证。
* app 最终 merged manifest 包含所有预期模块 metadata。
