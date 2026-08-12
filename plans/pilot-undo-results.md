# Pilot — `undo` Test Triage Results

Phase 12 of [`contract-driven-rollout.md`](./contract-driven-rollout.md), against the
contracts Phase 11 wrote. Phase 13 reads this file.

## Numbers

| | Before (Phase 11 exit) | After (Phase 12 triage) | After (data-driven follow-up) |
|---|---:|---:|---:|
| Tests | 141 | 161 | 161 |
| Main LOC | 1,266 | 1,266 | 1,266 |
| Test LOC | 3,574 | 3,789 | 3,730 |
| Test/main ratio | 2.82 | 2.99 | 2.95 |

Contracts: 0 written in this phase (Phase 11 wrote all of them); this phase triaged
tests against them, closed the four gaps Phase 11 named, and closed the gaps coverage
surfaced. Elapsed time was not separately instrumented — single continuous session.

**Data-driven follow-up.** After the triage commit, review caught `OpNamesTest`
hand-duplicating the same assertion shape across most of its nested classes — 41 `@Test`
methods where the algorithm under test was identical within each class and only the
input/expected-output literals varied, the exact case `.claude/guides/testing-unit.md`'s
"Parameterized Tests for Equivalence Classes and Invariants" section already covers, and
that I had available but didn't apply when writing the new cases earlier in this phase.
Rewrote all eight nested classes as `record`-based case tables driven by
`@ParameterizedTest`/`@MethodSource` (41 cases preserved exactly, same pass count, same
assertions); merged `deleteLineLabel`/`deleteEndingLabel` into the renamed `FixedLabel`
class alongside the five `remove*Label` no-arg methods, since they're the same shape (a
`Supplier<String>` and an expected key). Net -59 test LOC despite no case lost. Extended
the guide with a third worked example (a multi-field record case table, using
`OpNamesTest.DeleteLabel` itself) to close the gap that let this happen — the existing two
examples only varied a single parameter each.

## Triage outcome

All 141 tests inherited from Phase 11 mapped to a real contract case; every one of the
ten test classes already carried an accurate testing-approach Javadoc (D8) written
during Phase 11's contract pass, which made this phase's triage mostly confirmatory
rather than corrective. **Kept as-is: 139. Discarded: 2. Rewritten: 2 (renamed, same
assertions). Added: 22, all closing gaps the contract promises but nothing exercised.**

- **Discarded (2)** — `MutationLabelTest.testEmptyUndoStackShowsPlainUndoLabel` and
  `testEmptyRedoStackShowsPlainRedoLabel`. Duplicate coverage of the empty-stack clause
  of `undoLabel()`/`redoLabel()`'s contract, already exercised by
  `UndoOpNameLabelTest.testEmptyStackYieldsBareUndoAndRedo` — the class that owns "where
  the two halves of the contract meet." `MutationLabelTest`'s class Javadoc updated to
  cross-reference rather than claim the case.
- **Renamed (1)** — `MutationReplayerRoundTripTest.testLineKeyAccidentalCountChangeRoundTrips`
  → `testLineKeyTypeAndAccidentalCountChangeRoundTrips`. The test body already exercised
  both `KeyField` values (`KEY_TYPE` via `setKeyType`, `ACCIDENTAL_COUNT` via
  `setKeyAccidentalCount`) in one batch; the name credited only one.
- **Stale reference fixed (1)** — `UndoControllerTest`'s eviction-boundary test still
  said `DEFAULT_UNDO_STACK_MAX_DEPTH` in a comment; the constant was collapsed into
  `UNDO_STACK_MAX_DEPTH` during Phase 11 and the comment was missed.

## The four gaps Phase 11 named

1. **`PasteReconciliationUndoTest` / `UndoStaleSelectionTest` package location** —
   both classes' Javadoc already states the real reason they live in `songscribe.undo`:
   what they assert is an undo guarantee (paste's side of the complete-emission
   invariant; the live-selection splice), not because of any package-private access —
   `UndoController.reset()` has been public since Phase 9. Decided on merit: they stay.
   No stale "package-private `resetForTest()`" language remains in either file.
2. **`MutationLabelTest` / `UndoOpNameLabelTest` both cover the empty-stack label** —
   resolved above (discarded the duplicate, `UndoOpNameLabelTest` owns the case).
3. **`MutationLabelTest` hand-builds `SongDidChangeNotification`s** — real tension with
   `docs/mutations.md`'s unqualified "never construct directly." Resolved by qualifying
   the doc: the rule binds production, where the invariant it protects (one notification
   per outermost bracket, posted by `Song.endModification`) actually matters; a test
   fixture doing it to reach an otherwise-unreachable case (`FontChange`, which no `Song`
   edit can produce since it's `ScoreView`-scoped; a deliberately corrupt mutation
   forcing the replay-failure path) doesn't violate that invariant. `docs/mutations.md`
   now says so and names both call sites.
4. **Test-only surface** — see below; already resolved in Phase 11, reconfirmed here.

## Test-only surface (Phase 10's record)

Phase 10 found exactly one candidate in `undo`:
`UndoController.DEFAULT_UNDO_STACK_MAX_DEPTH`. Phase 11 already collapsed it and the
identical `undoStackMaxDepth` field into one public `UNDO_STACK_MAX_DEPTH`, cited by
the class contract. Reconfirmed with `jet_brains_find_referencing_symbols`: production
reads it once (`songDidChange`'s eviction check), it's cited by two `{@value}` Javadoc
tags, and every remaining reference outside the class is a real caller, not a
test-only one. No test-only surface remains in the package.

## Gaps coverage surfaced

Coverage was run once, deliberately, scoped to the package's own ten test classes
(`./scripts/coverage.sh unit <the ten classes>`), and every uncovered region was
traced to one of two outcomes — never to a manufactured test:

**Real missing contract cases (fixed, 22 new tests):**

- `OpNames` — six fixed-name methods (`deleteEndingLabel` and the five simple
  `remove*Label` methods) had no case, despite the class's own precedent
  (`deleteLineLabel`, an identically-shaped fixed method, was already tested) and its
  own stated principle that a fixed method promises one name as much as a parameterized
  one does. `deleteHairpinLabel` (2 kinds), `removeArticulationLabel` (2 types) and
  `removeAttachmentLabel` (5 kinds) were named in the class Javadoc as small closed
  domains belonging there "enumerated in full" but were never actually enumerated.
  `deleteLabel`'s plural form was tested for `NOTE` and `REST` but not `BARLINE`,
  `REPEAT` or `BREATH_MARK`, despite the contract promising the plural uniformly across
  categories.
- `UndoController` — `undo()`/`redo()`'s documented no-op ("posting nothing, when
  `canUndo()`/`canRedo()` is false or no document is open") and `documentWasSaved`'s
  no-op ("no-op when no document is open") had zero coverage; nothing in any of the ten
  classes ever called `undo()`/`redo()` on an empty stack or with no document mocked.
  Added to `UndoControllerSavePointTest`, which already builds the
  `MainFrame`/`ScoreView` mocking seam these cases need — matching what
  `UndoControllerTest`'s own "Not covered here" note already pointed at, now made real
  instead of aspirational. The `documentWasSaved` case is asserted by absence of an
  exception rather than by the document's modified flag, because `Song` itself also
  subscribes to `DocumentWasSavedNotification` and clears its own flag unconditionally
  — that flag can't distinguish `UndoController`'s early return from it running to
  completion, but a missing guard would `NullPointerException` on the null `scoreView`.

**Not real gaps (left alone, explained):**

- `OpNames.categoryOf`'s `null` return and `addLabel`'s `IllegalArgumentException` —
  every current `ElementType` constant falls into one of the five categories
  (confirmed against the full enum), so there is no live input that reaches either
  branch. Exactly what the class's own Javadoc already says: "no insertion the user can
  perform produces" this case. Fabricating a fake `ElementType` to force it isn't
  possible (it's an enum) and wouldn't test anything the domain can produce.
- `UndoController.withPendingOpName`'s lambda body — uncovered only because coverage
  was scoped to the `undo` package's own tests. It's exercised by
  `songscribe.ui.action.OpNameThreadingTest`, which is the right owner (the path only
  matters through `UIAction`'s dispatch template). Confirmed by re-running the search
  against the whole test tree, not manufactured as an `undo`-package test.
- `MutationReplayer`'s per-dispatch branch misses (`applyUndo`, `applyRedo`, and the
  small field-switch helpers) — 0 missed *lines* throughout the class, only missed
  *branches*, and the branch counts match exactly `case count + 1`: the compiler's
  synthetic exhaustiveness-check branch that Java generates for a `switch` expression
  over a sealed type or enum with no `default`, which is unreachable by construction.
  Every real case arm is exercised by `MutationReplayerRoundTripTest`'s enumeration of
  the sealed `Mutation` hierarchy and each field enum; the remainder is a compiler
  artifact, not a missing test.

## Diagrams (D17)

`docs/undo.md` carried four ASCII diagrams under "Runtime flow": recording a forward
edit, composing the Edit-menu label, and the undo/redo call sequences. All four
restated, arrow by arrow, contracts Phase 11 already wrote as Javadoc on
`UndoController.songDidChange`, `undoLabel`/`redoLabel`/`composeLabel`, `undo`/`redo`,
and `MutationReplayer.applyUndo`/`applyRedo` — the "diagram is the contract drawn a
second time" case the rule warns about. Dropped all four; "Runtime flow" is now a short
pointer to the methods that state each step, so the document doesn't carry a second,
driftable copy of what the code's own Javadoc already promises. The tier-3 "What the
engine guarantees" section and the prose design-rationale sections (op-name tiers, why
a normal bracket, why not `UndoManager`) are untouched — they state cross-subsystem
rules no single class's Javadoc can carry, which is exactly what a tier-3 document is
for.

## What this confirms about the pilot

Phase 11 (contract-writing) and this phase (triage) turned out to be more asymmetric
than the plan anticipated: nearly all of the triage work — deciding what each existing
test covers and whether that maps to a contract clause — had already happened while
Phase 11 wrote the testing-approach Javadoc, because writing an accurate "here is what
this class is responsible for" comment requires actually checking each test against the
contract. What was left for this phase was smaller and sharper: two duplicate tests, one
stale reference, one real doc/test tension, and — the most consequential single tool in
the phase — a single scoped coverage run that found 22 real missing contract cases no
amount of *reading* the tests would have surfaced, because the tests that did exist read
as complete without them (each class's own Javadoc claimed full enumeration and was
wrong by a few cases). Phase 13 should weigh that: coverage-as-gap-finder (never as a
target) earned its keep here, and a pilot retrospective that only measured "tests kept
vs. discarded" would have missed the more interesting finding, which is tests *added*
despite a prior "this is fully enumerated" claim.
