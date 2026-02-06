# Configuration Guide

ApplicationLauncher supports flexible path configuration to work across different machines.

## Configuration Methods

### Method 1: Relative Paths (Recommended for Sibling Projects)

Use paths relative to the ApplicationLauncher directory:

**Example:**
```json
{
  "apps": [
    {
      "id": "my-app",
      "name": "My App",
      "path": "..\\MyApp",
      "command": "gradlew.bat run"
    }
  ]
}
```

**When to use:**
- All your apps are in sibling directories (same parent folder)
- Works across different drives (C:, D:, etc.)
- No configuration needed per machine

**Directory Structure:**
```
D:\Users\Toby\Documents\ClaudeMCP\
├── ApplicationLauncher\
├── ChipReader\          → path: "..\\ChipReader"
├── ChipWriter\          → path: "..\\ChipWriter"
└── MLVisualiser\        → path: "..\\MLVisualiser"
```

### Method 2: Environment Variables

Use environment variable substitution in `apps.json` paths:

**Supported Formats:**
- `${VAR_NAME}` — Unix/cross-platform style
- `%VAR_NAME%` — Windows style

**Example:**
```json
{
  "apps": [
    {
      "id": "my-app",
      "name": "My App",
      "path": "${USERPROFILE}\\Documents\\ClaudeMCP\\MyApp",
      "command": "gradlew.bat run"
    }
  ]
}
```

**Common Environment Variables:**
- Windows: `%USERPROFILE%`, `%APPDATA%`, `%LOCALAPPDATA%`
- Unix/Mac: `${HOME}`, `${USER}`
- Custom: Define your own like `${PROJECTS_DIR}`

**Setting Custom Variables:**

Windows (PowerShell):
```powershell
$env:PROJECTS_DIR = "D:\Users\YourName\Documents\ClaudeMCP"
```

Windows (System):
```
System Properties → Environment Variables → New
```

### Method 2: Local Configuration Override

Create `apps.local.json` to override paths without modifying `apps.json`:

**1. Copy the example:**
```bash
cp apps.local.example.json apps.local.json
```

**2. Edit `apps.local.json`:**
```json
{
  "overrides": {
    "chip-reader": {
      "path": "D:\\Users\\Toby\\Documents\\ClaudeMCP\\ChipReader"
    },
    "ml-visualiser": {
      "path": "C:\\Projects\\MLVisualiser",
      "command": "python scripts\\start_fullstack.py"
    }
  }
}
```

**Benefits:**
- `apps.json` stays portable (committed to git)
- `apps.local.json` is machine-specific (git-ignored)
- Only override what you need

### Method 3: Combined Approach

Use environment variables in `apps.json` AND local overrides:

**apps.json:**
```json
{
  "path": "${PROJECTS_DIR}\\ChipReader"
}
```

**apps.local.json:**
```json
{
  "overrides": {
    "special-app": {
      "path": "C:\\Different\\Location\\SpecialApp"
    }
  }
}
```

## Resolution Order

1. Load `apps.json`
2. Apply overrides from `apps.local.json` (if present)
3. Resolve environment variables in final paths

## Multi-Machine Workflow

**Scenario:** You work on Laptop A (Windows) and Laptop B (Windows)

**Setup:**

**Laptop A:**
```json
// apps.local.json
{
  "overrides": {
    "chip-reader": {
      "path": "D:\\Users\\Toby\\Documents\\ClaudeMCP\\ChipReader"
    }
  }
}
```

**Laptop B:**
```json
// apps.local.json
{
  "overrides": {
    "chip-reader": {
      "path": "C:\\Work\\Projects\\ChipReader"
    }
  }
}
```

**Both laptops:**
- `apps.json` is shared via git (portable config)
- `apps.local.json` stays local (not committed)

## Troubleshooting

**App fails to start:**
- Check console output for path resolution
- Verify environment variables: `echo %USERPROFILE%` (Windows) or `echo $HOME` (Unix)
- Ensure paths use escaped backslashes: `\\` in JSON

**Override not working:**
- Verify app `id` matches between `apps.json` and `apps.local.json`
- Check JSON syntax validity
- Restart the launcher after config changes

**Path with spaces:**
```json
{"path": "C:\\Program Files\\My App"}  // ✅ Correct
{"path": "C:\Program Files\My App"}    // ❌ Wrong (single backslash)
```

## Examples

### Example 1: All apps under one base directory
```json
// apps.json
{
  "apps": [
    {"path": "${PROJECTS_DIR}\\ChipReader", ...},
    {"path": "${PROJECTS_DIR}\\ChipWriter", ...}
  ]
}
```

Set once per machine:
```
PROJECTS_DIR=D:\Users\Toby\Documents\ClaudeMCP
```

### Example 2: Mixed directories
```json
// apps.json
{
  "apps": [
    {"path": "${USERPROFILE}\\Documents\\ClaudeMCP\\ChipReader", ...},
    {"path": "C:\\Work\\MLVisualiser", ...}  // Absolute path
  ]
}
```

### Example 3: Python virtual environment
```json
{
  "id": "ml-app",
  "path": "${PROJECTS_DIR}\\MLVisualiser",
  "command": ".venv\\Scripts\\python.exe scripts\\start.py"
}
```
