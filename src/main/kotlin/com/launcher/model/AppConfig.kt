package com.launcher.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PortConfig(
    val port: Int,
    val label: String = ""
)

@Serializable
data class AppConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val path: String,
    val command: String,
    val color: String = "#1A365D",
    val group: String = "",
    val tags: List<String> = emptyList(),
    val url: String = "",
    val autoStart: Boolean = false,
    val ports: List<PortConfig> = emptyList()
)

@Serializable
data class AppConfigFile(
    val version: String = "1.0",
    val apps: List<AppConfig> = emptyList()
)

@Serializable
data class AppOverride(
    val path: String? = null,
    val command: String? = null
)

@Serializable
data class LocalConfigFile(
    val overrides: Map<String, AppOverride> = emptyMap()
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Resolves environment variables in a string.
 * Supports both ${VAR_NAME} and %VAR_NAME% formats.
 */
private fun resolveEnvVars(input: String): String {
    var result = input

    // Resolve ${VAR_NAME} format
    val dollarPattern = """\$\{([^}]+)\}""".toRegex()
    dollarPattern.findAll(input).forEach { match ->
        val varName = match.groupValues[1]
        val value = System.getenv(varName) ?: ""
        result = result.replace(match.value, value)
    }

    // Resolve %VAR_NAME% format (Windows style)
    val percentPattern = """%([^%]+)%""".toRegex()
    percentPattern.findAll(result).forEach { match ->
        val varName = match.groupValues[1]
        val value = System.getenv(varName) ?: ""
        result = result.replace(match.value, value)
    }

    return result
}

/**
 * Resolves a path to an absolute path.
 * Handles both absolute paths and relative paths (resolved from current working directory).
 */
private fun resolveAbsolutePath(path: String): String {
    val file = File(path)
    return if (file.isAbsolute) {
        file.absolutePath
    } else {
        file.canonicalFile.absolutePath
    }
}

/**
 * Applies local overrides to an app config and resolves environment variables.
 */
private fun applyOverrides(config: AppConfig, override: AppOverride?): AppConfig {
    val resolvedPath = override?.path?.let { resolveEnvVars(it) } ?: resolveEnvVars(config.path)

    return config.copy(
        path = resolveAbsolutePath(resolvedPath),
        command = override?.command ?: config.command
    )
}

/**
 * Loads app configurations from apps.json and optionally merges with apps.local.json.
 *
 * Features:
 * - Environment variable substitution: ${VAR_NAME} or %VAR_NAME%
 * - Local overrides: apps.local.json can override paths per app ID
 */
fun loadAppConfigs(file: File): List<AppConfig> {
    if (!file.exists()) return emptyList()

    // Load main config
    val configFile = json.decodeFromString<AppConfigFile>(file.readText())

    // Load local overrides if present
    val localFile = File(file.parentFile, "apps.local.json")
    val localConfig = if (localFile.exists()) {
        json.decodeFromString<LocalConfigFile>(localFile.readText())
    } else {
        LocalConfigFile()
    }

    // Apply overrides and resolve environment variables
    return configFile.apps.map { app ->
        val override = localConfig.overrides[app.id]
        applyOverrides(app, override)
    }
}
