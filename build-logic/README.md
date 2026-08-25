# Prism Gradle Plugin

`build-logic` contains the optional `com.whisper.prism` plugin. Prism reads one TOML application configuration and applies its exported values, shared Android configuration, and optional environment product flavors.

## Apply the plugin

The root build exposes the included build from `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
}
```

Apply Prism after an Android application or library plugin:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.whisper.prism")
}
```

Prism does not enable `buildFeatures.buildConfig` or `buildFeatures.resValues`. Modules using those TOML sections must enable the corresponding Android build feature.

## Select the configuration

By default Prism reads `app-config.toml` from the root project. This is sufficient for a single application configuration.

To select another configuration explicitly, set a root Gradle property:

```properties
prism.appConfig.file=app-configs/example.toml
```

An explicit file must exist and never silently falls back. When the property is omitted, the root `app-config.toml` fallback must exist. The two files are not required at the same time.

## TOML contract

```toml
[values]
applicationId = "com.example.app"
serviceApiKey = "example-api-key"

[exports]
applicationId = { reference = "values.applicationId" }

[default]
buildConfig.API_HOST = "https://api.example.com"
buildConfig.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
manifestPlaceholders.SERVICE_API_KEY = { reference = "values.serviceApiKey" }
resValues.app_name = { type = "string", value = "Example" }

[[environments]]
name = "dev"
buildConfig.API_HOST = "https://dev-api.example.com"

[[environments]]
name = "prod"
buildConfig.API_HOST = "https://api.example.com"
```

- `[values]` stores reusable scalar values.
- `[exports]` exposes selected `values.*` entries to build scripts through `prismAppConfig.get<T>(name)`.
- `[default]` configures every variant through Android `defaultConfig`.
- `[[environments]]` is optional. When present, Prism creates an `env` flavor dimension and one product flavor per entry.
- `buildConfig`, `manifestPlaceholders`, and `resValues` are the only supported variant sections.
- References must use `{ reference = "values.<name>" }`.

Supported scalar types are String, Boolean, Integer/Long, and finite Double. Prism validates TOML structure, references, environment names, BuildConfig identifiers, and value types during Gradle configuration.

Application metadata remains explicit in the Android DSL:

```kotlin
android {
    defaultConfig {
        applicationId = prismAppConfig.get<String>("applicationId")
    }
}
```

Run `./gradlew :app:generateBuildConfig` to pre-generate BuildConfig sources for all configured variants after a clean build.

## Opt out

Projects that do not need Prism can remove `includeBuild("build-logic")`, remove `id("com.whisper.prism")`, replace any `prismAppConfig` reads with regular Android DSL values, and declare any required BuildConfig fields directly in the module build script.
