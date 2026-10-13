plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("com.whisper.aster")
}

android {
    namespace = "com.whisper.feature.project"
    compileSdk = 37
    resourcePrefix = "project_"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

aster {
    segment = "project"
}

dependencies {
    implementation(project(":foundation"))
    implementation(project(":feature:project-api"))
    implementation(project(":aster:aster-runtime"))
    implementation(project(":habitat:habitat-runtime"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    add("kspDebug", project(":aster:aster-compiler"))
    add("kspRelease", project(":aster:aster-compiler"))
}
