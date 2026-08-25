plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.auto.service.annotations)
    kapt(libs.auto.service)
    implementation(libs.kotlinpoet)
    implementation(libs.ksp.symbol.processing.api)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit)
}

tasks.test {
    dependsOn("jar")
    systemProperty("habitat.test.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("habitat.test.kspVersion", libs.versions.ksp.get())
}
