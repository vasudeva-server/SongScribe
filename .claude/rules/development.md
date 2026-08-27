Language-neutral principles — contracts, contract-driven testing, signature
quality — live in `~/.claude/rules/development.md` (global). This file holds
only project-specific mechanics.

## Development Scripts

- Compile: run `./scripts/compile.sh --test`, which builds both trees. Never use `./gradlew`, `gradle`, `javac`, or `java -cp`. Outputs SUCCESS/FAILURE. Fix errors before proceeding. Include `--test` on every change: with the suite dormant, compiling the test tree is the always-on check that nothing still pins a member you deleted or renamed. `clean` is available for a from-scratch build.
- Run: Use `./scripts/run.sh` directly via Bash — flags: `--log-level=debug|info|warn|error|trace`, `--truncate-log`. UI debug features: prefix with `DEBUG=1`. NEVER execute without user permission.
- Tests: always `./scripts/test.sh` (never `./gradlew test`). **Name the test classes covering what you just wrote or changed** — `./scripts/test.sh SomeTest AnotherTest`. A run naming no class, or more than four, is denied by `.claude/hooks/no-full-test-suite.sh`: the whole suite is the user's to start, and they start it themselves. There is no form that runs both suites, so reaching e2e always takes typing `e2e`, and any e2e run requires user approval. Run unit before e2e. See the usage header in `scripts/test.sh` for targets and flags; it validates them and prints the corrected command when a target is wrong. Do not compile first: `./scripts/test.sh` builds both trees itself, on the same Gradle goal `./scripts/compile.sh --test` uses.
  - Failures: read output for error info/location. Do NOT rerun with flags. Never assume pre-existing — do NOT stash to check. Fix before new changes.

## Unused Imports

Don't hunt for imports left stale by code you deleted, and never grep for them. Nothing
in the build reports one — errorprone runs with `disableAllChecks` and only NullAway
enabled, javac has no unused-import warning, and Checkstyle cannot parse this codebase's
`import module` declarations — but they are harmless, and the IDE clears them all in one
pass. Leave them.

Removing an import you can see is dead while editing the surrounding lines is fine. Going
looking is not.

## Writing Tests

Before reading, creating, or modifying tests, read the relevant guide (not auto-loaded): [testing-common.md](../guides/testing-common.md), [testing-unit.md](../guides/testing-unit.md), [testing-e2e.md](../guides/testing-e2e.md), [testing-manual.md](../guides/testing-manual.md).

## Spelling

The file header uses "Sri Chinmoy Centre" with British spelling, but *you* should always use "center" in the American spelling.
