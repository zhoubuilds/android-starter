# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# StarterNetworkComponentManager 通过无参构造器反射创建 API 声明的组件.
# app 使用显式 DI 映射替代反射兜底后, 应删除或收窄以下规则.
-keepclasseswithmembers,allowoptimization,allowobfuscation class * implements okhttp3.Interceptor {
    <init>();
}

-keepclasseswithmembers,allowoptimization,allowobfuscation class * implements com.whisper.architecture.network.component.OkHttpCustomizer {
    <init>();
}

-keepclasseswithmembers,allowoptimization,allowobfuscation class * implements com.whisper.architecture.network.component.RetrofitCustomizer {
    <init>();
}
