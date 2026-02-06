package com.launcher.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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
    val url: String = ""
)

@Serializable
data class AppConfigFile(
    val version: String = "1.0",
    val apps: List<AppConfig> = emptyList()
)

private val json = Json { ignoreUnknownKeys = true }

fun loadAppConfigs(file: File): List<AppConfig> {
    if (!file.exists()) return emptyList()
    val configFile = json.decodeFromString<AppConfigFile>(file.readText())
    return configFile.apps
}
