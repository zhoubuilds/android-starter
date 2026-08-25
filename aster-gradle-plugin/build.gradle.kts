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
        create("aster") {
            id = "com.whisper.aster"
            implementationClass = "com.whisper.aster.gradle.AsterPlugin"
        }
    }
}

dependencies {
    // 仅用于编译期访问公开 Android Components API, 不会随插件打包.
    // 该版本是插件的编译基线, 不代表宿主工程必须使用相同 AGP 版本.
    compileOnly("com.android.tools.build:gradle-api:$agpApiVersion")

    // 仅用于编译期访问 KspExtension 等 Gradle API, 不会随插件打包.
    // 该版本必须与主工程使用的 KSP 版本保持一致.
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspPluginVersion")

    // 插件单元测试需要 Gradle API 和 JUnit.
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("com.android.tools.build:gradle-api:$agpApiVersion")
    // TestKit 的 KSP host stub 使用公开 KspExtension 类型.
    testImplementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspPluginVersion")
    testImplementation("junit:junit:$junitVersion")
}
