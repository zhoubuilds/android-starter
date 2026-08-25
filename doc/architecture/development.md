# Architecture 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                         |
|-----------------|---------|----------------------------------|
| 2026-08-20      | whisper | 标记稳定基础契约的 Aegis 保护范围 |
| 2026-08-20      | whisper | 迁移 Aegis 修改保护标记           |
| 2026-07-30      | whisper | 明确通用 UI 工具迁往 Kit         |
| 2026-07-27      | whisper | 重构业务状态元信息模型           |
| 2026-07-25      | whisper | 新增 architecture 模块维护说明   |

本文面向 `architecture` 模块维护者, 描述源码目录、维护规则、测试方式和当前关注点。设计取舍见 [设计文档](design.md), 使用方式见 [使用文档](usage.md)。

## 1. 模块结构

```text
architecture/
|- build.gradle.kts
`- src/
   |- main/
   |  |- java/com/whisper/architecture/
   |  |  |- business/
   |  |  |- network/
   |  |  |- ui/
   |  |  `- viewmodel/
   |  |- AndroidManifest.xml
   |  `- keepRules/rules.keep
   `- test/java/com/whisper/architecture/
      |- business/
      |- network/
      `- ui/
```

| 包                            | 职责                                  |
|-------------------------------|---------------------------------------|
| `business.exception`          | 服务端业务错误异常包装                |
| `business.function`           | Flow 与业务状态转换辅助函数           |
| `business.model`              | `ArchitectureBusiness` 状态模型       |
| `business.processor`          | 业务错误、元信息和进度处理协议        |
| `network`                     | API 创建和网络组件声明                |
| `ui.activity` / `ui.fragment` | Architecture UI 基类                  |
| `ui.component`                | Architecture UI 状态绑定组件          |
| `ui.message`                  | 页面消息模型                          |
| `ui.state`                    | Architecture UI 状态容器              |
| `viewmodel`                   | 提供 Architecture UI 状态的 ViewModel |

## 2. 构建基线

当前模块配置:

```text
compileSdk: 37
minSdk: 24
Java target: 17
```

主要依赖:

* AndroidX AppCompat / Core KTX / Lifecycle Runtime KTX。
* Material。
* OkHttp, 通过 `api` 暴露给网络注解和组件接口。
* Retrofit, 通过 `api` 暴露给 `ApiFactory` 和 `NetworkComponentManager`。

## 3. 维护规则

### 3.1 业务无关

`architecture` 只能表达技术抽象, 不写入具体业务规则。例如:

* 不在 `ArchitectureBusiness` 中假设应用级公共响应字段。
* 不在 `BusinessException` 中解释业务错误语义。
* 不在网络层内置 token、登录态或真实域名。
* 不在 Architecture UI 状态中绑定具体页面文案。
* 不放仅表达 Android 控件行为的通用工具; 这类能力归属 `kit`。

### 3.2 Aegis 修改保护

修改类、接口、注解和函数前需要检查是否存在 `@aegis`。若存在, 调整受保护的 API 契约、可观察行为或明确实现约束前必须先请求授权;
无法证明行为等价时按受保护修改处理。

受保护的 API 契约包括:

```text
包名 / 类名 / 方法名 / 参数 / 返回类型 / 注解参数 / 默认值 / 可见性
```

当前受保护范围:

* 业务状态基础契约: `BusinessException`、`ArchitectureBusiness`、三个 `Business*Processor` 和
  `BusinessFlowExtensions.kt` 中的公开 Flow 转换函数。
* 网络基础契约: `ApiFactory`、`OkHttpClientFactory`、四个网络声明注解、`NetworkComponentManager`、
  `OkHttpCustomizer` 和 `RetrofitCustomizer`。
* Architecture UI 契约: `ArchitectureActivity`、`ArchitectureFragment`、`ArchitectureUiComponent`、
  三个 UI 消息类型、三个 UI 状态类型、`ArchitectureUiStateOwner` 和 `ArchitectureViewModel`。

这些标记只保护通用技术契约和已文档化行为, 不保护应用级业务数据、错误码解释、域名、证书、鉴权 Header、页面文案或具体业务状态。

### 3.3 注释和类型

新增 Kotlin 类型时遵守项目根目录 `AGENTS.md`:

* 类、接口、数据类、注解类使用中文文档注释。
* `@author` 读取 `local.properties` 中的 `author`, 当前为 `whisper`。
* `@since` 使用类创建日期。
* 变量、常量和参数保持显式类型声明。
* 异常信息和日志使用英文。

## 4. 测试

当前单元测试入口:

```bash
./gradlew :architecture:testDebugUnitTest
```

重点覆盖:

* `BusinessFlowExtensionsTest`: 业务状态 Flow 转换。
* `ApiFactoryTest`: 网络声明解析、执行顺序和重复声明拦截。
* `ArchitectureUiStateTest`: Architecture UI 状态更新。

修改网络组件声明、状态模型或 Architecture UI 状态并发语义时, 需要同步补充对应单元测试。

## 5. 命名维护重点

历史命名调整已经合并到当前包结构中, 后续维护继续遵守以下规则:

* 业务状态链路按 `business.*` 领域聚合, 不回退到根级技术类型分包。
* 业务状态处理协议使用 `Processor`, 不使用容易和 Android 消息处理混淆的 `Handler`。
* `ArchitectureBusiness` 保持 `T` 和 `M` 双泛型, 应用级公共响应字段由业务公共层固定。
* `BusinessException` 只描述服务端业务错误信息摘要, 不把具体业务解释写入架构层。
* Architecture UI 相关 API 保持 `ArchitectureUi` 和 `pendingTask` 术语一致。

## 6. 当前关注点

* `network` 只提供 API 创建骨架, 默认域名和安全策略必须由 app 组合根配置。
* 多域名或动态租户路由不属于模板默认能力, 出现实际需求后再通过 app 层组件扩展。
