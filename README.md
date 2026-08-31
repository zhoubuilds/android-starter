# Android Starter

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明               |
|-----------------|---------|------------------------|
| 2026-08-31      | whisper | 区分项目规范与智能体规则 |
| 2026-08-28      | whisper | 建立开源仓库入口文档   |

Android Starter 是一个持续演进的 Android 多模块起始工程. 它提供通用架构, 应用业务基座, Android 工具, 日志,
路由, 能力发现, Dao 注册和可选构建配置能力, 用于快速建立新的 Android 应用, 而不是绑定具体业务的成品应用.

项目当前仍处于开发阶段. 公共 API 和模块边界可能继续调整; 用于正式项目时建议固定到经过验证的 commit 或
release tag.

## 设计边界

* `app` 是组合根, 负责选择环境, Endpoint 和应用级实现.
* `architecture` 只提供通用技术抽象, 不解释具体业务响应, 错误码, Meta 或域名.
* `foundation` 承载应用业务基座中的响应映射, Meta 建模和领域转换.
* 下层模块不读取 app `BuildConfig`, 也不感知 product flavor.
* Prism, Aster 和 Habitat 通过独立插件接入, 不应成为所有项目的隐式前提.

## 模块

| 模块                                                    | 职责                                                   |
|---------------------------------------------------------|--------------------------------------------------------|
| [`app`](app/)                                           | 示例应用和最终装配入口                                 |
| [`architecture`](architecture/)                         | Business 管线, Architecture UI 和网络创建骨架          |
| [`foundation`](foundation/)                             | 应用业务基座及 architecture 抽象的应用级实现           |
| [`kit`](kit/)                                           | Android, View 和 RecyclerView 通用能力                 |
| [`quill`](quill/)                                       | 可插拔, 延迟构建消息的日志能力                         |
| [`aster`](aster/)                                       | Activity 路由和 Capability 发现的 runtime / compiler   |
| [`aster-gradle-plugin`](aster-gradle-plugin/)           | Aster 的 Android Variant 与 Manifest 接入              |
| [`habitat`](habitat/)                                   | Dao 注册和发现的 runtime / compiler                    |
| [`habitat-gradle-plugin`](habitat-gradle-plugin/)       | Habitat 的 Android Variant 与 Manifest 接入            |
| [`build-logic`](build-logic/)                           | 可选 Prism TOML 应用配置插件                           |

## 构建基线

| 维度                           | 当前基线   |
|--------------------------------|------------|
| Android Gradle Plugin          | `9.2.1`    |
| Gradle Wrapper                 | `9.6.1`    |
| Android compileSdk / targetSdk | `37` / `37` |
| Android minSdk                 | `24`       |
| Gradle 运行 JDK                | `21`       |
| Java / Kotlin JVM target       | `17`       |
| JVM 模块 Kotlin                | `2.4.10`   |
| KSP                            | `2.3.10`   |

推荐使用支持上述版本的 Android Studio, 并安装 Android SDK 37. 其它版本组合没有被本仓库声明为已验证
基线.

## 快速开始

在仓库根目录执行:

```bash
./gradlew :app:assembleDebug
```

开始实际应用开发前至少完成以下调整:

1. 修改 [`app-config.toml`](app-config.toml) 中的 `applicationId` 和 `API_HOST`. 默认的
   `https://placeholder.invalid/` 只用于防止模板携带真实环境地址, 不能提供实际服务.
2. 修改 [`app/build.gradle.kts`](app/build.gradle.kts) 中的 `namespace`, 并迁移
   `com.whisper.starter` 示例包.
3. 调整应用名称, 图标, 主题, 版本和签名配置.
4. 根据项目需要保留或移除 Prism, Aster, Habitat, Quill, Kit 等可选能力.

Prism 的配置契约和完整退出步骤见 [`build-logic/README.md`](build-logic/README.md).

## 验证

当前公共模块和示例 app 可以使用以下命令完成主要验证:

```bash
./gradlew \
  :architecture:testDebugUnitTest \
  :architecture:lintDebug \
  :architecture:bundleDebugAar \
  :foundation:testDebugUnitTest \
  :app:compileDebugKotlin \
  --no-configuration-cache
```

涉及 Aster, Habitat, Prism, Quill 或 Kit 时, 还应按照对应开发文档扩大测试范围.

## 文档

* Architecture: [设计](doc/architecture/design.md), [开发](doc/architecture/development.md),
  [使用](doc/architecture/usage.md).
* Network: [设计](doc/network/design.md), [开发](doc/network/development.md), [使用](doc/network/usage.md).
* Aster: [设计](doc/aster/design.md), [开发](doc/aster/development.md), [使用](doc/aster/usage.md).
* Habitat: [设计](doc/habitat/design.md), [开发](doc/habitat/development.md), [使用](doc/habitat/usage.md).
* Quill: [设计](doc/quill/design.md), [开发](doc/quill/development.md), [使用](doc/quill/usage.md).
* Kit: [开发](doc/kit/development.md), [使用](doc/kit/usage.md).

所有维护者遵守 [代码规范](doc/application/code-style.md) 和 [开发规范](doc/application/development.md). 使用智能体维护项目时,
还需遵守智能体专属的 [执行规范](AGENTS.md).

## 许可证

本项目使用 [Apache License 2.0](LICENSE). 该许可证允许个人和组织免费使用, 修改, 商用, 再许可和分发源码或
二进制产物, 同时要求分发者保留许可证及必要的归属说明, 并标明对文件所做的修改.

本项目按 "AS IS" 提供, 不提供任何明示或默示保证. 在适用法律允许的范围内, 作者和贡献者不对使用或无法使用
本项目造成的损失承担责任. 具体权利, 义务, 免责和责任限制以英文许可证原文为准.

Copyright 2025-2026 whisper.
