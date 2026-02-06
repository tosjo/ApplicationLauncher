package com.launcher.process

import com.launcher.model.AppConfig
import com.launcher.model.AppStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ProcessManager {

    private val processes = mutableMapOf<String, Process>()
    private val statusFlows = mutableMapOf<String, MutableStateFlow<AppStatus>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outputJobs = mutableMapOf<String, Job>()
    private val logBuffers = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    companion object {
        private const val MAX_LOG_LINES = 200
    }

    fun statusFlow(appId: String): StateFlow<AppStatus> {
        return statusFlows.getOrPut(appId) { MutableStateFlow(AppStatus.STOPPED) }.asStateFlow()
    }

    fun logFlow(appId: String): StateFlow<List<String>> {
        return logBuffers.getOrPut(appId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    private fun appendLog(appId: String, line: String) {
        val flow = logBuffers.getOrPut(appId) { MutableStateFlow(emptyList()) }
        val current = flow.value
        flow.value = if (current.size >= MAX_LOG_LINES) {
            current.drop(1) + line
        } else {
            current + line
        }
    }

    fun start(config: AppConfig) {
        if (processes[config.id]?.isAlive == true) return

        val flow = statusFlows.getOrPut(config.id) { MutableStateFlow(AppStatus.STOPPED) }
        flow.value = AppStatus.STARTING

        // Clear previous log
        logBuffers.getOrPut(config.id) { MutableStateFlow(emptyList()) }.value = emptyList()

        scope.launch {
            try {
                val command = buildCommand(config.command)
                System.err.println("[${config.id}] Starting: $command")
                System.err.println("[${config.id}] Working dir: ${config.path}")

                val processBuilder = ProcessBuilder(command)
                    .directory(File(config.path))
                    .redirectErrorStream(true)

                // Ensure Python subprocesses can handle Unicode output
                processBuilder.environment()["PYTHONIOENCODING"] = "utf-8"

                val process = processBuilder.start()
                processes[config.id] = process

                // Drain stdout/stderr and capture to log buffer
                outputJobs[config.id] = launch {
                    try {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            System.err.println("[${config.id}] $line")
                            appendLog(config.id, line)
                        }
                    } catch (_: Exception) { }
                }

                // Give the process a moment to fail or start
                delay(2000)

                if (process.isAlive) {
                    System.err.println("[${config.id}] Process is alive -> RUNNING")
                    flow.value = AppStatus.RUNNING
                    // Monitor the process in background
                    launch {
                        val exitCode = process.waitFor()
                        System.err.println("[${config.id}] Process exited with code $exitCode")
                        if (flow.value == AppStatus.RUNNING) {
                            flow.value = AppStatus.STOPPED
                        }
                        processes.remove(config.id)
                    }
                } else {
                    val exitCode = process.exitValue()
                    System.err.println("[${config.id}] Process exited early with code $exitCode")
                    if (exitCode != 0) {
                        flow.value = AppStatus.ERROR
                    } else {
                        flow.value = AppStatus.STOPPED
                    }
                    processes.remove(config.id)
                }
            } catch (e: Exception) {
                System.err.println("[${config.id}] Exception: ${e.message}")
                appendLog(config.id, "ERROR: ${e.message}")
                flow.value = AppStatus.ERROR
                processes.remove(config.id)
            }
        }
    }

    fun startAll(configs: List<AppConfig>) {
        configs.forEach { start(it) }
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
