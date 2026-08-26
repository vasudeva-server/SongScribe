---
name: design-pass
description: Run the design pass over one system, named by its row number in the design pass register — fix what types can carry, contract what fan-in earns, keep only the tests the design cannot enforce
model: opus
effort: high
disable-model-invocation: true
---

## Design Pass

Take one target from *whatever it is now* to *illegal states unrepresentable,
contracts where fan-in earns them, tests only where the design cannot enforce the
promise*. One stash snapshot per step, resumable across context clears.

**A pass never commits.** It leaves its work in the tree and snapshots each step
to the stash. Committing is the user's, whenever they choose.

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

- **`~/.claude/guides/design.md`** — the architecture gate and the order of
  attack, class design, types over tests, boundaries, guards, `@Nullable`,
  scoped globals, extraction, contract depth, the testing floor and the gate
  that proposes tests before they are written. Everything here serves it.
- **`.claude/guides/contracts.md`** — the Javadoc form of a contract.
- **`.claude/guides/testing-common.md`** — where cases come from, triage,
  diagnostics.
- **`.claude/guides/testing-unit.md`** — parameterized case tables and the
  exhaustiveness assertion.
- **`.claude/rules/java.md`** — Javadoc syntax and the signature rules.
- **`reference/classification.md`** — which contracts you may decide and which
  you must propose, and the checkpoint format.

## Step 0: Resolve, check, resume

`$ARGUMENTS` is a **row number in the register**, `plans/design-pass-register.md`
— `0`, `7`, `18`, or a suffixed row a split produced, `14a`. Nothing else is a
target: the register decides what a pass covers and in what order, so work that
is not on it gets a row before it gets a pass.

**A number whose row has been split is not a target.** If `14` now reads `14a`,
`14b`, `14c`, say so and ask which; taking one on the user's behalf picks a
system for them.

**With no argument, take the work already open**, in this order:

1. **A row marked 🔄 resumes.** Its record holds the state; open it and continue
   at the first step not marked ✅. Where more than one row is 🔄, ask which —
   two passes open at once is a split the register does not intend.
2. **Otherwise the first ⏳ row in table order starts.** The ordering is a
   dependency fact, not a preference, so nothing further down is ready before it.
   Suffixed rows sort inside their number — `14a`, `14b`, then `15`.
3. **No ⏳ row left means the regime is done.** Say so; do not invent a target.

Say which row you took, and whether you resumed it or started it, before touching
anything.

Read that row. Its *System* column names the pass; its *Where* column names the
target, which is **a set of types, not a directory**. The register's *The unit is
a system, not a package* says why: `dom` is one directory and about ten systems,
so a row reading `dom`: `Ss`, `DocPx`, `ViewPx`, `DocumentScale` is four files in
a package whose other files belong to other passes.

Resolve *Where* into two lists of paths and confirm every one exists before going
further. A bare class name is located with `jet_brains_find_symbol`. Where a row
names a whole package, each list is that one directory.

| Row says | Production | Tests |
|---|---|---|
| `ui/selection` | `src/main/java/songscribe/ui/selection/` | `src/test/java/songscribe/ui/selection/` |
| `dom`: `Key`, `KeySignature` | `…/songscribe/dom/Key.java`, `…/dom/KeySignature.java` | `…/test/…/dom/KeyTest.java`, `…/dom/KeySignatureTest.java` |

A row marked *(undecomposed)* is a directory nobody has read yet. **Split it on
the read**, never before: step 1 names the systems the directory actually holds,
you take one of them, and step 12 rewrites the register's single row into those
systems. Splitting from a directory listing predicts boundaries instead of
finding them, which is the error the register exists to avoid.

Everything below calls the resolved target `<target>`, and its record name is the
row's system name in lower-case kebab — `units-and-scale.md`, `ui-selection.md`.

1. **Open the record.** `plans/design-pass/<target>.md`. If it exists, read it
   and resume at the first step not marked ✅ — it is the only memory across a
   context clear. If not, create it from the template at the end of this file.

   **Resume inside that step, not at the top of it.** A step's row carries a
   *Plan* link once the step has one (see *Steps decompose; the record links
   where*). Open that plan and take the first phase its dashboard does not mark
   ✅. Restarting a step whose first three phases are already done redoes work
   the tree already holds, and the redo is invisible until it conflicts.
2. **Claim what the register holds for this pass.** Open
   `plans/design-pass-register.md` and read *Carry-forward findings*. Every item
   tagged for this pass **is this pass's work**, established by an earlier pass
   that could not act on it: copy each into the record's *Findings claimed*, and
   **delete the line from the register**. One list owns a finding at a time, so
   neither copy can drift from the other.

   Skip this on a resume; a record that already exists has already claimed.

   An item you conclude this pass should not act on goes **back** to the register
   with what you learned, retagged to whatever will actually reach the code. It is
   never dropped, and whether to act is the user's call, not yours.
3. **Confirm the tree is clean.** `git status`. The pass leaves its own work
   uncommitted, so anything already in the tree becomes indistinguishable from
   it; stop and say so.
4. **Verify the branch, and stop if it is a base branch.** A pass runs on its
   own branch, off `develop`. If `git rev-parse --abbrev-ref HEAD` says
   `develop` or `main`, **say so and stop** — do not offer to branch, and do not
   proceed while waiting for an answer. A pass leaves a large uncommitted tree,
   and on a base branch that tree is in everyone's way and cannot be set aside
   without taking the branch with it.
5. **Capture the baseline into the record's *Before* column**, before touching
   anything. `MAIN` and `TEST` are the two paths from the table, so the same
   commands serve a whole package and a handful of named types alike:

   ```bash
   MAIN=(<resolved production paths>)   # files, a directory, or a mix
   TEST=(<resolved test paths>)
   git rev-parse --short HEAD           # the commit the pass starts from
   find "${MAIN[@]}" -name '*.java' | xargs cat | wc -l
   find "${TEST[@]}" -name '*.java' | xargs cat | wc -l
   ```

   Then `./scripts/test.sh <the target's test classes>` and record the **passing
   count**, not the number of `@Test` methods.

## Step 1: Inventory

**Delegatable when the row is a whole package** — one agent, or one per
subpackage if it is large. **A row naming a handful of types is never delegated**;
it is small enough to read directly, and a subagent's summary of it costs more
than the files. An *(undecomposed)* row is where delegation earns the most: the
read has to name the systems the directory holds before one of them can be taken.

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
- which existing `docs/*.md` describe the subsystem this target sits in, since a
  method cites that overview rather than restating it;
- every test class, with its case count and what it appears to cover.

Subagent prompts must bound the search surface: absolute working directory,
repo-relative paths for every directory to examine, Serena's `jet_brains_*` tools
named explicitly, and an instruction to report and stop rather than widen the
search. Include *"Read `.claude/rules/serena.md` and follow it for all Java
exploration and refactoring."*

**Snapshot.** Record the inventory in the record file.

## Step 2: Class design

**Inline. Never delegated.** Step 1 may be handed to a subagent because it
reports facts; this step decides what exists, and a hierarchy a subagent decided
is one nobody can audit afterward.

Against step 1's inventory, three questions:

1. **Does each concept in the target have exactly one type?** A type that is two
   concepts splits; two types that are one concept dissolve into one; a concept
   with no type at all is named here. Dissolving `KeyChange` into `Key` is the
   pattern, and it deleted the whole of a later step's work rather than adding
   to it.
2. **What varies?** That is what a hierarchy or interface is shaped around. What
   was deliberately *not* abstracted is stated too — an absence cannot be
   approved unless it is named.
3. **Which type owns each invariant step 1 found uncarried?** That answer is
   step 3's input. Making an invariant unrepresentable on the wrong type moves
   the runtime check rather than retiring it.

**Where the answer is "the types are wrong", this is the architecture gate** in
`design.md`: a summary, a diagram sized to the work, and the two or three forks
with what each buys and costs. A single coherent proposal leaves the user
nothing to do but agree. The decision is theirs, and every step below is derived
from the answer — so a change here is made before step 3 begins, not alongside
it.

**Snapshot.**

## Step 3: Make illegal states unrepresentable

**Inline. Before any contract is written**, because every invariant a type
carries is a contract clause that never has to be written and a test that never
has to exist.

From step 1's inventory, over the type set step 2 settled, in this order:

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

**Lead with two counts.** How many guards the proposal retires — `@Nullable`
among them — and how many types it introduces to carry an invariant the code
currently checks at runtime. That pair is the yield the user is deciding on:
every guard retired is a runtime check the compiler now makes instead, and a
proposal that introduces types without retiring guards has added structure
without moving anything. State both before the per-change detail, from step 1's
inventory of `@Nullable`s and guards.

**Then delete the guards it retired**, and the tests that were pinned to them.

**Snapshot per change or per coherent group.**

## Step 4: Extraction and placement

Domain-defined operations move onto the type that owns them — statable without
naming any caller, named as a verb on its arguments. A `*Utils` or `*Helper`
static bag in the target is a finding: its contents are redistributed to the
types owning the invariants, and anything left that names no owner **goes back
to step 2** — a concept with no type is a class-design decision, not something
this step can place.

**Snapshot.**

## Step 5: Write the contracts

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

**Snapshot per class or per coherent group.**

## Step 6: Triage the tests

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

**One table, before any test is written or deleted.** Triage and proposal are one
conversation: a test kept, a test rewritten, a test discarded and a case added
are four dispositions of one list, and splitting them into two conversations
hides that a discard and an addition are usually the same case moving.

| Test | Kind | Disposition | Why |
|---|---|---|---|
| the promise being checked, in one line | which of the three kinds in `design.md` | keep · rewrite · discard · add | one line |

**A discard is a row whose *Kind* cannot be filled.** It fits none of the three
kinds, which is why it is going — so the column is the table's own check. A row
you can justify only by naming a branch, a guard or a private helper is a
discard, whatever you meant to put in it.

**Then wait.** No `@Test` is written and none is deleted until the user has seen
the table and had the chance to veto it.

Two traps, both here:

- **Before writing any `@Test`, ask whether it will sit beside a same-shape
  sibling.** If yes, both are rows in one `record` case table from the first case.
  A varying lambda does not disqualify a case; only a varying *assertion* does.
- **Do not claim "enumerated in full" unless something fails when the domain
  grows.** Drive cases from `@EnumSource` / `values()` / a sealed hierarchy, or
  assert that the table's rows are exactly the domain — a hand-listed table can
  carry that claim for domains it never actually checks.

**Snapshot per test class or per coherent group.**

## Step 7: Fix test-only surface

Each finding is one of four kinds, and the kind decides the fix:

- **Genuinely test-only** — delete it, or restructure the seam so production uses
  it too.
- **A replaceable process-global** — a setter, a probe or a widened field that
  lets a test swap something process-wide. The fix is a stack of instances plus
  an `AutoCloseable` scope, never a setter, per *Scope a global; never swap it*
  in `design.md`. **Name the production caller that justifies the scope** — the
  headless converters justified `MessageBusScope` — because a scope only tests
  push is a test-only injection point in better clothes.
- **Misnamed internal API** — it takes arguments and returns a value and is
  already a coherent unit. Rename it to its concept and write its contract.
- **Lifecycle** — a class with `initialize()` and no way back has an incomplete
  lifecycle contract, tests or no tests. Follow `docs/lifecycle.md` and the
  `Disposable` seam.

**Reflection into production internals is the same violation**, not an escape
from it. When a test cannot arrange the state it needs, the answer is a
constructor or factory taking that state, used by production too.

**Snapshot.**

## Step 8: Compile and run

Every snapshot in steps 2 through 7 builds and runs the classes it touched, or
that state is unverified. This step is the **full-suite gate**: run
`./scripts/compile.sh --test`, then `./scripts/test.sh <the target's test
classes>`. A contract or type change here can break a caller in another package,
which is what the whole suite is for — and the whole suite is not yours to start.

**Ask the user to run it, and wait.** `.claude/hooks/no-full-test-suite.sh`
denies a run naming no class or more than four, and that is the rule rather than
an obstacle to route around: state that the pass is at its gate, name what
changed and which packages the change can reach, and let the user decide when to
spend the run. Never attempt the suite yourself, in any form — not by naming
classes four at a time, not through Gradle, not by any other spelling.

Never rerun a failure with extra flags, and never assume a failure is
pre-existing. **A failing test means one of three things — code, test, or
contract** — and weakening a contract to reach green is legitimate only when the
contract was wrong about the domain, stated explicitly, never decided silently.

**Then the visual gate.** A green suite says nothing about pixels. Where a pass
moved a measurement, a geometry, or a rendering call site, a regression shows as
text shifted by a fraction of a pixel, a box clipped at one edge, or a label that
stopped centring — and nothing in this repo asserts any of that.

**Derive the checklist from the call sites the pass re-pointed, never from a
stock list.** For each member the pass moved or changed, follow its fan-in from
step 1 out to the surface that draws it, and write one line naming three things:
what to open, what to look at, and what a regression looks like there. A pass
that re-points a glyph-ink query writes "volta bracket labels — the number sits
inside the bracket, not clipped at its right edge", not "check rendering". A line
a person cannot act on without reading the diff is not on the list.

**Ask the user to run it, and wait** — the application is never launched without
their permission. If the pass reached nothing a person can see, say so plainly
instead of emitting a checklist of things it cannot have broken.

**Snapshot.**

## Step 9: Judge the diagrams

Any diagram in the target's `docs/` document or Javadoc. Keep only what shows
what prose cannot — a topology, a state machine, a sequence with genuine
concurrency. A diagram walking through a sequence the contracts already state is
the contract drawn a second time, and the second copy goes stale.

**Snapshot** if anything changed.

## Step 10: Coverage — once, scoped

`./scripts/coverage.sh unit <the target's test classes>`. Once, as the closing
step, never as a target.

Of **each** unexecuted region, ask exactly one question:

> Does this correspond to a contract case that is missing, or to implementation
> the contract promises nothing about?

The first answer amends the contract and its tests. The second leaves it alone,
**and you write down why**. If nothing can reach a region at all, that is a
dead-code finding against production.

**You never write a test to turn a region green.**

Re-run the unit suite. **Snapshot.**

## Step 11: Mutation — opportunistic

`./scripts/mutation-test.sh [target]`, only if a specific case is worth
investigating. Under contract testing a **high surviving-mutant count is the
expected, healthy state**. **Never report the percentage.**

## Step 12: Close out

**Harvest, and delete the record** — `plans/design-pass-register.md` states
why under *The record is working memory*. Two things leave the record first:

1. **Anything a later pass owns** goes to the register's *Carry-forward
   findings*, tagged with the row that owns it — `**→ Pass 7.**` — with file and
   line, and stated so a reader who never saw this pass can act on it. Where the
   owning row may never be scheduled, tag the track that will actually reach the
   code instead. Nothing speculative, and nothing this pass could have fixed
   itself: a finding inside your own reach is fixed here, while the reason for it
   is still in hand.
2. **Anything about the system's shape** goes to `docs/`, written in this pass
   rather than promised to a later one.

Then flip the register row to ✅ and delete the record. The row, the findings and
the deletion are one change, and they land in the tree beside the pass's own
work for the user to commit.

**A row you split rewrites itself here.** Where step 1 read an *(undecomposed)*
directory and named the systems it holds, replace that single row with one row
per system, ordered by what each takes its types from, and mark ✅ only the one
this pass took. The names come from the read, so this is the one place the
register learns something it could not have predicted.

**A split suffixes; it never renumbers.** Row `14` becomes `14a`, `14b`, `14c`,
and every row below keeps the number it had. A row number is permanent identity —
carry-forward findings are tagged with it, and those tags are written by passes
that are over and cannot be asked what they meant. Renumbering would silently
re-point every one of them at the wrong system. A suffixed row that splits again
suffixes again: `14b1`, `14b2`.

Then report: what the types now carry that runtime checks used to — with step 3's
two counts as they actually landed — what the contracts turned out to promise,
what the checkpoints changed, and anything you surfaced that is not yours to
decide.

## Things that stay true throughout

- **Findings outside the target are still findings.** A design flaw, a
  duplication, a wrong abstraction or a misleading name in a caller, a neighbor,
  or a shared helper this work merely passes through gets raised with file and
  line references. Never call anything out of scope.
- **No production surface exists solely for tests** — no method, no accessor, no
  widened field, no relaxed visibility, no reflection.
- **Serena's `jet_brains_*` tools for all Java exploration and refactoring**, per
  `.claude/rules/serena.md`.
- **Never commit.** The pass leaves everything in the working tree; when to
  commit, and in what shape, is the user's. Do not stage, do not amend, and do
  not invoke `/commit-commands:commit`.
- **Snapshot per step**, each with the record's status change. One Bash call,
  which leaves the working tree untouched:

  ```bash
  git add -A && git stash store -m "design pass <target>: step N" "$(git stash create)"
  ```

  `git add -A` is required — `stash create` snapshots the index and tracked
  files only, so an untracked new file is otherwise lost. **Never snapshot with
  `git stash push`**, which empties the tree first. Restore with `git stash
  apply <sha>`, never `pop`: the stash stack is shared with every worktree and
  another session may be using it. An unsnapshotted step is a lost step.
- **Steps decompose; the record links where.** A step whose approved work spans
  more than one sitting gets an execution plan in `plans/` via
  `/make-plan`, and the record's row for that step links it. Step 3 over a
  package, and any step that turns out to touch tens of files, are the usual
  cases.

  The record holds *what was decided*; the plan holds *what is left to do*, one
  phase at a time, with a dashboard the implementer ticks. Neither restates the
  other — a plan that re-argues a decision drifts from the record, and a record
  that tracks progress drifts from the plan.

  **The plan is deleted with the record at step 12.** It is working memory on the
  same terms, and `plans/design-pass-register.md` states why under *The record is
  working memory*.

## The record template

`plans/design-pass/<target>.md`, created at step 0:

```markdown
# Design Pass — `<target>`

Run by `/design-pass <row number>` — register row `<n>`, *<System>*.

**Status:** ⏳ not started · 🔄 in progress · ✅ complete

| Step | Status | Plan | Notes |
|---|---|---|---|
| 1 Inventory | ⏳ | — | |
| 2 Class design | ⏳ | — | |
| 3 Unrepresentable states | ⏳ | — | |
| 4 Extraction | ⏳ | — | |
| 5 Contracts | ⏳ | — | |
| 6 Test triage | ⏳ | — | |
| 7 Test-only surface | ⏳ | — | |
| 8 Compile and run | ⏳ | — | |
| 9 Diagrams | ⏳ | — | |
| 10 Coverage | ⏳ | — | |
| 11 Mutation | ⏳ | — | opportunistic |

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

## Findings raised

Anything surfaced that was not this target's to fix. Harvested to the register at
step 12, before this file is deleted.
```
