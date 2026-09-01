# Aster 开发文档

## 修订记录

| 修订时间（CST）  | 修订人  | 修订说明                                                                                   |
|------------------|---------|--------------------------------------------------------------------------------------------|
| 2026-09-01       | whisper | 收敛名称类型安全解析和类型唯一解析契约                                               |
| 2026-08-31       | whisper | 反转 Registry Manifest metadata 并采用固定发现标记                                          |
| 2026-08-20       | whisper | 标记稳定契约与关键协议的 Aegis 保护范围                                                     |
| 2026-08-20       | whisper | 迁移 Aegis 修改保护标记                                                                     |
| 2026-07-24 18:40 | whisper | 同步当前 AGP 9.2.1、Gradle 9.6.1、Kotlin 2.4.10、KSP 2.3.10、API 37 和 JVM 17 验证基线      |
| 2026-07-23 19:26 | whisper | 补充依赖兼容矩阵、候选兼容通道、升级规则及官方来源                                          |
| 2026-07-23 18:35 | whisper | 归并模块结构、架构原理、实现链路、协议约束、测试方式及技术债                                 |

本文面向 Aster 维护者，描述模块边界、源码目录、构建与运行时链路、协议约束、测试方式和当前技术债。设计取舍见 [设计文档](design.md)，业务接入见 [使用文档](usage.md)。

## 1. 模块总览

```text
aster-gradle-plugin/       Gradle 插件 Included Build
aster/aster-compiler/      KSP 注解处理器
aster/aster-runtime/       注解、公开 API 和 Android Runtime
```

三个模块的依赖方向：

```text
业务 Android 模块
  |- implementation -> aster-runtime
  |- KSP processor  -> aster-compiler
  `- Gradle plugin  -> aster-gradle-plugin

aster-compiler --生成代码引用--> aster-runtime Registry API
aster-gradle-plugin --协议协作--> aster-compiler / aster-runtime
```

Gradle 插件、compiler 和 runtime 不直接共享一个 protocol artifact。协议常量分散在三个模块中，修改时必须人工同步并执行全链路测试。

## 2. Gradle 插件

### 2.1 模块结构

```text
aster-gradle-plugin/
|- settings.gradle.kts
|- build.gradle.kts
`- src/
   |- main/kotlin/com/whisper/aster/gradle/
   |  |- AsterPlugin.kt
   |  |- AsterExtension.kt
   |  |- AsterAndroidIntegration.kt
   |  |- AsterKspIntegration.kt
   |  |- AsterSegmentRegistryService.kt
   |  `- GenerateAsterManifestTask.kt
   `- test/
      |- kotlin/com/whisper/aster/gradle/
      `- resources/META-INF/gradle-plugins/
```

| 文件 | 职责 |
| --- | --- |
| `AsterPlugin.kt` | 插件入口，检查 Android 模块类型，注册 `aster` DSL、共享 segment 服务及 AGP/KSP 集成 |
| `AsterExtension.kt` | 保存、校验并冻结模块 `segment`，同步 KSP 参数 |
| `AsterAndroidIntegration.kt` | 使用 Android Components API 监听 application/library Variant，并接入生成 Manifest |
| `AsterKspIntegration.kt` | 使用公开 `KspExtension.arg()` 传递 segment 和 Registry 包名 |
| `AsterSegmentRegistryService.kt` | 在同一 Gradle Build 的已配置源码模块之间检查 segment 唯一性 |
| `GenerateAsterManifestTask.kt` | 为单个 Variant 生成包含 Registry metadata 的 Manifest |

### 2.2 插件应用流程

```text
AsterPlugin.apply
  -> 确认 com.android.application 或 com.android.library 已应用
  -> 创建 aster DSL
  -> 获取 Build 级 AsterSegmentRegistryService
  -> KSP 存在时配置处理器参数
  -> Android Components finalizeDsl
       -> 校验、冻结并注册 segment
  -> Android Components onVariants
       -> 注册 generateAster<Variant>Manifest
       -> addGeneratedManifestFile
```

插件要求 Android plugin 在 Aster 之前应用。KSP 可以在 Aster 之前或之后应用，`withPlugin()` 会处理两种顺序。

### 2.3 AGP 与 KSP 隔离

`AsterPlugin` 本身只依赖 Gradle API。AGP 和 KSP 类型分别隔离在 `AsterAndroidIntegration` 与 `AsterKspIntegration`，并以 `compileOnly` 引入：

* AGP 编译基线为 `9.2.1`。
* 当前验证版本为 AGP `9.2.1`。
* 当前 KSP 编译和验证版本为 `2.3.10`。
* 当前 Kotlin JVM 插件验证版本为 `2.4.10`。

这可以避免非目标模块在加载插件入口时提前解析可选宿主 API。发生 `LinkageError` 时，集成边界会尽量转换为包含版本信息和原始 cause 的 `GradleException`。

编译基线不等于已声明的最低支持版本。最低 AGP/KSP 兼容矩阵仍需真实构建验证。

### 2.4 Manifest 输出

每个生产 Variant 的任务输出为：

```text
<module>/build/generated/aster/<variant>/manifest/AndroidManifest.xml
```

核心内容：

```xml
<meta-data
    android:name="<registry-qualified-name>"
    android:value="com.whisper.aster.registry" />
```

library 的生成 Manifest 随 AAR 发布，application 的生成 Manifest 进入 APK。插件不修改 `src/main/AndroidManifest.xml`。

## 3. KSP Compiler

### 3.1 模块结构

```text
aster/aster-compiler/src/main/java/com/whisper/aster/compiler/
|- AsterProcessorProvider.kt
|- AsterProcessor.kt
|- AsterCompilerContract.kt
|- model/
|  `- AsterEntries.kt
|- symbol/
|  |- RouteParser.kt
|  |- CapabilityParser.kt
|  |- AsterValidator.kt
|  `- KspExtensions.kt
`- codegen/
   `- AsterRegistryWriter.kt
```

| 目录或文件 | 职责 |
| --- | --- |
| `AsterProcessorProvider` | KSP ServiceLoader 入口，创建处理器 |
| `AsterProcessor` | 管理 KSP 多轮处理、deferred symbols、累计结果、失败状态和最终生成 |
| `AsterCompilerContract` | 保存注解类名、KSP 参数、命名格式和 Registry 代码生成协议 |
| `model` | 保存解析完成的 Route/Capability 条目 |
| `symbol` | 读取注解参数，检查类型、可见性、构造函数、segment 和重复声明 |
| `codegen` | 使用 KotlinPoet 生成固定类名的模块 Registry |

### 3.2 处理流程

```text
Resolver
  -> 查找 @Route / @Capable
  -> 读取 aster.segment
  -> 检查同一类型不能同时使用两个注解
  -> 解析当前轮可用声明
  -> 累计 RouteEntry / CapabilityEntry
  -> 返回 deferred symbols
  -> finish() 检查最终结果
  -> AsterRegistryWriter 生成 Registry
```

处理器跨轮次保存已经成功解析的条目。其他 Processor 后续生成的注解类可以在新一轮加入 Registry。任何解析错误、未解析 symbol 或重复声明都会阻止最终 Registry 生成。

### 3.3 编译期约束

`@Route`：

* 目标是命名的普通、非 inner、非抽象 `Activity` class。
* 对生成 Registry 可访问。
* path 格式合法且首段等于模块 segment。
* 当前模块内 path 唯一。

`@Capable`：

* 目标是命名的普通、非 inner、非抽象 class。
* 实现 `Capability`。
* 对生成 Registry 可访问。
* 提供 JVM public 无参构造。
* name 格式合法且首段等于模块 segment。
* 当前模块内 name 唯一。

同一个 class 不能同时使用 `@Route` 和 `@Capable`。

### 3.4 生成代码

生成位置由 KSP 管理，通常为：

```text
<module>/build/generated/ksp/<variant>/kotlin/
  <android-namespace>/aster/generated/AsterGeneratedRegistry.kt
```

生成类实现 `AsterRegistryInstaller`，在 `install(AsterRegistrar)` 中按名称稳定排序后提交路由和能力映射。即使模块没有注解，也会生成空 Registry，以保持 Manifest 与 class 协议一致。

Registry 是 aggregating 输出，依赖当前模块参与处理的源文件。新增、修改和删除注解后都必须刷新生成内容。

## 4. Runtime

### 4.1 包结构

```text
aster/aster-runtime/src/main/java/com/whisper/aster/
|- Aster.kt
|- Capability.kt
|- Postcard.kt
|- annotation/
|  |- Route.kt
|  `- Capable.kt
|- registry/
|  |- AsterRegistrar.kt
|  `- AsterRegistryInstaller.kt
`- internal/
   |- RoutePathValidator.kt
   |- CapabilityNameValidator.kt
   |- LogcatErrorHandler.kt
   `- registry/
      |- ManifestRegistryLoader.kt
      |- RegistrationSession.kt
      |- RegistryState.kt
      |- RouteRegistry.kt
      |- CapabilityRegistry.kt
      `- CapabilityDescriptor.kt
```

| 包或文件 | 职责 |
| --- | --- |
| `Aster` | 初始化入口、路由构建、能力查询和只读状态发布 |
| `Capability` | 所有能力契约的基础接口和初始化回调 |
| `Postcard` | 保存一次 Activity 路由请求的 extras、flags 和 options，并创建或启动 Intent |
| `annotation` | 供业务源码和 KSP 使用的二进制保留注解 |
| `registry` | compiler 生成代码与 Runtime 之间的公开安装协议 |
| `internal/*Validator` | 动态调用时复用的名称格式检查 |
| `ManifestRegistryLoader` | 从最终 Manifest metadata 精确加载 Installer |
| `RegistrationSession` | 收集一次初始化中的定义、检测全局冲突并冻结状态 |
| `RouteRegistry` | 只读路由查询和按需 Activity 结构检查 |
| `CapabilityRegistry` | 能力描述符查询、类型匹配、实例化和单例缓存 |

### 4.2 初始化流程

```text
Aster.initialize(application)
  -> ManifestRegistryLoader 读取 ApplicationInfo.metaData
  -> 按 value 筛选 com.whisper.aster.registry 固定标记
  -> 读取 metadata name 并执行 Class.forName(name, initialize = false)
  -> 实例化 AsterRegistryInstaller
  -> installer.install(RegistrationSession)
  -> 检查全局路由和能力冲突
  -> freeze(application)
  -> volatile 发布 RegistryState
```

初始化使用双重检查和 `initLock`。只有全部 Installer 成功、注册无冲突且状态冻结后，才通过一次 `@Volatile` 写入对外发布。失败时 session 会关闭并清空，调用方可以在问题修复后重新尝试初始化。

使用同一个 `Application` 重复初始化只记录 warning。使用不同 `Application` 重复初始化抛出 `IllegalStateException`。

### 4.3 路由状态

`RouteRegistry` 保存不可变 path 到 Activity class 映射。`isRegistered()` 只判断 path 是否存在；`createIntent()` 首次真正使用目标时再检查 class 是否为 public、具体、合法 Activity。

`Postcard` 每次 `createIntent()` 都生成新 Intent：

* extras 复制到 Intent。
* `setFlags()` 替换请求 flags，`addFlags()` 合并 flags。
* `navigate(context)` 在 Context 包装链中不存在 Activity 时自动添加 `NEW_TASK`。
* `launch()` 不自动添加 `NEW_TASK`，并拒绝调用方显式设置该 flag。
* `ActivityOptionsCompat` 同时传递给 navigate 与 launch。

Android 启动、权限和 Launcher 生命周期异常原样传播。

### 4.4 能力状态

`CapabilityRegistry` 保存三类数据：

* 不可变的 `name -> CapabilityDescriptor`。
* 单例 `name -> instance` 并发缓存。
* `requested type -> sorted names` 类型查询缓存。

单例能力在 descriptor monitor 内构造和初始化，完整成功后写入 `ConcurrentHashMap`。非单例能力每次查询都重新创建。

`resolve<T>(name)` 先按能力名精确查找, 再使用 descriptor 的实现 class 做 `isAssignableFrom` 校验.
名称未注册或格式非法时返回 `null`; 类型不匹配时于实例化前抛出 `IllegalStateException`. 异常包含能力名,
请求类型, 已注册实现类型以及检查名称常量和请求契约的建议. Java 调用方使用 `resolve(name, type)` 完成相同查询.
`resolveCapability(name)` 保留动态 `Capability` 返回值, 仅用于调用方明确自行执行 `as?` 等动态类型处理的场景.

类型查询先按实现 class 做 `isAssignableFrom` 匹配, 再按能力名排序. `resolve(type)` 在没有匹配时返回 `null`,
在只有一个匹配时实例化并返回该实现, 在存在多个匹配时于实例化任何能力前抛出 `IllegalStateException`.
异常包含请求类型, 匹配数量, 按名称排序的全部候选名称及显式选择建议. `resolveAll(type)` 仍实例化并返回全部匹配项.
能力名排序不构成单个解析的优先级.

Capability 初始化期间禁止解析其它 Capability。Runtime 不检测递归或跨线程循环，违反约束可能导致递归溢出或锁顺序死锁。

## 5. Registry 协议

以下值必须保持一致：

| 协议项               | 当前值                                                        | 维护位置                    |
|-------------------|------------------------------------------------------------|-------------------------|
| KSP segment 参数    | `aster.segment`                                            | plugin、compiler         |
| KSP Registry 包参数  | `aster.registryPackage`                                    | plugin、compiler         |
| 生成包名              | `<androidNamespace>.aster.generated`                        | plugin、compiler         |
| 生成类名              | `AsterGeneratedRegistry`                                   | plugin、compiler、R8 规则语义 |
| metadata name      | `<registry-qualified-name>`                                | plugin、runtime、R8 规则语义 |
| metadata 固定标记    | `com.whisper.aster.registry`                               | plugin、runtime          |
| Installer 接口      | `com.whisper.aster.runtime.registry.AsterRegistryInstaller` | compiler、runtime、R8     |
| Registrar 接口      | `com.whisper.aster.runtime.registry.AsterRegistrar`         | compiler、runtime、R8     |

修改任何协议项后必须同时验证：

1. KSP 生成源码。
2. application 和 library 生成 Manifest。
3. AAR 中的 class 与 metadata。
4. 最终 APK 合并 Manifest。
5. Runtime 反射加载和安装。
6. R8 后的类名、构造函数和方法。
7. 旧 AAR 与新宿主的兼容策略。

## 6. 错误边界

### 6.1 构建期失败

* Android/KSP 插件缺失或模块类型不支持。
* segment 缺失、格式非法或源码 Build 内重复。
* 注解名称、目标类型、可见性、构造函数或模块内重复不合法。
* KSP 最终仍有 deferred symbol。

### 6.2 Runtime 抛出

* 未初始化就使用 Aster。
* 使用不同 Application 重复初始化。
* 已确认的 Installer 无法实例化。
* Registry 安装冲突或冻结后继续注册。
* 已注册的 Activity/Capability class 结构非法。
* Capability 构造或初始化失败。

### 6.3 Runtime 安全返回

非法动态名称、路由未找到、能力未找到和 Activity Result 携带 `NEW_TASK` 会记录英文 error，并返回 `null`、`false` 或无效 Postcard。

Manifest 固定标记对应的 metadata name 为空、class 不存在或类型不匹配时记录英文 warning 并忽略。

## 7. R8 契约

`aster-runtime/consumer-rules.keep` 保护两类对象：

* 所有 `AsterRegistryInstaller` 实现：保留类名、public 无参构造和 `install()`，允许优化方法体。
* 所有 `@Capable` class：保留 class 和 public 无参构造，允许混淆和优化。

Registry 通过 Manifest 字符串加载，不能改名。Capability 通过生成代码中的 class literal 引用，可以改名。

改变发现方式、注解保留策略、构造协议或生成类结构时，必须重新执行 minified fixture 验证。

## 8. 依赖兼容矩阵

本节中的外部信息核对于 2026-07-24。版本兼容必须同时满足上游工具约束和 Aster 实际验证，不能把“官方范围内可能兼容”写成“Aster 已支持”。

### 8.1 判定标准

| 状态 | 含义 |
| --- | --- |
| 支持且已验证 | Aster 仓库使用真实 AGP、KSP 和 Android 构建任务验证过该精确组合 |
| 官方范围内但未验证 | 各上游公开约束存在交集，但尚未通过 Aster 真实兼容 fixture，不作为发布承诺 |
| 不支持 | 明确违反上游最低/最高版本、Aster artifact 契约或 Android 构建规则 |

插件 TestKit 当前使用 Fake AGP/KSP，只能验证插件自身逻辑，不能提升某个版本组合的兼容等级。真实 app、compiler TestKit 和 Android library 构建才计入当前基线验证。

### 8.2 当前支持基线

| 维度 | 当前值 | 官方约束或来源结论 | Aster 结论 |
| --- | --- | --- | --- |
| AGP | `9.2.1` | AGP 9.2 要求 Gradle 至少 `9.4.1`、JDK 至少 `17`，最大支持 API `37.0`；当前 IDE 支持上限为 AGP `9.2.1` | 支持且已验证；不要升到 `9.3.x`，否则 IDE 会报不兼容 |
| Gradle | `9.6.1` | 高于 AGP 9.2 最低要求；Gradle 9.6.1 支持在 JDK 21 上运行 | 支持且已验证 |
| Android Kotlin | AGP 9.2 built-in Kotlin，当前 AGP 9.2.1 artifact 解析 KGP `2.2.10` | AGP 9.0 起默认启用 built-in Kotlin，Android 模块不再应用 `org.jetbrains.kotlin.android` | 支持且已验证 |
| JVM 模块 Kotlin | KGP `2.4.10` | 用于 `aster-compiler` 等 JVM/KAPT 模块；与 AGP built-in Kotlin 分离 | 当前精确组合已验证 |
| KSP | `2.3.10` | KSP `2.3.0` 起版本号与 Kotlin 解耦 | 支持且已验证；其它 KSP 版本默认未验证 |
| Gradle 运行 JDK | daemon criteria `21`；wrapper launcher JDK `17.0.17` | AGP 9.2 的最低和默认 JDK 均为 17；Gradle 9.6.1 支持在 JDK 21 上运行 | daemon criteria 固定为 21，当前精确组合支持且已验证 |
| compileSdk | Android `37` | 等于 AGP 9.2 的最大 API `37.0` | 支持且已验证 |
| targetSdk | `37` | 不高于当前 compileSdk | 支持且已验证 |
| minSdk | `24` | AndroidX Activity 1.13.0 继承的最低要求为 API 23；`aster-runtime` 自身声明 API 24 | Aster 最低支持 API 24，与构建工具版本独立 |
| Java/Kotlin JVM target | `17` | `targetCompatibility`/`jvmTarget` 控制 class 文件目标，不是运行 Gradle 的 JDK；Java API 可用性还受 minSdk 和 desugaring 影响 | 支持且已验证，不能用它替代 daemon JDK 21 |

当前精确组合整体归类为“支持且已验证”。该结论只覆盖本仓库当前版本行，不能由此推导其它 Kotlin/Gradle/KSP 越界组合也受支持。

Android 模块和 JVM 工具模块的 Kotlin 版本必须分开理解：

```text
Android application/library -> AGP 9.2 built-in Kotlin
aster-compiler              -> org.jetbrains.kotlin.jvm 2.4.10
Gradle Kotlin DSL           -> Gradle embedded Kotlin 2.3.21
```

版本目录中的 `kotlin = "2.4.10"` 不会覆盖 AGP 9.2 的 built-in Kotlin。它用于 `aster-compiler` 等显式应用 Kotlin JVM/KAPT plugin 的模块。`./gradlew --version` 显示的 Kotlin `2.3.21` 是 Gradle 自身用于 Kotlin DSL 的 embedded Kotlin，也不能当作 Android 或 compiler 的 KGP 版本。

### 8.3 候选兼容通道

以下组合用于后续建立 CI fixture，不是当前支持范围：

| 等级 | AGP | Gradle | Android Kotlin | JVM KGP | KSP | compileSdk 上限 | JDK | minSdk / JVM target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 支持且已验证 | `9.2.1` | `9.6.1` | built-in Kotlin，AGP artifact 解析 KGP `2.2.10` | `2.4.10` | `2.3.10` | `37.0`，当前 `37` | daemon `21`，launcher `17` | `24` / `17` |
| 官方范围内但未验证 | `8.10.0` | `8.11.1` | `org.jetbrains.kotlin.android` `2.4.10` | `2.4.10` | `2.3.10` | `36.0` | `17` | `24` / `17` |
| 官方范围内但未验证 | `8.7.3` | `8.9` | `org.jetbrains.kotlin.android` `2.4.10` | `2.4.10` | `2.3.10` | `35` | `17` | `24` / `17` |

AGP 8.x 没有默认 built-in Kotlin，验证 fixture 必须显式应用 `org.jetbrains.kotlin.android`。AGP `8.7.3` 出现在候选通道中不代表最低支持版本已经成立。

下列组合直接归类为不支持：

* AGP 9.2 配合低于 `9.4.1` 的 Gradle。
* AGP 9.2 使用低于 17 的 Gradle 运行 JDK。
* AGP 9.2 使用高于 `37.0` 的 compileSdk。
* 消费 `aster-runtime` 时使用低于 24 的 minSdk。
* 启用 AGP 9.x built-in Kotlin 后仍在 Android 模块应用 `org.jetbrains.kotlin.android`。
* Java `targetCompatibility` 与 Kotlin `jvmTarget` 不一致。

未列出的 AGP、Gradle、Kotlin、KSP、JDK 或 SDK 组合默认为“未验证”，而不是自动判定为兼容或不兼容。尤其不能根据 KSP `2.3.x` 的版本号与 Kotlin 解耦，就推断任意 KSP/Kotlin/AGP 组合都兼容。

### 8.4 版本升级规则

修改矩阵中的任何一个维度后，都要创建新的完整验证行，至少执行：

1. 使用真实 AGP/KSP 构建 application 和 library 的 debug/release Variant。
2. 执行 compiler TestKit，覆盖 Kotlin/Java 源码、多轮处理和增量删除。
3. 检查生成 Registry、AAR Manifest、最终 merged Manifest 和 Runtime 加载。
4. 执行 minified release，检查 consumer rules 和最终 DEX。
5. 记录运行 JDK、Java/Kotlin target、compileSdk、minSdk，不能只记录 AGP/KSP/Kotlin。

升级决策顺序：先根据 AGP 发布说明锁定 Gradle、JDK 和 compileSdk 上限，再检查 Kotlin/KGP 的官方 Gradle/AGP 范围，随后选择 KSP 并运行真实 fixture，最后独立验证 minSdk 和 desugaring。

### 8.5 官方来源

* [AGP 9.2 发布说明与兼容表](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
* [AGP 9.2.1 官方 POM 与 built-in KGP 依赖](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.2.1/gradle-9.2.1.pom)
* [AGP 8.10 发布说明与兼容表](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
* [AGP 8.7 发布说明与兼容表](https://developer.android.com/build/releases/agp-8-7-0-release-notes)
* [Gradle 9.6.1 Java 兼容表](https://docs.gradle.org/9.6.1/userguide/compatibility.html)
* [Kotlin Gradle plugin 的 Gradle/AGP 完全支持范围](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin)
* [Android built-in Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)
* [Android 的 Kotlin、D8 与 R8 兼容表](https://developer.android.com/build/kotlin-support)
* [KSP 2.3.0 发布说明](https://github.com/google/ksp/releases/tag/2.3.0)
* [KSP 2.3.10 发布说明](https://github.com/google/ksp/releases/tag/2.3.10)
* [Android 构建中的 JDK、toolchain 与 targetCompatibility](https://developer.android.com/build/jdks)
* [AndroidX Activity minSdk 调整记录](https://developer.android.com/jetpack/androidx/releases/activity#1.12.0-alpha06)

## 9. 测试与验证

常用命令：

```bash
./gradlew :aster-gradle-plugin:test :aster-gradle-plugin:validatePlugins
./gradlew :aster:aster-compiler:test
./gradlew :aster:aster-runtime:testDebugUnitTest
./gradlew :aster:aster-runtime:lintDebug
./gradlew :feature:user-impl:bundleReleaseAar
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:assembleDebugAndroidTest
```

测试职责：

* plugin 测试覆盖 DSL、segment 服务和 Manifest 任务，但当前 AGP/KSP host 是 Fake 实现。
* compiler TestKit 覆盖真实 KSP、多轮处理、Kotlin/Java 构造函数和增量变更。
* runtime 单元测试覆盖注册会话、反射边界、能力实例策略、类型唯一解析和目标校验。
* app instrumentation 覆盖最终 Manifest 注册、导航参数和 Android 启动边界。

发布前还需要保留真实 AGP/KSP fixture、外部 AAR fixture、Variant fallback 和 minified release 验证。

## 10. 当前技术债

### 10.1 androidTest Registry 同名

全局 `ksp(...)` 会让 `debugAndroidTest` 生成与 app `debug` 相同全限定名的 Registry。测试 APK classloader 可能用空测试 Registry 遮蔽目标 APK Registry。当前不支持在 androidTest 源集中声明 Aster 注解。

长期方案应由插件按生产 Variant 自动接入 compiler，或为测试 Registry 定义独立身份和合并规则。

### 10.2 compiler 接入未强制

插件只检查 KSP plugin，没有检查 `aster-compiler` 是否进入对应 Variant。遗漏 processor 时 Manifest 仍会生成，Runtime 最终只 warning 并使用不完整注册表。

### 10.3 真实兼容矩阵不足

插件功能测试使用 Fake AGP/KSP。当前完整矩阵只有 AGP `9.2.1`、Gradle `9.6.1`、built-in Kotlin、JVM KGP `2.4.10` 和 KSP `2.3.10` 一行；AGP 8.10.0 与 8.7.3 候选通道、最低版本、flavor、更多 fallback 和插件 classloader 仍需要真实 fixture。

### 10.4 协议未版本化

固定发现标记 `com.whisper.aster.registry` 不承载协议版本。当前要求 plugin、compiler 和 runtime 使用同一发布版本；如果发布后需要让
旧 AAR 与新宿主独立组合，应先定义兼容读取周期、版本协商和依赖平台约束，不能把固定发现标记本身视为兼容性证明。

### 10.5 compiler 单 JAR 分发

compiler 依赖 KotlinPoet 和 KSP API。通过 Maven/Gradle metadata 使用时可以传递依赖，但只复制单个 JAR 不完整。文件分发需要依赖集合或经过评估的 shaded artifact。

### 10.6 Build 全局 segment 边界

共享 BuildService 只能看到实际配置的源码模块，不覆盖外部 AAR、独立 Included Build，以及可能被 Configuration on Demand 或 Isolated Projects 跳过的模块。Runtime 冲突检查仍是最终保护。

## 11. Aegis 保护范围

当前受保护类型:

* Runtime 公开契约: `Aster`、`Capability`、`Postcard`、`Route`、`Capable`、`AsterRegistrar`、
  `AsterRegistryInstaller`。
* Runtime 关键行为: `RoutePathValidator`、`CapabilityNameValidator`、`ManifestRegistryLoader`、
  `RegistrationSession`、`CapabilityRegistry`、`RouteRegistry`。
* Compiler 协议: `AsterCompilerContract`、`AsterProcessorProvider`、`AsterRegistryWriter`、`RouteParser`、
  `CapabilityParser`、`AsterValidator`。
* Gradle 协议: `AsterExtension`、`AsterPlugin`、`GenerateAsterManifestTask`、`AsterSegmentRegistryService`。

保护范围聚焦公开 API、校验规则、生成 Registry ABI、Manifest metadata 和生命周期语义。`AsterProcessor` 的轮次编排、
AGP/KSP 集成类、Variant 接线和兼容性适配仍允许在不改变上述受保护结果的前提下演进。

## 12. 修改检查表

修改 Aster 时按影响范围选择检查项：

* 修改公开 API：检查 Kotlin/Java 调用、依赖可见性、KDoc 和 `@aegis` 受保护范围。
* 修改注解或校验：同步 compiler、runtime validator、错误信息和功能测试。
* 修改 Registry：同步三个模块的协议常量、Manifest、AAR、R8 和旧产物兼容性。
* 修改 Variant 接入：验证 application、library、debug、release、flavor、fallback 和 androidTest。
* 修改 Capability 生命周期：验证并发、失败重试、递归约束和实例发布时机。
* 修改 Postcard：验证 Activity/ContextWrapper、flags、options、Activity Result 和异常传播。
* 修改 consumer rules：至少检查 merged configuration、mapping、seeds 和最终 DEX。
