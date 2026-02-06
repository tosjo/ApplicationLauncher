# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
