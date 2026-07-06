---
name: check
description: Code review and cleanup
model: opus
disable-model-invocation: true
---

## Check: Code Review and Cleanup

Review code for reuse, quality, and efficiency. This is an adversarial review, the code may have been written by a human or different LLM. Fix any issues found.

IMPORTANT: All reviews MUST apply the Java style rules in addition to the criteria below. When the review is done and fixes are applied, DO NOT run any other commands or skills on your own volition.


## Phase 1: Determine Scope

This skill supports three modes based on `$ARGUMENTS`:

- **Empty** → Mode A (working-tree diff).
- **A git commit-ish** → Mode C (commit). Before treating a non-empty argument as a package/file, test whether it is a commit: run `/opt/homebrew/bin/git rev-parse --verify --quiet "$ARGUMENTS^{commit}"`. If that succeeds (and the argument is not also an existing file/directory path), use Mode C.
- **Anything else** → Mode B (package or file).

### Mode A: Git Diff (default)

If `$ARGUMENTS` is empty or not provided, review changed files from git:

- Run `/opt/homebrew/bin/git diff` (or `/opt/homebrew/bin/git diff HEAD` if there are staged changes) to get the diff.
- If there are no git changes, review the most recently modified files that the user mentioned or edited earlier in this conversation.
- The diff output is the **review target** passed to agents in Phase 2.

### Mode C: Commit Review

If `$ARGUMENTS` is a commit-ish (resolved as above), review the changes introduced by that commit:

- Run `/opt/homebrew/bin/git show <commit>` to get the diff for the review target.
- Run `/opt/homebrew/bin/git diff-tree --no-commit-id --name-only -r <commit>` to get the list of changed file paths. These are the **review target** (their diff). Exclude deleted files.

### Mode B: Package or File Review

If `$ARGUMENTS` is provided, it specifies what to review. It can be:

- A **dotted package name** (e.g. `music`, `ui.component.score`, `smufl`) -- the `songscribe.` prefix is implicit and must not be included. Convert to a directory path: `src/main/java/songscribe/<dots-to-slashes>`.
- A **file path** (contains `/` or ends in `.java`/`.kt`) -- used as-is.

Resolution steps:

1. Convert the argument to a filesystem path as described above.
2. If the path is a directory, collect all source files (`.java`, `.kt`) recursively.
3. If the path is a file, use just that file.
4. Read the full content of each file using Serena's `jet_brains_get_symbols_overview` (depth=2) for an efficient overview, then read specific symbol bodies only as needed for the review.
5. The collected code is the **review target** passed to agents in Phase 2.
6. If there are many files, process them in batches of ~5-8 files per agent invocation to stay within context limits. Run multiple rounds of Phase 2 if needed.

### Flags

- **`--fix`** — suppress all interactive questions and fix every finding, including minor and low-confidence ones. Forward this flag to check-tests in Phase 2b.
- **`--mutation`** — forward to check-tests in Phase 2b to enable mutation testing.

### Test Scope Detection

After resolving the scope in any mode, partition it into two subsets:

- **Production scope** — all non-test source files (`.java`, `.kt` files that do not end in `Test.java`).
- **Test scope** — all `*Test.java` files.

Pass only the production scope to agents in Phase 2. The test scope drives Phase 2b; if it is empty, skip Phase 2b entirely.

## Phase 2: Launch Three Review Agents in Parallel

Use the Agent tool to launch all three agents concurrently in a single message. Pass each agent the review target so it has the complete context. Run all three agents (Reuse, Quality, Efficiency) with `model: sonnet` — they surface candidate findings that the orchestrator re-validates before fixing.

### Agent 1: Code Reuse Review

IMPORTANT: This agent must search the **entire codebase**, not just the review target. The goal is to find reuse opportunities between the reviewed code and the rest of the project.

For each piece of code under review:

1. **Search the rest of the codebase for existing utilities and helpers** that could replace code in the review target. Use Grep and Serena tools to find similar patterns in other packages -- common locations are utility directories, shared modules, and files adjacent to the reviewed ones.
2. **Search for duplicate logic across packages.** If the review target contains logic that is duplicated (or near-duplicated) in other packages, flag it and suggest extracting a shared utility or using the existing copy.
3. **Flag any inline logic that could use an existing utility** -- hand-rolled string manipulation, manual path handling, custom environment checks, ad-hoc type guards, and similar patterns are common candidates.

### Agent 2: Code Quality Review

Review the same code for hacky patterns:

1. **Redundant state**: state that duplicates existing state, cached values that could be derived, observers/effects that could be direct calls
2. **Parameter sprawl**: adding new parameters to a function instead of generalizing or restructuring existing ones
3. **Copy-paste with slight variation**: near-duplicate code blocks that should be unified with a shared abstraction
4. **Leaky abstractions**: exposing internal details that should be encapsulated, or breaking existing abstraction boundaries
5. **Stringly-typed code**: using raw strings where constants, enums (string unions), or branded types already exist in the codebase

### Agent 3: Efficiency Review

Review the same code for efficiency:

1. **Unnecessary work**: redundant computations, repeated file reads, duplicate network/API calls, N+1 patterns
2. **Missed concurrency**: independent operations run sequentially when they could run in parallel
3. **Hot-path bloat**: new blocking work added to startup or per-request/per-render hot paths
4. **Unnecessary existence checks**: pre-checking file/resource existence before operating (TOCTOU anti-pattern) -- operate directly and handle the error
5. **Memory**: unbounded data structures, missing cleanup, event listener leaks
6. **Overly broad operations**: reading entire files when only a portion is needed, loading all items when filtering for one

## Phase 2b: Test Audit (only if test scope is non-empty)

Skip this phase entirely if no `*Test.java` files were identified in Phase 1.

Read `.agents/skills/check-tests/SKILL.md`. Execute **Phases 1–4** of that skill against the test scope identified in Phase 1:

- Treat the already-resolved test files as the output of check-tests Phase 1 — do not re-resolve scope.
- Mutation testing is **off by default**. Enable it only if `--mutation` was present in `$ARGUMENTS`.
- If `--fix` was present in `$ARGUMENTS`, include it when invoking check-tests so its agents receive the flag.
- **Do not execute check-tests Phase 5.** The unified report, approval, and fix flow is handled by Phase 3 of this skill.

If mutation testing is not needed, Phase 2b's static-analysis agents (check-tests Phases 1–2) may be launched concurrently with Phase 2's agents in a single message for efficiency. If mutation is enabled, run Phase 2b after Phase 2 to avoid build contention.

## Phase 3: Review and Approve Findings

Wait for Phase 2 (and Phase 2b, if it ran) to complete, then choose the path based on whether `--fix` was in `$ARGUMENTS`.

### Path A: `--fix` mode

1. **Fix all findings immediately** — every finding from every agent (Reuse, Quality, Efficiency, and Test Quality if Phase 2b ran), including minor and low-confidence ones. Do not ask any questions or seek approval.
2. **Summarize** what was fixed when done, grouped by the same axes.

### Path B: Interactive mode (default)

1. **Present findings.** Output all findings in a single organized summary. Group by: Reuse, Quality, Efficiency (from Phase 2), and — if Phase 2b ran — Test Quality (Correctness → Usefulness → Coverage sub-groups). Add an empty line followed by "Ready for questions."

2. **Clarifying questions.** If any findings need clarification (ambiguous code intent, unclear whether something is intentional, etc.), ask them via AskUserQuestion.

3. **Questionable findings.** For any findings you believe are false positives or not worth addressing, present them via AskUserQuestion and ask whether the user wants them fixed anyway. Do not silently skip findings.

4. **Approval.** Once all questions are resolved, use AskUserQuestion to present the final list of issues to fix and ask for approval to proceed (or for further discussion).

5. **Fix.** After approval, fix the approved issues. Briefly summarize what was fixed when done.
