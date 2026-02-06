# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-02-06

### Added

- Log viewer — expandable per-card terminal panel showing recent process output (last 50 lines)
- Auto-start support — `"autoStart": true` in config launches apps on launcher startup
- Port display — clickable port badges open `http://localhost:<port>` in browser when app is running
- GitHub link icons and open-folder icons on each card
- `url`, `autoStart`, and `ports` fields in app config schema
- Process output logging to stderr for diagnostics
- `PYTHONIOENCODING=utf-8` environment variable for Python subprocesses

### Fixed

- Python apps crashing due to emoji output on Windows CP1252 encoding
- Process output buffer blocking (now drained in background)
- Codex ChipWriter using correct Gradle task (`:host:ui:run`)
- MLVisualiser using venv Python directly instead of broken activate chain

## [0.1.0] - 2026-02-06

### Added

- Initial release
- Compose Desktop launcher with dark Material3 theme
- JSON-configurable app definitions (`apps.json`)
- Pre-configured entries for ChipReader, ChipWriter (x2), MLVisualiser, CVCReader, and CertificateToolbox
- ProcessBuilder-based start/stop with process tree cleanup
- Adaptive grid layout (LazyVerticalGrid, min 300dp columns)
- Animated status indicators (stopped, starting, running, error)
- Reload button to re-read config without restart
- Graceful shutdown of all child processes on launcher exit
