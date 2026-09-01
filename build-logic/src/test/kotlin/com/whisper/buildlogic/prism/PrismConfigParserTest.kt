package com.whisper.buildlogic.prism

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Prism TOML 解析与配置模型映射.
 *
 * @author whisper
 * @since 2026/09/01
 */
class PrismConfigParserTest {

    private val parser: PrismConfigParser = PrismConfigParser()

    @Test
    fun parse_withLiteralsAndReferences_mapsAppConfig() {
        val config: AppConfig = parser.parse(
            source =
                """
                [values]
                apiHost = "https://api.example.com"
                enabled = true

                [exports]
                applicationId = "com.example.app"
                enabled = { reference = "values.enabled" }

                [default]
                buildConfig.API_HOST = { reference = "values.apiHost" }
                buildConfig.RETRY_COUNT = 3
                manifestPlaceholders.API_HOST = { reference = "values.apiHost" }
                resValues.app_name = { type = "string", value = "Example" }

                [[environments]]
                name = "dev"
                buildConfig.DEBUG_ENDPOINT = "https://dev.example.com"
                """.trimIndent(),
            sourcePath = "fixture.toml"
        )

        assertEquals("com.example.app", config.exportedValues["applicationId"])
        assertEquals(true, config.exportedValues["enabled"])
        assertEquals(
            BuildConfigValue(type = "String", literal = "\"https://api.example.com\""),
            config.defaultConfig.buildConfigFields["API_HOST"]
        )
        assertEquals(
            BuildConfigValue(type = "int", literal = "3"),
            config.defaultConfig.buildConfigFields["RETRY_COUNT"]
        )
        assertEquals(
            "https://api.example.com",
            config.defaultConfig.manifestPlaceholders["API_HOST"]
        )
        assertEquals(
            ResValueConfig(type = "string", value = "Example"),
            config.defaultConfig.resValues["app_name"]
        )
        assertEquals(listOf("dev"), config.environments.map(EnvironmentConfig::name))
    }

    @Test
    fun parse_whenEnvironmentNameContainsWhitespace_reportsConfigPath() {
        val error: GradleException = parseFailure(
            """
            [[environments]]
            name = " dev "
            """.trimIndent()
        )

        assertTrue(error.message.orEmpty().contains("environments[0].name must not contain whitespace"))
    }

    @Test
    fun parse_whenBuildConfigNameIsInvalid_reportsConfigPath() {
        val error: GradleException = parseFailure(
            """
            [default]
            buildConfig."INVALID-NAME" = true
            """.trimIndent()
        )

        assertTrue(
            error.message.orEmpty().contains(
                "default.buildConfig.INVALID-NAME has invalid BuildConfig field name"
            )
        )
    }

    @Test
    fun parse_whenReferenceNamespaceIsUnsupported_reportsUsePath() {
        val error: GradleException = parseFailure(
            """
            [default]
            buildConfig.INVALID_REFERENCE = { reference = "exports.applicationId" }
            """.trimIndent()
        )

        assertTrue(
            error.message.orEmpty().contains(
                "Unsupported app config reference at " +
                    "default.buildConfig.INVALID_REFERENCE: exports.applicationId"
            )
        )
    }

    @Test
    fun parse_whenExportReferenceNamespaceIsUnsupported_reportsExportPath() {
        val error: GradleException = parseFailure(
            """
            [exports]
            invalidReference = { reference = "exports.other" }
            """.trimIndent()
        )

        assertTrue(
            error.message.orEmpty().contains(
                "Unsupported app config reference at exports.invalidReference: exports.other"
            )
        )
    }

    @Test
    fun parse_whenReferencedValueIsMissing_reportsExportPath() {
        val error: GradleException = parseFailure(
            """
            [exports]
            missingReference = { reference = "values.missing" }
            """.trimIndent()
        )

        assertTrue(
            error.message.orEmpty().contains(
                "Unknown app config reference at exports.missingReference: values.missing"
            )
        )
    }

    @Test
    fun parse_whenSyntaxIsInvalid_reportsSourceLineAndColumn() {
        val error: GradleException = parseFailure("[default")

        assertTrue(error.message.orEmpty().contains("Invalid app config syntax"))
        assertTrue(error.message.orEmpty().contains("fixture.toml:1:"))
    }

    private fun parseFailure(source: String): GradleException {
        return assertThrows(GradleException::class.java) {
            parser.parse(source = source, sourcePath = "fixture.toml")
        }
    }
}
