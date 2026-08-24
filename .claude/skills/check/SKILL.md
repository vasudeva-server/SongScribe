---
name: check
description: Adversarial contract-driven review — design, contracts and API, correctness and efficiency, test conformance
model: opus
effort: high
disable-model-invocation: true
---

## Check: Contract-Driven Code Review and Cleanup

Review code along four axes — **Design**, **Contract & API**, **Correctness &
Efficiency**, and **Test Conformance** — and fix what it finds. This is an
adversarial review; the code may have been written by a human or by a different
LLM.

Fix it at **every** level, including the design level. A review that patches the
symptoms of a structural flaw and leaves the flaw standing has not improved the
code — it has made the debt harder to see.

**The yardstick is the design, then the contract, never the implementation.**
Ask first what a type could carry that the code checks at runtime, because an
invariant the type carries needs no contract clause and no test — that ordering
is in `~/.claude/guides/design.md`, which every axis reads. Then: code is judged
against what it promises; the promise against what the domain requires; a test
against whether the design could have enforced it instead. Nothing in this skill
is judged by how many lines or branches ran.

IMPORTANT: All reviews MUST apply the Java style rules in `.claude/rules/java.md`
in addition to the criteria below. When the review is done and fixes are applied,
DO NOT run any other commands or skills on your own volition.

### Doctrine (read all three before Phase 2)

These files govern every phase. Read them yourself before launching agents; do
not paraphrase them into prompts.

- **`reference/check-findings.md`** — how findings must be written, and the rule that
  a defect outside the review target is still a defect.
- **`reference/design-flaws.md`** — how to tell a symptom from its cause, what
  may never be proposed, how to report a design finding without arguing against
  it, and the approval rule for design-level findings. This one binds what you
  may *do* and how you may *frame* it, not just what you may report.
- **`reference/axes.md`** — the prompt preamble every agent opens with, and the
  four axis briefs.

Paths in this file are relative to `.claude/skills/check/`.

## Phase 1: Determine Scope

`$ARGUMENTS` may contain optional flags plus a scope token. Parse all flags
first, then resolve the remaining token as the scope.

### Flags

- **`--fix`** — suppress interactive questions and fix every finding, including
  minor and low-confidence ones. Design findings and changes to an existing
  contract are the exceptions; see Phase 3.
- **`--tests-only`** — review the tests rather than the production code. Run
  Test Conformance and Design over the `*Test.java` files in scope and their
  production counterparts; skip Contract & API and Correctness & Efficiency.
  Design still runs because a test that strains is evidence about the production
  design, and that diagnosis is the whole reason to audit tests separately.

### Mode selection (the remaining token)

- **Empty** → Mode A (working-tree diff).
- **A git commit-ish** → Mode C (commit). Before treating a non-empty argument
  as a package or file, test whether it is a commit: run
  `/opt/homebrew/bin/git rev-parse --verify --quiet "$ARGUMENTS^{commit}"`. If
  that succeeds, and the argument is not also an existing file or directory
  path, use Mode C.
- **Anything else** → Mode B (package or file).

### Mode A: Git Diff (default)

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

### Mode B: Package or File Review

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
   invocation to stay within context limits, running multiple rounds of Phase 2.

### Mode C: Commit Review

- Run `/opt/homebrew/bin/git show <commit>` to get the diff for the review
  target.
- Run `/opt/homebrew/bin/git diff-tree --no-commit-id --name-only -r <commit>`
  to get the list of changed file paths. Exclude deleted files.

### Partitioning the resolved scope

After resolving the scope in any mode, split it into two subsets:

- **Production scope** — all non-test source files (`.java`, `.kt` that do not
  end in `Test.java`).
- **Test scope** — all `*Test.java` files.

For each test in the test scope, identify its **production counterpart** (e.g.
`StringUtilsTest` → `songscribe.util.StringUtils`) using Serena per
`.claude/rules/serena.md`, and locate that counterpart's contract — the method
Javadoc, the class Javadoc, and any `docs/*.md` they link to. The Test
Conformance axis is given the contract, not just the class.

Either scope may be empty. If both are empty, say so and stop.

## Phase 2: Launch the Axes in Parallel

Launch every applicable agent concurrently in a single message via the Agent
tool. Open each prompt with the preamble in **`reference/axes.md`**, reproduced
as written, then that axis's brief from the same file, then the material being
reviewed.

| Axis | Model | Runs when |
|---|---|---|
| Design | opus | any scope is non-empty |
| Contract & API | opus | production scope non-empty, and no `--tests-only` |
| Correctness & Efficiency | sonnet | production scope non-empty, and no `--tests-only` |
| Test Conformance | sonnet | test scope non-empty |

Give the Design agent the tests as well when the test scope is non-empty: a test
that strains is evidence about the production design, and that diagnosis is this
axis's job.

With both scopes populated that is four agents in one message.

Design and Contract & API run on opus by design. Root-cause analysis and
contract judgment are the two axes where a smaller model reliably returns a
plausible-sounding workaround instead of the actual cause, or a contract
paraphrased out of the method body instead of a promise the domain requires.

**Style conformance is not an axis.** It is the cheapest finding to produce, and
spending an agent on it crowds out the quietest ones. Apply the Java style rules
yourself while fixing.

## Diagnostics are not phases

Coverage and mutation testing do not run as part of this skill. That is a
statement about *this skill*, not a ranking of the two tools — coverage is a
required closing step of a design pass (`/design-pass`, step 9),
where the whole package's contracts have just been written and a claim of
completeness can be checked against what actually ran. A review of a diff is not
that situation: there is no finished body of contracts to check the claim against,
and the ranked list of uncovered regions arrives with nothing to weigh it against
but the diff.

Both are invoked deliberately and scoped to something specific you are
investigating:

- `./scripts/coverage.sh` answers *did this code run?* Of each unexecuted region
  it asks exactly one question — does this correspond to a contract case that is
  missing, or to implementation the contract promises nothing about? You never
  write a test to turn a region green.
- `./scripts/mutation-test.sh` answers *does anything observe what this code
  produces?* Under contract testing a high surviving-mutant count is the
  expected, healthy state, because contract tests deliberately leave the
  implementation free to change. The score is never reported as a grade.

See **Diagnostics: coverage and mutation** in
`.claude/guides/testing-common.md`. A ranked list of uncovered regions handed to
an agent told to fix what it finds is a to-do list, and it structurally produces
the green-chasing the contract regime exists to remove — which is why neither one
runs here automatically.

## Phase 3: Report, Approve, Fix

Wait for every launched agent to complete, then choose the path based on whether
`--fix` was in `$ARGUMENTS`.

**Before writing or editing any contract**, read `~/.claude/guides/design.md` for
whether the API's fan-in earns one, then `.claude/guides/contracts.md` and the
**Writing a Contract in Javadoc** section of `.claude/rules/java.md` in full —
once, not once per contract.

**Before writing or editing any test**, read `~/.claude/guides/design.md` for
whether the behavior earns a test at all, then `.claude/guides/testing-common.md`
and `.claude/guides/testing-unit.md` in full — once, not once per test. Writing
tests requires the whole guide's conventions: derivation from the contract,
naming, structure, fixtures, and assertion style. **If any test being written or edited is an e2e test** (extends
`E2ETest`, lives under `src/test/java/songscribe/e2e/`), also read
`.claude/guides/testing-e2e.md` in full — on top of, not instead of, the two
guides above.

### Two kinds of finding require explicit approval, in both paths

1. **Design findings** — per *Approval* in `design-flaws.md`. Present the flaw,
   the corrected design, what the change touches, what it costs to leave alone,
   and which tests it simplifies or removes.
2. **Changes to an existing contract** — a contract is what callers rely on, so
   changing one is a visible decision, stated explicitly and agreed, never made
   silently in the middle of a fix. Present what it promises now, what it would
   promise instead, and why the domain requires the new promise. Weakening a
   contract because a test fails is legitimate **only when the contract was wrong
   about the domain** — never because it is the cheapest route to green.

Writing a *missing* contract for an API that has none is an ordinary fix, not an
approval case — unless the promise is a musical or domain judgment (tuplets,
beaming, ties, melisma placement, key signatures), in which case propose it and
get it confirmed before writing it.

### Path A: `--fix` mode

1. **Present the approval cases above** via AskUserQuestion before changing any
   code. `--fix` means "don't pester me about ordinary fixes"; it does not
   authorize restructuring the user's architecture or altering what the code
   promises its callers unattended. If there are none, skip to step 2.
2. **Fix every remaining finding immediately** — every finding from every axis,
   including minor and low-confidence ones, **and including findings in code
   outside the review target** (see *Findings Outside the Review Target* in
   `check-findings.md`). Do not ask any questions about these. Apply approved design
   and contract changes here too, along with the test changes they enable.
3. **Do not paper over a declined finding.** If the user declined the structural
   fix, apply only the fixes that stand on their own. Where the only available
   fix would have been a workaround around the declined flaw — another parameter
   or cache in production code, another mock or test-only accessor in a test —
   leave it and say so in the summary rather than adding debt.
4. **Compile and test.** Any change under `src/main/` or `src/test/` requires
   `./scripts/compile.sh`; then re-run the relevant `./scripts/test.sh <target>`
   (unit only) to confirm green.
5. **Summarize** what was fixed, leading with the design and contract changes if
   any were approved, then grouped by axis. Write the summary in plain language
   per `check-findings.md` — for each fix, say what the code or test did before, what
   it does now, and what that means in practice.

### Path B: Interactive mode (default)

1. **Write findings** to `plans/check-findings.md`, overwriting any existing content,
   in a single organized document, in this order:

   - any concrete **production bug**, first;
   - **design findings**, each followed by the symptoms and tests it explains,
     so the reader sees one decision rather than a list of unrelated patches;
   - **contract findings** — missing, incomplete, or implementation-derived
     contracts, wrong names, and signatures that need a parameter object or an
     enum. Put a proposed change to an existing contract at the top of this
     group, since it is an approval case;
   - the remaining findings, grouped by axis — Correctness & Efficiency, then
     Test Conformance. Within Test Conformance, put first the tests that pass
     while the code could be broken, then the tests asserting nothing the
     contract promises, then the contract cases with no test.

   Where a symptom's only standalone fix would be a workaround around a design
   flaw you reported, say so where you list it. The reader needs to know which
   fixes become pointless if they approve the structural change, and which of
   them add debt if they don't.

   Rewrite every agent finding in your own words before writing it. Do not pass
   an agent's text through untouched — the agents write for other agents; you
   write for a person who has not read the code or the tests. Apply
   `check-findings.md` to each one: where it is, what the code does now, what's wrong
   in plain terms, and what you would change. If you cannot explain a finding
   plainly, you do not understand it well enough to report it — either dig in
   until you can, or drop it.

   Once the file is written, tell the user it's ready at `plans/check-findings.md`
   and move straight into clarifying questions below — do not also paste the
   findings into chat.

2. **Clarifying questions.** If any finding needs clarification — ambiguous code
   intent, unclear whether something is intentional, a promise that turns on a
   musical judgment you should not make alone — ask via AskUserQuestion. Give the
   background in the question text itself: what the code or test does, what
   looked off, and what each answer would cause you to do. The options must be
   understandable without looking at the code.

   **Check every question against *Never offer a menu of workarounds* in
   `design-flaws.md` before you send it.** If you cannot point to one option
   that leaves the code in a state you would defend, do not ask — go work out
   the design and come back with a finding. A question of the form "which of
   these two places should own X" is the shape this fails in most often: answer
   "does X belong in either, in this form?" first.

3. **Questionable findings.** For any finding you believe is a false positive or
   not worth addressing, present it via AskUserQuestion and ask whether the user
   wants it fixed anyway, saying plainly why you think it is not worth acting
   on. Do not silently skip findings. **"It's outside the review target" is not
   a reason to put a finding here** — see *Findings Outside the Review Target* —
   and neither is **"the fix would be a big change"** or **"the fix would mean
   changing production code"**; see `design-flaws.md`. This step is for findings
   you believe are **wrong**, not for real defects you'd rather not touch.

4. **Approval.** Once all questions are resolved, use AskUserQuestion to present
   the final list of issues to fix and ask for approval to proceed (or for
   further discussion). Describe each item in one plain sentence naming the
   actual change, not a category label. Findings in code outside the review
   target belong in this list on equal footing with the rest.

   **Put each design finding and each existing-contract change as its own
   decision**, not as one line item among the small fixes — each has a different
   cost and a different payoff, and burying it in a list of tidy-ups is a way of
   discouraging it. State the corrected design or the new promise, what the
   change touches, and which tests it removes in the question, so the choice can
   be made without reopening the files.

5. **Fix.** After approval, fix the approved issues, following the guides read at
   the start of this phase for any contract or test work. If a design fix was
   declined, apply only the fixes that stand on their own and say which ones you
   left undone because their only fix would have been a workaround. Any change
   under `src/main/` or `src/test/` requires `./scripts/compile.sh`; then re-run
   the relevant `./scripts/test.sh <target>` (unit only) to confirm green.
   Briefly summarize what changed, in the same plain language.
