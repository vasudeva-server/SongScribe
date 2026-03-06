# Check: Code Review and Cleanup

Review code for reuse, quality, and efficiency. Fix any issues found.

IMPORTANT: All reviews MUST apply the rules in .claude/rules/code-styles/java+kotlin.md in addition to the criteria below.

## Phase 1: Determine Scope

This skill supports two modes based on `$ARGUMENTS`:

### Mode A: Git Diff (default)

If `$ARGUMENTS` is empty or not provided, review changed files from git:

- Run `git diff` (or `git diff HEAD` if there are staged changes) to get the diff.
- If there are no git changes, review the most recently modified files that the user mentioned or edited earlier in this conversation.
- The diff output is the **review target** passed to agents in Phase 2.

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

## Phase 2: Launch Three Review Agents in Parallel

Use the Agent tool to launch all three agents concurrently in a single message. Pass each agent the review target so it has the complete context.

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

## Phase 3: Review and Approve Findings

Wait for all three agents to complete, then:

1. **Present findings.** Output all findings from the three agents in a single organized summary.

2. **Dummy prompt.** Immediately present a prompt with the text "Press Enter to continue". Do NOT use AskUserQuestion for this prompt, and do not act on the response.

3. **Clarifying questions.** If any findings need clarification (ambiguous code intent, unclear whether something is intentional, etc.), ask them via AskUserQuestion.

4. **Questionable findings.** For any findings you believe are false positives or not worth addressing, present them via AskUserQuestion and ask whether the user wants them fixed anyway. Do not silently skip findings.

5. **Approval.** Once all questions are resolved, use AskUserQuestion to present the final list of issues to fix and ask for approval to proceed (or for further discussion).

6. **Fix.** After approval, fix the approved issues. Briefly summarize what was fixed when done.
