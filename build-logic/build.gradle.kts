plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
}

gradlePlugin {
    plugins {
        register("buildConfigFields") {
            id = "com.whisper.starter.build-config-fields"
            implementationClass = "com.whisper.starter.gradle.BuildConfigFieldsPlugin"
        }
    }
}
