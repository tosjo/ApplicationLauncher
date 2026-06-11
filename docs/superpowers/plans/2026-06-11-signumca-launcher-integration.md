# SignumCA Launcher Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SignumCA to the ApplicationLauncher as a single one-click card that starts/stops the full local dev stack (HSM simulator + WildFly 36 + Vite UI) via an orchestration script.

**Architecture:** A PowerShell orchestration script `scripts/start-dev.ps1` lives in the SignumCA repo and conforms to the launcher's one-command/one-process-tree model — no launcher code changes. The script does a smart staleness check (rebuild WAR when sources are newer), syncs the WAR into WildFly's deployments dir, starts the HSM simulator (+ `StartOS`), WildFly, and Vite as child processes, and stays alive monitoring them. The launcher's existing process-tree kill stops everything.

**Tech Stack:** Windows PowerShell 5.1 (the launcher invokes `powershell`, not `pwsh` — script must avoid PS7-only syntax like `&&`), Maven wrapper, npm/Vite, WildFly 36, Utimaco QuantumProtect simulator.

**Spec:** `docs/superpowers/specs/2026-06-11-signumca-launcher-integration-design.md`

**Verified facts (do not re-derive):**
- PostgreSQL Windows service name: `postgresql-x64-17`
- Simulator binary: `resources\QuantumProtect-1.5.0.0-Evaluation\QuantumProtect-1.5.0.0-Evaluation\windows\sim5_windows\bin\bl_sim5.exe` (must run with that `bin` dir as working directory — device data lives there)
- csadm tool: `resources\u.trust-GP-HSM-Simulator_v6.3.0.0\u.trust-GP-HSM-Simulator_v6.3.0.0\Software\Windows\Administration\csadm.exe`
- SignumCA GitHub remote: `https://github.com/tosjo/SignumCA` (exists)
- Launcher version property: `gradle.properties` → `app.version=0.3.0`
- OpenAPI endpoint for backend health verification: `http://localhost:8080/signumca/openapi`
- Vite dev server: port 5180 (pinned in `signumca-ui/vite.config.ts`)
- Neither repo has tests covering this change (launcher has no test suite; SignumCA changes are script+docs only, no Java/TS code touched), so verification is by running the script and the launcher.

**Testing note:** This is an ops/orchestration script — there is no unit-test framework for it in either repo. TDD is replaced by explicit run-and-verify steps with exact commands and expected output. Do not skip the verification steps.

---

### Task 1: Create `scripts/start-dev.ps1` in SignumCA

**Files:**
- Create: `C:\Users\tze\Documents\CCPilot\SignumCA\scripts\start-dev.ps1`

- [ ] **Step 1: Write the script**

Create `C:\Users\tze\Documents\CCPilot\SignumCA\scripts\start-dev.ps1` with exactly this content:

```powershell
# start-dev.ps1 - one-command SignumCA local dev stack
#
# Starts (in order): HSM simulator (+ StartOS), WildFly 36, Vite dev server.
# Rebuilds the WAR when sources are newer than the built WAR, and syncs it
# into WildFly's deployments directory. Components already listening on
# their ports are reused, not restarted.
#
# Stop the stack by killing this script's process tree (the
# ApplicationLauncher does this automatically on card stop).
#
# Compatible with Windows PowerShell 5.1.

$ErrorActionPreference = 'Stop'

$root       = Split-Path -Parent $PSScriptRoot
$warSource  = Join-Path $root 'signumca-web\target\signumca.war'
$deployDir  = Join-Path $root 'wildfly-36.0.0.Final\standalone\deployments'
$wildflyBat = Join-Path $root 'wildfly-36.0.0.Final\bin\standalone.bat'
$uiDir      = Join-Path $root 'signumca-ui'
$simBin     = Join-Path $root 'resources\QuantumProtect-1.5.0.0-Evaluation\QuantumProtect-1.5.0.0-Evaluation\windows\sim5_windows\bin'
$csadm      = Join-Path $root 'resources\u.trust-GP-HSM-Simulator_v6.3.0.0\u.trust-GP-HSM-Simulator_v6.3.0.0\Software\Windows\Administration\csadm.exe'
$pgService  = 'postgresql-x64-17'

function Log($tag, $msg) { Write-Output "[$tag] $msg" }

function Test-PortListening($port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

# Kills the full process tree of whatever is listening on a port.
# Used only for components this script started itself.
function Stop-PortOwner($port, $tag) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    $owners = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $owners) {
        Log $tag "killing process tree PID $procId (port $port)"
        & taskkill /F /T /PID $procId 2>&1 | Out-Null
    }
}

# --- 1. PostgreSQL (Windows service, never killed by this script) ---------

$pg = Get-Service $pgService -ErrorAction SilentlyContinue
if (-not $pg) {
    Log setup "WARNING: service '$pgService' not found - is PostgreSQL 17 installed?"
} elseif ($pg.Status -ne 'Running') {
    Log setup "PostgreSQL service stopped - starting it"
    try {
        Start-Service $pgService
        Log setup "PostgreSQL service started"
    } catch {
        Log setup "WARNING: could not start PostgreSQL: $_"
    }
} else {
    Log setup "PostgreSQL service running"
}

# --- 2. Smart build check --------------------------------------------------

$srcDirs = @('signumca-core\src', 'signumca-store\src', 'signumca-web\src') |
    ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path $_ }
$poms = @('pom.xml', 'signumca-core\pom.xml', 'signumca-store\pom.xml', 'signumca-web\pom.xml') |
    ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path $_ }

$newest = ($srcDirs | Get-ChildItem -Recurse -File | Measure-Object LastWriteTime -Maximum).Maximum
$newestPom = ($poms | Get-Item | Measure-Object LastWriteTime -Maximum).Maximum
if ($newestPom -gt $newest) { $newest = $newestPom }

$needBuild = (-not (Test-Path $warSource)) -or ((Get-Item $warSource).LastWriteTime -lt $newest)
if ($needBuild) {
    Log build "WAR missing or older than sources - rebuilding (skipping tests)"
    Push-Location $root
    & .\mvnw.cmd -pl signumca-core,signumca-store install -DskipTests 2>&1 | ForEach-Object { "[build] $_" }
    if ($LASTEXITCODE -ne 0) { Log build 'ERROR: core/store build failed'; exit 1 }
    & .\mvnw.cmd -pl signumca-web package -DskipTests 2>&1 | ForEach-Object { "[build] $_" }
    if ($LASTEXITCODE -ne 0) { Log build 'ERROR: web build failed'; exit 1 }
    Pop-Location
    Log build 'build complete'
} else {
    Log build 'WAR up to date - skipping build'
}

# --- 3. Sync WAR into WildFly deployments ----------------------------------

$warDeployed = Join-Path $deployDir 'signumca.war'
$syncNeeded = (-not (Test-Path $warDeployed)) -or ((Get-Item $warDeployed).LastWriteTime -lt (Get-Item $warSource).LastWriteTime)
if ($syncNeeded) {
    Log deploy 'copying fresh WAR to WildFly deployments'
    Copy-Item $warSource $warDeployed -Force
    Remove-Item "$warDeployed.failed", "$warDeployed.undeployed" -Force -ErrorAction SilentlyContinue
} else {
    Log deploy 'deployed WAR up to date'
}

# --- 4. UI dependencies -----------------------------------------------------

if (-not (Test-Path (Join-Path $uiDir 'node_modules'))) {
    Log ui 'node_modules missing - running npm install'
    Push-Location $uiDir
    & cmd /c 'npm install' 2>&1 | ForEach-Object { "[ui] $_" }
    if ($LASTEXITCODE -ne 0) { Log ui 'ERROR: npm install failed'; exit 1 }
    Pop-Location
}

# --- 5. HSM simulator -------------------------------------------------------

$startedSim = $false
if (Test-PortListening 3001) {
    Log hsm 'simulator already listening on port 3001 - reusing'
} else {
    Log hsm 'starting bl_sim5.exe'
    Start-Process -FilePath (Join-Path $simBin 'bl_sim5.exe') -ArgumentList '-h' -WorkingDirectory $simBin -WindowStyle Hidden
    $startedSim = $true
    $deadline = (Get-Date).AddSeconds(30)
    while (-not (Test-PortListening 3001) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-PortListening 3001)) {
        Log hsm 'WARNING: simulator did not open port 3001 within 30s - continuing (app retries HSM every 30s)'
    }
}

if (Test-PortListening 3001) {
    try {
        $state = & $csadm 'Dev=3001@127.0.0.1' GetState 2>&1 | Out-String
        if ($state -match 'Operational Mode') {
            Log hsm 'already in Operational Mode'
        } else {
            Log hsm 'running StartOS (Bootloader -> Operational Mode)'
            & $csadm 'Dev=3001@127.0.0.1' StartOS 2>&1 | ForEach-Object { "[hsm] $_" }
            $state = & $csadm 'Dev=3001@127.0.0.1' GetState 2>&1 | Out-String
            if ($state -match 'Operational Mode') {
                Log hsm 'Operational Mode confirmed'
            } else {
                Log hsm 'WARNING: not in Operational Mode after StartOS - continuing (app retries HSM every 30s)'
            }
        }
    } catch {
        Log hsm "WARNING: csadm failed: $_ - continuing (app retries HSM every 30s)"
    }
}

# --- 6 + 7. WildFly and Vite as monitored background jobs -------------------

$jobs = @()
$startedWildfly = $false
$startedVite = $false

if (Test-PortListening 8080) {
    Log wildfly 'port 8080 already in use - assuming WildFly is running, reusing'
} else {
    Log wildfly 'starting WildFly'
    $jobs += Start-Job -Name wildfly -ScriptBlock {
        param($bat) & cmd /c "`"$bat`"" 2>&1
    } -ArgumentList $wildflyBat
    $startedWildfly = $true
}

if (Test-PortListening 5180) {
    Log vite 'port 5180 already in use - assuming Vite is running, reusing'
} else {
    Log vite 'starting Vite dev server'
    $jobs += Start-Job -Name vite -ScriptBlock {
        param($dir) Set-Location $dir; & cmd /c 'npm run dev' 2>&1
    } -ArgumentList $uiDir
    $startedVite = $true
}

Log setup 'stack starting - UI: http://localhost:5180  API: http://localhost:8080/signumca/api/v1/'

# --- Wait loop: relay child output, exit non-zero if a child dies -----------

while ($true) {
    foreach ($job in $jobs) {
        Receive-Job $job | ForEach-Object { "[$($job.Name)] $_" }
        if ($job.State -ne 'Running') {
            Receive-Job $job | ForEach-Object { "[$($job.Name)] $_" }
            Log setup "ERROR: $($job.Name) exited unexpectedly (state: $($job.State)) - shutting down stack"
            foreach ($j in $jobs) { Stop-Job $j -ErrorAction SilentlyContinue }
            if ($startedWildfly) { Stop-PortOwner 8080 wildfly }
            if ($startedVite)    { Stop-PortOwner 5180 vite }
            if ($startedSim)     { Stop-PortOwner 3001 hsm }
            exit 1
        }
    }
    Start-Sleep -Seconds 1
}
```

- [ ] **Step 2: Syntax-check the script**

Run:
```powershell
powershell -NoProfile -Command "[void][System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw 'C:\Users\tze\Documents\CCPilot\SignumCA\scripts\start-dev.ps1'), [ref]$null); 'PARSE OK'"
```
Expected: `PARSE OK` and no error output.

- [ ] **Step 3: Record pre-test state**

Run:
```powershell
Get-NetTCPConnection -LocalPort 3001,8080,5180 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
```
Expected for a clean test: no output (nothing running). If components are already running, note which — the script should log "reusing" for those, and the stop-test in Step 6 must NOT expect those ports to be freed.

- [ ] **Step 4: Run the script standalone (background) and verify startup**

Start it the same way the launcher will, capturing the PID:
```powershell
$p = Start-Process powershell -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','scripts\start-dev.ps1' -WorkingDirectory 'C:\Users\tze\Documents\CCPilot\SignumCA' -PassThru -RedirectStandardOutput 'C:\Users\tze\Documents\CCPilot\SignumCA\start-dev-test.log'
$p.Id
```

Wait up to ~120 s for WildFly to deploy, then verify (each command with expected result):

```powershell
Get-Content 'C:\Users\tze\Documents\CCPilot\SignumCA\start-dev-test.log'
```
Expected log lines (order matters): `[setup] PostgreSQL service running`, `[build] WAR up to date - skipping build` (WAR was built 2026-06-10), `[deploy] ...` (either copy or up-to-date), `[hsm] ...` ending in `Operational Mode confirmed` or `already in Operational Mode`, `[wildfly] starting WildFly`, `[vite] starting Vite dev server`, then streamed `[wildfly] ...`/`[vite] ...` output.

```powershell
(Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/signumca/openapi').StatusCode
```
Expected: `200` (backend deployed).

```powershell
(Invoke-WebRequest -UseBasicParsing 'http://localhost:5180').StatusCode
```
Expected: `200` (Vite serving the UI).

```powershell
& 'C:\Users\tze\Documents\CCPilot\SignumCA\resources\u.trust-GP-HSM-Simulator_v6.3.0.0\u.trust-GP-HSM-Simulator_v6.3.0.0\Software\Windows\Administration\csadm.exe' 'Dev=3001@127.0.0.1' GetState
```
Expected: output contains `mode             = Operational Mode`.

- [ ] **Step 5: Verify stop kills the whole stack (simulates launcher card stop)**

```powershell
taskkill /F /T /PID $p.Id
Start-Sleep -Seconds 3
Get-NetTCPConnection -LocalPort 3001,8080,5180 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
Get-Process bl_sim5 -ErrorAction SilentlyContinue
Get-Service postgresql-x64-17 | Select-Object Status
```
Expected: no listeners on 3001/8080/5180 (except components noted as pre-existing in Step 3), no `bl_sim5` process, PostgreSQL still `Running`.

Note: `taskkill /T` kills the tree the same way the launcher's `process.descendants().forEach { destroyForcibly() }` does. If ports linger a few seconds in TIME_WAIT that is fine — only `Listen` state matters.

Then delete the test log:
```powershell
Remove-Item 'C:\Users\tze\Documents\CCPilot\SignumCA\start-dev-test.log'
```

- [ ] **Step 6: Verify the staleness check triggers and skips correctly**

Touch a backend source file, then re-run only the build-check logic by starting the script again briefly:
```powershell
(Get-Item 'C:\Users\tze\Documents\CCPilot\SignumCA\signumca-web\src\main\java' | Get-ChildItem -Recurse -File | Select-Object -First 1) | ForEach-Object { $_.LastWriteTime = Get-Date }
```
Start the script as in Step 4 and check the log within ~15 s:
Expected: `[build] WAR missing or older than sources - rebuilding (skipping tests)` followed by Maven output. You may kill the script (`taskkill /F /T /PID $p.Id`) once the rebuild is confirmed running, or let it finish.

After the rebuild completes (if you let it finish), start once more and verify the log now shows `[build] WAR up to date - skipping build`. Kill the script tree again and clean up the test log.

- [ ] **Step 7: Verify the reuse path (externally started simulator survives a stop)**

Start the simulator by hand, then run the script and stop it:
```powershell
Start-Process -FilePath 'C:\Users\tze\Documents\CCPilot\SignumCA\resources\QuantumProtect-1.5.0.0-Evaluation\QuantumProtect-1.5.0.0-Evaluation\windows\sim5_windows\bin\bl_sim5.exe' -ArgumentList '-h' -WorkingDirectory 'C:\Users\tze\Documents\CCPilot\SignumCA\resources\QuantumProtect-1.5.0.0-Evaluation\QuantumProtect-1.5.0.0-Evaluation\windows\sim5_windows\bin'
# wait until port 3001 listens, then start the script as in Step 4
```
Expected in the log: `[hsm] simulator already listening on port 3001 - reusing`.

Kill the script tree (`taskkill /F /T /PID $p.Id`), then verify the simulator survived:
```powershell
Get-Process bl_sim5
```
Expected: the `bl_sim5` process is still listed. Clean up afterwards:
```powershell
Stop-Process -Name bl_sim5 -Force
Remove-Item 'C:\Users\tze\Documents\CCPilot\SignumCA\start-dev-test.log' -ErrorAction SilentlyContinue
```

- [ ] **Step 8: Commit (SignumCA repo, conventional commits)**

```powershell
git -C C:\Users\tze\Documents\CCPilot\SignumCA add scripts/start-dev.ps1
git -C C:\Users\tze\Documents\CCPilot\SignumCA commit -m "feat: add one-command dev stack startup script (start-dev.ps1)"
```

---

### Task 2: Document the one-command start in SignumCA

**Files:**
- Modify: `C:\Users\tze\Documents\CCPilot\SignumCA\docs\operations\dev-environment.md` (insert after the `## Prerequisites` section, before `## Quick Start`)
- Modify: `C:\Users\tze\Documents\CCPilot\SignumCA\CHANGELOG.md` (add to the `### Added` list of the `[0.15.0-SNAPSHOT]` section)

- [ ] **Step 1: Add a "One-Command Start" section to dev-environment.md**

Insert this section after `## Prerequisites` and before `## Quick Start` (keep the existing Quick Start as the manual/Docker procedure):

```markdown
## One-Command Start (native, no Docker)

For machines without Docker, `scripts/start-dev.ps1` starts the entire native dev stack in one go — it is also what the [ApplicationLauncher](https://github.com/tosjo/ApplicationLauncher) runs for its SignumCA card:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-dev.ps1
```

The script, in order:

1. Verifies the PostgreSQL 17 Windows service is running (starts it if stopped)
2. Rebuilds the WAR if any source under `signumca-*/src` (or a `pom.xml`) is newer than `signumca-web/target/signumca.war`
3. Copies the WAR into `wildfly-36.0.0.Final/standalone/deployments/` when outdated
4. Runs `npm install` in `signumca-ui` if `node_modules` is missing
5. Starts the HSM simulator and transitions it to Operational Mode via `csadm StartOS` (skipped when port 3001 is already listening)
6. Starts WildFly (`standalone.bat`) and the Vite dev server (port 5180), skipping any that are already running

Stop everything by killing the script's process tree (the ApplicationLauncher does this on card stop). The PostgreSQL service and any components that were already running before the script started are left untouched.
```

- [ ] **Step 2: Add CHANGELOG entry**

In `CHANGELOG.md`, append to the `### Added` list of the `[0.15.0-SNAPSHOT]` section:

```markdown
- `scripts/start-dev.ps1` — one-command native dev stack startup (HSM simulator + StartOS, WildFly, Vite) with smart WAR rebuild/sync; used by the ApplicationLauncher
```

- [ ] **Step 3: Commit**

```powershell
git -C C:\Users\tze\Documents\CCPilot\SignumCA add docs/operations/dev-environment.md CHANGELOG.md
git -C C:\Users\tze\Documents\CCPilot\SignumCA commit -m "docs: document one-command dev stack startup"
```

---

### Task 3: Add SignumCA to the launcher (v0.4.0)

**Files:**
- Modify: `C:\Users\tze\Documents\CCPilot\ApplicationLauncher\apps.json` (append to `apps` array)
- Modify: `C:\Users\tze\Documents\CCPilot\ApplicationLauncher\gradle.properties` (version bump)
- Modify: `C:\Users\tze\Documents\CCPilot\ApplicationLauncher\README.md` (managed-apps table)
- Modify: `C:\Users\tze\Documents\CCPilot\ApplicationLauncher\CHANGELOG.md` (new release section)

- [ ] **Step 1: Add the SignumCA entry to apps.json**

Append this object to the `apps` array (after the `cvc-reader` entry, since both are in the "Certificate" group):

```json
{
  "id": "signumca",
  "name": "SignumCA",
  "description": "Certificate Authority — HSM simulator + WildFly + React UI (dev stack)",
  "path": "..\\SignumCA",
  "command": "powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\start-dev.ps1",
  "color": "#7B341E",
  "group": "Certificate",
  "tags": ["java", "wildfly", "react", "pki"],
  "url": "https://github.com/tosjo/SignumCA",
  "ports": [
    {"port": 5180, "label": "UI"},
    {"port": 8080, "label": "API"},
    {"port": 9990, "label": "WildFly"}
  ]
}
```

- [ ] **Step 2: Validate the JSON parses**

```powershell
powershell -NoProfile -Command "(Get-Content -Raw 'C:\Users\tze\Documents\CCPilot\ApplicationLauncher\apps.json' | ConvertFrom-Json).apps.Count"
```
Expected: `6` (was 5).

- [ ] **Step 3: Bump version to 0.4.0**

In `gradle.properties`, change `app.version=0.3.0` to `app.version=0.4.0`.

- [ ] **Step 4: Update README managed-apps table**

In `README.md`, add this row to the "Managed Applications" table:

```markdown
| SignumCA | WildFly + React (full stack) | `powershell -File scripts\start-dev.ps1` | [tosjo/SignumCA](https://github.com/tosjo/SignumCA) |
```

- [ ] **Step 5: Update CHANGELOG**

Add above the `## [0.3.0]` section in `CHANGELOG.md`:

```markdown
## [0.4.0] - 2026-06-11

### Added

- SignumCA dev stack as a managed app — one card starts/stops the HSM simulator, WildFly 36, and the Vite UI via SignumCA's `scripts/start-dev.ps1` orchestration script
- Design spec for multi-component app integration in `docs/superpowers/specs/`
```

- [ ] **Step 6: Verify the launcher still builds**

```powershell
& 'C:\Users\tze\Documents\CCPilot\ApplicationLauncher\gradlew.bat' -p 'C:\Users\tze\Documents\CCPilot\ApplicationLauncher' build
```
Expected: `BUILD SUCCESSFUL`. (No test suite exists; this verifies compilation and config only. If Gradle hangs at "0% configuring", see README troubleshooting: `gradlew.bat --stop` first.)

- [ ] **Step 7: Commit**

```powershell
git -C C:\Users\tze\Documents\CCPilot\ApplicationLauncher add apps.json gradle.properties README.md CHANGELOG.md
git -C C:\Users\tze\Documents\CCPilot\ApplicationLauncher commit -m "feat: add SignumCA dev stack to launcher - v0.4.0"
```

---

### Task 4: End-to-end verification through the launcher UI

This task needs a human clicking the launcher UI — coordinate with the user.

- [ ] **Step 1: Ensure a clean slate**

```powershell
Get-NetTCPConnection -LocalPort 3001,8080,5180 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
```
Expected: empty. Kill leftovers from earlier testing if present (`taskkill /F /T /PID <pid>`).

- [ ] **Step 2: Start the launcher**

```powershell
& 'C:\Users\tze\Documents\CCPilot\ApplicationLauncher\gradlew.bat' -p 'C:\Users\tze\Documents\CCPilot\ApplicationLauncher' run
```

- [ ] **Step 3: Ask the user to verify in the UI**

Checklist for the user:
1. A **SignumCA** card appears in the "Certificate" group.
2. Click start → status goes STARTING then RUNNING; the log panel shows the merged `[setup]`/`[build]`/`[hsm]`/`[wildfly]`/`[vite]` stream.
3. Once WildFly is up (~60 s), the **UI (5180)** port badge opens the SignumCA UI in the browser; **API (8080)** answers.
4. Click stop → card returns to STOPPED.

- [ ] **Step 4: Verify cleanup after stop**

```powershell
Get-NetTCPConnection -LocalPort 3001,8080,5180 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
Get-Process bl_sim5 -ErrorAction SilentlyContinue
```
Expected: empty — all three components are gone; PostgreSQL service untouched.

- [ ] **Step 5: Push both repos** (user's global instructions: commit and push with updated version)

```powershell
git -C C:\Users\tze\Documents\CCPilot\SignumCA push
git -C C:\Users\tze\Documents\CCPilot\ApplicationLauncher push
```
