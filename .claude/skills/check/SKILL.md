---
name: check
description: Adversarial code review and cleanup — design, reuse, quality, efficiency, and test quality
model: opus
effort: high
disable-model-invocation: true
---

## Check: Code Review and Cleanup

Review code for design, reuse, quality, and efficiency, and audit any tests in
scope for correctness, usefulness, and coverage. This is an adversarial review;
the code may have been written by a human or by a different LLM. Fix what it
finds.

Fix it at **every** level, including the design level. A review that patches the
symptoms of a structural flaw and leaves the flaw standing has not improved the
code — it has made the debt harder to see.

IMPORTANT: All reviews MUST apply the Java style rules in addition to the
criteria below. When the review is done and fixes are applied, DO NOT run any
other commands or skills on your own volition.

### Doctrine (read both before Phase 2)

These two files govern every phase of this skill and are given to every agent it
spawns. Read them yourself before launching agents; do not paraphrase them into
prompts.

- **`reference/findings.md`** — how findings must be written, and the rule that
  a defect outside the review target is still a defect.
- **`reference/design-flaws.md`** — how to tell a symptom from its cause, what
  may never be proposed, how to report a design finding without arguing against
  it, and the approval rule for design-level findings. This one binds what you
  may *do* and how you may *frame* it, not just what you may report.

Paths in this file are relative to `.agents/skills/check/`.

## Phase 1: Determine Scope

Follow **`reference/scope.md`**. It parses the flags (`--fix`, `--mutation`,
`--tests-only`), resolves `$ARGUMENTS` to a review target by one of three modes
(working-tree diff, commit, or package/file), and partitions the result into a
**production scope** and a **test scope**, mapping each test to its production
counterpart.

Either scope may be empty, and that decides which agents run in Phase 2. If both
are empty, say so and stop.

## Phase 2: Launch Review Agents in Parallel

Launch every applicable agent concurrently in a single message via the Agent
tool. Open each prompt with **`reference/agent-preamble.md`**, which points the
agent at the doctrine files and at `.agents/rules/serena.md`.

- **Production scope non-empty** → the four agents in
  **`reference/agents-production.md`**: Reuse, Quality, Efficiency (sonnet), and
  Architecture (opus). Skip these when `--tests-only` was passed.
- **Test scope non-empty** → the three agents in
  **`reference/agents-tests.md`**: Correctness, Usefulness (sonnet), and
  Testability and Design (opus).

With both scopes populated that is seven agents in one message.

Two agents run on opus by design — Architecture and Testability and Design. They
are the axes where a smaller model reliably returns a plausible-sounding
workaround instead of the actual cause, which is the failure this skill is built
to avoid.

## Phase 3: Coverage

Runs only when the test scope is non-empty. Follow the **Coverage** section of
`reference/agents-tests.md`: generate a fresh JaCoCo report for the production
classes identified in Phase 1 and report the gaps in plain words.

## Phase 4: Mutation Testing

Runs only when `--mutation` was passed **and** the test scope is non-empty.
Follow the **Mutation Testing** section of `reference/agents-tests.md`.

Mutation is the slow path and contends with Phase 2 for the build. When it is
enabled, run Phases 3–4 after Phase 2's agents have returned rather than
overlapping them.

## Phase 5: Report, Approve, Fix

Follow **`reference/report-approve-fix.md`**. It carries both paths — `--fix`
and interactive — the ordering of the unified report, the guides that must be
read before any test is written, and the approval rule that applies to design
findings in both paths. In interactive mode, findings are written to
`plans/findings.md` rather than displayed in chat, and the skill moves
straight into clarifying questions.
