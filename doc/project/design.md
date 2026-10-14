# Project 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-14 | whisper | 将仓库 Capability 消费收敛为契约类型唯一解析 |
| 2026-10-13 | whisper | 通过 Project API 与 Aster 暴露仓库入口能力 |
| 2026-09-17 | whisper | 按页面可见状态检查缓存新鲜期并修正 GitHub 限流识别 |
| 2026-09-16 | whisper | 接入 Business 数据管线并保留页面独立错误处理 |
| 2026-09-16 | whisper | 将 GitHub 拦截器收敛为接收应用配置的具体组件 |
| 2026-09-15 | whisper | 使用接口级拦截器声明 GitHub Endpoint 与系统参数 |
| 2026-09-14 | whisper | 将模块收敛为独立项目概览并移出应用信息页面 |
| 2026-09-14 | whisper | 完成项目概览、下拉刷新和 Room/Habitat 缓存设计 |
| 2026-09-14 | whisper | 建立 About 示例模块与 Fragment 组合边界 |

## 1. 定位

`feature:project` 提供 Starter 的项目信息入口. 项目概览读取 GitHub 公开仓库信息, 演示真实网络请求、
下拉刷新与 Room/Habitat 缓存. 应用设置和“关于 Starter”属于 app, 不再由 Project 承载.

网络传输模型、数据库 Entity、Dao、Repository、页面状态和 GitHub 网络组件实现归 Project 所有. GitHub 固定协议
Header 由组件维护; 具体 Endpoint、应用 `User-Agent`、最终 RoomDatabase 以及进程级初始化由 app 组合根装配.
页面不伪造 GitHub 数据或数据库状态.

## 2. 组合边界

`ProjectFragment` 由 app 组合根直接创建并直接呈现“项目”Tab 内容. `feature:project` 不依赖其它 feature 实现模块,
也不引用 app 类型或资源. `feature:project-api` 声明仓库入口 Capability, Project 使用 Aster 注册具体实现.

Settings 只依赖 Project API, 通过契约类型解析唯一实现并取得公开仓库 URL. 稳定能力名仍用于 Project 实现注册.
Capability 返回静态项目身份派生的地址,
不读取页面 ViewModel、网络响应或 Room 缓存. GitHub 响应和缓存结构不进入 API 契约, 也不是 Architecture 或
Foundation 的通用能力.

Project 内部的 Retrofit API 通过 `@ApplicationInterceptors` 声明 `GithubEndpointRoutingInterceptor` 和
`GithubSystemParametersInterceptor`. 它们是分别接收 `endpoint: HttpUrl` 和 `userAgent: String` 的具体组件,
app 直接创建并提供配置, 不再使用空抽象类型配合 app 子类. 这些类型服务于 API 级网络装配, 不是跨 feature 路由或
Capability, 当前不为它们单独建立 `*-api` 模块.

## 3. 数据与状态

GitHub API 使用普通 Retrofit suspend API, 在 Project Repository 中映射原生 JSON 和可空字段. Retrofit 先基于
`NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL` 构建请求, GitHub Endpoint 拦截器在发送前按注入的地址
替换 origin 并保留 path 与 query. GitHub 系统参数拦截器统一提供推荐的 JSON media type、固定 REST API 版本和
app 注入的 `User-Agent`, API 方法不重复声明这些 Header.

Repository 将网络和缓存操作转换为 Architecture 的冷 `Business` Flow. 缓存查询返回
`Flow<Business.Outcome<Unit, GithubProject?>>`, 网络刷新返回 `Flow<Business<Unit, GithubProject?>>`,
并通过 `withLoading()` 添加加载状态. `Unit` 表示当前没有额外业务 Meta, 不为 GitHub 原生 JSON 伪造
`code/message/data` 响应包装. `Business` 描述单次操作, `ProjectUiState` 继续描述整个页面的持续状态.

ViewModel 显式组合 `consumeLoading`、`consumeError` 和 `consumeSuccessMeta`, 使用页面专用进度与错误处理器.
错误处理器将 HTTP 错误、网络错误、限流和其它异常整理为页面失败类型, 写入持续的 `ProjectUiState.failure`,
由 Fragment 呈现. 使用管线不要求接入公共通知或继承 `BusinessViewModel`; 页面仍同时表达初次加载、带缓存刷新和非阻断失败.
Repository 只转换自身操作异常, 协程取消和下游处理异常继续传播.
HTTP 429 直接识别为限流; HTTP 403 只有在剩余配额为 0 或包含有效秒数 `Retry-After` 时才识别为限流,
普通访问拒绝仍按一般页面错误呈现.

成功响应写入 `GithubProjectEntity`. 页面首次可见时读取缓存, 15 分钟内的缓存直接展示, 过期缓存先展示后自动更新;
Tab 返回或应用回到前台时再次检查已展示数据的新鲜期. 隐藏页面的创建不触发加载, 手动下拉始终强制请求网络.
缓存读取失败时由页面显示失败, 不继续启动网络请求; 同一 ViewModel 的无数据失败保留手动重试,
不会因再次可见或 Activity 重建自动重试. 网络数据成功写入缓存后才发布到页面.
刷新失败时保留已展示数据. 页面显示网络或缓存来源及对应写入时间, 使缓存行为可观察.

## 4. 界面层级

项目页使用单列滚动内容, 首屏优先展示仓库身份、描述和三项核心统计, 随后展示语言、许可证、默认分支和最近推送.
底部主按钮交给系统浏览器打开仓库. 加载、空数据失败、缓存告警和刷新头部使用各自明确状态, 不将应用设置或
无关入口塞入工具栏, 也不把页面区块堆叠成装饰卡片.
