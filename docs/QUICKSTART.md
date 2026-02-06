# Multi-Machine Setup Quick Start

## TL;DR

Your ApplicationLauncher now works across different laptops! 🎉

## What Changed

1. **Environment Variables** — `apps.json` now uses `${USERPROFILE}` instead of hardcoded paths
2. **Local Overrides** — You can create `apps.local.json` for machine-specific paths
3. **Git-Ignored Config** — `apps.local.json` won't be committed

## Setup on a New Laptop

### Option 1: Zero Configuration (if you use standard paths)

If your projects are in `%USERPROFILE%\Documents\ClaudeMCP\`, **it just works™**!

```bash
git clone <repo>
cd ApplicationLauncher
.\gradlew.bat run
```

### Option 2: Custom Paths

If your projects are elsewhere, create `apps.local.json`:

```bash
# 1. Copy example template
cp apps.local.example.json apps.local.json

# 2. Edit with your paths
notepad apps.local.json
```

Example `apps.local.json`:
```json
{
  "overrides": {
    "chip-reader": {
      "path": "C:\\MyProjects\\ChipReader"
    },
    "ml-visualiser": {
      "path": "D:\\Work\\MLVisualiser"
    }
  }
}
```

### Option 3: Environment Variable

Set a custom variable once per machine:

**PowerShell:**
```powershell
$env:PROJECTS_DIR = "C:\MyProjects"
# Or set permanently via System Properties → Environment Variables
```

**Then in apps.json:**
```json
{
  "path": "${PROJECTS_DIR}\\ChipReader"
}
```

## Current Configuration

Your `apps.json` now uses **relative paths**:
- `..\\ChipReader` — sibling directory to ApplicationLauncher
- `..\\ChipWriter` — sibling directory to ApplicationLauncher
- `..\\..\\Codex\\ChipWriter` — for the Codex version in a different parent

This works on **any drive** (C:, D:, etc.) as long as the directory structure is the same.

## Testing

Run the launcher and check:
```bash
.\gradlew.bat run
```

If an app fails to start, check the log viewer in the app card for path resolution errors.

## See Also

- [CONFIGURATION.md](CONFIGURATION.md) — Detailed configuration guide
- `apps.local.example.json` — Template for local overrides
