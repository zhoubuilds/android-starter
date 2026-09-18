# Foundation 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-17 | whisper | 指向 Kit 的窗口 Insets 接入方式 |
| 2026-09-17 | whisper | 说明连续分段按钮共享样式及页面扩展方式 |
| 2026-09-15 | whisper | 说明顶层标题使用 Starter Mono SemiBold |
| 2026-09-14 | whisper | 说明 Starter Mono 全局中英文字体行为 |
| 2026-09-14 | whisper | 同步 Project 与应用设置页面的主题接入 |
| 2026-09-14 | whisper | 同步首页功能模块的接入状态 |
| 2026-09-11 | whisper | 说明启动器主题图标不改变共享主题接入 |
| 2026-09-11 | whisper | 说明共享固定主题的接入, 品牌替换和资源使用 |

## 1. 模块接入

应用或业务模块依赖 foundation:

```kotlin
dependencies {
    implementation(project(":foundation"))
}
```

使用 Material 控件的模块应声明自己的 `implementation(libs.material)` 依赖, 不依赖 foundation 内部
`implementation` 依赖作为源码可见 API. Kit, Architecture 和 Quill 的既有公开依赖规则保持不变.

Foundation 的业务响应适配和网络接入见 [Network 使用文档](../network/usage.md), 业务 UI 状态见
[Architecture 使用文档](../architecture/usage.md).

## 2. 主题接入

app 使用自己的主题入口继承共享主题:

```xml
<resources>
    <style name="Theme.Androidstarter" parent="Foundation.Theme" />
</resources>
```

在 app Manifest 中设置 `android:theme="@style/Theme.Androidstarter"`. Activity 通过应用主题获得固定
浅色和深色配色. 主题继承 `Theme.Material3Expressive.DayNight.NoActionBar`, 默认跟随系统;
app 可通过 AppCompat 固定浅色或深色, 不需要注册 `DynamicColors`.
共享主题没有强制页面使用某一种布局或导航结构. 启动器主题图标由 app 的 monochrome 资源提供,
用户启用后仅改变启动器中的图标着色,
应用内配色保持不变.

Foundation 同时提供 Starter Mono 全局字体. 页面使用 Material 3 的 `textAppearanceDisplay*`、
`textAppearanceHeadline*`、`textAppearanceTitle*`、`textAppearanceBody*` 和 `textAppearanceLabel*`
语义属性即可, 不需要单独设置 `fontFamily`. JetBrains Mono 提供拉丁字符、数字、符号和编程连字,
Noto Sans Mono CJK SC 提供内置 CJK 字形, 因此中文不依赖设备厂商的系统字体回退.
字体族提供原生 Regular 400 和 SemiBold 600. 顶层目的地 Toolbar 使用
`@style/Foundation.TextAppearance.TopLevelTitle`; 其它页面继续优先使用 Material 3 语义排版属性,
需要斜体或其它字重时由 Android 按字体族和标准 `textStyle` 解析.

在布局中引用语义属性:

```xml
<com.google.android.material.textview.MaterialTextView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:textAppearance="?attr/textAppearanceBodyLarge"
    android:textColor="?attr/colorOnSurface" />
```

标题, 正文, 标签和不同背景上的内容分别选择对应角色. 需要交互状态的控件优先保留 Material 默认
ColorStateList, 不用固定颜色覆盖所有状态. 常用背景包括 `colorSurface`, `colorSurfaceContainer` 和
`colorSurfaceContainerHigh`; 描边使用 `colorOutline` 或 `colorOutlineVariant`.

连续分段选择在 `MaterialButtonToggleGroup` 上设置
`style="@style/Foundation.Widget.SegmentedButtonGroup"`, 每个子 `MaterialButton` 使用
`style="@style/Foundation.Widget.SegmentedButton"`. 单选并且始终保留选中项时, 在组上声明
`app:singleSelection="true"` 和 `app:selectionRequired="true"`.
这组样式保持两端外圆角和内部直角, 切换选中项只改变颜色. 页面仍负责至少 48dp 的触控尺寸和具体布局;
需要额外内边距或文本缩放时, 以 `Foundation.Widget.SegmentedButton` 为 parent 定义页面子样式.

新 Activity 仍需按 AndroidX 方式启用 edge-to-edge 并处理窗口 Insets. 继承主题不等于布局已经避开系统栏.
实时 padding 处理可复用 [Kit 窗口 Insets 扩展](../kit/usage.md#14-窗口-insets-padding), 页面显式选择处理边与 IME.

## 3. 替换品牌

当前品牌源文件位于 [`design/brand/`](../../design/brand/), seed 为电青 `#006A86`.
图标的石墨 `#1B2025` 和冰银 `#EAF1F5` 属于设计素材色, 不作为全部界面背景和文本的硬编码值.

颜色输入位于 [`theme-seed.properties`](../../design/brand/theme-seed.properties), 生成入口为
[`GenerateTheme.java`](../../scripts/GenerateTheme.java). 使用 Material Components `1.14.0` 官方 AAR
中的 `classes.jar` 运行生成工具, 使其 `SchemeTonalSpot` 与工程依赖版本保持一致.

在仓库根目录运行以下命令. 需要 JDK 21, `curl`, `unzip` 和访问 Google Maven 的网络:

```bash
brand_theme_dir="$(mktemp -d)"
curl --fail --location \
  https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.14.0/material-1.14.0.aar \
  --output "$brand_theme_dir/material.aar"
unzip -q "$brand_theme_dir/material.aar" classes.jar -d "$brand_theme_dir"
java --class-path "$brand_theme_dir/classes.jar" scripts/GenerateTheme.java .
```

已经从工程依赖缓存取得同版本 AAR 时, 可直接解出其 `classes.jar`, 无需重复下载. 生成工具的可选参数是
仓库根目录, 不传时使用当前目录. 临时目录只用于外部依赖, 不进入 Git.

修改输入后同步生成 `foundation/src/main/res/values/foundation_colors.xml` 和
`foundation/src/main/res/values-night/foundation_colors.xml`, 并构建 app 检查资源绑定. 种子是算法输入,
生成后的 `colorPrimary` 不一定等于原始种子. 不手动只替换某一个角色.

图标设计输入, 预览和各平台资源按 [App 使用文档](../app/usage.md) 维护. 素材备份不打包进 APK,
生成工具依赖和临时构建输出也不属于品牌备份.

## 4. 验证方式

在仓库根目录执行:

```bash
./gradlew :foundation:assembleDebug :app:assembleDebug :app:assembleRelease --no-configuration-cache
```

设备连接后可使用系统设置切换明暗模式; 也可以通过 adb 复现, 检查完成后恢复原设置:

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
```

确认同一页面在两种模式下内容清晰, 控件状态正确且系统栏不遮挡内容. 更换桌面壁纸后, 应用配色保持不变.
当前示例首页的 Function、Project 和 Settings 页面均通过应用主题使用 Foundation 的共享配色.
