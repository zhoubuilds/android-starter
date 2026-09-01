# Habitat 使用文档

## 修订记录

| 修订时间（CST）  | 修订人     | 修订说明                  |
|------------|---------|-----------------------|
| 2026-09-01 | whisper | 补充双父接口 Dao accessor 的 Room 约束 |
| 2026-09-01 | whisper | 明确 Dao 工厂取消异常传播边界 |
| 2026-09-01 | whisper | 明确父 accessor qualifier 的 override 校验 |
| 2026-09-01 | whisper | 明确静态注册、初始化、qualifier 和 Room 集成边界 |
| 2026-08-31 | whisper | 支持 Dao qualifier 多数据库绑定和类型安全获取 |
| 2026-08-31 | whisper | 补充编译错误定位和冲突参与者说明 |
| 2026-08-31 | whisper | 明确生成代码可访问性和声明形态约束 |
| 2026-08-31 | whisper | 明确继承 Dao accessor 的注册语义 |
| 2026-08-31 | whisper | 明确 compiler 接入条件与 Registry 缺失降级语义 |
| 2026-08-25 | whisper | 迁移到公共模板并更新插件命名空间       |
| 2026-07-28 | whisper | 整理 Habitat 接入、使用和排错说明 |

本文面向使用 Habitat 的业务开发者, 说明接入方式、数据库声明、Dao 获取、初始化顺序、限制和排错。维护实现请阅读 [开发文档](development.md), 方案取舍请阅读 [设计文档](design.md)。

## 1. 适用范围

Habitat 提供:

* 通过 Dao 类型获取唯一 Room Dao 实例, 或通过 Dao 类型和 qualifier 精确获取实例。
* application/library 装配模块的 Provider、Registry 和 Manifest 自动生成。
* 多 RoomDatabase 下的 Dao qualifier 完整性和唯一性校验。
* 在进程启动阶段一次性安装编译期已知的静态 Dao binding。

Habitat 不提供:

* 自动生成 RoomDatabase。
* 自动维护 `@Database.entities`。
* 数据库迁移、备份、加密或清理策略。
* 依赖注入、Repository 查找或按数据库名获取 Dao。
* 动态 Feature 安装后的 binding 追加、Registry 重载或运行时数据库切换。
* 手写 Registry / Provider 扩展协议。

当前已验证构建基线沿用工程基线: AGP `9.2.1`、Gradle `9.6.1`、Kotlin `2.4.10`、KSP `2.3.10`、
Room `2.8.4`、compileSdk `37`、JVM target `17`。

## 2. 接入模块类型

需要使用 Habitat 自动注册 Dao 时, 只有最终数据库装配模块需要应用 Habitat Gradle 插件和 KSP processor。该模块通常是 app,
也可以是一个被 app 依赖的 Android library。

library 只有在它拥有最终应用的完整数据库装配、且消费它的 app 不再声明另一个 Habitat 装配入口时才适合作为装配模块。普通
可复用 library 和 feature 不应应用 Habitat 插件; 否则其 Manifest Registry 会与消费 app 或其它 AAR 的固定 metadata 冲突。

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

需要使用 Habitat 自动注册 Dao 的装配模块依赖 runtime 和 compiler:

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

当前插件只检查 KSP Gradle 插件是否存在, 不会强制或自动添加 `habitat-compiler` 依赖。未接入 compiler 不会导致构建失败;
Runtime 找不到生成 Registry 时会记录 warning, 并安装空 Dao 注册表。这是受支持的容错结果, 不是自动注册 Dao 时的推荐配置。
需要自动注册 Dao 时, 最终装配模块仍必须为每个生产 Variant 接入 processor。

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

数据库及其外层声明需要对同模块生成代码可见, 可以使用 `public` 或 `internal`, 不能使用 `private`、`protected` 或
Java package-private。

Dao 方法约束:

* 必须是无参抽象方法。
* 返回类型必须是标记了 Room `@Dao` 的类型。
* accessor、Dao 类型及其外层声明必须对同模块生成代码可见, 可以使用 `public` 或 `internal`。
* 不能带 extension receiver, 不能是 `suspend`, 也不能声明类型参数。
* Dao 返回值不能 nullable。
* 可以直接声明在当前数据库中, 也可以从数据库父类或接口继承。
* 子类 override 继承方法时只注册最派生声明。
* 泛型父类 accessor 按最终数据库绑定的具体 Dao 返回类型注册。
* 同一个 Dao 类型可以出现在多个参与 Habitat 的数据库中, 但必须通过 `@HabitatDaoBinding` 消除歧义。

Entity 约束:

* Entity 仍由 Room `@Database.entities` 手动声明。
* 同一个 Entity 可以出现在多个数据库中, Habitat 不额外限制; schema 和 Dao 查询合法性由 Room 编译器检查。

### 5.1 声明 Dao binding

推荐使用语义稳定的常量作为 qualifier, 不使用数据库类名:

```kotlin
object UserStorage {

    const val ACCOUNT: String = "user.account"

    const val ARCHIVE: String = "user.archive"
}
```

同一个 Dao 由多个数据库提供时, 每个 accessor 都必须显式标记:

```kotlin
@HabitatDaoBinding(UserStorage.ACCOUNT)
abstract fun userDao(): UserDao
```

另一个数据库使用不同 qualifier:

```kotlin
@HabitatDaoBinding(UserStorage.ARCHIVE)
abstract fun userDao(): UserDao
```

绑定规则:

* 标准接入推荐为 accessor 显式声明 qualifier; 省略注解只用于唯一绑定的简化写法和现有代码兼容。
* 同一个 Dao 只有一个 accessor 时可以省略 `@HabitatDaoBinding`; 生成的内部 key 为 `null`。
* 唯一 accessor 显式标记后, 既可以带 qualifier 获取, 也可以省略 qualifier 获取。
* 同一个 Dao 有多个 accessor 时必须全部显式标记, 混用显式和省略形式会导致 KSP error。
* qualifier 不能为空白, 并且在同一个 Dao 类型内必须唯一。
* qualifier 按区分大小写的原始字符串精确匹配, Habitat 不执行 trim、大小写转换或其它规范化。
* qualifier 应是稳定、非敏感的语义常量, 不使用数据库类名、用户输入、账号标识或凭据。
* 不同 Dao 类型可以复用同一个 qualifier。
* Room 不允许同一个 RoomDatabase 直接声明或从多个父类型继承多个返回同一 Dao 类型的抽象 accessor; 多绑定用于不同数据库之间的装配。
* Habitat 不提供默认 binding; 多个 binding 下省略 qualifier 不会选择其中任意一个。
* 子类 override 已标记的 accessor 时, qualifier 不会从父声明继承, 必须在最派生 accessor 上重新标记, 否则 KSP 会在该 override 声明处报错。

如果两个无继承关系的父接口提供同签名 accessor, 直接同时继承会被 Room compiler 拒绝:

```kotlin
interface AccountDaoAccessor {

    @HabitatDaoBinding(UserStorage.ACCOUNT)
    fun userDao(): UserDao
}

interface ArchiveDaoAccessor {

    @HabitatDaoBinding(UserStorage.ARCHIVE)
    fun userDao(): UserDao
}
```

最终数据库需要显式 override, 使 Room 只处理一个 accessor, 并在该最派生声明上确定 Habitat qualifier。省略其它数据库配置后,
accessor 继承部分如下:

```kotlin
abstract class AppDatabase :
    RoomDatabase(),
    AccountDaoAccessor,
    ArchiveDaoAccessor {

    @HabitatDaoBinding(UserStorage.ACCOUNT)
    abstract override fun userDao(): UserDao
}
```

父接口上的 qualifier 不会合并或自动继承。最终 override 选择的 qualifier 是唯一有效绑定; 省略该注解会导致 Habitat KSP error。

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
* 入口及其外层声明必须对同模块生成代码可见, 可以使用 `public` 或 `internal`。
* 返回类型必须是当前数据库类型。
* 返回值不能 nullable。
* 属性和函数都不能带 extension receiver。
* 函数入口不能声明参数, 不能是 `suspend`, 也不能声明类型参数。

不支持的实例入口或 Dao accessor 会由 KSP 直接在原始声明处报告文件、行号和具体原因, 不会继续生成必然无法编译的
Provider。`@HabitatDaoBinding` 未落在参与 Habitat 数据库继承层级的 Dao accessor 上、同一 Dao 的 qualifier 缺失或重复、
Provider 生成名称冲突时, KSP 会在对应 accessor 或数据库声明处列出冲突参与者。Entity 跨数据库复用和 schema 合法性由
Room compiler 处理。

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
* 第一次初始化完成后 binding 快照不可追加或替换; Registry 缺失或损坏时安装的空快照也不会通过再次调用自动重试。

`HabitatFactory.initialize()` 只安装延迟工厂, 不会初始化或读取数据库实例, 因此数据库可以先于 Habitat 初始化, 也可以在 Habitat
初始化后、第一次 Dao 获取前完成初始化。当前 `HabitatFactory.get()` 和推荐数据库实例写法都能等待各自正在进行的初始化发布完成;
但完全未初始化仍属于使用错误, 会直接抛异常。

## 8. 获取 Dao

按 Dao 类型获取:

```kotlin
val userDao: UserDao? = HabitatFactory.get(UserDao::class)
```

或使用 reified 版本:

```kotlin
val userDao: UserDao? = HabitatFactory.get<UserDao>()
```

按 Dao 类型和 qualifier 精确获取:

```kotlin
val userDao: UserDao? = HabitatFactory.get(
    UserDao::class,
    UserStorage.ACCOUNT,
)
```

或使用 reified 版本:

```kotlin
val userDao: UserDao? = HabitatFactory.get<UserDao>(UserStorage.ACCOUNT)
```

返回值为 nullable:

* 目标 Dao 只有一个绑定时, 非限定获取返回该唯一绑定, 无论 accessor 是否显式标记。
* 目标 Dao 有多个绑定时, 非限定获取记录 warning 并返回 `null`, 不会任意选择数据库。
* Dao 未注册时返回 `null`。
* qualifier 为空、未注册或不属于请求的 Dao 类型时返回 `null`。
* Dao 工厂抛出普通 `Exception` 或发生链接错误时返回 `null`; `CancellationException` 会原样传播, JVM `Error` 等不可恢复错误也不会被吞掉。
* Dao 工厂返回类型不匹配时返回 `null`。

Provider 和 `HabitatFactory` 保存的是延迟工厂函数。安装 Registry 时不会访问数据库实例或缓存 Dao; 只有成功命中绑定后才会
调用对应数据库 accessor。每次 `get()` 都会调用一次工厂; Dao 是否复用由 RoomDatabase accessor 自身决定。

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

* Dao 方法是否声明在 `@HabitatDatabase` 数据库中, 或由该数据库继承。
* Dao 返回类型是否标记了 Room `@Dao`。
* 装配模块是否添加 `ksp(project(":habitat:habitat-compiler"))`。
* 是否执行了 `HabitatFactory.initialize(application)`。

### 10.2 `Dao binding not found` 或 `Multiple Dao bindings found`

检查:

* `HabitatFactory.get()` 使用的 qualifier 是否与 accessor 上的 `@HabitatDaoBinding` 完全一致。
* 同一个 Dao 存在多个 binding 时是否使用了带 qualifier 的重载。
* qualifier 是否为非空白的稳定常量。

### 10.3 `Missing KSP option 'habitat.registryPackage'`

检查装配模块是否应用 `com.whisper.habitat` 插件, 并且 Android plugin 是否在 Habitat 之前应用。

### 10.4 `Only one Habitat assembly module is allowed`

同一个 Gradle Build 中有多个源码模块应用了 `com.whisper.habitat`。只保留最终数据库装配模块上的插件。

### 10.5 生成 Registry 为空

检查:

* 是否存在 `@HabitatDatabase`。
* 数据库类是否继承 RoomDatabase。
* 数据库是否声明唯一的 `@HabitatDatabaseInstance`。
* KSP 是否有 error 或 final deferred symbol。

### 10.6 运行时 Registry class not found

Runtime 会记录 warning 并安装空 Dao 注册表。需要自动注册 Dao 时检查:

* 装配模块是否为当前 Variant 接入 `habitat-compiler`。
* merged Manifest 中是否存在 `com.whisper.habitat.registry`。
* metadata value 是否指向 `<android.namespace>.habitat.generated.GeneratedHabitatRegistry`。
* release 包是否保留 Registry 类名和 public 无参构造。
