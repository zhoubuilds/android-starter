# App 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 同步独立 Settings feature 的页面归属 |
| 2026-09-17 | whisper | 说明 Room schema 导出与迁移回归入口 |
| 2026-09-17 | whisper | 说明公共请求头跟随应用语言选择即时更新 |
| 2026-09-16 | whisper | 说明 GitHub Endpoint 的 TOML 配置与构建时生效规则 |
| 2026-09-16 | whisper | 更新公共占位地址与 GitHub 组件参数注入方式 |
| 2026-09-16 | whisper | 更新仅传 Application 的网络初始化方式 |
| 2026-09-15 | whisper | 说明 GitHub 请求的接口级拦截器装配 |
| 2026-09-15 | whisper | 增加 RecyclerView 分割线和布局切换演示入口 |
| 2026-09-14 | whisper | 说明三 Tab、多 Activity、颜色模式和应用语言入口 |
| 2026-09-14 | whisper | 说明项目页网络刷新与数据库缓存行为 |
| 2026-09-14 | whisper | 说明底部 Tab 与 Function/About 页面入口 |
| 2026-09-14 | whisper | 说明系统 Splash 图标行为及生成和动画配置入口 |
| 2026-09-11 | whisper | 说明启动器主题图标与应用固定配色的独立行为 |
| 2026-09-11 | whisper | 说明传统图标使用多密度 WebP, 向量图层仅用于 Adaptive Icon |
| 2026-09-11 | whisper | 说明原创 S 图标, 固定日夜主题和 Foundation 品牌资源入口 |
| 2026-09-11 | whisper | 说明 Starter 当前图标主题和后续示例入口 |

## 1. 构建与启动

构建环境遵循 [项目 README](../../README.md) 中的版本基线. 在仓库根目录执行:

```bash
./gradlew :app:assembleDebug
```

安装构建产物或从 Android Studio 启动 app 后, 启动器中显示的应用名称为 `Starter`.
首页底部提供“演示”、“项目”和“设置”三个 Tab, 默认打开演示目录, Tab 往返会保留已创建 Fragment 的页面状态.
组件详情和“关于 Starter”使用独立 Activity, 系统返回键回到对应根 Tab.

## 2. 图标和主题行为

应用使用 Material 3 Expressive 的 DayNight 无 ActionBar 主题. 设置页可以选择跟随系统、浅色或深色模式,
并在进程重启后恢复. 两套颜色均由电青种子 `#006A86` 生成, 更换桌面壁纸不会改变应用配色.
应用语言可以选择跟随系统、简体中文或英文; Android 13 及以上也会在系统应用语言设置中声明这两种语言.
后续请求的公共 `Locale` Header 使用当前选择的应用语言; 恢复跟随系统后使用 Context 资源语言, 无需重建网络组件.

图标为原创模块化 S, 使用石墨 `#1B2025`, 冰银 `#EAF1F5` 和电青品牌色. 图标兼容 API 24 / 25 的传统
形式和 API 26+ 的 Adaptive Icon, 并为 Android 13+ 提供 monochrome 单色图层.

在支持主题图标的启动器中启用该设置后, 启动器会根据壁纸和系统主题重新着色. 关闭主题图标时显示
普通品牌图标. 这项设置不影响应用内固定的浅色和深色配色.

Android 12+ 的系统启动画面使用独立的向量动画图标, 两片电青色块有 180ms 的透明度过渡.
系统决定启动画面的实际显示过程, app 不为动画增加等待. 启动窗口背景随应用明暗模式变化,
桌面图标和应用内固定配色仍按上述设置生效.

## 3. 替换应用品牌

将 Starter 用作新应用时, 按以下位置配置自己的品牌:

1. 在 [strings.xml](../../app/src/main/res/values/strings.xml) 中修改应用名称.
2. 修改 [`design/brand/theme-seed.properties`](../../design/brand/theme-seed.properties) 中的品牌种子,
   按 [Foundation 使用文档](../foundation/usage.md) 重新生成并校验两套完整配色.
3. 更新 [`design/brand/icon.json`](../../design/brand/icon.json), 同步生成传统图标, 各密度资源,
   Adaptive Icon 前景, 背景, monochrome 图层, Splash 向量图标及预览素材.
4. app 保留 `Theme.Androidstarter` 入口, 共享样式在 foundation 中维护. 本工程默认使用固定品牌色,
   `StarterApplication` 不注册动态取色.

图标由 [`generate-brand-icon.mjs`](../../scripts/generate-brand-icon.mjs) 生成. 在仓库根目录执行下面命令,
可更新 SVG 备份, `drawable` 中的向量图层和 Splash 动画图标定义, 以及 `mipmap-anydpi-v26` 中的
Adaptive Icon XML, 仅需 Node.js:

```bash
node scripts/generate-brand-icon.mjs
```

更新 PNG 预览, 商店素材和各密度 WebP 时, 还需 `sharp@0.35.4`. 可将它安装在临时工具目录中, 无需改变
Android 工程依赖:

```bash
brand_tools_dir="$(mktemp -d)"
npm install --prefix "$brand_tools_dir" --no-save --package-lock=false sharp@0.35.4
NODE_PATH="$brand_tools_dir/node_modules" node scripts/generate-brand-icon.mjs --raster
```

图标修改后应完成包含 `--raster` 的完整生成, 避免向量和位图不一致. API 24 / 25 使用各密度 WebP,
不在 `mipmap` 中放置 VectorDrawable. `icon-monochrome.svg` 备份实际用于主题图标的单色 Adaptive Icon
图层, Android 对应资源为 `drawable/ic_launcher_monochrome.xml`. `icon.png` 用于预览,
`icon-play-store.png` 是保留完整正方形背景的商店素材.

[`icon-splash.svg`](../../design/brand/icon-splash.svg) 是 Splash 底图的静态设计备份, 不包含动画时间线.
生成器维护 `ic_splash_screen.xml` 和 `ic_splash_screen_vector.xml`; 动画行为单独维护在
[`splash_accent_alpha.xml`](../../app/src/main/res/animator/splash_accent_alpha.xml), 两片电青色块共用该配置.
修改时长时只更新 [`integers.xml`](../../app/src/main/res/values/integers.xml) 中的
`splash_icon_animation_duration`, animator 和 API 31+ 主题会引用同一个值. 系统绑定位于
[`values-v31/themes.xml`](../../app/src/main/res/values-v31/themes.xml).

主题维护和平台验证要求见 [App 开发文档](development.md). 包名, applicationId 和网络环境仍按
[项目快速开始](../../README.md#快速开始) 配置.

## 4. 当前网络配置

Retrofit 使用 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL` 初始化, 不需要在
[app-config.toml](../../app-config.toml) 配置 `API_HOST`. 占位地址不会承载 Project 的真实请求.
`GithubProjectApi` 通过 `@ApplicationInterceptors` 声明 GitHub Endpoint 路由和系统参数; app 创建 Project 提供的
具体拦截器并注入构建配置中的 Endpoint 与应用 `User-Agent`, 请求执行时改写地址并添加 GitHub 共用请求头.
Project 使用无需登录的仓库详情接口; 匿名请求受 GitHub 配额限制, 不应通过频繁刷新做压力测试.

GitHub 实际地址在 `app-config.toml` 的 `[default]` 中配置:

```toml
buildConfig.GITHUB_API_ENDPOINT = "https://api.github.com/"
```

Prism 将其生成到 app 的 `BuildConfig.GITHUB_API_ENDPOINT`, 由管理器读取并注入路由拦截器.
修改后需要重新构建安装, 不提供运行时环境切换. 当前只替换 Endpoint 的 origin, 不用该配置添加路径前缀.

Application 通过 `ApiFactory.install(StarterNetworkComponentManager(this))` 安装网络组件管理器,
具体 Endpoint 和应用请求参数在管理器内部装配, GitHub 协议 Header 由 Project 拦截器维护.
Feature 使用 `ApiFactory` 创建接口, 通过注解声明组件类型, 不读取 app 的构建配置.
无依赖无参组件可由管理器反射创建, 需要注入参数的组件使用显式工厂, 生命周期统一由解析方法决定.
当前实例池自行调用集中创建入口, 调用解析方法时不传构造函数.
完整说明见 [Network 使用文档](../network/usage.md).

## 5. 示例入口

当前入口如下:

| 模块 | 当前内容 | 入口 |
| --- | --- | --- |
| `:feature:function` | RecyclerView 目录及范围选择、分格输入、线条和列表分割线详情 | 首页“演示”Tab |
| `:feature:project` | GitHub 项目概览、下拉刷新和 Room/Habitat 缓存 | 首页“项目”Tab |
| `:feature:settings` | 颜色模式、应用语言与应用信息 | 首页“设置”Tab |

三个根 Fragment 由 app 直接组合, feature 之间没有依赖, 当前不使用 Aster, 也不创建没有实际契约的 `*-api` 模块.
后续需要对外暴露路由或 Capability 时, 必须先由对应 API 模块声明稳定契约, 再接入 Aster 实现.

项目页展示 Star、Fork、开放项、主要语言、许可证、默认分支和最近推送时间, 可下拉更新或打开
[仓库主页](https://github.com/zhoubuilds/android-starter). 首次成功结果写入 Room; 15 分钟内再次进入优先直接使用缓存,
超时后自动更新, 手动下拉始终请求网络. 有缓存时请求失败不会清空页面数据.

“关于 Starter”由 Settings 模块提供, 从设置页进入后查看当前安装包的应用名称和版本, 与 GitHub 数据加载状态无关.
GitHub JSON 与现有 `ApiResponse<T>` 不是同一种响应格式, Project 使用普通 Retrofit suspend API 适配;
模块和数据边界见 [App 设计文档](design.md).

## 6. 数据库 schema 与迁移

构建时 Room 自动将 schema 导出至 `app/schemas/com.whisper.starter.database.StarterDatabase/`.
当前保存版本 1 和 2 的 JSON, 应随源码一起维护; 不修改旧快照代替数据库迁移. 版本 1 的重建来源见
[App 开发文档](development.md#5-feature-组合约束). 新版本由 Room 编译器生成, 导出本身不更改数据库版本或清除缓存.

`StarterDatabaseMigrationInstrumentedTest` 使用这些快照与生产迁移验证升级, `HabitatDatabaseInstrumentedTest`
验证 Dao 提供及读写. 运行时仅选择用户指定的模拟器, 不使用会同时操作所有连接设备的测试任务.
