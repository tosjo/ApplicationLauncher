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
| SignumCA | WildFly + React (full stack) | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-dev.ps1` | [tosjo/SignumCA](https://github.com/tosjo/SignumCA) |

Each managed app must be cloned as a **sibling directory** of this repo and brings its own runtime requirements (JDKs, Python venvs, Node, databases). In particular, **SignumCA needs one-time machine provisioning** (WildFly 36, PostgreSQL 17, Utimaco simulator) before its card works on a fresh machine — see [SignumCA's machine setup guide](https://github.com/tosjo/SignumCA/blob/main/docs/operations/machine-setup.md).

## Requirements

- JDK 17+
- Windows (primary target)

## Running

```bash
.\gradlew.bat run
```

## Configuration

Applications are defined in `apps.json` at the project root. Edit this file to add, remove, or modify applications — then click the reload button in the launcher to apply changes.

### Multi-Machine Support

**Relative Paths** (recommended for sibling projects):
```json
{
  "path": "..\\MyApp"
}
```
Works on any drive (C:, D:, etc.) as long as directory structure is the same.

**Environment Variables** (for flexible paths):
```json
{
  "path": "${PROJECTS_DIR}\\MyApp"
}
```

**Local Overrides** (machine-specific paths):
```bash
cp apps.local.example.json apps.local.json
# Edit apps.local.json with your local paths
```

See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for detailed guide.

### App Schema

```json
{
  "version": "1.0",
  "apps": [
    {
      "id": "unique-id",
      "name": "Display Name",
      "description": "Short description",
      "path": "..\\MyApp",
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

## Troubleshooting

### Gradle stuck at "0% configuring"

Gradle builds (this launcher and managed apps like ChipReader, ChipWriter, CVCReader) can hang at configuration if stale daemon processes or lock files accumulate.

**Fix:**

1. Stop all Gradle daemons:
   ```bash
   .\gradlew.bat --stop
   ```

2. If that doesn't help, remove the cache lock file:
   ```bash
   del %USERPROFILE%\.gradle\caches\journal-1\journal-1.lock
   ```

3. Retry your build. Use `--no-daemon` to rule out daemon issues:
   ```bash
   .\gradlew.bat run --no-daemon
   ```

**Why it happens:** Each killed or timed-out Gradle build can leave a stopped daemon behind. These pile up and eventually block new builds from acquiring the cache lock.

## Tech Stack

- Kotlin 2.1.10
- Compose Desktop 1.7.3 (Material3, dark theme)
- kotlinx-serialization for JSON config
- Gradle 8.14
