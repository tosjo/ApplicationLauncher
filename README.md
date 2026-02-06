# ApplicationLauncher

A Compose Desktop launcher for managing multiple interconnected desktop applications from a single UI. Start, stop, and monitor all your projects without remembering individual commands.

## Managed Applications

| Application | Type | Command | GitHub |
|---|---|---|---|
| ChipReader | Kotlin/Compose | `gradlew.bat :ui:run` | [tosjo/ChipReader](https://github.com/tosjo/ChipReader) |
| ChipWriter (Claude Code) | Kotlin/Compose | `gradlew.bat :ui:run` | [tosjo/ChipWriter](https://github.com/tosjo/ChipWriter) |
| ChipWriter (Codex) | Kotlin/Compose | `gradlew.bat :host:ui:run` | [tosjo/ChipWriter](https://github.com/tosjo/ChipWriter) |
| MLVisualiser | FastAPI + React | `.venv\Scripts\python.exe scripts\start_fullstack.py` | [tosjo/MLInspector](https://github.com/tosjo/MLInspector) |
| CVCReader | Kotlin/Compose | `gradlew.bat run` | [tosjo/CVCReader](https://github.com/tosjo/CVCReader) |

## Requirements

- JDK 17+
- Windows (primary target)

## Running

```bash
.\gradlew.bat run
```

## Configuration

Applications are defined in `apps.json` at the project root. Edit this file to add, remove, or modify applications — then click the reload button in the launcher to apply changes.

```json
{
  "version": "1.0",
  "apps": [
    {
      "id": "unique-id",
      "name": "Display Name",
      "description": "Short description",
      "path": "D:\\path\\to\\project",
      "command": "gradlew.bat run",
      "color": "#1A365D",
      "group": "Optional Group",
      "tags": ["kotlin", "compose"],
      "url": "https://github.com/user/repo",
      "autoStart": false,
      "ports": [
        {"port": 8000, "label": "API"}
      ]
    }
  ]
}
```

## Features

- **Adaptive grid layout** — responsive card grid that adjusts to window size
- **Live status indicators** — animated dots show stopped, starting, running, or error state
- **Auto-start** — set `"autoStart": true` in config to launch apps when the launcher opens
- **Quick restart** — restart a running app with a single click
- **Log viewer** — expandable per-card terminal showing recent process output
- **Port links** — clickable port badges that open `http://localhost:<port>` in the browser when the app is running
- **GitHub links** — link icon opens the repository in the browser
- **Open folder** — folder icon opens the project directory in file explorer
- **Process tree management** — stops entire process trees, not just parent processes
- **Hot reload** — re-read `apps.json` without restarting the launcher
- **Graceful shutdown** — all child processes are cleaned up when the launcher exits

## Tech Stack

- Kotlin 2.1.10
- Compose Desktop 1.7.3 (Material3, dark theme)
- kotlinx-serialization for JSON config
- Gradle 8.14
