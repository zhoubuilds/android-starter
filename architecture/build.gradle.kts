plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.whisper.architecture"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.keep")
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
}

dependencies {
    api(libs.androidx.appcompat)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.retrofit)

    implementation(libs.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
