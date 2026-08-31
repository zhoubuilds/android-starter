# Prism 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                                        |
|-----------------|---------|-------------------------------------------------|
| 2026-08-31      | whisper | 调整 BuildConfig 辅助任务名称                   |
| 2026-08-31      | whisper | 迁移至统一文档目录并补充 `env` 维度契约         |
| 2026-08-31      | whisper | 将 Prism 文档统一为中文                         |
| 2026-08-31      | whisper | 允许 `exports` 使用字面量或引用                 |

本文面向使用 Prism 的工程维护者, 说明插件接入, 配置文件选择, TOML 契约, 环境变体和退出方式. 维护实现请阅读
[开发文档](development.md), 方案取舍请阅读 [设计文档](design.md).

## 1. 适用范围

Prism 是插件 ID 为 `com.whisper.prism` 的可选 Gradle 插件. 它读取一份 TOML 应用配置, 并提供以下能力:

* 向模块构建脚本导出经过校验的标量值.
* 向 Android `defaultConfig` 写入所有构建变体共享的配置.
* 按需创建固定名称为 `env` 的 product flavor dimension 及其环境 product flavor.
* 为已开启 BuildConfig 的模块提供聚合生成任务.

Prism 不负责设置 `applicationId`, 版本, 签名或内置具体环境名称和值, 也不会自动开启 `buildConfig` 或 `resValues`. 不需要 TOML
构建配置的项目可以完全移除 Prism.

## 2. 接入插件

根工程通过 `settings.gradle.kts` 引入 `build-logic`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
}
```

在需要消费配置的 Android application 或 library 模块中, 将 Prism 放在 Android 插件之后:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.whisper.prism")
}
```

Prism 只能应用于 Android application 或 library 模块. 下层模块不应仅为了感知当前环境而应用 Prism; app 仍是环境, 域名和
应用级实现的组合根.

## 3. 选择配置文件

未指定其它路径时, Prism 读取根工程的 `app-config.toml`. 单套应用配置使用这一回退文件即可.

需要显式选择其它配置文件时, 在根工程 `gradle.properties` 中设置:

```properties
prism.appConfig.file=app-configs/example.toml
```

也可以在单次命令中传入 Gradle 属性:

```bash
./gradlew :app:assembleDebug -Pprism.appConfig.file=app-configs/example.toml
```

相对路径以根工程目录为基准, 也可以使用绝对路径. 显式指定的文件必须存在, 不会静默回退到 `app-config.toml`; 未设置属性时,
根目录回退文件必须存在. 空文件可以被解析, 但不会导出或注入任何配置.

## 4. TOML 契约

```toml
[values]
serviceApiKey = "example-api-key"

[exports]
applicationId = "com.example.app"
serviceApiKey = { reference = "values.serviceApiKey" }

[default]
buildConfig.API_HOST = "https://api.example.com"
buildConfig.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
manifestPlaceholders.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
resValues.app_name = { type = "string", value = "Example" }

[[environments]]
name = "dev"
buildConfig.API_HOST = "https://dev-api.example.com"
manifestPlaceholders.DEPLOYMENT_ENV = "dev"

[[environments]]
name = "prod"
buildConfig.API_HOST = "https://api.example.com"
manifestPlaceholders.DEPLOYMENT_ENV = "prod"
```

顶层只允许以下四个分组:

| 分组                 | 是否必需 | 职责                                                        |
|----------------------|----------|-------------------------------------------------------------|
| `[values]`           | 否       | 保存 TOML 内部可复用的标量值                                |
| `[exports]`          | 否       | 向应用了 Prism 的模块构建脚本导出值                         |
| `[default]`          | 否       | 配置所有 Android 构建变体共享的 `defaultConfig` 字段        |
| `[[environments]]`   | 否       | 创建 `env` 维度下的环境 product flavor 并配置环境差异字段   |

未知顶层分组, 未知配置字段和不支持的值类型会在 Gradle 配置阶段失败.

### 4.1 复用值和引用

`[values]` 支持 `String`, `Boolean`, TOML Integer 和有限 `Double`. 它是唯一允许被引用的命名空间, 自身不允许使用引用.

引用固定使用以下结构:

```toml
{ reference = "values.<name>" }
```

引用不能指向 `exports`, `default`, `environments` 或另一个引用, 因此不会形成递归解析或循环引用. 公共模板不得在
`values` 或其它分组中提交真实密钥.

### 4.2 导出值

`[exports]` 的每个值可以直接使用受支持的标量字面量, 也可以引用 `values.*`. 模块构建脚本通过
`prismAppConfig.get<T>(name)` 读取:

```kotlin
android {
    defaultConfig {
        applicationId = prismAppConfig.get<String>("applicationId")
    }
}
```

支持的 `T` 为 `String`, `Boolean`, `Int`, `Long` 和 `Double`. TOML Integer 由解析器保存为 `Long`; 读取为 `Int` 时,
Prism 会检查是否超出 `Int` 取值范围. 未导出, 类型不匹配或类型不受支持时, Gradle 配置会失败.

### 4.3 Android 配置字段

`[default]` 和每个 `[[environments]]` 只允许以下分组:

| 分组                   | TOML 值要求                                                        | Android DSL 行为              |
|------------------------|--------------------------------------------------------------------|-------------------------------|
| `buildConfig`          | `String`, `Boolean`, Integer, 有限 `Double` 或对应的 `values` 引用 | 调用 `buildConfigField(...)`  |
| `manifestPlaceholders` | `String` 或解析为 `String` 的 `values` 引用                        | 写入 `manifestPlaceholders`   |
| `resValues`            | 包含非空 `type` 和字符串 `value` 的表, `value` 可以使用引用        | 调用 `resValue(...)`          |

`buildConfig` 字段名必须是合法 Java 标识符. Integer 在 `Int` 范围内生成 `int`, 超出后生成 `long`. 字符串会转换为合法的
Java 字符串字面量. Manifest placeholder 的 key 包含点号时需要使用 TOML 引号:

```toml
manifestPlaceholders."provider.authorities" = "com.example.provider"
```

使用 `buildConfig` 或 `resValues` 的模块必须显式开启对应 Android 构建功能:

```kotlin
android {
    buildFeatures {
        buildConfig = true
        resValues = true
    }
}
```

## 5. `env` 环境维度

`[[environments]]` 非空时, Prism 明确使用固定名称为 `env` 的 product flavor dimension. 每个条目的 `name` 会成为该维度下
的一个 product flavor:

```text
env
|- dev
`- prod
```

环境名称必须是非空且不含空白的字符串, 不能重复, 也不能以 `test` 开头. 同名字段遵守 AGP 原生合并语义, 环境配置覆盖
`[default]` 中的共享配置.

没有 `[[environments]]` 或数组为空时, Prism 仍应用 `[exports]` 和 `[default]`, 但不会创建 `env` 维度或环境 product flavor.
因此默认模板可以只使用根目录回退配置, 不必为了单环境应用引入 flavor.

项目已有其它 flavor dimension 时, `env` 会与其它维度共同生成组合变体. 例如 `dev`, `prod` 两个环境与 `direct`, `store`
两个分发渠道会形成四组 flavor 组合, 再分别与 build type 组合. 模块可以手动补充 `env` 维度下的 flavor, 但 Prism 会输出警告;
环境 flavor 应优先统一维护在选中的 TOML 配置中.

## 6. BuildConfig 辅助任务

Prism 在应用它的模块中注册 `prism` 任务组下的 `generatePrismBuildConfigSources` 聚合任务. 开启 BuildConfig 后, 可以在
`clean` 后运行:

```bash
./gradlew :app:generatePrismBuildConfigSources
```

该任务依赖所有已配置 Android 构建变体的 BuildConfig 生成任务, 仅用于开发期预生成源码. 它不创建 flavor, 不替代
`assemble`, 也不参与打包. 模块未开启 BuildConfig 时, 聚合任务不会挂接不存在的变体任务.

## 7. 退出 Prism

不需要 Prism 时按以下顺序迁移:

1. 将 `prismAppConfig` 导出值和 TOML 中的 Android 配置迁回标准 Android DSL 或其它配置来源.
2. 从所有模块移除 `id("com.whisper.prism")`.
3. 从 `settings.gradle.kts` 移除 `includeBuild("build-logic")`.
4. 删除不再使用的 `prism.appConfig.file` 属性和 TOML 配置文件.
5. 编译所有受影响构建变体, 确认 BuildConfig, Manifest placeholder 和资源值没有遗漏.

Prism 是可选能力. 移除它不应要求 architecture, foundation 或业务模块修改代码, 环境配置仍应由 app 组合根提供.
