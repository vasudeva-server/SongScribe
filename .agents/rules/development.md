## Branch Topology

Feature branches are based on `develop`, not `main`. Before any operation that references a base branch (diff, checkout, rebase, PR creation), verify the actual parent with `git log --oneline --graph` or `git merge-base`. Never assume `main`.

## ripgrep (`rg`) — `-r` is NOT recursive

`rg` is recursive by **default** and `-r` is `--replace`, not recursion. So `rg -rn "pattern"` silently rewrites every match to `n` in the output instead of erroring — do not carry the `grep -rn` habit to `rg`. Use `rg -n "pattern" path/`.

## Null Handling

- Never use `Objects.requireNonNull` / `Objects.requireNonNullElse`.
- Optional value with fallback → null guard + early return / default.
- Annotate nullable fields, parameters, and returns with `@Nullable`. Do not use `Optional`.

## Development Scripts

MANDATORY: Never invoke the `/run` or `/verify` skill (via the Skill tool or otherwise) in this project, even when it appears in the available-skills list and even when its trigger conditions match the current task. This rule overrides the general instruction to invoke matching skills. Use the plain shell commands below instead. As a backstop, project skills at `.agents/skills/run/SKILL.md` and `.agents/skills/verify/SKILL.md` (symlinked from `.claude/skills/`) redirect back to this rule if `/run` or `/verify` is ever invoked anyway.

- Compile: run `./scripts/compile.sh` exactly — no flags, no pipes, no additions. Never use `./gradlew`, `gradle`, `javac`, or `java -cp`. Outputs SUCCESS/FAILURE. Fix errors before proceeding.
- Run: Use `./scripts/run.sh` directly via Bash — flags: `--log-level=debug|info|warn|error|trace`, `--truncate-log`. UI debug features: prefix with `DEBUG=1`. NEVER execute without user permission.
- Tests: always `./scripts/compile.sh` first if any file under `src/main/` changed; always `./scripts/test.sh` (never `./gradlew test`). Run unit before e2e. Any e2e test requires user approval.
  - Targets: bare = all; `unit` / `e2e` pick the task and must come first; `ClassName`, `ClassName.method`, `'Class$Nested'`, `'Class$Nested.method'`, space-separated multiple, and `-Dtest=*Pattern` filter by name. A bare name with no `unit`/`e2e` prefix runs under the **unit** task (which excludes e2e), so e2e classes need the prefix: `e2e ClassName` (see [testing-e2e.md](../guides/testing-e2e.md)).
  - Flags (`--debug`, `--verbose`) apply to e2e only.
  - Failures: read output for error info/location. Do NOT rerun with flags. Never assume pre-existing — do NOT stash to check. Fix before new changes.

## Writing Tests

Before writing tests, read the relevant guide (not auto-loaded): [testing-common.md](../guides/testing-common.md), [testing-unit.md](../guides/testing-unit.md), [testing-e2e.md](../guides/testing-e2e.md).

## Javadoc References to Constants

Never write a named constant's raw literal value in a Javadoc comment — the doc silently rots the moment the constant changes.

Prefer `{@value}`, which inlines the real value at render time, so the reader still sees the number without it being duplicated in the source:

- Same class: `{@value #MAX_ZOOM_PERCENT}`
- Another class: `{@value ViewScale#MAX_ZOOM_PERCENT}`

Use `{@link ClassName#CONSTANT_NAME}` when the prose refers to the constant *as a thing* rather than quoting its value ("clamped by {@link ViewScale#MAX_ZOOM_PERCENT}"), or when `{@value}` is not legal.

`{@value}` only works on a *constant variable* — `static final` of a primitive or `String` type, initialized with a compile-time constant expression. It does not work on `static final Color`, `Dimension`, arrays, enums, or anything computed at runtime; those must use `{@link}`.

Exception: illustrating an example calculation/formula, where literals are needed to show the math — reference the constant elsewhere in the same doc if possible.

## Generated Files

Never edit files in `build/generated-sources/`.
