# App 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
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
当前打开的是已有测试首页; Function 和 About 页面尚未实现.

## 2. 图标和主题行为

应用使用 Material 3 Expressive 的 DayNight 无 ActionBar 主题. 系统切换浅色或深色模式时, 应用使用
对应的固定配色. 两套颜色均由电青种子 `#006A86` 生成, 更换桌面壁纸不会改变应用配色.

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

[app-config.toml](../../app-config.toml) 中的 `API_HOST` 仍为 `https://placeholder.invalid/`.
这是不可访问的模板占位地址, 当前测试首页的请求不是可用的在线服务, 本轮主题和图标接入没有将其替换为
GitHub API.

正式接入网络请求时, Endpoint 由 app 配置并在 Application 中安装网络组件管理器. Feature 使用
`ApiFactory` 创建接口, 不读取 app 的构建配置. 完整说明见 [Network 使用文档](../network/usage.md).

## 5. 后续示例入口

以下功能处于后续方案阶段:

| 模块 | 计划内容 | Aster 入口 |
| --- | --- | --- |
| `:feature:function` | 各类 View 和功能示例 | `/function/home` |
| `:feature:about` | 应用信息, 仓库信息和网络请求状态 | `/about/home` |

两个模块计划以 Activity 作为跨模块入口, 通过 Aster 导航. 当前不预先拆分为四个 API/实现模块.

About 无需自建服务器, 计划通过 GitHub 公共 API 获取仓库元数据, 并用浏览器打开
[仓库主页](https://github.com/zhoubuilds/android-starter). 公共仓库读取无需配置 Token. 页面将提供加载,
重试, 缓存和离线展示, 以适应网络不可用或匿名请求限流.

GitHub JSON 与现有 `ApiResponse<T>` 不是同一种响应格式, 后续需要在 about 数据层进行适配, 不能仅把
当前占位域名替换为 GitHub 后沿用原接口. 模块和数据边界见 [App 设计文档](design.md).
