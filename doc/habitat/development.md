# Habitat 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                                  |
|-----------------|---------|-------------------------------------------|
| 2026-08-25      | whisper | 迁移到公共模板并更新插件命名空间与验证基线 |
| 2026-08-20      | whisper | 标记稳定契约与关键协议的 Aegis 保护范围    |
| 2026-07-28      | whisper | 整理 Habitat 模块结构、实现链路和验证基线 |

本文面向 Habitat 维护者, 描述模块边界、源码目录、KSP 处理流程、Gradle 插件协议、Runtime 行为、验证方式和当前技术债。方案取舍见 [设计文档](design.md), 业务接入见 [使用文档](usage.md)。

## 1. 模块总览

```text
habitat-gradle-plugin/       Gradle 插件 Included Build
habitat/habitat-compiler/    KSP 注解处理器
habitat/habitat-runtime/     注解、公开 API 和 Android Runtime
```

依赖方向:

```text
业务 Android 装配模块
  |- implementation -> habitat-runtime
  |- KSP processor  -> habitat-compiler
  `- Gradle plugin  -> habitat-gradle-plugin

habitat-compiler --生成代码引用--> habitat-runtime Registry API
habitat-gradle-plugin --协议协作--> habitat-compiler / habitat-runtime
```

当前插件、compiler 和 runtime 不共享单独 protocol artifact。修改以下协议常量时必须同步三端并跑全链路验证:

* KSP 参数 `habitat.registryPackage`。
* Manifest metadata name `com.whisper.habitat.registry`。
* Registry 简单类名 `GeneratedHabitatRegistry`。
* 生成包后缀 `habitat.generated`。

## 2. Gradle 插件

### 2.1 模块结构

```text
habitat-gradle-plugin/
|- settings.gradle.kts
|- build.gradle.kts
`- src/main/kotlin/com/whisper/habitat/gradle/
   |- HabitatPlugin.kt
   |- HabitatAndroidIntegration.kt
   |- HabitatKspIntegration.kt
   |- HabitatRegistryModuleService.kt
   `- GenerateHabitatManifestTask.kt
```

| 文件                                | 职责                                            |
|-----------------------------------|-----------------------------------------------|
| `HabitatPlugin.kt`                | 插件入口, 检查 Android 模块类型, 注册 AGP/KSP 集成          |
| `HabitatAndroidIntegration.kt`    | 监听 application/library Variant, 接入生成 Manifest |
| `HabitatKspIntegration.kt`        | 在 `finalizeDsl` 中读取 namespace 并写入 KSP 参数      |
| `HabitatRegistryModuleService.kt` | 校验同一个 Gradle Build 只有一个 Habitat 装配源码模块        |
| `GenerateHabitatManifestTask.kt`  | 为单个 Variant 生成包含 Registry metadata 的 Manifest |

### 2.2 插件应用流程

```text
HabitatPlugin.apply
  -> 确认 com.android.application 或 com.android.library 已应用
  -> 获取 Build 级 HabitatRegistryModuleService
  -> KSP 存在时配置 habitat.registryPackage
  -> Android Components finalizeDsl
       -> 注册唯一 Habitat 装配模块
  -> Android Components onVariants
       -> 注册 generateHabitat<Variant>Manifest
       -> addGeneratedManifestFile
```

插件要求 Android plugin 在 Habitat 之前应用。KSP 可以在 Habitat 之前或之后应用, `withPlugin()` 会处理两种顺序。

AGP 和 KSP 类型分别隔离在 `HabitatAndroidIntegration` 与 `HabitatKspIntegration`, 并以 `compileOnly` 引入。发生 `LinkageError` 时, 集成边界会转换为包含版本信息和原始 cause 的 `GradleException`。

### 2.3 Manifest 输出

插件通过 `variant.sources.manifests.addGeneratedManifestFile()` 接入生成 Manifest。当前 AGP 规整后的输出可见于:

```text
<module>/build/generated/manifests/generateHabitat<Variant>Manifest/AndroidManifest.xml
```

核心内容:

```xml
<meta-data
    android:name="com.whisper.habitat.registry"
    android:value="<android.namespace>.habitat.generated.GeneratedHabitatRegistry" />
```

插件不修改 `src/main/AndroidManifest.xml`。

## 3. KSP Compiler

### 3.1 模块结构

```text
habitat/habitat-compiler/src/main/java/com/whisper/habitat/compiler/
|- HabitatProcessorProvider.kt
`- HabitatSymbolProcessor.kt
```

| 文件                         | 职责                                             |
|----------------------------|------------------------------------------------|
| `HabitatProcessorProvider` | KSP ServiceLoader 入口, 创建处理器                    |
| `HabitatSymbolProcessor`   | 管理多轮处理、deferred symbols、校验、模型累计和 KotlinPoet 生成 |

### 3.2 处理流程

```text
Resolver
  -> 查找 @HabitatDatabase
  -> 读取 habitat.registryPackage
  -> validate() 当前轮可用声明
  -> 解析 RoomDatabase、实例入口、Dao 方法和 Entity
  -> 累计 HabitatDatabaseModel
  -> 返回 deferred symbols
  -> finish() 检查最终 deferred 和重复声明
  -> 写 Provider 和 GeneratedHabitatRegistry
```

处理器跨轮保存已解析数据库。Provider 和 Registry 统一延后到 `finish()` 生成, 避免首轮无数据库或依赖其它 KSP 生成代码时提前写入空 Registry。

最后仍有 deferred database symbols 时, `finish()` 会逐个输出带 symbol 的 KSP error, 并停止生成 Provider / Registry。

### 3.3 编译期约束

`@HabitatDatabase`:

* 目标必须是具名 RoomDatabase class。
* 必须同时标记 Room `@Database`。
* 必须继承 `androidx.room.RoomDatabase`。
* 必须在 companion object 中声明唯一的 `@HabitatDatabaseInstance`。

`@HabitatDatabaseInstance`:

* 可以标记公开非空 property。
* 可以标记公开无参 function。
* 返回类型必须等于当前 RoomDatabase 类型。
* 不能声明多个入口。

Dao 方法:

* 必须属于标记了 `@HabitatDatabase` 的 RoomDatabase。
* 必须是无参抽象方法。
* 返回类型声明必须标记 Room `@Dao`。

全局校验:

* Provider 全限定名不能重复。
* 同一个 Dao 不能被多个 Habitat 数据库注册。
* 同一个 Entity 不能被多个 Habitat 数据库声明。

### 3.4 生成代码

生成位置由 KSP 管理, 通常为:

```text
<module>/build/generated/ksp/<variant>/kotlin/
  <android.namespace>/habitat/generated/GeneratedHabitatRegistry.kt
  <android.namespace>/habitat/generated/providers/<database.package>/<Database>HabitatDaoProvider.kt
```

Provider 使用 lambda 延迟读取数据库实例:

```kotlin
override val daoFactories: Map<KClass<*>, () -> Any> = mapOf(
    UserDao::class to { AppDataBase.instance.userDao() },
)
```

如果实例入口是函数, 生成形态为:

```kotlin
UserDao::class to { AppDataBase.instance().userDao() }
```

空数据库场景也会生成空 Registry, 并输出 warning:

```text
No Habitat database was found. Generated empty Habitat registry.
```

### 3.5 增量依赖

Provider 关联 database source 和 Dao source。Registry 关联 database source、Dao source 和 Entity source。空 Registry 使用 `Dependencies.ALL_FILES`, 确保未来新增 `@HabitatDatabase` 时不会遗漏。

## 4. Runtime

### 4.1 包结构

```text
habitat/habitat-runtime/src/main/java/com/whisper/habitat/runtime/
|- HabitatFactory.kt
|- annotation/
|  |- HabitatDatabase.kt
|  `- HabitatDatabaseInstance.kt
|- registry/
|  |- HabitatDaoProvider.kt
|  `- HabitatRegistry.kt
`- internal/
   |- LogcatErrorHandler.kt
   `- registry/
      `- ManifestRegistryLoader.kt
```

| 包或文件                     | 职责                                      |
|--------------------------|-----------------------------------------|
| `HabitatFactory`         | 初始化 Registry, 安装 Dao 工厂, 按类型获取 Dao      |
| `annotation`             | 提供 KSP 使用的 SOURCE 注解                    |
| `registry`               | 生成代码与 runtime 之间的公开协议                   |
| `ManifestRegistryLoader` | 从 Application Manifest metadata 反射加载注册表 |
| `LogcatErrorHandler`     | 通过 Android Logcat 输出可恢复问题               |

### 4.2 初始化和状态发布

`HabitatFactory.initialize(application)` 只安装一次。初始化过程:

```text
读取 Manifest metadata
  -> 反射创建 GeneratedHabitatRegistry
  -> registry.providers()
  -> provider.daoFactories
  -> 过滤重复 Dao
  -> AtomicReference 发布 FactoryState
```

`FactoryState` 发布后不再修改。`get()` 在 state 为空时进入同一把初始化锁等待正在进行的初始化完成; 如果最终仍为空, 表示调用方未初始化, 直接抛异常。

### 4.3 可恢复失败

以下问题会记录 Logcat warning 并使用安全降级:

* Manifest metadata 缺失。
* metadata 指向类不存在、类加载失败或类型不符合 `HabitatRegistry`。
* Registry 构造失败。
* Registry providers 读取失败。
* Provider factories 读取失败。
* Dao 未注册。
* Dao 工厂执行失败。
* Dao 工厂返回类型不匹配或 cast 失败。

使用错误仍直接抛异常:

```text
HabitatFactory.initialize(application) must be called before getting DAOs.
```

## 5. R8 / ProGuard

`habitat-runtime` 通过 consumer rules 保留 Registry 实现类:

```proguard
-keep,allowoptimization class * implements com.whisper.habitat.runtime.registry.HabitatRegistry {
    public <init>();
    public java.util.List providers();
}
```

Registry 通过 Manifest 字符串反射加载, 因此类名、public 无参构造和 `providers()` 必须保留。Provider 由 Registry 生成代码直接引用, 不需要单独按字符串保留。

## 6. 已处理风险

| 编号  | 等级 | 结论                                     |
|-----|----|----------------------------------------|
| 2.1 | P1 | 补充 Runtime AAR consumer keep rules     |
| 2.2 | P2 | 空数据库场景也生成空 Registry                    |
| 2.3 | P2 | 补全 KSP 增量依赖来源                          |
| 2.4 | P3 | 生成包名改为插件协议维护                           |
| 2.5 | P2 | 增加 Habitat 装配模块唯一性约束                   |
| 2.6 | P1 | library 装配模块同步配置 KSP 生成包名参数            |
| 2.7 | P2 | Provider 生成路径纳入数据库全限定名                 |
| 2.8 | P2 | 拒绝 nullable `@HabitatDatabaseInstance` |
| 2.9 | P3 | 支持 KSP 多 round 和 final deferred error  |

剩余 P3 文档问题: `ManifestRegistryLoader.load()` 的 `@return` 仍描述为排序去重后的 Registry 列表, 但当前实现只读取固定 metadata 并最多创建一个 Registry。建议后续调整注释。

## 7. 验证基线

常规验证命令:

```bash
./gradlew :habitat:habitat-runtime:compileDebugKotlin -q
./gradlew :habitat:habitat-compiler:compileKotlin -q
./gradlew :habitat-gradle-plugin:compileKotlin -q
./gradlew :habitat:habitat-runtime:bundleDebugAar -q
./gradlew :habitat-gradle-plugin:test :habitat-gradle-plugin:validatePlugins -q
./gradlew :habitat:habitat-runtime:testDebugUnitTest -q
./gradlew :habitat:habitat-compiler:test -q
./gradlew test --configuration-cache -q
```

starter 默认不在 app 中启用 Habitat。接入到数据库装配模块后, 还应针对该模块验证以下生成物:

```text
<module>/build/generated/ksp/<variant>/kotlin/<android-namespace>/habitat/generated/GeneratedHabitatRegistry.kt
<module>/build/generated/ksp/<variant>/kotlin/<android-namespace>/habitat/generated/providers/<database-package>/<Database>HabitatDaoProvider.kt
<module>/build/intermediates/merged_manifest/<variant>/process<Variant>MainManifest/AndroidManifest.xml
```

专项测试覆盖正常生成、编译链路和 debug AAR consumer rules 打包。接入方还需要验证实际 Variant 的 Manifest 合并、release R8、library AAR 被外部 app 消费和异常分支运行时行为。

## 8. 测试覆盖

Habitat 已补充 dedicated test 目录:

* `habitat-gradle-plugin/src/test`: 覆盖 Manifest 任务、插件模块类型白名单和装配模块唯一性服务。
* `habitat/habitat-runtime/src/test`: 覆盖 `HabitatFactory` 未初始化使用错误和已安装 Provider 的 Dao 获取。
* `habitat/habitat-compiler/src/test`: 使用真实 compiler JAR 和 KSP Gradle 插件覆盖 Provider / Registry 生成、function 实例入口、nullable 实例入口拒绝和重复 Dao 拒绝。

后续建议继续补充:

* Gradle plugin application / library 两类模块的完整 KSP 参数配置功能测试。
* KSP 多 round 和 final deferred error 测试。
* Runtime Manifest metadata 缺失、类不存在、类型不匹配、构造失败的加载测试。
* HabitatFactory 与数据库实例初始化交错的并发测试。

旧脚手架文件的 add/delete 暂存状态已清理。提交 Habitat 变更前仍应复查 staging, 避免将无关 scaffold 残影带入提交。

## 9. Aegis 保护范围

当前受保护类型:

* Runtime 公开契约: `HabitatFactory`、`HabitatDatabase`、`HabitatDatabaseInstance`、
  `HabitatDaoProvider`、`HabitatRegistry`。
* Runtime 加载协议: `ManifestRegistryLoader`。
* Compiler 协议: `HabitatProcessorProvider`、`HabitatSymbolProcessor`。
* Gradle 协议: `HabitatPlugin`、`GenerateHabitatManifestTask`、`HabitatRegistryModuleService`。

保护范围聚焦 Dao 获取 API、注解约束、生成 Provider/Registry ABI、Manifest metadata 和单装配模块规则。
具体 RoomDatabase、Entity、Dao 及业务数据库装配不属于保护范围; AGP/KSP 集成和 Variant 接线可以在不改变受保护结果的前提下演进。
