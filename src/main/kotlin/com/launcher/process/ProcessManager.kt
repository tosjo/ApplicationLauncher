package com.launcher.process

import com.launcher.model.AppConfig
import com.launcher.model.AppStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ProcessManager {

    private val processes = mutableMapOf<String, Process>()
    private val statusFlows = mutableMapOf<String, MutableStateFlow<AppStatus>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outputJobs = mutableMapOf<String, Job>()

    fun statusFlow(appId: String): StateFlow<AppStatus> {
        return statusFlows.getOrPut(appId) { MutableStateFlow(AppStatus.STOPPED) }.asStateFlow()
    }

    fun start(config: AppConfig) {
        if (processes[config.id]?.isAlive == true) return

        val flow = statusFlows.getOrPut(config.id) { MutableStateFlow(AppStatus.STOPPED) }
        flow.value = AppStatus.STARTING

        scope.launch {
            try {
                val command = buildCommand(config.command)
                val processBuilder = ProcessBuilder(command)
                    .directory(File(config.path))
                    .redirectErrorStream(true)

                // Ensure Python subprocesses can handle Unicode output
                processBuilder.environment()["PYTHONIOENCODING"] = "utf-8"

                val process = processBuilder.start()
                processes[config.id] = process

                // Drain stdout/stderr to prevent buffer blocking
                outputJobs[config.id] = launch {
                    try {
                        process.inputStream.bufferedReader().forEachLine { /* discard */ }
                    } catch (_: Exception) { }
                }

                // Give the process a moment to fail or start
                delay(2000)

                if (process.isAlive) {
                    flow.value = AppStatus.RUNNING
                    // Monitor the process in background
                    launch {
                        process.waitFor()
                        if (flow.value == AppStatus.RUNNING) {
                            flow.value = AppStatus.STOPPED
                        }
                        processes.remove(config.id)
                    }
                } else {
                    val exitCode = process.exitValue()
                    if (exitCode != 0) {
                        flow.value = AppStatus.ERROR
                    } else {
                        flow.value = AppStatus.STOPPED
                    }
                    processes.remove(config.id)
                }
            } catch (e: Exception) {
                flow.value = AppStatus.ERROR
                processes.remove(config.id)
            }
        }
    }

    fun stop(appId: String) {
        val process = processes[appId] ?: return
        val flow = statusFlows[appId] ?: return

        scope.launch {
            try {
                // Destroy the process tree (children first)
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                process.waitFor()
            } catch (_: Exception) {
                // Best-effort cleanup
            } finally {
                flow.value = AppStatus.STOPPED
                processes.remove(appId)
                outputJobs.remove(appId)?.cancel()
            }
        }
    }

    fun stopAll() {
        processes.keys.toList().forEach { stop(it) }
    }

    fun isRunning(appId: String): Boolean = processes[appId]?.isAlive == true

    private fun buildCommand(command: String): List<String> {
        // On Windows, wrap commands in cmd /c for proper execution
        // If the command already starts with cmd, don't double-wrap
        return if (command.startsWith("cmd", ignoreCase = true)) {
            listOf("cmd", "/c", command.removePrefix("cmd /c ").removePrefix("cmd /C "))
        } else {
            listOf("cmd", "/c", command)
        }
    }
}
