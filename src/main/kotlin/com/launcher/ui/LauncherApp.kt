package com.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.launcher.model.AppConfig
import com.launcher.model.loadAppConfigs
import com.launcher.process.ProcessManager
import com.launcher.ui.theme.LauncherTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherApp(processManager: ProcessManager, configFile: File) {
    var apps by remember { mutableStateOf(loadAppConfigs(configFile)) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        try {
            apps = loadAppConfigs(configFile)
            loadError = null
        } catch (e: Exception) {
            loadError = "Failed to load config: ${e.message}"
        }
    }

    // Auto-start apps on first composition
    LaunchedEffect(Unit) {
        apps.filter { it.autoStart }.forEach { config ->
            processManager.start(config)
        }
    }

    LauncherTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Application Launcher",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload config")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                loadError?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No applications configured.\nEdit apps.json to add applications.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    AppGrid(apps, processManager)
                }
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppConfig>, processManager: ProcessManager) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(apps, key = { it.id }) { config ->
            val status by processManager.statusFlow(config.id).collectAsState()
            val logLines by processManager.logFlow(config.id).collectAsState()

            AppCard(
                config = config,
                status = status,
                logLines = logLines,
                onStart = { processManager.start(config) },
                onStop = { processManager.stop(config.id) }
            )
        }
    }
}
