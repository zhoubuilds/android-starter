# Project 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-14 | whisper | 使用契约类型唯一解析仓库 Capability |
| 2026-10-13 | whisper | 说明 Project API 与 Aster 仓库 Capability |
| 2026-10-13 | whisper | 同步 Settings feature 的页面归属 |
| 2026-09-17 | whisper | 说明保留 Tab 的过期更新、失败重试与准确限流提示 |
| 2026-09-16 | whisper | 说明 Business 管线与页面独立错误处理示例 |
| 2026-09-16 | whisper | 更新 GitHub 拦截器配置注入和公共占位地址用法 |
| 2026-09-15 | whisper | 说明 GitHub API 的接口级网络组件声明 |
| 2026-09-14 | whisper | 说明独立 Project Tab 与模块接入 |
| 2026-09-14 | whisper | 说明项目概览、下拉刷新和本地缓存行为 |
| 2026-09-14 | whisper | 说明 About 页面入口与当前功能 |

## 1. 页面入口

安装并启动 Starter 后, 点击首页底部“项目”Tab 打开项目概览. App 直接创建 `ProjectFragment`, 页面入口不使用
Aster 路由. Project 通过独立 `project-api` 契约向 Settings About 提供仓库地址 Capability.

## 2. 当前功能

项目概览从 GitHub 读取仓库描述、公开状态、Star、Fork、开放项、主要语言、许可证、默认分支和最近推送时间.
向下拖动页面可以刷新; 点击“查看 GitHub 仓库”后交给系统浏览器打开
[android-starter](https://github.com/zhoubuilds/android-starter). 设备没有可处理网页的应用时显示提示.

成功数据写入 Room. 缓存 15 分钟内再次进入时直接显示, 过期时先显示缓存再自动更新; 手动下拉始终访问 GitHub.
项目 Tab 切回或应用回到前台时都会检查已展示数据是否过期; 页面隐藏时不会因创建而提前加载.
网络不可用、服务异常或匿名请求限流时, 已有缓存继续保留并显示提示. 没有缓存时提供重试入口,
需手动重试, 同一页面状态不会因 Activity 重建或再次进入自动重试. 普通 HTTP 403 不会被误提示为限流.

## 3. 接入方式

最终 app 通过实现依赖组合页面:

```kotlin
dependencies {
    implementation(project(":feature:project"))
}
```

其它 feature 不应直接依赖此实现模块. 仓库入口调用方只依赖 `:feature:project-api`, 并通过
`Aster.resolve<ProjectRepositoryCapability>()` 解析唯一实现. 如果最终应用依赖图中存在多个该契约的实现, Aster 会拒绝歧义解析.

GitHub 公共仓库读取不需要 Token. 默认匿名配额有限, 演示刷新时应避免短时间连续请求. Retrofit 使用
`NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL` 初始化, 无需在 `app-config.toml` 重复配置占位地址.
`GithubProjectApi` 通过 `@ApplicationInterceptors` 声明 GitHub Endpoint 路由和系统参数, app 的管理器直接创建
`GithubEndpointRoutingInterceptor(endpoint)` 与 `GithubSystemParametersInterceptor(userAgent)`, 注入实际服务地址和
应用标识. Project 拦截器负责路由与 GitHub 协议 Header, 不读取 app `BuildConfig`; API 方法不重复维护这些共用 Header.

## 4. 数据管线示例

Project 演示了不使用统一响应包装的接口如何接入 Architecture 数据管线. `GithubProjectApi` 保持普通 suspend 返回类型,
`GithubProjectRepository` 将缓存读取转换为 `Business.Outcome`, 将网络刷新转换为包含 Loading 的 `Business` 冷流.
管线 Meta 使用 `Unit`, 不要求 GitHub 提供 `ApiResponse<T>` 的字段. 创建 Flow 不触发请求, 每次收集分别执行对应操作.

`ProjectViewModel` 使用 `consumeLoading(refreshProgressProcessor)` 管理刷新进度,
使用 `consumeError(projectErrorProcessor)` 将错误交给页面, 再使用 `consumeSuccessMeta()` 脱壳成功载荷.
缓存查询复用错误和成功载荷处理, 不额外触发刷新进度. 页面专用错误处理器更新 `ProjectUiState.failure`,
Fragment 继续展示重试入口或非阻断告警, 已展示的数据保持可见, 不会改为一次性公共通知.

扩展此示例时, 可以替换页面处理器或直接消费 `Business.Outcome`, 错误是否交给公共处理由页面需求决定.
使用数据管线无需改变缓存优先、15 分钟新鲜期和手动强制刷新策略, 也无需继承 `BusinessViewModel`.
