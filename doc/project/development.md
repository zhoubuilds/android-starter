# Project 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 同步 Settings feature 的页面归属 |
| 2026-09-17 | whisper | 明确可见加载入口、刷新去重及 GitHub 限流响应头判断 |
| 2026-09-16 | whisper | 规范 Repository 冷流和页面专用管线处理器 |
| 2026-09-16 | whisper | 明确 GitHub 协议实现与 app 参数注入边界 |
| 2026-09-15 | whisper | 规范 GitHub API 级 Endpoint 与系统参数声明 |
| 2026-09-14 | whisper | 重命名 Project 模块并移除 About 子页面导航 |
| 2026-09-14 | whisper | 补充 GitHub、Room/Habitat 与模块内导航维护规则 |
| 2026-09-14 | whisper | 建立 About 模块维护规则 |

本文补充 `feature:project` 的维护约束. 通用规则见 [代码规范](../application/code-style.md) 和
[开发规范](../application/development.md), 设计边界见 [Project 设计文档](design.md).

## 1. 模块结构

```text
feature/project/
|- build.gradle.kts
`- src/main/
   |- java/com/whisper/feature/project/
   |  |- ProjectFragment.kt
   |  |- ProjectViewModel.kt
   |  `- data/
   |     |- GithubProjectRepository.kt
   |     |- database/
   |     `- network/
   |        |- GithubEndpointRoutingInterceptor.kt
   |        |- GithubProjectApi.kt
   |        `- GithubSystemParametersInterceptor.kt
   `- res/
      |- drawable/
      |- layout/
      |  |- project_fragment.xml
      |  `- project_refresh_header.xml
      |- values/project_strings.xml
      `- values-en/project_strings.xml
```

资源统一使用 `project_` 前缀. Fragment 使用 Kit 的 ViewBinding 委托, Binding 只在 View 生命周期内访问.
仓库身份图标通过当前宿主 Context 查询, 不依赖 app 的 `R` 或 `BuildConfig`.

## 2. 依赖与公开面

模块依赖 Foundation、Material、Room runtime、Habitat runtime 和 Gson, 不依赖 app 或 Function. `ProjectFragment`
为 app 组合保留 public 可见性, `ProjectRefreshHeaderView` 为 XML inflation 保留 public 可见性. Entity 与 Dao 必须允许
app 的 RoomDatabase 装配. 两个 GitHub 拦截器具体类保持 public, 供 app 的 NetworkComponentManager 创建并注入配置;
其它网络模型、Repository 和页面状态保持 internal. 这些装配入口不是稳定路由或 Capability 契约. 当前模块不应用
Aster 或 KSP.

浏览器入口使用 `Intent.ACTION_VIEW`; 没有可处理应用时给出用户可见提示. 不把完整 Activity、Context 或 Intent
保存在静态对象中.

## 3. 网络与缓存

GitHub API 保持相对路径, 并通过 `@ApplicationInterceptors` 按顺序声明
`GithubEndpointRoutingInterceptor`、`GithubSystemParametersInterceptor`. 这两个组件具有构造依赖, app 在解析方法中
使用显式工厂分别注入实际 `HttpUrl` 和应用 `User-Agent`, 不通过无参反射创建, 也不建立只用于传递配置的 app 子类.
前者按注入的 Endpoint 改写 origin; 后者在 Project 内维护并统一覆盖 `Accept: application/vnd.github+json`、
`X-GitHub-Api-Version: 2026-03-10`, 同时添加注入的有效 `User-Agent`. 这些 API 共用参数不得退回到方法级 `@Headers`.
组件实例生命周期由 app 的解析方法决定, 当前使用实例池跨 API 复用; 拦截器不能持有可变请求状态.

Retrofit 的默认 base URL 直接复用 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL`, 不重复声明构建配置,
也不能因为当前只有一个真实 Endpoint 就改为 GitHub. `https://api.github.com/` 由 app 装配, Project 不读取 app `BuildConfig`.
GitHub 原生 JSON 不经过 Foundation 的 `ApiResponse<T>` 业务 Flow CallAdapter. Repository 自行接入 Architecture
的泛型 `Business` 管线, 不改变 Retrofit suspend API 或构造虚假的响应包装.

`getCachedProject()` 返回冷 `Flow<Business.Outcome<Unit, GithubProject?>>`, 无缓存是载荷为 `null` 的成功结果.
`refreshProject()` 返回冷 `Flow<Business<Unit, GithubProject?>>`, 通过 `withLoading()` 在每次收集时先发送加载状态.
两者在收集时执行实际操作, 重复收集会重新执行; `Unit` 表示没有额外业务 Meta. 网络成功结果在映射和 Dao 写入均完成后
发送, 失败结果保留原异常, 不把已有缓存恢复成一次网络成功.

异常转换只包裹 Repository 自身的数据操作; `emit` 必须位于转换边界之外, 避免把下游处理器或 Collector 异常转成业务失败.
`CancellationException` 必须重抛, 不发送失败状态. HTTP 限流和页面错误分类继续在 Project 页面层解释.
HTTP 429 直接归类为限流; HTTP 403 必须有 `X-RateLimit-Remaining: 0` 或非负整数秒的 `Retry-After` 作为依据.
普通 403 或无效限流 Header 归类为一般错误, 不读取或展示服务器原始异常文案.

缓存新鲜期由 Repository 统一维护, 当前为 15 分钟. 首次可见时先读 Dao; 缓存不存在或过期时请求网络,
再次可见时检查已展示项目的新鲜期, 手动刷新不受新鲜期限制. Dao 读取失败先交给页面处理, 不继续访问网络.
同一 ViewModel 无数据加载失败后只保留手动重试, 不因可见回调或 Activity 重建重复请求. 只有成功映射的响应才能写入数据库;
写入失败同样作为刷新失败处理. 刷新异常不删除已有记录, 也不使用旧数据伪装为网络成功.

Habitat 遵守单装配入口: Project 只定义 Entity、Dao 和 Repository, app 持有 RoomDatabase、Room compiler、Habitat
plugin/compiler 及初始化顺序. `StarterDatabase` 必须先建立实例, 再初始化 Habitat. 修改 Entity 时同步更新数据库版本、
迁移、持久化测试和本文, 不使用 destructive migration 隐藏 schema 变化.

模块从 About 更名为 Project 时, app 数据库版本提升到 2, 通过 `Migration(1, 2)` 将
`about_github_project` 重命名为 `project_github_project`, 现有缓存内容保持不变.

## 4. 页面与导航

`ProjectFragment` 直接呈现完整项目概览, 不维护 child Fragment 返回栈. 工具栏只表达项目页标题, 应用信息和设置入口
由 Settings feature 承担.

`onResume` 与 `onHiddenChanged` 共同维护可见状态, 只有 resumed 且未 hidden 的进入边沿才调用
`ProjectViewModel.refreshIfStale()`. show/hide 不会触发生命周期切换, 不能只依赖 `onResume`;
重复可见回调也不能在快速失败后立即重试. ViewModel 构造本身不加载数据, 隐藏恢复的 Tab 不提前访问 Dao 或网络.

`KitRefreshLoadLayout` 仅启用刷新, 禁用加载更多. 边界回调委托给 NestedScrollView; header 根据 Pull、Release 和
Refreshing 状态切换箭头、文案与进度, ViewModel 完成请求后负责结束 active action.

`ProjectViewModel` 直接继承 AndroidX `ViewModel`, 显式组合 Architecture 扩展, 不为接入管线强制继承
`BusinessViewModel`. 网络刷新按 `consumeLoading(refreshProgressProcessor)`、`consumeError(projectErrorProcessor)`、
`consumeSuccessMeta()` 的顺序消费. 进度处理器负责本轮收集的开始和结束, 成功、失败、异常及取消都必须收尾;
单次刷新只有一轮收集, 不需要使用 `Cycles` 扩展. 缓存查询只消费错误和成功载荷, 不启动网络刷新进度.

`projectErrorProcessor` 将失败写入 `ProjectUiState.failure`, 保留当前项目及来源, 由 Fragment 展示空数据失败或带数据告警.
这里不使用公共 Notice 错误处理器, 也不使用会静默丢弃失败的 `BusinessErrorProcessor.NONE`. 刷新开始时清理上次失败,
通过已有 `loadJob` 避免重复请求; 操作状态不能与 `ProjectUiState` 混为一个模型.

## 5. 验证

```bash
./gradlew :feature:project:testDebugUnitTest :feature:project:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache
```

Repository 单元测试至少覆盖冷流重复收集、Loading 与结果顺序、缓存映射、网络写入、新鲜期边界、失败保留原异常、取消传播
和下游异常透明性. ViewModel 测试覆盖首次可见加载、再次可见的新鲜期边界、无数据失败的手动重试、页面错误、
刷新失败保留数据、重复刷新守卫、进度收尾以及普通 403、主限流和次级限流的区分.
Fragment Robolectric 测试覆盖隐藏初始页延迟加载、show/hide 返回后过期刷新以及无数据失败后的 Activity 重建.
App instrumented test 验证 Habitat 按类型提供 Dao 并完成 Room 往返. 连接模拟器后检查首次网络加载、下拉刷新、
失败保留缓存、仓库跳转、浅深色模式、Tab 往返和 Activity 重建.
