package com.whisper.starter.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Maps generate.<buildType>.<type>.<name> Gradle properties to BuildConfig fields.
 */
class BuildConfigFieldsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId(ANDROID_APPLICATION_PLUGIN_ID) {
            val android = target.extensions.getByType(ApplicationExtension::class.java)
            configureFields(target = target, android = android)
        }
    }

    private fun configureFields(
        target: Project,
        android: ApplicationExtension,
    ) {
        val properties = target.providers
            .gradlePropertiesPrefixedBy(PROPERTY_PREFIX)
            .get()
            .toSortedMap()

        properties.forEach { (key: String, rawValue: String) ->
            val match = PROPERTY_PATTERN.matchEntire(key)
                ?: throw GradleException(
                    "Invalid BuildConfig property '$key'. Expected " +
                        "generate.<default|buildType>.<string|int|boolean|float|double|long>.<name>."
                )
            val (variantName: String, valueType: String, fieldName: String) = match.destructured
            val javaType = valueType.toJavaType()
            val literal = rawValue.toBuildConfigLiteral(valueType)

            if (variantName == DEFAULT_VARIANT) {
                android.defaultConfig.buildConfigField(javaType, fieldName, literal)
            } else {
                val buildType = android.buildTypes.findByName(variantName)
                    ?: throw GradleException(
                        "BuildConfig property '$key' targets unknown build type '$variantName'."
                    )
                buildType.buildConfigField(javaType, fieldName, literal)
            }
        }
    }

    private fun String.toJavaType(): String = when (this) {
        "string" -> "String"
        "int", "boolean", "float", "double", "long" -> this
        else -> throw GradleException("Unsupported BuildConfig field type '$this'.")
    }

    private fun String.toBuildConfigLiteral(valueType: String): String {
        if (valueType != "string") {
            return this
        }
        val escaped = buildString(length + 2) {
            this@toBuildConfigLiteral.forEach { character: Char ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
        return "\"$escaped\""
    }

    private companion object {
        private const val ANDROID_APPLICATION_PLUGIN_ID = "com.android.application"
        private const val DEFAULT_VARIANT = "default"
        private const val PROPERTY_PREFIX = "generate."
        private val PROPERTY_PATTERN = Regex(
            "^generate\\.([A-Za-z0-9_]+)\\." +
                "(string|int|boolean|float|double|long)\\.([A-Za-z0-9_]+)$"
        )
    }
}
