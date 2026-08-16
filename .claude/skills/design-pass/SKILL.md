---
name: design-pass
description: Run the design pass over a package or class — fix what types can carry, contract what fan-in earns, keep only the tests the design cannot enforce
model: opus
effort: high
disable-model-invocation: true
---

## Design Pass

Take one target from *whatever it is now* to *illegal states unrepresentable,
contracts where fan-in earns them, tests only where the design cannot enforce the
promise*. One commit per step, resumable across context clears.

Paths in this file are relative to `.claude/skills/design-pass/`.

### The yardstick

**The design is the completeness criterion, not the contract and not coverage.**
A target is finished when the invariants it depends on are carried by types
rather than checked at runtime, the contracts that exist are the ones fan-in
earns, and every remaining test sits on the floor in `design.md`.

Steps run in the order of attack: what a type can carry never becomes a contract
clause, and what a contract can state never becomes a test. **Work that arrives
at the test step in volume means the earlier steps were skipped.**

### Doctrine — read before step 2, do not paraphrase

- **`~/.claude/guides/design.md`** — the order of attack, types over tests,
  boundaries, guards, `@Nullable`, extraction, contract depth, the testing floor.
  Everything here serves it.
- **`.claude/guides/contracts.md`** — the Javadoc form of a contract.
- **`.claude/guides/testing-common.md`** — where cases come from, triage,
  diagnostics.
- **`.claude/guides/testing-unit.md`** — parameterized case tables and the
  exhaustiveness assertion.
- **`.claude/rules/java.md`** — Javadoc syntax and the signature rules.
- **`reference/classification.md`** — which contracts you may decide and which
  you must propose, and the checkpoint format.

## Step 0: Resolve, check, resume

`$ARGUMENTS` is a **package or a single class**, dotted, with the `songscribe.`
prefix implicit — `undo`, `ui.selection`, `dom.Key`, `KeyChangeDialog`.
**With no argument, ask what to examine rather than guessing.**

Resolve which kind it is from the last segment: an initial capital is a class,
anything else a package. A bare class name with no package is located with
`jet_brains_find_symbol`. Confirm the resolved paths exist before going further,
and ask rather than guess if the name is ambiguous or matches nothing.

| Target | Production | Tests |
|---|---|---|
| package `ui.selection` | `src/main/java/songscribe/ui/selection/` | `src/test/java/songscribe/ui/selection/` |
| class `dom.Key` | `src/main/java/songscribe/dom/Key.java` | `src/test/java/songscribe/dom/KeyTest.java` |

Everything below calls the resolved target `<target>`, and its record name is the
dotted argument with dots as hyphens — `ui-selection.md`, `dom-Key.md`.

1. **Open the record.** `plans/design-pass/<target>.md`. If it exists, read it
   and resume at the first step not marked ✅ — it is the only memory across a
   context clear. If not, create it from the template at the end of this file.
2. **Confirm the tree is clean.** `git status`. Uncommitted work from another
   task makes the per-step commits wrong; stop and say so.
3. **Verify the branch.** Feature branches come off `develop`, never `main`.
4. **Capture the baseline into the record's *Before* column**, before touching
   anything. `MAIN` and `TEST` are the two paths from the table, so the same
   commands serve a package and a single class:

   ```bash
   MAIN=<resolved production path>   # a directory, or one .java file
   TEST=<resolved test path>
   git rev-parse --short HEAD        # record as the start commit
   find "$MAIN" -name '*.java' | xargs cat | wc -l
   find "$TEST" -name '*.java' | xargs cat | wc -l
   ```

   Then `./scripts/test.sh <the target's test classes>` and record the **passing
   count**, not the number of `@Test` methods.

## Step 1: Inventory

**Delegatable when the target is a package** — one agent, or one per subpackage
if it is large. **A single class is never delegated**; it is small enough to read
directly, and a subagent's summary of it costs more than the file.

Produce:

- **every type in the target and what invariant it currently fails to carry** —
  a validated record whose domain is a known finite set, a primitive that could
  be transposed with a sibling, a mode-selecting boolean, a constructor that can
  build an unusable instance;
- **every `@Nullable`**, with which of the three answers in `design.md` applies;
- **every guard**, with the callers that can actually produce the value it
  rejects;
- **fan-in per public method** — how many callers rely on it, which is what
  decides whether it earns a contract at all;
- which existing `docs/*.md` state rules governing this target, since those are
  tier-3 contracts a method cites rather than restates;
- every test class, with its case count and what it appears to cover.

Subagent prompts must bound the search surface: absolute working directory,
repo-relative paths for every directory to examine, Serena's `jet_brains_*` tools
named explicitly, and an instruction to report and stop rather than widen the
search. Include *"Read `.claude/rules/serena.md` and follow it for all Java
exploration and refactoring."*

**Commit.** Record the inventory in the record file.

## Step 2: Make illegal states unrepresentable

**Inline. Before any contract is written**, because every invariant a type
carries is a contract clause that never has to be written and a test that never
has to exist.

From step 1's inventory, in this order:

1. **Closed enums** where the domain is a known finite set currently guarded by a
   validating constructor.
2. **Wrapper types** for interchangeable primitives a call site could transpose.
3. **Enums** replacing mode-selecting booleans and bare ints.
4. **Boundary conversions** — a file, network or UI edge that validates and then
   passes a raw value inward converts instead, so no layer below can hold an
   unproven one.
5. **Constructor validation** only where a closed type is not possible.

Each of these is a design change, so **raise it before making it**: file and
line, the correct structure concretely, the recommendation. Whether to do it now
is the user's call.

**Then delete the guards it retired**, and the tests that were pinned to them.

**Commit per change or per coherent group.**

## Step 3: Extraction and placement

Domain-defined operations move onto the type that owns them — statable without
naming any caller, named as a verb on its arguments. A `*Utils` or `*Helper`
static bag in the target is a finding: its contents are redistributed to the
types owning the invariants, and anything left that names no owner is a missing
concept, raised as such.

**Commit.**

## Step 4: Write the contracts

**Inline. Never delegated.** A subagent guessing at a music-notation promise
produces confident, plausible, wrong Javadoc — worse than none, because
everything downstream then tests against it.

**Depth follows fan-in.** A method nothing outside its class calls gets an
accurate name and no contract. Do not write a contract to be thorough; write it
where callers rely on the promise.

For each class that earns one:

1. **Classify each contract** per `reference/classification.md`. Write the
   mechanical ones straight through.
2. **Batch the domain ones into one checkpoint for that class**, in the four-line
   form that file specifies. Write what the reviewer decides.
3. **Then the class Javadoc** — invariants spanning several of its methods.
4. **Then `package-info.java`** — invariants spanning the classes.
5. **Anything spanning subsystems goes to `docs/`.** If a `docs/` document
   already states it, cite rather than restate.

Three things that are not optional:

- **`@return` on every method whose return type is not `void`.** It is what the
  IDE shows at the call site.
- **The signature rules apply as you go** — more than four parameters or two
  transposable same-typed parameters take a `record`; a mode-selecting `boolean`
  takes an enum; a misdescribing name gets renamed via `jet_brains_rename`.
- **The tell of a real contract is that the implementation could in principle
  violate it.** If you cannot imagine the code breaking the promise, you have
  described the code.

**Do not document a guard no caller can reach.** A `@throws` for an impossible
condition propagates outward into callers that handle it.

**Commit per class or per coherent group.**

## Step 5: Triage the tests

**Inline.** Each existing test resolves against the floor in `design.md`, not
against a clause count:

- **keep** — it exercises a real algorithm, an invariant spanning several calls,
  or behavior with a known-correct corpus, and it can fail.
- **rewrite** — the case is real but the test is wrong about it: wrong level, a
  name that does not name the promise, assertions against mocks, an arrangement
  that does not reach the case, or several tests pinning one case a parameterized
  test states once.
- **discard** — a test of a guard no caller can reach, of a state a type now
  makes unrepresentable, of a private helper's decomposition, of an
  implementation detail the contract promises nothing about, or a duplicate.

Then **add only the cases the floor calls for and nothing asserts.** A clause
with no case is not automatically a gap; ask first whether the design already
enforces it.

Two traps, both here:

- **Before writing any `@Test`, ask whether it will sit beside a same-shape
  sibling.** If yes, both are rows in one `record` case table from the first case.
  A varying lambda does not disqualify a case; only a varying *assertion* does.
- **Do not claim "enumerated in full" unless something fails when the domain
  grows.** Drive cases from `@EnumSource` / `values()` / a sealed hierarchy, or
  assert that the table's rows are exactly the domain — a hand-listed table can
  carry that claim for domains it never actually checks.

**Commit per test class or per coherent group.**

## Step 6: Fix test-only surface

Each finding is one of three kinds, and the kind decides the fix:

- **Genuinely test-only** — delete it, or restructure the seam so production uses
  it too.
- **Misnamed internal API** — it takes arguments and returns a value and is
  already a coherent unit. Rename it to its concept and write its contract.
- **Lifecycle** — a class with `initialize()` and no way back has an incomplete
  lifecycle contract, tests or no tests. Follow `docs/lifecycle.md` and the
  `Disposable` seam.

**Reflection into production internals is the same violation**, not an escape
from it. When a test cannot arrange the state it needs, the answer is a
constructor or factory taking that state, used by production too.

**Commit.**

## Step 7: Compile and run

Every commit in steps 2 through 6 builds and runs the classes it touched, or
those commits are unverified. This step is the **full-suite gate**:
`./scripts/compile.sh` if anything under `src/main/` changed, then
`./scripts/test.sh` for the whole unit suite. A contract or type change here can
break a caller in another package.

Never rerun a failure with extra flags, and never assume a failure is
pre-existing. **A failing test means one of three things — code, test, or
contract** — and weakening a contract to reach green is legitimate only when the
contract was wrong about the domain, stated explicitly, never decided silently.

**Commit.**

## Step 8: Judge the diagrams

Any diagram in the target's `docs/` document or Javadoc. Keep only what shows
what prose cannot — a topology, a state machine, a sequence with genuine
concurrency. A diagram walking through a sequence the contracts already state is
the contract drawn a second time, and the second copy goes stale.

**Commit** if anything changed.

## Step 9: Coverage — once, scoped

`./scripts/coverage.sh unit <the target's test classes>`. Once, as the closing
step, never as a target.

Of **each** unexecuted region, ask exactly one question:

> Does this correspond to a contract case that is missing, or to implementation
> the contract promises nothing about?

The first answer amends the contract and its tests. The second leaves it alone,
**and you write down why**. If nothing can reach a region at all, that is a
dead-code finding against production.

**You never write a test to turn a region green.**

Re-run the unit suite. **Commit.**

## Step 10: Mutation — opportunistic

`./scripts/mutation-test.sh [target]`, only if a specific case is worth
investigating. Under contract testing a **high surviving-mutant count is the
expected, healthy state**. **Never report the percentage.**

## Step 11: Close out

Re-measure with the **same commands step 0 used** and fill in the *After* column,
plus types changed, guards retired, contracts written, domain checkpoints raised,
the triage counts, and elapsed wall clock.

Then report: what the types now carry that runtime checks used to, what the
contracts turned out to promise, what the checkpoints changed, tests before and
after with the triage outcomes, what coverage found, and anything you surfaced
that is not yours to decide.

## Things that stay true throughout

- **Findings outside the target are still findings.** A design flaw, a
  duplication, a wrong abstraction or a misleading name in a caller, a neighbor,
  or a shared helper this work merely passes through gets raised with file and
  line references. Never call anything out of scope.
- **No production surface exists solely for tests** — no method, no accessor, no
  widened field, no relaxed visibility, no reflection.
- **Serena's `jet_brains_*` tools for all Java exploration and refactoring**, per
  `.claude/rules/serena.md`.
- **Commit per step**, each with the record's status change, using the
  `/commit-commands:commit` skill. An uncommitted step is a lost step.

## The record template

`plans/design-pass/<target>.md`, created at step 0:

```markdown
# Design Pass — `<target>`

Run by `/design-pass <target>`.

**Status:** ⏳ not started · 🔄 in progress · ✅ complete

| Step | Status | Notes |
|---|---|---|
| 1 Inventory | ⏳ | |
| 2 Unrepresentable states | ⏳ | |
| 3 Extraction | ⏳ | |
| 4 Contracts | ⏳ | |
| 5 Test triage | ⏳ | |
| 6 Test-only surface | ⏳ | |
| 7 Compile and run | ⏳ | |
| 8 Diagrams | ⏳ | |
| 9 Coverage | ⏳ | |
| 10 Mutation | ⏳ | opportunistic |

## Numbers

| | Before | After |
|---|---:|---:|
| Test cases | | |
| Main LOC | | |
| Test LOC | | |
| Ratio | | |

Types changed: · Guards retired: · Contracts written: · Elapsed:

## Domain contracts confirmed

One line per proposal that went to a checkpoint, and what was decided.

## Triage outcome

Kept: · Rewritten: · Discarded: · Added:

## Coverage

Real missing cases (fixed), then uncovered regions left alone with the reason each.

## Findings raised

Anything surfaced that was not this target's to fix.
```
