## Branch Topology

Feature branches are based on `develop`, not `main`. Before any operation that references a base branch (diff, checkout, rebase, PR creation), verify the actual parent with `git log --oneline --graph` or `git merge-base`. Never assume `main`.

## Null Handling

- Never use `Objects.requireNonNull` / `Objects.requireNonNullElse`.
- Optional value with fallback → null guard + early return / default.
- Annotate nullable fields, parameters, and returns with `@Nullable`. Do not use `Optional`.

## Development Scripts

- Compile: run `./scripts/compile.sh` exactly — no flags, no pipes, no additions. Never use `./gradlew`, `gradle`, `javac`, or `java -cp`. Outputs SUCCESS/FAILURE. Fix errors before proceeding.
- Run: `./scripts/run.sh` — flags: `--log-level=debug|info|warn|error|trace`, `--truncate-log`. UI debug features: prefix with `DEBUG=1`.
- Tests: always `./scripts/compile.sh` first if any file under `src/main/` changed; always `./scripts/test.sh` (never `./gradlew test`). Run unit before e2e. Any e2e test requires user approval.
  - Targets: bare = all; `unit` / `e2e` (cannot be combined with any other target); `ClassName`; `ClassName.method`; `'Class$Nested'`; `'Class$Nested.method'`; space-separated multiple; `-Dtest=*Pattern`.
  - Flags (`--debug`, `--verbose`) apply to e2e only.
  - Failures: read output for error info/location. Do NOT rerun with flags. Never assume pre-existing — do NOT stash to check. Fix before new changes.

## Writing Tests

Before writing tests, read the relevant guide (not auto-loaded): [testing-common.md](../guides/testing-common.md), [testing-unit.md](../guides/testing-unit.md), [testing-e2e.md](../guides/testing-e2e.md).

## Generated Files

Never edit files in `build/generated-sources/`.
