# Prism 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                              |
|-----------------|---------|---------------------------------------|
| 2026-08-31      | whisper | 调整 BuildConfig 辅助任务名称         |
| 2026-08-31      | whisper | 建立 Prism 实现, 维护和验证说明       |

本文面向 Prism 维护者, 描述源码结构, 插件执行流程, 稳定契约, 维护边界和验证方式. 方案取舍见 [设计文档](design.md), 工程接入见
[使用文档](usage.md).

## 1. 模块结构

```text
build-logic/
|- settings.gradle.kts
|- build.gradle.kts
`- src/
   |- main/kotlin/com/whisper/prism/gradle/
   |  |- PrismPlugin.kt
   |  |- PrismAppConfigExtension.kt
   |  `- PrismBuildConfigTaskRegistrar.kt
   `- test/kotlin/com/whisper/prism/gradle/
      |- PrismPluginFunctionalTest.kt
      `- FakeAndroidApplicationPlugin.kt
```

| 文件                                | 职责                                                        |
|-------------------------------------|-------------------------------------------------------------|
| `PrismPlugin.kt`                    | 选择和解析配置, 校验契约, 配置 Android DSL                  |
| `PrismAppConfigExtension.kt`        | 提供类型安全的 `prismAppConfig.get<T>(name)`                |
| `PrismBuildConfigTaskRegistrar.kt`  | 按最终 Android 构建变体挂接 BuildConfig 聚合任务             |
| `PrismPluginFunctionalTest.kt`      | 使用 Gradle TestKit 验证配置, 诊断和任务依赖                |
| `FakeAndroidApplicationPlugin.kt`   | 为功能测试提供最小 Android DSL 和 Variant 行为              |

`build-logic` 使用 `kotlin-dsl`, 以 `compileOnly` 依赖 AGP 实现, 通过 `org.tomlj` 解析 TOML. 插件坐标为
`com.whisper.prism`.

## 2. 插件执行流程

```text
PrismPlugin.apply
  |- 创建 prismAppConfig 扩展
  |- 注册 generatePrismBuildConfigSources 聚合任务
  |- 等待 com.android.application 或 com.android.library
  |    |- 选择并解析 app config TOML
  |    |- 冻结 exports
  |    |- 写入 defaultConfig
  |    |- 有 environments 时创建 env 维度和 product flavors
  |    `- 使用 Android Components 挂接各 Variant 的 BuildConfig 任务
  `- afterEvaluate
       |- 未找到 Android 插件时失败
       `- 警告手动补充的 env flavors
```

application 和 library 分支必须保持相同的配置语义. 两者使用不同 AGP 扩展类型, 但最终都委托给相同的解析模型和
`VariantDimension.applyVariantConfig()`.

## 3. 稳定契约

以下标识符和行为已被接入脚本或配置文件使用, 修改时按公开契约评估兼容性并同步迁移文档:

| 契约                         | 当前值或行为                                          |
|------------------------------|-------------------------------------------------------|
| 插件 ID                      | `com.whisper.prism`                                   |
| 构建脚本扩展                 | `prismAppConfig`                                      |
| 显式配置属性                 | `prism.appConfig.file`                                |
| 默认配置文件                 | 根工程 `app-config.toml`                              |
| 环境维度                     | `env`                                                 |
| 顶层配置分组                 | `values`, `exports`, `default`, `environments`        |
| 可注入 Android 配置分组      | `buildConfig`, `manifestPlaceholders`, `resValues`    |
| 引用命名空间                 | 仅允许 `values.*`                                     |
| BuildConfig 聚合任务         | `generatePrismBuildConfigSources`, 任务组为 `prism`   |

`env` 维度只在 `environments` 非空时创建. 每个 environment 名称都成为该维度下的 product flavor. 手动声明但未出现在 TOML
中的 `env` flavor 只产生警告, 不会自动并入 Prism 的已管理环境集合.

## 4. 解析和校验边界

解析顺序为 `values`, `exports`, `default`, `environments`. `values` 先解析为不可递归的标量表, 其它分组再按需解析
`{ reference = "values.<name>" }`.

维护解析逻辑时保持以下约束:

* 未知顶层字段和未知变体配置分组直接失败.
* 配置错误包含配置文件路径; 深层错误同时包含 TOML 结构路径.
* `values` 不支持引用, 所有引用只能指向已经解析的 `values.*`.
* `exports` 同时支持标量字面量和引用, 不要求每个导出都经过 `values`.
* BuildConfig 字段名遵守 Java 标识符规则, 字符串必须正确转义为 Java 字面量.
* `manifestPlaceholders` 和 `resValues.value` 最终必须是字符串.
* 非有限 `Double` 不进入 BuildConfig.
* 环境名称不能为空, 不能含空白, 不能重复且不能以 `test` 开头.

空 TOML 和不含环境的 TOML 都是合法输入. 前者不产生任何导出或 Android 配置; 后者仍应用 `exports` 和 `default`, 但不创建
`env` 维度.

## 5. Android 和 Gradle 边界

* 仅使用 AGP 公开 DSL 和 Android Components API, 不接入 AGP 私有任务或内部类型.
* Prism 不自动应用 Android 插件. 接入顺序错误或没有 Android application/library 插件时应给出明确错误.
* Prism 不自动开启 `buildFeatures.buildConfig` 或 `buildFeatures.resValues`.
* Prism 不直接设置 `applicationId`, 版本, 签名或其它应用发布元数据.
* `prismAppConfig` 在解析后冻结, 避免其它构建逻辑修改已导出值.
* `generatePrismBuildConfigSources` 根据 Android Components 提供的完整 Variant 名称挂接任务, 不能自行拼装 flavor 组合.
* 多个 flavor dimension 必须保留 AGP 原生笛卡尔积和命名顺序.
* 未接入 Prism 的项目和模块必须继续使用标准 Android 构建配置, 不能依赖插件产生的隐式全局状态.

## 6. 修改同步规则

修改以下内容时需要同步更新对应位置:

| 变更                         | 必须同步                                                       |
|------------------------------|----------------------------------------------------------------|
| TOML 分组, 字段或值类型      | 解析器, 错误诊断, 功能测试, `usage.md`                         |
| 插件 ID 或扩展 API           | 插件声明, 示例 app, 退出步骤, 根 README                        |
| `env` 维度或环境名称规则     | application/library 分支, 组合变体测试, 设计和使用文档         |
| BuildConfig 任务挂接         | 注册器, feature 开关测试, 多维度变体测试                       |
| 配置文件选择和回退           | 路径解析, 缺失文件测试, `app-config.toml` 注释, 使用文档       |
| AGP 或 Gradle 基线           | 版本目录, 功能测试, 示例 app 编译和根 README                   |

Prism 是公共模板的可选能力. 新增字段前应确认它属于通用 Android 构建配置, 不把具体应用的域名, token, 错误码, 渠道或页面规则
固化到插件实现.

## 7. 验证

插件功能测试使用 Gradle TestKit, 当前覆盖:

* 默认和显式配置文件的选择, 缺失和回退行为.
* `exports` 的标量字面量, `values` 引用和类型读取.
* BuildConfig feature 开启和关闭时的聚合任务依赖.
* `env` 与其它 flavor dimension 组合后的完整 Variant 任务名.
* 环境名称, BuildConfig 字段名和引用错误的结构化诊断.

修改解析, 环境维度或任务挂接后至少运行:

```bash
./gradlew -p build-logic test
```

修改公共插件行为后继续验证示例 app 的配置和调用方编译:

```bash
./gradlew \
  :app:generatePrismBuildConfigSources \
  :app:compileDebugKotlin \
  --no-configuration-cache
```

涉及 `env` 时, 还应使用至少两个 environment 和一个额外 flavor dimension 的测试配置, 确认组合 Variant, 字段覆盖和辅助任务均
使用 AGP 提供的真实 Variant 名称. 验证结束后不要把测试域名, 临时属性或本机配置提交到公共模板.
