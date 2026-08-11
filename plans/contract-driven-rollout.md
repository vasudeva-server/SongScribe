# Contract-Driven Rollout — Work Plan

Execution plan for the shift decided in
[`contract-driven-testing.md`](./contract-driven-testing.md) — read that first;
it holds the philosophy, the guardrails, the dialog defects, and the inventory of
existing test-only surface that these phases act on.

---

## Reading order and prerequisites

A session resuming this work needs both documents:

1. **`plans/contract-driven-testing.md`** — the philosophy (§2), the conflicts
   with the current guides and their line numbers (§3), the six guardrails (§4),
   the dialog policy and the two `SongSettingsDialog` defects (§5), and the
   31-member test-only-surface inventory in its three categories (§6). Phases
   below cite it by section.
2. **This document** — decisions, phases, tasks.

**Where things live.** `.agents` is a **symlink to `.claude`**; both spellings
reach the same files. `check` is project-local at `.claude/skills/check/`
(SKILL.md plus 7 files in `reference/`). `make-plan`, `review-plan`,
`execute-plan` and `spec-developer` are **global** at `~/.claude/skills/`.
Project guides are `.claude/guides/`, project rules `.claude/rules/`.

**Branch:** `refactor/contract-driven-development`, from `develop` at `215959c0`.

**Not driven by `execute-plan`.** Phases carry a model and an effort level but no
`Files:` field and no `BlockedBy`; they run in the listed order.

---

## How to resume

Context is cleared between phases, so this document is the only memory.

1. **Find the current phase** — the first one whose status is not ✅. Statuses:
   ⏳ not started · 🔄 in progress · ✅ complete.
2. **Read both documents** named above before touching anything.
3. **Run the phase** as written. Its tasks are self-contained; they name the
   files and inline the decisions they depend on.
4. **Finish the phase** by setting its status to ✅ *in this file* and committing
   — the status change goes in the same commit as the work (D24). An
   uncommitted phase is a lost phase.

If a phase turns out to be wrong or underspecified, fix this document as part of
that phase rather than working around it in the moment; the next session sees
only what is written here.

---

## Decisions

| # | Decision |
|---|---|
| D1 | **Full audit.** Every nontrivial method gets a documented contract; tests rewritten or discarded accordingly. Duration is not the constraint. |
| D2 | Dialog unit tests go; the front-end/back-end restructuring stays. The back end must be completely UI-independent and unit-testable. A separate dialog e2e set confirms wiring only, run when a dialog is created or gains a feature. |
| D3 | `coverage.sh` and `mutation-test.sh` stay, invoked deliberately, out of `check`'s automatic phases. |
| D4 | Design a decoupling seam for `StandardDialog` — see the dialog phase. |
| D5 | The ~11 lifecycle hooks become proper singleton teardown (discussion doc §6.3). |
| D6 | Per-area order: write API contracts → write the testing-approach Javadoc → modify, rewrite or discard tests. |
| D7 | **Language-neutral principles live in `~/.claude/rules/development.md`** (global). Only language- and project-specific mechanics stay local. |
| D8 | The testing-approach Javadoc lives on the **test class**, not the production method. |
| D9 | The dialog track runs after the pilot and the guide revision. |
| D10 | **No other work proceeds until this is complete** — revisited after the pilot retrospective, when a rate exists. |
| D11 | One commit per *step* within a package, not per package. |
| D12 | Branch `refactor/contract-driven-development`, from `develop`. |
| D13 | Issue #773 is a re-sort by kind, done once the guides are rewritten. |
| D14 | `check`, `make-plan`, `review-plan`, `execute-plan` and `spec-developer` are all rewritten to conform. |
| D15 | Document `ui/platform`'s contract; skip `converter`, `uiconverter`, `export`; write no tests for any of them. |
| D16 | Pilot package is **`undo`**. |
| D17 | **Diagrams are dropped** — from the skills, from `docs/`, and from Javadoc — unless a diagram materially adds what the contracts cannot. Judged per package, once that package's contracts exist. |
| D18 | `check` is reorganized along **four condensed axes**, not narrowed to two. |
| D19 | **Mutation testing is a scoped debugging aid, never a score.** |
| D20 | **The per-phase task limit is removed.** `make-plan` capped Sonnet phases at 5 tasks and `review-plan` at 6; both were written for a 200K context. Sonnet now has 1M. Phases are sized by coherence, not task count. |
| D21 | **Every phase carries a model and an effort level.** Default to Sonnet; Opus only where the phase decides something rather than applying something already decided. |
| D22 | Phases carry no `Files:` field; this plan is not run through `execute-plan`. |
| D23 | **Phase 1 writes to `~/.claude/rules/development.md` directly.** It is outside the repo and outside this branch: it takes effect for every project the moment it lands, and discarding the branch will not undo it. Accepted. |
| D24 | **Every phase ends with a commit**, using the `/commit-commands:commit` skill, and that commit includes the phase's status change to ✅. Package phases additionally commit per step (D11). A phase is not complete until it is committed — context is cleared between phases, so an uncommitted phase is a lost phase. |

Three principles that must land in the global rules:

- **Contract-first.** Write a method's API contract *first*, then the code to
  fulfill it. It forces thinking about *what* rather than *how*; it forces
  thinking as a caller (is the signature unambiguous? more than 4 parameters, in
  which case a record is the better API?); and writing code to fulfill a stated
  API is itself a preliminary pass at testing.
- **Method names are the first level of the contract.** They must state clearly
  and accurately what the method does. Because `jetbrains_rename` does most of
  the work, **there is never a reason to resist renaming** once a better name is
  determined.
- **Signature quality is contract quality.** A maximum parameter count past which
  a record is required; enums over booleans (already in `java.md`); a record
  whenever several same-typed parameters could be misordered at a call site.

---

## Splitting principle

Opus decides; Sonnet applies. A phase goes to Opus when it must **choose** —
what a contract promises, what an axis of review covers, how a seam is shaped.
It goes to Sonnet when the choice is already recorded in this plan or in the
discussion doc and the work is to carry it out: editing named files to a stated
end, renaming through `jetbrains_rename`, enumerating symbols, triaging tests
against a contract that already exists.

Each phase is written to be executed by a session with no memory of the
conversation that produced this plan. Phases name exact files and inline the
decisions they depend on.

---

## Phase 1 — Global, language-neutral rules

**Model:** Opus · **Effort:** high · **Status:** ✅

Authoring the rules that will govern all future work in every project. The
content is decided; the phrasing is load-bearing.

Rewrite `~/.claude/rules/development.md` to add a Contracts section and a
contract-driven testing section covering:

1. Contract-first, and the three-tier hierarchy: method-level contract in the
   method's doc comment; object and subsystem invariants at class/package level;
   architectural and domain rules spanning subsystems in prose documents.
2. Method names as the first level of the contract; rename freely.
3. Signature quality rules: parameter count past which a record is required;
   enums over booleans; records for misorderable same-typed parameters.
4. Contract-driven testing: derive cases from the contract, one representative
   per materially distinct input class plus the extremes; enumerate the domain
   when it is finite and small rather than sampling it; test invariants where the
   contract is an invariant rather than a table.
5. A failing test means one of three things — code, test, or contract — and
   changing the contract to reach green is legitimate only when the contract was
   wrong about the domain, never as the cheapest route. A contract change is
   stated explicitly, never made silently mid-fix. (Discussion doc §4.1.)
6. Contracts are derived from the domain, not from reading the implementation. A
   contract the current implementation could not possibly violate is describing
   the code, not promising anything. (§4.2.)
7. Coverage is a sanity check, never a target.
8. No production surface exists solely for tests — no methods, no accessors, and
   fields are never widened for tests. Reflection into production internals is
   the same violation, not an escape from it. (§4.4.)
9. When a test cannot arrange the state it needs, the answer is a constructor or
   factory that takes that state — real API, used by production too — never an
   accessor and never reflection. (§4.5.)
10. Private helpers are tested through the contract they serve. Promote one to an
    internal API only when it has become a distinct concept with a stable
    contract — not because it is long or awkward to reach.
11. Diagrams only where they show what prose cannot (D17).

Keep it language-neutral: no Java, JUnit, or SongScribe specifics. Those go in
the project guides.

**Decided (D23):** this file is outside the repo and outside this branch. It
takes effect for every project the moment it lands, and discarding the branch
will not undo it. That is accepted — write it directly, do not stage it in the
project first.

---

## Phase 2 — `contracts.md`

**Model:** Opus · **Effort:** high · **Status:** ✅

Create `.claude/guides/contracts.md`: the worked Java form of the Phase 1
principles, loaded on demand rather than always in context.

1. What belongs in a method contract: preconditions (valid inputs, ranges,
   nullability, required state), postconditions, `@throws`, boundary semantics
   (inclusive/exclusive, empty inputs, special values), result invariants, side
   effects, and input/output relationships.
2. A full worked example built from a real SongScribe API, showing the Javadoc
   and the test cases derivable from it without reading the implementation.
3. Class and package Javadoc: object and subsystem invariants that would
   otherwise be repeated across methods and eventually contradict each other.
4. When a rule belongs in `docs/` instead — it spans subsystems.
5. The project bans `Optional`, so a `@Nullable` return's contract must state
   what null *means*; the type says nothing.
6. Constants: a constant is part of the contract if a contract's Javadoc names it
   via `{@value #X}` or `{@link Class#X}`. If no contract mentions it, it is
   implementation, and a test needing it is testing implementation. (§4.6.)
7. Cross-reference `java.md` for `{@value}` mechanics rather than restating them.

---

## Phase 3 — `testing-common.md` rewrite

**Model:** Opus · **Effort:** high · **Status:** ✅

Rewrite `.claude/guides/testing-common.md` around contract-derived cases.

1. Replace the Correctness / Usefulness / Coverage triad with: derive the cases
   from the contract, then a short trustworthiness list — can the test fail; does
   its name name the contract case it asserts; no flakiness; no order dependence.
2. **Delete `Testability Over Encapsulation` entirely** (currently lines
   208–229). It instructs authors to widen private methods and add
   getters/setters for tests, which Phase 1 forbids. Keep only its ban on
   redeclaring a constant's literal in test code, folded into the constants rule.
3. Delete the sanctioned reason "widening a member to package-private to test it
   directly" from the unit-level rubric (currently line 86).
4. Coverage: gap-finder only, invoked deliberately, never routine.
5. Keep intact: the unit/e2e/none level rubric, Test Independence, Fixture
   Ordering, the MBassador unsubscribe rule, frameworks, naming, fixtures.
6. Add: the testing-approach Javadoc belongs on the test class (D8), stating
   which equivalence classes, boundaries and invariants that class exercises.

---

## Phase 4 — Mechanical guide edits

**Model:** Sonnet · **Effort:** medium · **Status:** ⏳

Every change here is already decided; apply it.

1. `.claude/guides/testing-unit.md` — add `@ParameterizedTest` / `@MethodSource`
   as the normal shape for equivalence classes and for invariants over many
   inputs, with examples. Demote the `MainFrame` singleton-mocking recipes from
   first resort to a fallback, noting that a dependency needing this much mocking
   is usually a constructor-injection finding. Remove references to
   `Testability Over Encapsulation`.
2. `.claude/guides/testing-e2e.md` — rewrite the Core Principle: e2e proves
   *wiring*, one test per path, never per case. Everything else (runner flags,
   helpers, coordinate and layout synchronization) is unchanged.
3. `.claude/rules/java.md` — add a Javadoc contract-syntax section (how to write
   preconditions, postconditions, `@throws`, invariants in Javadoc), and the
   signature rules in Java terms: parameter count past which a record is
   required, enums over booleans (already present — cross-reference rather than
   duplicate), records for misorderable same-typed parameters.
4. `.claude/rules/development.md` (project) — reduce to project mechanics only:
   branch topology, the `scripts/` commands, unused imports, the testing-guide
   pointer. Everything language-neutral now lives in the global rules; add a
   pointer there rather than restating it.
5. `CLAUDE.md` — the entry point every session reads first, and it currently says
   nothing about contracts. Add contract-writing to the **Required Reading by
   Task** table pointing at `.claude/guides/contracts.md`, with a trigger line
   covering any new or changed API. Update the Design Docs paragraph, which
   currently says *"Guides in `.agents/guides/` tell you the conventions to
   follow; `docs/` explains why a subsystem is built the way it is"* — under the
   new hierarchy `docs/` holds tier-3 *contracts*, not explanations. Leave the
   `.agents/guides/` paths alone here; Phase 7 moves the files and fixes every
   reference in one pass.

---

## Phase 5 — Rebuild the `check` skill

**Model:** Opus · **Effort:** high · **Status:** ⏳

`.claude/skills/check/` currently runs 7 agents across 5 phases with 7 reference
documents. Rebuild it around four axes:

| Axis | Model | Covers |
|---|---|---|
| Design | opus | architecture, boundaries, reuse and duplication, wrong abstractions |
| Contract & API | opus | contract stated / complete / derived from the domain; method naming; signature clarity and the signature rules |
| Correctness & efficiency | sonnet | real defects, null contracts, repeated or redundant work |
| Test conformance | sonnet | do the tests exercise the contract's classes, boundaries and invariants |

1. Reuse folds into Design — both answer *is this the right structure?*, and
   duplication is the most common instance of a structural fault, which is how
   the global rules already frame it.
2. Style conformance stops being an axis. It is the cheapest finding to produce
   and spending an agent on it crowds out the quietest ones.
3. Remove the coverage phase and the mutation phase from the automatic path
   (D3). Both remain available as deliberate, scoped invocations.
4. Add to the Contract & API axis the mechanical test-only-surface check: a
   member whose every reference resolves under `src/test/` is a hard finding, not
   an advisory one.
5. Collapse `reference/` from 7 documents to 2–3. `findings.md` and
   `design-flaws.md` carry doctrine worth keeping; the four agent-roster
   documents collapse into one.
6. The test axis must not reintroduce coverage thinking: it checks the tests
   against the *contract's* classes and boundaries, never against the
   implementation's branches.
7. The test axis also checks **derivation**, which is a distinct fault from
   coverage thinking: a test can be aimed at a real contract case and still
   assert a detail its author took from the method body. The mechanical form is
   *does every assertion correspond to a clause of the contract?* — an assertion
   with no clause is either a missing clause or a test written from the code.
   See *Write from the contract, not from the code* in
   `.claude/guides/testing-common.md`.

---

## Phase 6 — Planning and review skills

**Model:** Sonnet · **Effort:** medium · **Status:** ⏳

All five files are global, under `~/.claude/skills/`.

1. **`review-plan/SKILL.md`** — three edits. Delete line 35, *"Well-tested code
   is non-negotiable; I'd rather have too many tests than too few"*, and replace
   it with a preference for contract-derived cases. Rewrite §3 Test review (line
   97), which currently diagrams "new branching if statements or outcomes" and
   demands a test for each — that is coverage thinking at plan time; replace with
   a check that the plan states the contracts it adds or changes and that each
   materially distinct behavior class has a case. Cut the diagram-liberally
   guidance (lines 51–54 and the priority hierarchy at line 31) per D17.
2. **`review-plan/SKILL.md`** — remove the "roughly 6 tasks max" Sonnet phase cap
   (line 27) per D20.
3. **`make-plan/SKILL.md`** — remove the 5-task Sonnet cap (line 28) per D20. Add
   an effort level alongside the model in the per-phase assignment table (line
   34–41). Add the rule that any phase adding or changing an API carries a task
   to write the contract *before* the implementation task, and that its test
   tasks are derived from that contract.
4. **`make-plan/plan-templates.md`** — mirror the same two changes wherever the
   task cap or the model table appears.
5. **`execute-plan/SKILL.md`** and **`spec-developer/`** — read, then update for
   the same regime: contract-before-implementation ordering, effort levels, no
   task cap. Report what was found rather than assuming what needs changing.

---

## Phase 7 — Issue #773: re-sort guides and docs

**Model:** Sonnet · **Effort:** medium · **Status:** ⏳

Issue #773 asks whether there is a real distinction between `.agents/guides/` and
`docs/`. There is, and the contract hierarchy names it: **`docs/` holds tier-3
contracts** — what the system promises across subsystems — and **`guides/` holds
conventions** — how to write code here. Several current guides sit on the wrong
side of that line.

1. Tier-3 contracts, currently in `guides/`, that state system invariants:
   `unit-conversion.md`, `zoom.md`, `lyrics.md`, `messages.md`, `mutations.md`.
   Assess each against the criterion and move what qualifies.
2. Conventions that stay: `dialogs.md`, `singletons.md`, `strings.md`,
   `logging.md`, `flatlaf-props.md`, `serena-reference.md`, `option-dialogs.md`,
   `prefs.md`, `null-handling.md`, and the testing guides.
3. Decide whether `guides/` nests under `docs/` or sits beside it, and apply it.
4. Update every reference to a moved file: `CLAUDE.md`'s required-reading table,
   `.claude/rules/*`, `.claude/skills/**`, and cross-references between guides.
5. Close #773 with a comment recording the criterion used, so the distinction
   survives.

**Not in this phase:** the diagram sweep. Diagrams in `docs/` document
subsystems whose contracts do not exist until their package phases; judging them
now would be judging them against nothing. Each package phase judges its own, and
a final pass catches the rest.

---

## Phase 8 — Singleton lifecycle contracts

**Model:** Opus · **Effort:** high · **Status:** ⏳

Discussion doc §6.3 lists ~11 members that exist only because singletons hold
static mutable state and MBassador subscriptions that production never tears
down: `Actions.resetForTest` / `unsubscribeForTest`, `UndoController.resetForTest`
/ `unsubscribeForTest`, `PlaybackController.unsubscribeForTest`,
`SelectionCoordinator.unsubscribeForTest` (public),
`Prefs.removeObsoleteKeysForTest` and four siblings,
`RecentDocumentsManager.resetForTest` / `reloadForTest`,
`PreviewElementManager.resetOverlaysForTest`, `MainFrame.clearStartupErrorsForTest`,
`PreferencesDialog.resetInstrumentsForTesting`.

The resolution is not to delete them but to recognize that **a class with
`initialize()` and no way back has an incomplete lifecycle contract**, tests or
no tests.

1. For each owning class, decide and document the lifecycle contract: what
   `initialize()` establishes, what teardown must undo, what state may legally
   survive it, and whether re-initialization is permitted.
2. Decide each member's correct name and signature from that contract.
3. Where a member does not fit any legitimate lifecycle contract, say so — it
   belongs in the delete-or-restructure category instead.

Produce the decisions; Phase 9 applies them. This phase must precede every
package's test rewrite, because how a singleton tears down determines how every
test in every package sets up.

---

## Phase 9 — Apply the lifecycle renames

**Model:** Sonnet · **Effort:** medium · **Status:** ⏳

Apply Phase 8's decisions. Use `jetbrains_rename` so call sites update
automatically; write the lifecycle contract Javadoc on each class and each
renamed member. Compile with `./scripts/compile.sh` and run the unit suite with
`./scripts/test.sh`.

Also in this phase: the four members in discussion doc §6.2 that are already
coherent internal APIs wearing scaffolding names —
`SMuFLMetadata.requireMapValueForTesting`, `getAdvanceWidthForTesting`,
`getAdvanceWidthOrZeroForTesting`, and `Prefs.parseJsonValueForTest`. Rename each
to its concept and write its contract.

---

## Phase 10 — Full test-only-surface sweep

**Model:** Sonnet · **Effort:** medium · **Status:** ⏳

The 31-member inventory in discussion doc §6 is name-based only — it found
members *called* `*ForTest*`. Members widened without a telltale name are
invisible to it, and `FontDialog.java:37` proves they exist.

Checking all ~4,392 production members individually is not feasible. Work from
the tests instead:

1. For each test source file, collect the production symbols it references.
2. Deduplicate into a candidate set — every production member any test touches.
3. For each candidate, run `jet_brains_find_referencing_symbols`. If every
   reference resolves under `src/test/`, it is test-only surface.
4. Classify each hit into the three categories of discussion doc §6: genuinely
   test-only (delete or restructure), misnamed internal API (rename and
   document), or lifecycle (already handled in Phases 8–9).
5. Record the result in `plans/test-only-surface.md`. Do not fix anything here
   beyond what Phases 8–9 covered; each package phase fixes its own.

Also flag any test that reaches into production internals by reflection —
`getDeclaredField`, `setAccessible` — which is the same violation in a form the
compiler cannot see. `ReflectionTestHelper` (`src/test/java/songscribe/ui/selection/ReflectionTestHelper.java:228-236`)
is the known instance; its fix belongs to the `ui/selection` phase.

---

## Phase 11 — Pilot: `undo` contracts

**Model:** Opus · **Effort:** high · **Status:** ⏳

`undo` is 1,032 main LOC and 3,354 test LOC across 141 tests — ratio 3.25. Small
enough to finish quickly, real logic rather than wiring, and `docs/undo.md`
already seeds the tier-3 layer.

1. Inventory the package's nontrivial APIs and note what `docs/undo.md` and
   `.claude/guides/mutations.md` already state.
2. Write method contracts, class and package invariants, and update `docs/undo.md`
   for anything spanning subsystems.
3. Write the testing-approach Javadoc on each test class that will survive (D8).

Commit per step (D11). Contracts precede any deletion, so there is always
something to check a deletion against.

---

## Phase 12 — Pilot: `undo` test triage

**Model:** Sonnet · **Effort:** high · **Status:** ⏳

With Phase 11's contracts in place, triage all 141 tests: keep, rewrite, or
discard. A test mapping to no contract case is discarded, not preserved on the
theory that it might catch something. Fix the package's test-only surface from
Phase 10's record. Compile, run the unit suite, then run coverage once
deliberately — investigating whether an uncovered region is a missing contract
case or code the contract promises nothing about, never manufacturing a test to
turn it green.

Judge `docs/undo.md`'s diagrams against the contracts that now exist (D17): keep
only what shows something prose cannot.

Record the numbers in `plans/pilot-undo-results.md`: tests before, tests after,
contracts written, main and test LOC before and after, elapsed time. Phase 13
reads this file, so it must exist before that phase starts.

---

## Phase 13 — Retrospective and revision

**Model:** Opus · **Effort:** high · **Status:** ⏳

1. Write the retrospective to `plans/pilot-retrospective.md`, from the numbers in
   `plans/pilot-undo-results.md`: what a contract looks like in this codebase,
   the survival rate of the 141 tests, the rate per thousand lines, and what
   Phases 1–4 got wrong.
2. Revise the global rules, the guides, and the `check` skill accordingly.
3. Revisit D10's freeze with a real rate in hand.
4. Re-plan the remaining packages using the rewritten `make-plan`.

---

## Remaining phases

Deliberately not expanded into tasks yet. Each follows the same per-area
procedure, and their real shape depends on what Phase 13 learns — writing task
lists for them now would be writing against an unmeasured rate and an unrevised
guide.

| Area | Main LOC | Tests | Model | Notes |
|---|---:|---:|---|---|
| `ui/dialog` | 10,010 | 257 | Opus then Sonnet | Architectural, not a contract pass — see below |
| Foundations | 10,931 | 448 | Sonnet | `util`, `message`, `prefs`, `smufl`, `hit`, `engraving`, `shape`, `font`, `lifecycle`, `error`; one agent per package |
| `dom` | 15,483 | 1,213 | Opus | Heaviest domain judgment; paced by review |
| `io`, `midi` | 15,557 | 899 | Opus then Sonnet | Round-trip invariants |
| `layout`, `engraving` | 19,856 | 1,279 | Sonnet | Geometry: mechanical, invariant-heavy |
| `ui/selection` | 2,961 | 375 | Sonnet | Ratio 3.40; `ActionReflector` constructor removes the reflection |
| `ui/clipboard` | 754 | 78 | Sonnet | Ratio 2.83 |
| `ui/edit` | 2,270 | 156 | Sonnet | |
| `ui/action` | 6,669 | 420 | Sonnet | |
| `ui/playback`, `ui/menu`, `ui` root | 5,064 | 82 | Sonnet | |
| `ui/renderer` | 6,693 | 369 | Sonnet | |
| `ui/component` | 21,120 | 1,114 | Sonnet | Needs its own sub-split, drawn once the rate is known |
| `ui/platform` | 590 | 0 | Sonnet | Contract only; no tests (D15) |
| Final diagram pass | — | — | Sonnet | Whatever the package phases did not reach |

**`ui/dialog` scope (D2, D4).** An architectural track. The dialog is decoupled
in both directions: a record passed **in**, so it never reaches for `Song`,
`MainFrame` or the score; a record gathered **out** of the widgets; and a lambda
or method reference implementing a small interface passed in, which the dialog
calls to validate and to save. The dialog then knows nothing about the domain —
a widget shell over `Input → Output` plus a callback, with the back end
unit-testable and no UI type in any signature. Design the seam, prototype on one
dialog before committing `BaseDialog`/`StandardDialog` (1,275 lines), prove it on
`SongSettingsDialog` (split `isValidData()`'s decision from its presentation;
delete `getLineWidthFieldForTest()`), roll out, contract-and-test the back ends,
delete the front-end tests, add the thin e2e wiring set, then write `dialogs.md`
from what the design turned out to be.

---

## Per-area procedure

Applied in every package phase. D6 is steps 2–4. One commit per step (D11).

1. **Inventory.** The package's nontrivial APIs, its test-only surface from Phase
   10's record, and which existing `docs/*.md` already cover it.
2. **Write API contracts.** Method Javadoc for local contracts; class and package
   Javadoc for object and subsystem invariants; `docs/` for rules spanning
   subsystems. Domain contracts are proposed and confirmed, not decided
   unilaterally.
3. **Write the testing-approach Javadoc on each test class** (D8).
4. **Triage every existing test** against the contracts: keep, rewrite, discard.
5. **Fix test-only surface** per its category.
6. **Compile and run the unit suite** for the package.
7. **Judge the package's diagrams** against the contracts that now exist (D17).
8. **Diagnostics, deliberately and scoped** (D3, D19). Coverage answers *did this
   code run?* and finds contract cases you forgot to write. Mutation answers
   *does anything observe what this code produces?* and finds cases you wrote a
   test for but pinned nothing useful about. Under contract testing a high
   surviving-mutant count is the expected, healthy state, because contract tests
   deliberately leave the implementation free to change — so the mutation *score*
   measures precisely what we have decided not to optimize and is never reported
   as a grade.

---

## Parallel agents

**Help:** the foundations phase (one agent per package), Phase 10's sweep, and
step 1 inventory anywhere.

**Do not help:** `dom`, and the dialog seam design. A subagent guessing at a
music-notation promise produces confident, plausible, wrong Javadoc — worse than
none, because everything downstream then tests against it.

**Mixed:** triage. An agent can classify tests against a written contract; it
should not decide a contract case does not matter.
