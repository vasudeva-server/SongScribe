---
name: check
description: Code review and cleanup
model: opus
effort: high
disable-model-invocation: true
---

## Check: Code Review and Cleanup

Review code for reuse, quality, and efficiency. This is an adversarial review, the code may have been written by a human or different LLM. Fix any issues found.

IMPORTANT: All reviews MUST apply the Java style rules in addition to the criteria below. When the review is done and fixes are applied, DO NOT run any other commands or skills on your own volition.

## How to Write Findings (applies to every phase and every agent)

The reader has not read the code you are reviewing and does not remember how it
works. Write every finding, question, and summary so that person understands it
without opening a single file. This is a hard requirement, not a style
preference — a finding the reader cannot understand is a failed finding.

**Every finding uses this shape:**

1. **Where** — `file.java:123`, plus the method or class name in plain words
   ("in the method that draws the beam").
2. **What the code does now** — one or two plain sentences describing the
   current behavior. Never assume the reader knows.
3. **What's wrong with it** — in plain words, including what actually goes wrong
   for the user or the program. Not "violates encapsulation" but "any other
   class can change this value behind the object's back, so a bug here would be
   very hard to trace."
4. **What to do instead** — the concrete change, described so the reader can
   picture it.

**Rules for the writing itself:**

- No abbreviations or internal shorthand the reader has not seen spelled out.
- No bare symbol names as explanation. `ViewScale.applyZoom()` means nothing on
  its own — say what it does.
- Full sentences, not telegraphic notes. "Redundant cached field" is not a
  finding; "the class stores the note count in a field even though it already
  has the list of notes, so the two can disagree" is.
- One idea per sentence. Short sentences beat dense ones.
- Explain *why it matters* concretely — what breaks, when, and who notices. If
  nothing observable breaks, say that plainly ("nothing breaks today; this is
  about making it harder to break later").
- Skip severity labels and grades unless they carry real information; say what
  the consequence is instead.

**Questions follow the same standard.** Before asking anything, give the reader
the background needed to answer it: what the code does, what you saw, why you
are unsure, and what each answer would lead you to do. Never ask a question that
presumes the reader has the file in mind.


## Phase 1: Determine Scope

This skill supports three modes based on `$ARGUMENTS`:

- **Empty** → Mode A (working-tree diff).
- **A git commit-ish** → Mode C (commit). Before treating a non-empty argument as a package/file, test whether it is a commit: run `/opt/homebrew/bin/git rev-parse --verify --quiet "$ARGUMENTS^{commit}"`. If that succeeds (and the argument is not also an existing file/directory path), use Mode C.
- **Anything else** → Mode B (package or file).

### Mode A: Git Diff (default)

If `$ARGUMENTS` is empty or not provided, review changed files from git:

- Run `/opt/homebrew/bin/git diff` (or `/opt/homebrew/bin/git diff HEAD` if there are staged changes) to get the diff.
- **If there are no uncommitted changes**, fall back to the branch's own commits — everything this branch has added since it forked from `develop`:
  - Determine the base ref: use `develop` if `/opt/homebrew/bin/git rev-parse --verify --quiet develop` succeeds, otherwise `origin/develop`.
  - List the branch's commits with `/opt/homebrew/bin/git log --oneline <base>..HEAD`. The two-dot form lists only commits reachable from `HEAD` but not from the base, so commits that came from `develop` are excluded automatically — this stays correct even if the branch was rebased onto `develop` during development.
  - Get the cumulative diff with `/opt/homebrew/bin/git diff <base>...HEAD` (three dots — diff against the merge base, not against the tip of `develop`).
  - Get the list of changed file paths with `/opt/homebrew/bin/git diff --name-only <base>...HEAD`. Exclude deleted files.
  - If `HEAD` is `develop` itself, or the commit list is empty, review the most recently modified files that the user mentioned or edited earlier in this conversation.
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

Use the Agent tool to launch all three agents concurrently in a single message. Pass each agent the review target so it has the complete context. When spawning agents, include `model: "sonnet"` in each Agent tool call. The three agents (Reuse, Quality, Efficiency) with `model: sonnet` surface candidate findings that the orchestrator re-validates before fixing.

Copy the entire **How to Write Findings** section above verbatim into each
agent's prompt, and tell the agent its findings will be shown to a reader who has
not read the code. An agent that returns dense, jargon-filled findings has not
done its job.

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

**If Phase 2b ran and any Test Quality findings are going to be fixed**, read
`.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` in full
before writing or editing a single test — do this once, before the first edit,
not once per test. check-tests Phase 5 (which would normally carry this same
requirement) is explicitly skipped in Phase 2b, so this skill must enforce it
directly. **If any test being written or edited is an e2e test** (extends
`E2ETest`, lives under `src/test/java/songscribe/e2e/`), also read
`.agents/guides/testing-e2e.md` in full before touching it — this is on top
of, not instead of, the two guides above.

### Path A: `--fix` mode

1. **Fix all findings immediately** — every finding from every agent (Reuse, Quality, Efficiency, and Test Quality if Phase 2b ran), including minor and low-confidence ones. Do not ask any questions or seek approval.
2. **Summarize** what was fixed when done, grouped by the same axes. Write the summary in plain language per **How to Write Findings** — for each fix, say what the code did before, what it does now, and what that means in practice.

### Path B: Interactive mode (default)

1. **Present findings.** Output all findings in a single organized summary. Group by: Reuse, Quality, Efficiency (from Phase 2), and — if Phase 2b ran — Test Quality (Correctness → Usefulness → Coverage sub-groups).

   Rewrite every agent finding in your own words before showing it. Do not pass
   an agent's text through untouched — the agents write for other agents; you
   write for a person who has not read the code. Apply **How to Write Findings**
   to each one: where it is, what the code does now, what's wrong in plain
   terms, and what you would change. If you cannot explain a finding plainly,
   you do not understand it well enough to report it — either dig in until you
   can, or drop it.

   Then add an empty line followed by "Ready for questions."

2. **Clarifying questions.** If any findings need clarification (ambiguous code intent, unclear whether something is intentional, etc.), ask them via AskUserQuestion. Give the background in the question text itself: what the code does, what looked off, and what each answer would cause you to do. The answer options must be understandable without looking at the code.

3. **Questionable findings.** For any findings you believe are false positives or not worth addressing, present them via AskUserQuestion and ask whether the user wants them fixed anyway. Say plainly why you think each one is not worth acting on. Do not silently skip findings.

4. **Approval.** Once all questions are resolved, use AskUserQuestion to present the final list of issues to fix and ask for approval to proceed (or for further discussion). Describe each item in one plain sentence naming the actual change, not a category label.

5. **Fix.** After approval, fix the approved issues. Briefly summarize what was fixed when done, in the same plain language.
