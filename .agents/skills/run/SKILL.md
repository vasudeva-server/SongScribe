---
name: run
description: Launch SongScribe for this project. Overrides the generic /run skill's app-discovery flow.
disable-model-invocation: true
---

## Do not use the generic `/run` skill for SongScribe

SongScribe is a native Swing desktop app (not a CLI, server, TUI, Electron,
or browser-driven app), so the generic `/run` skill's discovery/launch
patterns do not apply and should not be attempted.

Per `.agents/rules/development.md`, never invoke the `/run` or `/verify`
skill in this project — via the Skill tool or otherwise — even if it appears
to match the task. Use the plain scripts below directly via Bash instead.

## Launching the app

```bash
./scripts/run.sh
```

Flags:

- `--log-level=trace|debug|info|warn|error` — override the log level.
- `--truncate-log` — delete the log file before starting so the session begins fresh.
- `--profile` — attach async-profiler (wall-clock mode); flame graph written to `build/songscribe-profile.html` on quit.
- `DEBUG=1 ./scripts/run.sh` — enable UI debug features (FlatLaf inspector, debug drawing).

Any other arguments are passed through to the application.

## Compiling and testing

- `./scripts/compile.sh` — compile. No flags, no pipes, no additions.
- `./scripts/test.sh` — run tests. See `.agents/rules/development.md` and
  `.agents/guides/testing-e2e.md` for targets and conventions.

## Confirming a UI change works

This is a native macOS Swing app with no headless/browser automation surface
available in this environment. There is no reliable way to drive or
screenshot it programmatically here. To confirm a UI change:

1. Compile with `./scripts/compile.sh`.
2. Launch with `./scripts/run.sh` in the background and ask the user to
   exercise the change themselves, or describe the exact steps for them to
   follow.
3. Check `~/Library/Logs/SongScribe/songscribe.log` for exceptions after the
   relevant action, per `.agents/guides/logging.md`.

Do not attempt xvfb, Playwright, or other headless GUI automation against
this app — none of it applies to a native Swing desktop process.
