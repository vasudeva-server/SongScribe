# Scope Resolution

`$ARGUMENTS` may contain optional flags plus a scope token. Parse all flags
first, then resolve the remaining token as the scope.

## Flags

- **`--fix`** — suppress interactive questions and fix every finding, including
  minor and low-confidence ones. Design findings are the one exception; see
  *Approval* in `.agents/skills/check/reference/design-flaws.md`.
- **`--mutation`** — run the mutation-testing phase. Off by default because it
  is the slow path. Has no effect when the test scope is empty.
- **`--tests-only`** — audit only the test axes: skip the production review
  agents and review the `*Test.java` files and their production counterparts.

## Mode selection (the remaining token)

- **Empty** → Mode A (working-tree diff).
- **A git commit-ish** → Mode C (commit). Before treating a non-empty argument
  as a package or file, test whether it is a commit: run
  `/opt/homebrew/bin/git rev-parse --verify --quiet "$ARGUMENTS^{commit}"`. If
  that succeeds, and the argument is not also an existing file or directory
  path, use Mode C.
- **Anything else** → Mode B (package or file).

## Mode A: Git Diff (default)

- Run `/opt/homebrew/bin/git diff` (or `/opt/homebrew/bin/git diff HEAD` if
  there are staged changes) to get the diff.
- **If there are no uncommitted changes**, fall back to the branch's own commits
  — everything this branch has added since it forked from `develop`:
  - Determine the base ref: use `develop` if
    `/opt/homebrew/bin/git rev-parse --verify --quiet develop` succeeds,
    otherwise `origin/develop`.
  - List the branch's commits with
    `/opt/homebrew/bin/git log --oneline <base>..HEAD`. The two-dot form lists
    only commits reachable from `HEAD` but not from the base, so commits that
    came from `develop` are excluded automatically — this stays correct even if
    the branch was rebased onto `develop` during development.
  - Get the cumulative diff with `/opt/homebrew/bin/git diff <base>...HEAD`
    (three dots — diff against the merge base, not against the tip of
    `develop`).
  - Get the list of changed file paths with
    `/opt/homebrew/bin/git diff --name-only <base>...HEAD`. Exclude deleted
    files.
  - If `HEAD` is `develop` itself, or the commit list is empty, review the most
    recently modified files that the user mentioned or edited earlier in this
    conversation.
- **Also collect untracked files.** `git diff` never shows untracked files, so a
  brand-new file the user hasn't `git add`ed yet is otherwise invisible even
  though it is squarely part of "what changed." Run
  `/opt/homebrew/bin/git status --porcelain --untracked-files=all` and take
  every path marked `??` under `src/` (any extension — production code, tests,
  and resources alike). Read each one in full (Serena's
  `jet_brains_get_symbols_overview` / `jet_brains_find_symbol` for `.java` and
  `.kt`, plain `Read` otherwise) and add it to the review target alongside the
  diffed files. For these there is no diff hunk; the whole file is the target.

## Mode C: Commit Review

- Run `/opt/homebrew/bin/git show <commit>` to get the diff for the review
  target.
- Run `/opt/homebrew/bin/git diff-tree --no-commit-id --name-only -r <commit>`
  to get the list of changed file paths. Exclude deleted files.

## Mode B: Package or File Review

The token can be:

- A **dotted package name** (e.g. `music`, `ui.component.score`, `smufl`) — the
  `songscribe.` prefix is implicit and must not be included. Production code
  lives at `src/main/java/songscribe/<dots-to-slashes>`; tests at
  `src/test/java/songscribe/<dots-to-slashes>`.
- A **file path or class name** (contains `/`, or ends in `.java` / `.kt`, or
  names a class such as `StringUtilsTest`) — used as-is.

Resolution steps:

1. Convert the argument to a filesystem path as described above.
2. If the path is a directory, collect all source files (`.java`, `.kt`)
   recursively.
3. If the path is a file, use just that file.
4. Read the full content of each file using Serena's
   `jet_brains_get_symbols_overview` (depth=2) for an efficient overview, then
   read specific symbol bodies only as needed.
5. If there are many files, process them in batches of ~5–8 files per agent
   invocation to stay within context limits, running multiple rounds of the
   review phase.

## Partitioning the resolved scope

After resolving the scope in any mode, split it into two subsets:

- **Production scope** — all non-test source files (`.java`, `.kt` that do not
  end in `Test.java`).
- **Test scope** — all `*Test.java` files.

For each test in the test scope, identify its **production counterpart** (e.g.
`StringUtilsTest` → `songscribe.util.StringUtils`) using Serena per
`.agents/rules/serena.md`. Record the set of production classes they target; the
coverage and mutation phases need it.

The production scope drives the production review agents; the test scope drives
the test agents, coverage, and mutation. Either may be empty:

- **Empty test scope** — skip the test agents, coverage, and mutation entirely.
- **Empty production scope**, or `--tests-only` — skip the production agents.
- **Both empty** — report that nothing resolved and stop.
