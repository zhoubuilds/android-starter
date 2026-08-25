# Habitat 使用文档

## 修订记录

| 修订时间（CST）  | 修订人     | 修订说明                  |
|------------|---------|-----------------------|
| 2026-08-25 | whisper | 迁移到公共模板并更新插件命名空间       |
| 2026-07-28 | whisper | 整理 Habitat 接入、使用和排错说明 |

本文面向使用 Habitat 的业务开发者, 说明接入方式、数据库声明、Dao 获取、初始化顺序、限制和排错。维护实现请阅读 [开发文档](development.md), 方案取舍请阅读 [设计文档](design.md)。

## 1. 适用范围

Habitat 提供:

* 通过 Dao 类型获取 Room Dao 实例。
* application/library 装配模块的 Provider、Registry 和 Manifest 自动生成。
* 多 RoomDatabase 下的 Dao 唯一归属校验。

Habitat 不提供:

* 自动生成 RoomDatabase。
* 自动维护 `@Database.entities`。
* 数据库迁移、备份、加密或清理策略。
* 依赖注入、Repository 查找或按数据库名获取 Dao。

当前已验证构建基线沿用工程基线: AGP `9.2.1`、Gradle `9.6.1`、Kotlin `2.4.10`、KSP `2.3.10`、compileSdk `37`、JVM target `17`。

## 2. 接入模块类型

只有最终数据库装配模块需要应用 Habitat Gradle 插件和 KSP processor。该模块通常是 app, 也可以是一个被 app 依赖的 Android library。

starter 默认不在 app 中启用 Habitat。项目存在需要统一发现的 Room Dao 时, 再由唯一数据库装配模块按本文步骤显式接入。

业务 feature 模块只定义自己的 `Entity`、`Dao` 和 Repository, 不应用 Habitat 插件, 也不引用 app 数据库类。

推荐结构:

```text
feature-user-impl
  UserEntity
  UserDao
  UserRepository -> HabitatFactory.get(UserDao::class)

app
  AppDataBase
  @HabitatDatabase
  abstract fun userDao(): UserDao
```

同一个 Gradle Build 中只能有一个源码模块应用 `com.whisper.habitat`。

## 3. 配置 Gradle 插件

当前仓库通过 Included Build 提供插件:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("habitat-gradle-plugin")
}
```

在数据库装配模块中应用插件。Android plugin 必须在 Habitat 之前应用; KSP 可以在 Habitat 之前或之后应用, 但推荐保持以下顺序:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.whisper.habitat")
}
```

library 装配模块使用 `alias(libs.plugins.android.library)`。

## 4. 配置依赖

装配模块需要依赖 runtime 和 compiler:

```kotlin
dependencies {
    implementation(project(":habitat:habitat-runtime"))
    ksp(project(":habitat:habitat-compiler"))
}
```

只调用 `HabitatFactory.get()` 的业务模块需要依赖 runtime:

```kotlin
dependencies {
    implementation(project(":habitat:habitat-runtime"))
}
```

当前插件只检查 KSP Gradle 插件是否存在, 不会自动添加 `habitat-compiler` 依赖。遗漏 processor 时可能生成空 Registry 或无法生成 Registry, 因此需要检查每个生产 Variant 的 KSP 依赖。

## 5. 声明数据库

在最终装配模块的 RoomDatabase 上标记 `@HabitatDatabase`:

```kotlin
@HabitatDatabase
@Database(
    entities = [
        UserEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDataBase : RoomDatabase() {

    abstract fun userDao(): UserDao
}
```

Dao 方法约束:

* 必须是无参抽象方法。
* 返回类型必须是标记了 Room `@Dao` 的类型。
* 同一个 Dao 类型只能出现在一个参与 Habitat 的数据库中。

Entity 约束:

* Entity 仍由 Room `@Database.entities` 手动声明。
* 同一个 Entity 不能出现在多个参与 Habitat 的数据库中。

## 6. 声明数据库实例入口

每个 `@HabitatDatabase` 需要在 companion object 中声明一个 `@HabitatDatabaseInstance`。

推荐使用公开非空属性:

```kotlin
companion object {

    @Volatile
    private var INSTANCE: AppDataBase? = null

    private val initializeLock: Any = Any()

    @HabitatDatabaseInstance
    val instance: AppDataBase
        get() {
            val currentInstance: AppDataBase? = INSTANCE
            if (currentInstance != null) {
                return currentInstance
            }
            val initializedInstance: AppDataBase? = synchronized(initializeLock) {
                INSTANCE
            }
            return checkNotNull(initializedInstance) {
                "AppDataBase has not been initialized."
            }
        }

    fun initialize(context: Context) {
        INSTANCE ?: synchronized(initializeLock) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDataBase::class.java,
                "app.db",
            ).build().also { createdInstance: AppDataBase ->
                INSTANCE = createdInstance
            }
        }
    }
}
```

也支持公开无参函数:

```kotlin
@HabitatDatabaseInstance
fun instance(): AppDataBase {
    return checkNotNull(INSTANCE) {
        "AppDataBase has not been initialized."
    }
}
```

实例入口约束:

* 必须在 RoomDatabase companion object 中。
* 一个数据库只能声明一个入口。
* 入口必须 public。
* 返回类型必须是当前数据库类型。
* 返回值不能 nullable。
* 函数入口不能声明参数。

## 7. 初始化

在自定义 `Application` 中先初始化数据库, 再初始化 Habitat:

```kotlin
class StarterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDataBase.initialize(this)
        HabitatFactory.initialize(this)
    }
}
```

并在 Manifest 中声明 Application:

```xml
<application
    android:name=".StarterApplication" />
```

要求:

* 在任何 `HabitatFactory.get()` 之前调用 `HabitatFactory.initialize(application)`。
* 在 Habitat Dao 工厂真正执行前完成数据库初始化。
* 同一进程只需要初始化一次。

当前 `HabitatFactory.get()` 和推荐数据库实例写法都能等待正在进行的初始化发布完成; 但完全未初始化仍属于使用错误, 会直接抛异常。

## 8. 获取 Dao

按 Dao 类型获取:

```kotlin
val userDao: UserDao? = HabitatFactory.get(UserDao::class)
```

或使用 reified 版本:

```kotlin
val userDao: UserDao? = HabitatFactory.get<UserDao>()
```

返回值为 nullable:

* Dao 未注册时返回 `null`。
* Dao 工厂执行失败时返回 `null`。
* Dao 工厂返回类型不匹配时返回 `null`。

初始化前调用属于使用错误:

```text
HabitatFactory.initialize(application) must be called before getting DAOs.
```

## 9. 混淆规则

`habitat-runtime` 已通过 consumer rules 随 AAR 交付 R8 规则。正常使用 project dependency 或完整 AAR 依赖时, 业务模块不需要重复添加。

需要手动补规则时使用:

```proguard
-keep,allowoptimization class * implements com.whisper.habitat.runtime.registry.HabitatRegistry {
    public <init>();
    public java.util.List providers();
}
```

release 开启 R8 后至少检查:

1. runtime AAR 中存在 `proguard.txt`。
2. R8 merged configuration 中包含上述规则。
3. 最终产物中 `GeneratedHabitatRegistry` 全限定名未改变。
4. `HabitatFactory.initialize()` 和 Dao 获取在 release 包中正常工作。

## 10. 常见问题

### 10.1 `Dao not found`

检查:

* Dao 方法是否声明在 `@HabitatDatabase` 数据库中。
* Dao 返回类型是否标记了 Room `@Dao`。
* 装配模块是否添加 `ksp(project(":habitat:habitat-compiler"))`。
* 是否执行了 `HabitatFactory.initialize(application)`。

### 10.2 `Missing KSP option 'habitat.registryPackage'`

检查装配模块是否应用 `com.whisper.habitat` 插件, 并且 Android plugin 是否在 Habitat 之前应用。

### 10.3 `Only one Habitat assembly module is allowed`

同一个 Gradle Build 中有多个源码模块应用了 `com.whisper.habitat`。只保留最终数据库装配模块上的插件。

### 10.4 生成 Registry 为空

检查:

* 是否存在 `@HabitatDatabase`。
* 数据库类是否继承 RoomDatabase。
* 数据库是否声明唯一的 `@HabitatDatabaseInstance`。
* KSP 是否有 error 或 final deferred symbol。

### 10.5 运行时 Registry class not found

检查:

* merged Manifest 中是否存在 `com.whisper.habitat.registry`。
* metadata value 是否指向 `<android.namespace>.habitat.generated.GeneratedHabitatRegistry`。
* release 包是否保留 Registry 类名和 public 无参构造。
