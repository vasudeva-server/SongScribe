## Branch Topology

Feature branches are based on `develop`, not `main`. Before any operation that references a base branch (diff, checkout, rebase, PR creation), verify the actual parent with `git log --oneline --graph` or `git merge-base`. Never assume `main`.

## Development Scripts

- Compile: run `./scripts/compile.sh` (optionally `clean` or `--test`). Never use `./gradlew`, `gradle`, `javac`, or `java -cp`. Outputs SUCCESS/FAILURE. Fix errors before proceeding.
- Run: Use `./scripts/run.sh` directly via Bash — flags: `--log-level=debug|info|warn|error|trace`, `--truncate-log`. UI debug features: prefix with `DEBUG=1`. NEVER execute without user permission.
- Tests: always `./scripts/compile.sh` first if any file under `src/main/` changed; always `./scripts/test.sh` (never `./gradlew test`). Bare `./scripts/test.sh` runs the **unit suite only** — there is no form that runs both suites, so reaching e2e always takes typing `e2e`, and any e2e run requires user approval. Run unit before e2e. See the usage header in `scripts/test.sh` for targets and flags; it validates them and prints the corrected command when a target is wrong.
  - Failures: read output for error info/location. Do NOT rerun with flags. Never assume pre-existing — do NOT stash to check. Fix before new changes.

## Writing Tests

Before reading, creating, or modifying tests, read the relevant guide (not auto-loaded): [testing-common.md](../guides/testing-common.md), [testing-unit.md](../guides/testing-unit.md), [testing-e2e.md](../guides/testing-e2e.md).
