---
name: contract-pass
description: Run the contract-driven pass over a package — write contracts, derive the tests from them, close the gaps coverage finds
model: opus
effort: high
disable-model-invocation: true
---

## Contract Pass

Take one package from *whatever it is now* to *contracts written, tests derived
from them, gaps closed*. Seven steps, one commit each, resumable across context
clears.

This is the per-area procedure in
[`plans/contract-driven-rollout.md`](../../../plans/contract-driven-rollout.md),
made runnable. The pilot that produced it is
[`plans/pilot-undo-results.md`](../../../plans/pilot-undo-results.md) and
[`plans/pilot-retrospective.md`](../../../plans/pilot-retrospective.md); read the
retrospective's §5 before your first run, because every trap it names is one this
skill walks past.

Paths in this file are relative to `.agents/skills/contract-pass/`.

### The yardstick

**The contract is the completeness criterion. Not coverage, not branches, not the
count of tests.** A package is finished when every materially distinct class of
behavior the contracts promise has a case, plus the boundaries and the invariants.
Whether a method holds 5 branches or 25 is not an input.

### Doctrine — read before step 2, do not paraphrase

- **`~/.claude/rules/development.md`** — Contracts and Contract-Driven Testing.
  The principles; everything here serves them.
- **`.agents/guides/contracts.md`** — what a method contract states in Java, with
  a worked example.
- **`.agents/guides/testing-common.md`** — where the cases come from, the
  testing-approach Javadoc, triage, diagnostics.
- **`.agents/guides/testing-unit.md`** — parameterized case tables and the
  exhaustiveness assertion.
- **`.agents/rules/java.md`** — Javadoc contract syntax and the signature rules.
- **`reference/classification.md`** — which contracts you may decide and which
  you must propose, and the checkpoint format.

## Step 0: Resolve, check, resume

`$ARGUMENTS` is a package: a dotted name with the `songscribe.` prefix implicit
(`undo`, `ui.selection`, `io.musicxml`). Production is
`src/main/java/songscribe/<dots-to-slashes>`, tests the same under `src/test/`.
No argument: ask which package rather than guessing.

1. **Check for a pause point.** Read the *Pause points* note under **Remaining
   phases** in `plans/contract-driven-rollout.md`. If the package is named there,
   stop and raise it before doing anything else.
2. **Open the record.** `plans/contract-pass/<package>.md`, dots as hyphens
   (`ui-selection.md`). If it exists, read it and resume at the first step not
   marked ✅ — it is the only memory across a context clear. If not, create it
   from the template at the end of this file.
3. **Confirm the tree is clean.** `git status`. Uncommitted work from another
   task makes the per-step commits wrong; stop and say so.
4. **Verify the branch.** Feature branches come off `develop`, never `main`.
5. **Capture the baseline into the record's *Before* column**, before touching
   anything. It cannot be reconstructed afterwards without archaeology, and the
   numbers are the point of running this on a package nobody has measured:

   ```bash
   P=<dots-to-slashes>          # e.g. ui/selection
   git rev-parse --short HEAD   # record as the start commit
   find src/main/java/songscribe/$P -name '*.java' | xargs cat | wc -l
   find src/test/java/songscribe/$P  -name '*.java' | xargs cat | wc -l
   ```

   Then `./scripts/test.sh <the package's test classes>` and record the **passing
   count**, not the number of `@Test` methods — a `@ParameterizedTest` is one
   method and many cases, and the case count is the one that means something.
   Record the wall-clock start time; step 9 records the end.

If the package has no tests (`ui/platform`, per D15), steps 3, 4 and 7 are
recorded as N/A and skipped. Say so in the record; do not silently omit them.

## Step 1: Inventory

**Delegatable** — one agent, or one per subpackage if the package is large.

Produce:

- every nontrivial API in the package — public and package-private methods,
  classes, and `package-info.java`, with what each already documents;
- which existing `docs/*.md` already state rules governing this package, since
  those are tier-3 contracts a method must cite rather than restate;
- the package's entry in `plans/test-only-surface.md`, which Phase 10 already
  swept — its findings are step 4's work list;
- every test class, with its test count and what it appears to cover.

A trivial getter with no logic is not a nontrivial API and does not need a
contract. Everything else does.

Subagent prompts must bound the search surface: absolute working directory,
repo-relative paths for every directory to examine, Serena's `jet_brains_*` tools
named explicitly, and an instruction to report and stop rather than widen the
search. Include *"Read `.agents/rules/serena.md` and follow it for all Java
exploration and refactoring."*

**Commit.** Record the inventory in the record file.

## Step 2: Write the contracts

**Inline. Never delegated.** A subagent guessing at a music-notation promise
produces confident, plausible, wrong Javadoc — worse than none, because
everything downstream then tests against it.

Work class by class. For each:

1. **Classify each contract** per `reference/classification.md`. Write the
   mechanical ones straight through.
2. **Batch the domain ones into one checkpoint for that class** and present them
   in the four-line form that file specifies. Write what the reviewer decides.
3. **Then the class Javadoc** — the invariants spanning several of its methods.
   A rule that would otherwise be repeated on three methods belongs here, and the
   methods cite it.
4. **Then `package-info.java`** — invariants spanning the classes.
5. **Anything spanning subsystems goes to `docs/`**, not into Javadoc. If a
   `docs/` document already states it, cite rather than restate.

While writing, three things that are not optional:

- **`@return` on every method whose return type is not `void`.** No exceptions,
  including one-line methods and methods whose summary already says what comes
  back. It is what the IDE shows at the call site.
- **The signature rules apply as you go** — more than four parameters or two
  transposable same-typed parameters take a `record`; a mode-selecting `boolean`
  takes an enum; a name that misdescribes gets renamed via
  `jet_brains_rename`. A contract you cannot state cleanly is usually a signature
  finding, and the moment you are writing the contract is the moment to catch it.
- **The tell of a real contract is that the implementation could in principle
  violate it.** If you cannot imagine the code breaking the promise, you have
  described the code. Rewrite it as what the domain requires, or find the promise
  underneath.

**Commit per class or per coherent group, not once at the end.**

## Step 3: Testing-approach Javadoc, triaging as you go

**Inline.** These were two steps until Phase 13. They are one activity: writing an
accurate *what this class is responsible for* comment requires checking every test
in it against the contract, which is the triage
([retrospective §5.4](../../../plans/pilot-retrospective.md)).

For each test class:

1. **Write the testing-approach Javadoc** (D8) — which equivalence classes,
   boundaries and invariants this class exercises, stated as the contract's
   clauses rather than as a list of inputs.
2. **Triage each test against the contracts** as you account for it: **keep** (it
   asserts a contract case, at the right level, and can fail), **rewrite** (real
   case, wrong test), or **discard** (maps to no contract case). A test mapping to
   no clause is discarded, not preserved on the theory it might catch something.
3. **Record the missing cases** — clauses the contract promises that nothing
   asserts. Write them now.
4. **Where a clause is deliberately untested, say so in the Javadoc with the
   reason.** Untested with a reason is a decision; untested with no note is a gap.

Two traps the pilot walked into, both of them here:

- **Before writing any `@Test`, ask whether it will sit beside a same-shape
  sibling.** If yes, both are rows in one `record` case table from the first case
  — not a refactor once four have piled up. A varying lambda does not disqualify
  a case; only a varying *assertion* does. This check fires at the moment you
  would copy-paste, which is where it was missed.
- **Do not write "enumerated in full" unless something fails when the domain
  grows.** Drive the cases from `@EnumSource` / `values()` / a sealed hierarchy's
  permitted subclasses, or assert separately that the table's rows are exactly the
  domain. A hand-listed table passes forever while the sentence claiming it is
  complete goes quietly false — which is exactly how `OpNamesTest` claimed three
  domains it did not enumerate. Where the domain is private, no assertion can
  reach it: reword the claim, and do not widen the taxonomy to make it checkable.

**Commit per test class or per coherent group.**

## Step 4: Fix test-only surface

From step 1's inventory and `plans/test-only-surface.md`. Each finding is one of
three kinds, and the kind decides the fix:

- **Genuinely test-only** — delete it, or restructure the seam so production uses
  it too. A probe or a handler becomes legitimate the moment it is injected the
  way production injects things.
- **Misnamed internal API** — it takes arguments and returns a value and is
  already a coherent unit. Rename it to its concept via `jet_brains_rename` and
  write its contract.
- **Lifecycle** — a class with `initialize()` and no way back has an incomplete
  lifecycle contract, tests or no tests. Phases 8–9 handled the known set; a new
  one follows `docs/lifecycle.md` and the `Disposable` seam.

**Reflection into production internals is the same violation**, not an escape
from it — `getDeclaredField`, `setAccessible`. When a test cannot arrange the
state it needs, the answer is a constructor or factory taking that state, used by
production too. Never an accessor, never reflection.

**Commit.**

## Step 5: Compile and run

Compiling is not confined to this step — every commit in steps 2 through 4 builds
and runs the classes it touched, or those commits are unverified. This step is the
**full-suite gate**: `./scripts/compile.sh` if anything under `src/main/` changed,
then `./scripts/test.sh` for the whole unit suite, not just the package's classes.
A contract change here can break a caller's test in another package, and nothing
before this step would have caught it.

Never rerun a failure with extra flags, and never assume a failure is
pre-existing. Fix it. **A failing test means one of three things — code, test, or
contract** — and weakening a contract to reach green is legitimate only when the
contract was wrong about the domain, stated explicitly, never decided silently
mid-fix.

**Commit.**

## Step 6: Judge the diagrams

Any diagram in the package's `docs/` document or Javadoc, now that the contracts
exist (D17). Keep only what shows what prose cannot — a topology, a state machine,
a sequence with genuine concurrency.

A diagram that walks through a sequence the method contracts already state is the
contract drawn a second time, and the second copy is the one that goes stale. The
pilot dropped four such diagrams from `docs/undo.md` and replaced them with a
pointer to the methods that state each step.

**Commit** if anything changed.

## Step 7: Coverage — required, once, scoped

`./scripts/coverage.sh unit <the package's test classes>`. Not optional, and not
a repeat performance: **once**, as the closing step.

This is the only step that catches a contract claiming a domain it does not
cover, because that claim reads as its own evidence — re-reading it confirms it.
It produced 22 of the pilot's 161 final cases, all of them behind Javadoc
asserting the domains were already enumerated.

Of **each** unexecuted region, ask exactly one question:

> Does this correspond to a contract case that is missing, or to implementation
> the contract promises nothing about?

The first answer amends the contract and its tests. The second leaves it alone,
**and you write down why** — the pilot's non-gaps were a branch unreachable given
the current enum, a case owned by another package's tests, and the compiler's
synthetic exhaustiveness branch on sealed switches. If nothing can reach a region
at all, that is a dead-code finding against production, not a test finding.

**You never write a test to turn a region green.** Asking the question of each
region one at a time is what keeps the ranked list from becoming a to-do list.

Re-run the unit suite. **Commit.**

## Step 8: Mutation — opportunistic

`./scripts/mutation-test.sh [target]`, only if something specific is worth
investigating: a contract case you suspect is asserted but not observed. Under
contract testing a **high surviving-mutant count is the expected, healthy state**,
because contract tests deliberately leave the implementation free to change. The
score measures precisely what we have decided not to optimize. **Never report the
percentage.**

## Step 9: Close out

Re-measure with the **same commands step 0 used** and fill in the *After* column,
plus contracts written, domain checkpoints raised, the four triage counts, and
elapsed wall clock. Mark the row in `plans/contract-driven-rollout.md`'s
**Remaining phases** table, and commit.

The numbers exist to be compared against
[`pilot-retrospective.md`](../../../plans/pilot-retrospective.md) §1, so measure
the same things it did: main and test LOC, passing cases, ratio, and main-LOC
growth as a percentage — that last one is the contract Javadoc's cost and the
figure the rollout's remaining estimate rests on.

Then report to the user: what the contracts turned out to promise, what the
domain checkpoints changed, tests before and after with the three triage
outcomes, what coverage found, and anything you surfaced that is not yours to
decide.

## Things that stay true throughout

- **Findings outside the package are still findings.** A design flaw, a
  duplication, a wrong abstraction or a misleading name in a caller, a neighbor,
  or a shared helper this work merely passes through gets raised with file and
  line references. Never call anything out of scope; whether to fix it now is the
  user's call, and they can only make it once the option is in front of them.
- **No production surface exists solely for tests** — no method, no accessor, no
  widened field, no relaxed visibility, no reflection.
- **Contracts before deletions**, always, so there is something to check a
  deletion against.
- **Serena's `jet_brains_*` tools for all Java exploration and refactoring**, per
  `.agents/rules/serena.md`. `jet_brains_find_referencing_symbols` before deciding
  where a symbol is used; `jet_brains_rename` for renames.
- **Commit per step** (D11), each with the record's status change, using the
  `/commit-commands:commit` skill. An uncommitted step is a lost step.

## The record template

`plans/contract-pass/<package>.md`, created at step 0:

```markdown
# Contract Pass — `<package>`

Run by `/contract-pass <package>`. Per-area procedure in
[`contract-driven-rollout.md`](../contract-driven-rollout.md).

**Status:** ⏳ not started · 🔄 in progress · ✅ complete

| Step | Status | Notes |
|---|---|---|
| 1 Inventory | ⏳ | |
| 2 Contracts | ⏳ | |
| 3 Testing Javadoc + triage | ⏳ | |
| 4 Test-only surface | ⏳ | |
| 5 Compile and run | ⏳ | |
| 6 Diagrams | ⏳ | |
| 7 Coverage | ⏳ | |
| 8 Mutation | ⏳ | opportunistic |

## Numbers

| | Before | After |
|---|---:|---:|
| Test cases | | |
| Main LOC | | |
| Test LOC | | |
| Ratio | | |

Contracts written: · Elapsed:

## Domain contracts confirmed

One line per proposal that went to a checkpoint, and what was decided.

## Triage outcome

Kept: · Rewritten: · Discarded: · Added:

## Coverage

Real missing cases (fixed), then uncovered regions left alone with the reason each.

## Findings raised

Anything surfaced that was not this package's to fix.
```
