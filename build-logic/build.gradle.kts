plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.tomlj:tomlj:1.1.1")

    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("prism") {
            id = "com.whisper.prism"
            implementationClass = "com.whisper.buildlogic.prism.PrismPlugin"
        }
    }
}
