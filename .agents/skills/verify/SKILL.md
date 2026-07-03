---
name: verify
description: Confirm a SongScribe change works. Overrides the generic /verify skill's evidence-capture flow.
disable-model-invocation: true
---

## Do not use the generic `/verify` skill for SongScribe

Per `.agents/rules/development.md`, never invoke the `/run` or `/verify`
skill in this project — via the Skill tool or otherwise — even if it appears
to match the task. This project has no headless/browser automation surface
(SongScribe is a native macOS Swing desktop app), so `/verify`'s
drive-and-screenshot flow cannot be carried out here and should not be
attempted.

See `.agents/skills/run/SKILL.md` for how to launch the app.

## What "verified" means for this project

- `./scripts/compile.sh` returns `SUCCESS`.
- `./scripts/test.sh` passes for any changed/added tests (unit before e2e;
  e2e requires user approval — see `.agents/rules/development.md`).
- For UI-visible changes, either the user exercises the change themselves in
  a running instance (`./scripts/run.sh`), or you check
  `~/Library/Logs/SongScribe/songscribe.log` after describing the exact
  steps you took/expect, per `.agents/guides/logging.md`.

Report what you actually did (compiled, ran tests, asked the user to check
the UI) rather than a generic pass/fail verdict implying automated
end-to-end verification that didn't happen.
