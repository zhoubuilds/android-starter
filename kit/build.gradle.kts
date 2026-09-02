plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.whisper.kit"
    compileSdk = 37
    resourcePrefix = "kit"

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(libs.androidx.appcompat)
    api(libs.androidx.core.ktx)
    api(libs.androidx.fragment)
    api(libs.androidx.recyclerview)
    api(libs.androidx.viewbinding)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
