# Optional BuildConfig Plugin

This included build contains only the optional `com.whisper.starter.build-config-fields` plugin. It does not configure Android SDK versions, dependencies, build types, or other project conventions.

The plugin maps Gradle properties with the following format to Android application `BuildConfig` fields:

```properties
generate.<default|buildType>.<string|int|boolean|float|double|long>.<name>=value
```

The starter application opts in through two declarations:

- `includeBuild("build-logic")` in `settings.gradle.kts`.
- `id("com.whisper.starter.build-config-fields")` in `app/build.gradle.kts`.

Projects that do not need property-driven `BuildConfig` fields can remove both declarations and the corresponding `generate.*` properties. Application code must then stop referencing those custom fields or declare replacements directly through the Android DSL.
