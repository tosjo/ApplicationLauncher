package com.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.model.AppConfig
import com.launcher.model.AppStatus
import com.launcher.ui.theme.*
import java.awt.Desktop
import java.io.File
import java.net.URI

@Composable
fun AppCard(
    config: AppConfig,
    status: AppStatus,
    logLines: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLog by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when (status) {
            AppStatus.STOPPED -> StatusStopped
            AppStatus.STARTING -> StatusStarting
            AppStatus.RUNNING -> StatusRunning
            AppStatus.ERROR -> StatusError
        },
        animationSpec = tween(500)
    )

    val accentColor = parseHexColor(config.color) ?: MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: status dot + name + action icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusDot(status, statusColor)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (config.url.isNotBlank()) {
                    IconButton(
                        onClick = { Desktop.getDesktop().browse(URI(config.url)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "Open GitHub",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { Desktop.getDesktop().open(File(config.path)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Open folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (logLines.isNotEmpty()) {
                    IconButton(
                        onClick = { showLog = !showLog },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "Toggle log",
                            tint = if (showLog) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Description
            if (config.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tags + Ports row
            if (config.tags.isNotEmpty() || config.ports.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    config.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    config.ports.forEach { portConfig ->
                        val label = if (portConfig.label.isNotBlank()) {
                            "${portConfig.label} :${portConfig.port}"
                        } else {
                            ":${portConfig.port}"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (status == AppStatus.RUNNING) {
                                StatusRunning.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            },
                            modifier = Modifier.clickable(enabled = status == AppStatus.RUNNING) {
                                Desktop.getDesktop().browse(URI("http://localhost:${portConfig.port}"))
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (status == AppStatus.RUNNING) {
                                    StatusRunning
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Log viewer
            AnimatedVisibility(visible = showLog && logLines.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    val scrollState = rememberScrollState()
                    // Auto-scroll to bottom when new lines arrive
                    LaunchedEffect(logLines.size) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0D0D1A),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    ) {
                        Text(
                            text = logLines.takeLast(50).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFA0AEC0),
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action buttons
            val isRunning = status == AppStatus.RUNNING || status == AppStatus.STARTING

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (isRunning) onStop() else onStart() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        text = when (status) {
                            AppStatus.STOPPED -> "Start"
                            AppStatus.STARTING -> "Starting..."
                            AppStatus.RUNNING -> "Stop"
                            AppStatus.ERROR -> "Retry"
                        }
                    )
                }
                if (isRunning) {
                    IconButton(
                        onClick = onRestart,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Restart",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    return try {
        val value = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(
                red = ((value shr 16) and 0xFF) / 255f,
                green = ((value shr 8) and 0xFF) / 255f,
                blue = (value and 0xFF) / 255f
            )
            8 -> Color(
                alpha = ((value shr 24) and 0xFF) / 255f,
                red = ((value shr 16) and 0xFF) / 255f,
                green = ((value shr 8) and 0xFF) / 255f,
                blue = (value and 0xFF) / 255f
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun StatusDot(status: AppStatus, color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by if (status == AppStatus.STARTING) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        mutableStateOf(1f)
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
