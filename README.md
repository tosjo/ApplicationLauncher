# ApplicationLauncher

A Compose Desktop launcher for managing multiple interconnected desktop applications from a single UI. Start, stop, and monitor all your projects without remembering individual commands.

## Managed Applications

| Application | Type | Command |
|---|---|---|
| ChipReader | Kotlin/Compose | `gradlew.bat :ui:run` |
| ChipWriter (x2) | Kotlin/Compose | `gradlew.bat :ui:run` |
| MLVisualiser | FastAPI + React | `python scripts/start_fullstack.py` |
| CVCReader | Kotlin/Compose | `gradlew.bat run` |
| CertificateToolbox | Python/PySide6 | `python -m certtoolbox` (via venv) |

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
      "tags": ["kotlin", "compose"]
    }
  ]
}
```

## Features

- **Adaptive grid layout** — responsive card grid that adjusts to window size
- **Live status indicators** — animated dots show stopped, starting, running, or error state
- **Process tree management** — stops entire process trees, not just parent processes
- **Hot reload** — re-read `apps.json` without restarting the launcher
- **Graceful shutdown** — all child processes are cleaned up when the launcher exits

## Tech Stack

- Kotlin 2.1.10
- Compose Desktop 1.7.3 (Material3, dark theme)
- kotlinx-serialization for JSON config
- Gradle 8.14
