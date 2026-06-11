# SignumCA Launcher Integration — Design

**Date:** 2026-06-11
**Status:** Approved
**Repos affected:** ApplicationLauncher (config + docs), SignumCA (orchestration script + docs)

## Goal

Add SignumCA to the ApplicationLauncher as a single one-click card that starts and stops the full local dev stack: Utimaco HSM simulator, WildFly 36, and the Vite dev UI.

## Context

SignumCA runs **natively** on this machine (no Docker — corporate proxy). The stack consists of:

| Component | How it runs | Notes |
|---|---|---|
| HSM simulator | `bl_sim5.exe -h` from its `bin` dir, then `csadm StartOS` | Two-step: binary starts in Bootloader Mode; `StartOS` transitions to Operational Mode. Port 3001. |
| PostgreSQL 17 | Windows service | Not launcher-managed; usually already running. |
| WildFly 36 | `standalone.bat` from bundled `wildfly-36.0.0.Final/` | Deploys `signumca.war` from `standalone/deployments/`. Ports 8080/8443/9990. |
| Vite dev server | `npm run dev` in `signumca-ui/` | Port 5180, proxies `/api` to WildFly on 8080. |

The launcher's model is one app = one command = one process tree, with process-tree kill on stop. No launcher code changes are needed — a single orchestration script fits this model.

## Design

### 1. Orchestration script — `scripts/start-dev.ps1` (SignumCA repo)

Runs these steps in order, prefixing all child output (`[hsm]`, `[wildfly]`, `[vite]`) so the launcher log viewer shows one merged stream:

1. **PostgreSQL check** — verify the PostgreSQL 17 service is running; attempt `Start-Service` if not; warn and continue on failure.
2. **Smart build check** — rebuild the WAR when it is missing **or** stale: compare the newest `LastWriteTime` under `signumca-core/src`, `signumca-store/src`, `signumca-web/src` (plus the module `pom.xml` files and parent `pom.xml`) against `signumca-web/target/signumca.war`. If any source is newer, rebuild using the documented commands:
   - `mvnw.cmd -pl signumca-core,signumca-store install -DskipTests`
   - `mvnw.cmd -pl signumca-web package -DskipTests`
3. **WAR sync** — if `wildfly-36.0.0.Final/standalone/deployments/signumca.war` is missing or older than `signumca-web/target/signumca.war`, copy it over. WildFly's deployment scanner handles (re)deployment. No manual copying, ever.
4. **UI deps** — if `signumca-ui/node_modules` is missing, run `npm install`. (No UI build needed — Vite dev mode serves sources directly.)
5. **HSM simulator** — if port 3001 is not already listening, start `bl_sim5.exe -h` with working directory set to its `bin` folder (device data lives there). Wait for port 3001, then run `csadm StartOS` if `GetState` is not already Operational Mode, and verify. On failure: warn and continue — the app retries HSM connections every 30 s.
6. **WildFly** — start `standalone.bat`.
7. **Vite** — `npm run dev` in `signumca-ui`.
8. **Wait loop** — stay alive monitoring children. If WildFly or Vite exits, exit non-zero so the launcher card shows error/stopped.

**Stop behavior:** the launcher kills the whole process tree → simulator, WildFly (java), and Vite (node) die together. The PostgreSQL service is untouched. If the simulator was already running before start (started externally), it is not part of the tree and survives a stop — intentional reuse.

**Idempotency:** clicking start when components are already running externally (e.g. simulator on 3001) reuses them instead of failing.

### 2. Launcher config entry (`apps.json`)

```json
{
  "id": "signumca",
  "name": "SignumCA",
  "description": "Certificate Authority — HSM simulator + WildFly + React UI (dev stack)",
  "path": "..\\SignumCA",
  "command": "powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\start-dev.ps1",
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

Joins CVCReader in the existing "Certificate" group. Port badges give one-click access once running. (Verify the GitHub URL exists before committing; omit `url` if the repo is private/absent.)

### 3. Versioning & documentation

- **ApplicationLauncher:** bump to v0.4.0 (semver minor — new managed app), update README managed-apps table, update CHANGELOG.md.
- **SignumCA:** commit `scripts/start-dev.ps1`, add a "one-command start" section to `docs/operations/dev-environment.md`, CHANGELOG entry (conventional commits per that repo's standards).

## Error handling

- Each script step logs a clear `[setup]`-prefixed line before acting and on failure.
- Maven build failure → exit non-zero immediately (card shows error) with the Maven output in the log.
- HSM simulator failure → warn and continue (WildFly self-heals via 30 s retry).
- PostgreSQL not startable → warn and continue (WildFly datasource will report it; visible in logs).

## Testing

1. **Script standalone:** run `start-dev.ps1` from a PowerShell terminal in the SignumCA root. Verify: simulator reaches Operational Mode (`csadm GetState`), API answers at `http://localhost:8080/signumca/api/v1/`, UI loads at `http://localhost:5180`.
2. **Staleness check:** touch a file under `signumca-web/src`, restart, verify rebuild triggers; restart again without changes, verify no rebuild.
3. **Via launcher:** start the card; verify status goes RUNNING, merged logs appear, port badges work. Stop the card; verify `bl_sim5`, WildFly java, and node processes are gone and ports 3001/8080/5180 are freed.
4. **Reuse path:** start simulator externally, then start the card; verify the script skips simulator startup and the external simulator survives a card stop.
