package com.launcher

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.launcher.process.ProcessManager
import com.launcher.ui.LauncherApp
import java.io.File

fun main() = application {
    val processManager = ProcessManager()
    val configFile = File("apps.json")

    val windowState = rememberWindowState(
        size = DpSize(1200.dp, 800.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = {
            processManager.stopAll()
            exitApplication()
        },
        title = "Application Launcher",
        state = windowState
    ) {
        LauncherApp(processManager, configFile)
    }
}
