# AGENTS.md — iot / Mite

lsFusion application ("Mite"). Top module: `src/main/lsfusion/Mite.lsf`. Built against the lsFusion platform through the parent pom (`lsfusion.platform.build:logics`), currently **7.0-SNAPSHOT**.

## Build
- `mvn clean install -P assemble,embed-server` — produces the embedded lsFusion server jar.
- Logic lives in `src/main/lsfusion/**/*.lsf`; custom Java actions in `src/main/java`; config in `conf/`.

## Platform version bumps (read before changing the parent version)
When the parent platform version changes (e.g. 6.2 → 7.0), any module in a `REQUIRE` that the new platform **removed or renamed** breaks startup at `Initializing modules order`:

```
Error in module 'Mite': required module 'X' was not found.
```

This is a **missing source dependency**, NOT a DB-migration / `db.allowDropModules` problem (that one reports `Dropping modules … restricted`). Fix by updating the `REQUIRE` list; check the platform repo history for what happened to the module before removing/renaming.
- Precedent (2026-05, 6.2 → 7.0): platform removed the empty `ProcessUtils` module, so `ProcessUtils` was dropped from `Mite.lsf`'s `REQUIRE`.

## Deploy / "update app"
The live app (`https://app.mite.club`) is updated by the **`upgradeApp`** Jenkins pipeline on app.mite.club. It: clones this repo from GitHub → `mvn clean install -P assemble,embed-server` → over SSH stops the `lsfusion` service, swaps `/usr/lsfusion/lsfusion-server.jar`, starts it, and tails `/usr/lsfusion/logs/start.log` until `Server has successfully started in` (the build fails if it sees `Server has stopped`).

- A deploy picks up **only what's pushed to `master`** (the job re-clones each run).
- Verify after a deploy: `https://app.mite.club/ → 200` and the `successfully started` line in `start.log`.
- If a build hangs at `Executing script … stopServer.sh`, kill orphan `tail -f .../start.log` processes (the stop step is guarded with `systemctl is-active`, but old runs could leave a tail behind).

## Infra access
Build/CI/deploy run on app.mite.club (Jenkins under `/jenkins`; the app served by tomcat behind nginx). Maintainer-specific access — SSH, the Jenkins API token, the `mite` MCP server — is configured in the maintainer's local agent environment and is intentionally **not** stored in this (public) repo.
