plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    id("com.whisper.habitat")
    id("com.whisper.prism")
}

android {
    namespace = "com.whisper.starter"
    compileSdk = 37

    defaultConfig {
        applicationId = prismAppConfig.get<String>("applicationId")
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(project(":foundation"))
    implementation(project(":kit"))
    implementation(project(":architecture"))
    implementation(project(":feature:function"))
    implementation(project(":feature:project"))
    implementation(project(":feature:settings"))
    implementation(project(":habitat:habitat-runtime"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    ksp(libs.androidx.room.compiler)
    ksp(project(":habitat:habitat-compiler"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)

    constraints {
        // AndroidTest 与目标 APK 复用运行时版本, 仅为 Room 迁移测试对齐 Debug 已有的传递依赖.
        debugImplementation(libs.kotlinx.serialization.core) {
            because("Room 2.8.4 migration tests require the serialization 1.8.1 runtime ABI.")
        }
    }
}
