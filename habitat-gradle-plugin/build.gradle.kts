import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    // Included build 独立配置仓库, 不继承主工程 settings.gradle.kts 的仓库声明.
    google()
    mavenCentral()
}

val agpApiVersion: String = "9.2.1"
val kspPluginVersion: String = "2.3.10"
val junitVersion: String = "4.13.2"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

gradlePlugin {
    plugins {
        create("habitat") {
            id = "com.whisper.habitat"
            implementationClass = "com.whisper.habitat.gradle.HabitatPlugin"
        }
    }
}

dependencies {
    // 仅用于编译期访问公开 Android Components API, 不会随插件打包.
    compileOnly("com.android.tools.build:gradle-api:$agpApiVersion")

    // 仅用于编译期访问 KspExtension 等 Gradle API, 不会随插件打包.
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspPluginVersion")

    // 插件单元测试需要 Gradle API、AGP/KSP 公开 API 和 JUnit.
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("com.android.tools.build:gradle-api:$agpApiVersion")
    testImplementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspPluginVersion")
    testImplementation("junit:junit:$junitVersion")

    // 真实 Android TestKit fixture 离线解析完整 AGP 实现, 不进入插件发布产物.
    testRuntimeOnly("com.android.tools.build:gradle:$agpApiVersion")
}
