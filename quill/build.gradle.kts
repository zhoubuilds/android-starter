plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.whisper.quill"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.keep")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
