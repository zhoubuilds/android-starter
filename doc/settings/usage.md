# Settings 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 说明 Settings 模块入口与当前功能 |

## 1. 页面入口

安装并启动 Starter 后, 点击首页底部“设置”Tab 打开 `SettingsFragment`. App 直接组合该 Fragment;
当前没有独立路由或 `*-api` 契约.

## 2. 当前功能

设置页支持跟随系统、浅色和深色三种颜色模式, 以及跟随系统、简体中文和英文三种应用语言.
颜色模式会在进程重启后恢复, 应用语言由 AppCompat 持久化并按需重建 Activity.

“关于 Starter”展示当前安装包的应用图标、名称、版本名和版本号. 页面使用未导出的 `AboutStarterActivity`,
系统返回键或工具栏返回按钮回到设置页.

## 3. 接入方式

最终 app 添加实现依赖:

```kotlin
dependencies {
    implementation(project(":feature:settings"))
}
```

Application 在首个 Activity 创建前恢复颜色模式:

```kotlin
override fun onCreate() {
    super.onCreate()
    AppAppearancePreferences.initialize(this)
}
```

组合根可以直接创建 `SettingsFragment`. 其它 feature 不应直接依赖 Settings 实现模块; 出现跨模块导航需求后,
应先建立稳定导航契约.
