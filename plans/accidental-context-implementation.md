# Accidental Context — Implementation Plan

Derived from `plans/accidental-context.md`. Tracked by #675 (resolver scope) and #676 (reconciliation).

**All phases land on the current `paste-into-line` branch.** No phase creates a branch, switches
branches, or commits. The three-branch topology in the source plan is dropped.

---

## Issue order

**#675 must be finished before any of #676 starts.** Not a preference — every path in #676 resolves
through the `findEffectiveAccidental` that #675 rewrites, and Phase 4's projected backward scan
reimplements #675's barrier and tie-escape rules over a projected element sequence. Building it
against the old resolver would encode the defect in a second place.

| Order | Work | Phases | Notes |
|-------|------|--------|-------|
| 1 | Paste-mode lockout | 1 | No issue — a defect in what just landed on this branch. Independent of both issues, so it can land first, last, or alongside. |
| 2 | **#675** — accidental scope | 2, 3 | Phase 2 rewrites the resolver; Phase 3 tests it. Ships standalone: it is driven by export fidelity, migrates for free, and needs nothing from #676. |
| 3 | **#676** — reconcile across edits | 4–9 | Phases 4 and 5 are independent of each other; 6 → 7 → 8 → 9 are serial. Phase 8's fit gate is separable from the accidental work — it is a defect for dots and duration swaps regardless — and can be dropped out if it grows. |
| 4 | Verification | 10 | Yours, manual. Gates the two below. |
| 5 | Tests and docs | 11, 12 | Phase 11 tests #676; Phase 12 documents both. |

If you stop after step 2, #675 is complete and releasable on its own. Stopping part-way through
step 3 is not releasable: Phases 6–9 each close one call site, and until all four are closed some
edits still change pitches silently.

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Paste-Mode Line-Select Lockout](#-phase-1-paste-mode-line-select-lockout) | ✅ Complete | — |
| 2 | [Resolver Scope](#-phase-2-resolver-scope) | ✅ Complete | — |
| 3 | [Resolver Unit Tests](#-phase-3-resolver-unit-tests) | ✅ Complete | — |
| 4 | [Shared Reconciliation Unit](#-phase-4-shared-reconciliation-unit) | ✅ Complete | — |
| 5 | [Fragment Accidental Reshape](#-phase-5-fragment-accidental-reshape) | ✅ Complete | — |
| 6 | [Call Site 3 — Paste](#-phase-6-call-site-3--paste) | ✅ Complete | — |
| 7 | [Call Sites 1 and 2 — Single Insert and Delete](#-phase-7-call-sites-1-and-2--single-insert-and-delete) | ✅ Complete | — |
| 8 | [Modification Fit Gate + Call Site 4](#-phase-8-modification-fit-gate--call-site-4) | ✅ Complete | — |
| 9 | [Call Site 5 — Pitch Shift](#-phase-9-call-site-5--pitch-shift) | ✅ Complete | — |
| 10 | [Manual UI Verification](#-phase-10-manual-ui-verification) | ⏳ Pending | — |
| 11 | [Reconciliation and Fit-Gate Tests](#-phase-11-reconciliation-and-fit-gate-tests) | ⏸️ Blocked by 10 | — |
| 12 | [Documentation](#-phase-12-documentation) | ⏸️ Blocked by 11 | — |

---

## ✅ Phase 1: Paste-Mode Line-Select Lockout

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — one guard clause plus doc updates; the
defect, the fix, and the settled rule are all spelled out below.

### Context

Paste mode is a modal state owned by `songscribe.ui.edit.PasteModeManager` (singleton, reached via
`EditModeManager.getPasteModeManager()`). It is entered from `ScoreViewController.handlePaste()`
when Cmd+V is pressed with no selection, and is exited only through `PasteModeManager.exit()`.

The defect: `LineComponent.mousePressed`
(`src/main/java/songscribe/ui/component/score/LineComponent.java:846`) is not paste-mode aware,
while `LineComponent.mouseClicked` (same file, line 749) is. During paste mode, pressing on the
staff lines inside the clef/key-signature header makes
`selectionHandler.isStaffLineHit(e.getPoint())` true at line 861, which:

1. performs `Actions.SELECT_MODE_ACTION` — a permanent EDIT→SELECT flip, and
2. falls through to `selectionHandler.handlePress(e)` at line 880, which resolves
   `HitResult.StaffLine` and calls `lineSelectionState.setLineSelected(true)`
   (`LineSelectionHandler.java:218-225`), selecting the whole line.

The `mouseClicked` that follows routes to `PasteModeManager.mouseClicked`
(`PasteModeManager.java:329`), which consumes the event and calls `updateTarget`. `updateTarget`
sees the point is inside the header (`HorizontalSpacingCalculator.isWithinHeaderXSs`,
`PasteModeManager.java:381`) and calls `clearTarget()`, so `placeAtTarget()` is a no-op.

Net result: mode flipped to SELECT, the whole line selected, paste mode still active, nothing
pasted — a stuck state.

Settled rule (do not re-litigate): **line select is disabled during paste mode.** Replacing a line
means selecting it *before* Cmd+V, which is #612's Replace.

### Tasks

1. In `LineComponent.mousePressed`
   (`src/main/java/songscribe/ui/component/score/LineComponent.java:846`), insert a guard
   immediately after the `getGraceModeManager().mousePressed(this, e)` guard (line 851) and before
   the EDIT→SELECT switch (line 859) that returns when
   `EditModeManager.getPasteModeManager().isInProgress()` is true. Placement after the grace-mode
   guard is deliberate: grace mode and paste mode are never active together (paste mode blanket-
   disables actions), and the grace guard keeps its existing precedence.
2. Confirm by reading the method that the guard suppresses all four press behaviours for the whole
   press: the EDIT→SELECT switch (line 859-863), lyric selection (`hitTestLyric` →
   `selectLyric`, lines 865-872), the note pitch drag (`noteDragHandler.handlePress`, line 875),
   and line/rubber-band selection (`selectionHandler.handlePress`, line 880). All four must be
   inert during paste mode.
3. Add a comment at the guard explaining *why*: a single press must not both change the selection
   and be consumed by paste placement, and the settled rule is that replacing a line means
   selecting it before Cmd+V.
4. Extend the ASCII state-machine javadoc at the top of `PasteModeManager`
   (`src/main/java/songscribe/ui/edit/PasteModeManager.java:50-86`) with one line stating that
   while ACTIVE, presses on a line are inert — no line select, no lyric select, no pitch drag — so
   the click that follows is always a placement or a cancel.
5. Add the same rule to `docs/clipboard.md` under `## 5. Paste mode`.
6. Run `./scripts/compile.sh` and confirm it reports SUCCESS.

---

## ✅ Phase 2: Resolver Scope

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high effort — the resolver's target shape is settled but
the tie-escape loop, its termination argument, and the traversal seam all need careful
construction; every later phase resolves through this method.

### Context

A note stores a staff position plus an *optional explicit* accidental. It stores no pitch — pitch
is derived at read time by `StaffElement.getPitch()`
(`src/main/java/songscribe/dom/StaffElement.java:657`), which delegates to
`findLastAccidental()` → `findEffectiveAccidental(Line, int)` (same file, lines 748-771).

Current body of `findEffectiveAccidental`:

```java
public @Nullable Accidental findEffectiveAccidental(Line targetLine, int index) {
    for (var i = index - 1; i >= 0; i--) {
        var note = targetLine.getElement(i);

        if ((note.getStaffPosition() == staffPosition) && (note.getAccidental() != null)) {
            return note.getAccidental();
        }
    }

    return getAccidental(targetLine);
}
```

Two defects: the scan spills past barlines and repeats (convention says any structural marker
cancels prior accidentals), and it ignores ties across such a barrier (convention carries an
accidental through a tie that crosses a barline).

**Resetting at the end of a line is house convention, not a defect.** Do not change it. The
repertoire is largely meterless and metered music here closes its staves with a barline at the end
of a row, so the row boundary and the measure boundary coincide in practice.

**Why this matters beyond playback — export fidelity.**
`MusicXmlNoteWriter.writeAccidental` (`src/main/java/songscribe/io/musicxml/MusicXmlNoteWriter.java:160`)
emits `<accidental>` only when a note carries an explicit one, while
`PitchSpelling.soundingAlterFor` (`src/main/java/songscribe/io/musicxml/PitchSpelling.java`)
derives `<alter>` from this scan. A note inheriting an accidental across a barline therefore
exports as `<pitch>` with `<alter>1</alter>` and no `<accidental>` element, and a standard
consumer (MuseScore, Finale) draws a sharp that SongScribe does not.

**Playback picks this up for free, and is the fastest way to check the change by ear.** Every
interactive note-sounding site reads `StaffElement.getPitch()`, so all of them resolve through the
rewritten method with no edit: `EditModeManager.previewElementDidChange`
(`src/main/java/songscribe/ui/edit/EditModeManager.java:360`, the note played on insertion, after
the element is in the line), `AccidentalAction`
(`src/main/java/songscribe/ui/action/AccidentalAction.java:173`),
`LineSelectionHandler.playNoteIfPitched`
(`src/main/java/songscribe/ui/component/score/LineSelectionHandler.java:397`), and `PitchShifter`
(lines 160, 174, 186). Do not add a preview-specific resolver. `StaffElement` used to carry
`getPreviewElementPitch(Line)` and `getPreviewElementAccidental(Line)`, which resolved an
accidental from the note's own value or the key alone, skipping the backward scan. Both were dead
(nothing called the former; only the former called the latter) and have **already been deleted** on
this branch. Do not reintroduce them under any name — `getPitch()` is the only pitch resolver, and
the key-signature fallback they shared is `getAccidental(Line)`, which survives as branch 3 of
`findEffectiveAccidental` and is what `keyInEffectAt` delegates to.

**The migration is free.** `NoteAccumulator` ignores `<alter>` on read and re-derives sound from
the written `<accidental>` (`src/main/java/songscribe/io/musicxml/NoteAccumulator.java:507-508` —
the comment "The displayed accidental glyph comes from `<accidental>`, not `<alter>`"), so
existing files pick up the corrected reading on next open. No rewrite, no version bump, no
migration step. Do not add one.

### Target shape

```
findEffectiveAccidental(targetLine, index):
    tieEndElement = this          # the note whose incoming tie we may escape through
    cursor        = index

    loop:
        for scanIndex in precedingIndices(targetLine, cursor):     # <-- THE SEAM
            element = targetLine.getElement(scanIndex)
            type    = element.getType()

            if type.isBarLine() or type.isRepeat():                # barrier
                anchor = tieAnchorBefore(targetLine, tieEndElement, scanIndex)
                if anchor != null:
                    tieEndElement = anchor
                    cursor        = indexOf(anchor) + 1            # visit the anchor next
                    continue loop
                return keyInEffectAt(targetLine, index)

            if element.getStaffPosition() == staffPosition and element.getAccidental() != null:
                return element.getAccidental()

        return keyInEffectAt(targetLine, index)
```

Notes the implementation must honour:

- **The note's own explicit accidental is already handled by the callers**, not by this method:
  `getPitch()` (line 657) and `StatusBar.setNoteContent`
  (`src/main/java/songscribe/ui/component/StatusBar.java:193`) both check `getAccidental()` first
  and only call the resolver when it is null. Keep that contract; do not add the own-accidental
  check here.
- **Barrier predicate is `ElementType.isBarLine() || ElementType.isRepeat()`.** Both already exist.
  Do **not** use `ElementType.isNonDuration()` — it bundles in `isBreathMark()`, which cancels
  nothing.
- **Tie escape by identity, not by index.** `tieAnchorBefore` scans `targetLine.findTies()`
  (`src/main/java/songscribe/dom/Line.java:1577`) for a `Tie` whose `getEndElement()` **is**
  (reference identity) `tieEndElement` and whose `getAnchorElementIndex()` is `>= 0` and
  `< scanIndex`; it returns that tie's `getAnchorElement()`, else null. Identity comparison is
  required: `StaffElement` overrides neither `equals` nor `hashCode`, and a detached clone would
  otherwise match nothing meaningful.
- **Chains must escape repeatedly.** Advancing `tieEndElement` to the anchor (rather than always
  looking for a tie ending at `this`) is what carries an accidental through `A~B~C` where two
  barlines intervene. `Line` represents a chain as one `Tie` per adjacent pair
  (`Line.addTie` javadoc, line 1545), so each hop finds the next link.
- **Termination.** Each escape sets `cursor` to `anchorIndex + 1`, and `anchorIndex < scanIndex <
  cursor`, so `cursor` strictly decreases on every `continue loop`. The loop is bounded by the
  element count.
- **Isolate the backward traversal.** "Scan back through preceding elements" must be exactly one
  private method — today it yields this line's indices from `cursor - 1` down to `0`. If
  line-reset is ever revisited, that one method continues into the previous line and nothing else
  moves: the barrier test, the tie escape, the staff-position match and the key fallback stay
  untouched. Prefer a shape that does not allocate per call (an index-yielding loop encapsulated
  in one method, or a visitor callback) — `getPitch()` is on the playback and MIDI-export paths.
  A materialised `List` per call is acceptable only if the encapsulation is otherwise unworkable.
- **`keyInEffectAt` is introduced now as a no-op.** Add
  `private @Nullable Accidental keyInEffectAt(Line targetLine, int index)` whose body is
  `return getAccidental(targetLine);` — the existing private helper at
  `StaffElement.java:685`, which reads `line.keyExists(getPitchIndex())` and `line.getKeyType()`.
  Today a line carries exactly one key signature, so `index` is unused. Javadoc it as the seam
  that makes #53 (mid-line key changes) a one-method change instead of a resolver rewrite, and
  note that #53 will also add key changes to the barrier list. Leave
  `getPreviewElementAccidental` (line 677) calling `getAccidental(Line)` directly — it resolves
  for an element that is not in the line.
- **Same-octave matching stays.** The scan matches on staff position. That is ordinary staff-
  notation convention and what export fidelity depends on. Do not widen it to pitch class.

### Tasks

1. Read `src/main/java/songscribe/dom/StaffElement.java` lines 648-771 and
   `src/main/java/songscribe/dom/Line.java` lines 1505-1580 (`findTieAt`, `findExactTie`,
   `addTie`, `findTies`) so the tie API and the current resolver are both in hand.
2. Add the private traversal seam to `StaffElement` — one method that decides which positions the
   backward scan visits and in what order, today `cursor - 1` down to `0` on `targetLine`. Javadoc
   it as the single seam for revisiting line-reset.
3. Add the private tie-anchor lookup described under **Target shape** (`tieAnchorBefore`), using
   reference identity on `getEndElement()` and requiring `getAnchorElementIndex()` in
   `[0, scanIndex)`.
4. Add `private @Nullable Accidental keyInEffectAt(Line targetLine, int index)` delegating to the
   existing `getAccidental(Line)`, with the javadoc described above.
5. Rewrite `findEffectiveAccidental` to the target shape: barrier test first, then tie escape or
   stop, then the staff-position match, with `keyInEffectAt` as the fallback on both exits.
   Rewrite its javadoc to state the three branches, the barrier set, and the tie escape.
6. Update the javadoc on `PitchSpelling.soundingAlterFor`
   (`src/main/java/songscribe/io/musicxml/PitchSpelling.java`) to note that the derived `<alter>`
   now cancels at barlines and repeats, and carries through a tie that crosses one — so
   `<pitch>`/`<alter>` and the drawn accidental agree for a standard consumer.
7. Run `./scripts/compile.sh` and confirm SUCCESS, then run `./scripts/test.sh unit` and confirm
   green. Two existing tests in `src/test/java/songscribe/dom/StaffElementTest.java`
   (`testFindLastAccidentalInheritsPredecessorAccidental` at line 485,
   `testFindLastAccidentalFallsBackToKeySignature` at line 507) exercise this method. Both build
   two-element lines with no barlines, repeats or ties, so **both must still pass unchanged** — a
   failure means the rewrite is wrong, not that the fixture is stale. Do not edit either test in
   this phase.

---

## ✅ Phase 3: Resolver Unit Tests

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical test authoring against a
resolver whose contract is fully specified; the existing test class supplies the fixture idiom.

### Context

Phase 2 rewrote `StaffElement.findEffectiveAccidental`
(`src/main/java/songscribe/dom/StaffElement.java`) so that the backward scan for the nearest
earlier explicit accidental at the same staff position (a) stops at any element whose
`ElementType.isBarLine()` or `ElementType.isRepeat()` is true, and (b) escapes such a barrier when
the note being resolved ends a `Tie` whose anchor sits before the barrier, resuming the resolution
at that anchor and repeating for a chain of ties. On stopping, it falls back to the line's key
signature via the new private `keyInEffectAt(Line, int)`.

Before writing tests, read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md`
(neither is auto-loaded). Add tests to the existing
`src/test/java/songscribe/dom/StaffElementTest.java`, mirroring the fixture construction used by
`testFindLastAccidentalInheritsPredecessorAccidental` (line 485) and
`testFindLastAccidentalFallsBackToKeySignature` (line 507): build a `new Song()`, take
`song.getLine(0)`, construct `new StaffElement(ElementType.X)` with `setStaffPosition` /
`setAccidental`, and perform every `Line` mutation inside `song.withoutMutationTracking(() -> {
... })` — `Line`'s mutators otherwise require an open modification bracket. Key signatures are set
with `line.setKeyType(KeyType.SHARPS)` + `line.setKeyAccidentalCount(n)` inside that same block.
Ties are added with `line.addTie(new Tie(anchor, end))`.

Every staff position, key count and expected accidental in a test must be a named constant, not a
raw literal — the project's no-magic-numbers rule applies to tests, with only `0`, `1`, `-1` and
`*2`/`/2` exempt.

### Tasks

1. Test that an explicit accidental **before** a `SINGLE_BARLINE` is **not** inherited by a later
   note at the same staff position after it — the note falls back to the key signature.
2. Test the same for `DOUBLE_BARLINE`, `FINAL_DOUBLE_BARLINE`, `REPEAT_LEFT`, `REPEAT_RIGHT` and
   `REPEAT_LEFT_RIGHT`, and test that a `BREATH_MARK` between the two notes is **not** a barrier —
   the accidental is still inherited across it.
3. Test the tie escape: note A with an explicit SHARP, a `SINGLE_BARLINE`, then note B at the same
   staff position with no explicit accidental and a `Tie(A, B)` on the line — B resolves to SHARP.
   Test the negative: with no tie, B resolves to the key signature.
4. Test the tie chain: A (explicit SHARP) — barline — B — barline — C, with `Tie(A, B)` and
   `Tie(B, C)` both on the line; C resolves to SHARP. Test that removing `Tie(A, B)` leaves C on
   the key signature.
5. Test that a tie whose anchor sits **after** the barrier does not escape it, and that a note
   that merely *starts* a tie (is the tie's anchor, not its end) gets no escape.
6. Test that the key-signature fallback is unchanged when no explicit accidental and no barrier
   are present, and that a note's own explicit accidental is still returned by
   `StaffElement.getPitch()` regardless of any barrier (the caller checks it first).
7. Run `./scripts/compile.sh`, then `./scripts/test.sh unit StaffElementTest`, and finally
   `./scripts/test.sh unit`. All must be green before the phase is done.

---

## ✅ Phase 4: Shared Reconciliation Unit

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, high effort — the whole invariant lives here; a projected
pre-mutation resolver with two entry points, and every later call site depends on getting it right.

### Context

**The invariant:** *every note keeps the pitch it had, unless the user changed that note.* Two
populations — **pasted/inserted** notes keep the pitch they had in their source context;
**surviving** notes keep the pitch they had before the mutation.

**The rule:** for a note whose effective accidental would change, materialize an explicit one:

```
if adjustment(before) != adjustment(after):
    note.accidental = (before != null) ? before : NATURAL
```

- Compare **adjustments**, never enum identity. `null` and `NATURAL` sound alike, as do `FLAT` and
  `NATURAL_FLAT`. Use `StaffElement.getPitchAdjustment(Accidental)`
  (`src/main/java/songscribe/dom/StaffElement.java:717`), treating `null` as adjustment `0`. There
  is no glyph for a difference nobody can hear.
- **`null` → `NATURAL`** matters and is easy to miss: you cannot write "nothing" and get a natural
  in a context that alters that pitch. This direction is the entire cross-key paste case.
- The key signature never appears in the algorithm — it is already branch 3 of the resolver, so
  resolving against source and destination compares the two keys implicitly. Cross-key paste falls
  out with no source key stored and no key comparison written.

**Two bounds on the work:**

- Only a staff position carrying an explicit accidental **in the removed content or the inserted
  content** can change the context arriving at the boundary.
- For each such position, only the **first** following note lacking its own accidental needs
  fixing; later ones resolve from it. Stop at the first note that already has an explicit
  accidental.

Both bounds stay within the line, since the scan does.

**Where it lives:** a new class `songscribe.layout.AccidentalReconciliation` at
`src/main/java/songscribe/layout/AccidentalReconciliation.java`, beside
`InsertionSpacingCalculator` — *not* inside the paste path. It must be **pure** and
**pre-mutation**: it reads the live line and mutates nothing. Callers apply the result. (Phase 7
adds the shared apply-and-gate helper as a separate class, `AccidentalMaterializer`, precisely so
this one stays pure — do not add mutating helpers here.) There is a
package-dependency test (`src/test/java/songscribe/PackageDependencyTest.java`); `songscribe.layout`
already depends on `songscribe.dom`, so no new edge is introduced.

**Why pre-mutation and pure is mandatory:** accidentals must be materialized *before* the projected
column chain is built, because `ElementColumnBuilder` derives element extents including accidental
width and `LayoutEngine` treats accidental widths as a layout input. Get the ordering right and
both the fit gate and the committed layout are automatically correct, with no per-position shift
machinery — nothing in `layout/` reads `getXOffsetPx()`, so displayed geometry comes entirely from
the whole-line solve.

### Target API

```java
/** A note that must be given an explicit accidental so its pitch survives a mutation. */
public record Materialization(StaffElement note, StaffElement.Accidental accidental) {}

/** An insert, a delete, or a paste-replace, described before any of it happens. */
public record InsertionRegion(
    Line line,
    int insertIndex,
    InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
    List<StaffElement> inserted,
    List<StaffElement.@Nullable Accidental> insertedPriorAccidentals,
    List<RangeElement> insertedSpans) {}

public static List<Materialization> reconcile(InsertionRegion region)

/** One note's intended post-change state for an in-place modification. */
public record IntendedChange(int index, StaffElement.@Nullable Accidental accidental,
                             int staffPosition) {}

public static List<Materialization> reconcileModification(Line line, List<IntendedChange> changes)
```

- `insertedPriorAccidentals` is either **empty** or the same size as `inserted`; enforce exactly
  that in the record's compact constructor. When it is the same size, each entry is that element's
  effective accidental **in its source context** (null when it sounded unaltered there) — the paste
  case. When it is **empty**, the inserted elements have no source context and must **never** be
  materialized against themselves; only the notes that follow them are candidates. That is the
  fresh-insert case: a note the user is creating has no pitch it "had", so the invariant does not
  reach it. Do not encode "no source context" as a list of nulls — null already means "sounded
  unaltered there", which is a different claim.
- `insertedSpans` are the fragment's own `RangeElement`s (its ties), which are not yet on the
  destination line; the projected tie escape needs them.
- Both entry points return the notes needing an explicit accidental. A note the user themselves
  changed is **never** in the result — the invariant only protects notes the user did not touch.
  For `reconcileModification`, that means the elements named in `changes` are excluded.

### Algorithm — one left-to-right pass over a projected sequence

1. `successorIndex = deleteRange == null ? insertIndex : deleteRange.end() + 1`.
2. Build the projected element sequence the mutation will produce:
   `line[0 .. insertIndex-1]` ++ `inserted` ++ `line[successorIndex .. line.effectiveElementCount()-1]`.
   Keep, per projected position, whether it is a survivor (and its original line index) or an
   inserted element (and its index in `inserted`).
   For `reconcileModification` the projected sequence is the line's own elements with the changed
   positions read through their `IntendedChange` (same length, same order, indices preserved).
3. Compute each position's **before** accidental:
   - survivor at original index `j`: its own `getAccidental()` when non-null, else
     `element.findEffectiveAccidental(line, j)` resolved on the **live, unmutated** line;
   - inserted element `k`: its own `getAccidental()` when non-null, else
     `insertedPriorAccidentals.get(k)`;
   - a note named in an `IntendedChange`: its intended accidental (so it can never materialize
     against itself).
4. Walk the projected sequence left to right, starting at the projected position corresponding to
   `insertIndex` (for `reconcileModification`, at the lowest changed index). Nothing before the
   mutation point can change.
5. For each **pitched** note visited (`getType().isPitchedNote()`) that has **no** explicit
   accidental of its own, resolve its **after** accidental over the projected sequence using
   exactly the rules `StaffElement.findEffectiveAccidental` uses: scan back over the projected
   sequence; stop at any element whose `ElementType.isBarLine()` or `isRepeat()` is true, escaping
   that barrier when a tie ends at the note and its anchor's projected position is before the
   barrier (repeat for chains); match on equal staff position with a non-null accidental;
   otherwise fall back to the destination `line`'s key signature for that pitch class. Ties are
   found by reference identity on `Tie.getEndElement()`, searching `line.findTies()` **and**
   `insertedSpans`; an anchor not present in the projected sequence (deleted) yields no escape.
6. If `adjustment(before) != adjustment(after)`, emit
   `Materialization(note, before != null ? before : Accidental.NATURAL)` and treat that accidental
   as explicit for the remainder of the pass, so later notes at the same staff position resolve
   from it.
7. **Do not add a termination bound.** The two bounds stated under **Context** are already
   satisfied structurally by the left-to-right pass: once the first following note at a staff
   position materializes, later notes at that position resolve from it and match their own
   `before` value, so they emit nothing; and a note that already has its own explicit accidental
   is skipped at step 5. An additional early-stop over "staff positions carrying an explicit
   accidental in the removed or inserted content" would be a pure optimization over a pass that is
   bounded by one line's element count and runs once per mutation — not per frame. Record in the
   javadoc that the bounds are structural, not an early exit.

### Tasks

1. Read `src/main/java/songscribe/dom/StaffElement.java` (`findEffectiveAccidental`,
   `getPitchAdjustment`, the `Accidental` enum, `getPitchIndex`), `src/main/java/songscribe/dom/Line.java`
   (`findTies`, `keyExists`, `getKeyType`, `effectiveElementCount`, `getElement`), and
   `src/main/java/songscribe/layout/InsertionSpacingCalculator.java` (the `DeletedRange` record and
   the shape of `calculateFragmentInsertion`, which this class mirrors in structure).
2. Create `src/main/java/songscribe/layout/AccidentalReconciliation.java` with the records and the
   two public entry points from **Target API**. Class javadoc states the invariant, the rule, the
   two bounds, and that the unit is pure and pre-mutation.
3. Implement the projected sequence construction plus the projected backward resolver described in
   steps 1, 2 and 5 of **Algorithm**, as a private engine shared by both entry points.
4. Implement the before/after comparison and materialization emission (steps 3, 4, 6), using
   `StaffElement.getPitchAdjustment` with `null` treated as `0`. Never compare enum identity.
5. Implement `reconcile(InsertionRegion)` and `reconcileModification(Line, List<IntendedChange>)`
   on top of the shared engine, with `reconcileModification` excluding the changed notes from the
   result.
6. Add the termination bound from step 7, or javadoc why it was left out.
7. Run `./scripts/compile.sh` and confirm SUCCESS. (Tests for this class are Phase 11.)

---

## ✅ Phase 5: Fragment Accidental Reshape

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — a record-component addition and two loop
edits, but the capture trap and the offset-zeroing rationale must be followed exactly as written.

### Context

`songscribe.ui.clipboard.Fragment` (`src/main/java/songscribe/ui/clipboard/Fragment.java`) is an
immutable copy of a run of `StaffElement`s plus the `RangeElement` spans fully contained within it.
It is currently `record Fragment(List<StaffElement> elements, List<RangeElement> spans)` with a
compact constructor that defensively `List.copyOf`s both, a static `capture(Line, int, int)`
factory, and an `instantiate()` that clones 1:1 in order.

A pasted note must keep the pitch it had in its source context, and the source context is gone by
the time the paste happens. So the fragment must carry, alongside its elements, the effective
accidental each element had on the source line.

**The capture trap — read carefully.** Resolve each element's accidental inside `capture`'s loop
against the **live original**, i.e. `original.findEffectiveAccidental(line, i)`. Do **not** resolve
off the clone: a clone's `line` field still points at the source line, but
`line.getElementIndex(clone)` returns −1 (`StaffElement` overrides neither `equals` nor
`hashCode`), so `clone.findLastAccidental()` silently skips the whole scan and returns the key
signature alone — a wrong answer with no error.

**Manual offsets — a settled decision, implemented here.** `xOffset`'s intended meaning is a nudge
from the computed position (MusicXML `relative-x`). The feature is not implemented yet, but the
`Fragment` reshape is where the decision lands: **a fragment carries semantic content, not layout
corrections.** What the notes *mean* travels; how they were *nudged* does not. A nudge is purely
contextual — a correction to one spring solve, with specific neighbours, under a specific header
width — so pasted elsewhere it is meaningless at best and at worst recreates the collision it was
made to fix. Today `copyStateFrom` copies `xOffset` and `tryInsertFragment` happens to overwrite
it; once offsets mean something, that incidental overwrite becomes the only thing keeping it
correct. So zero it explicitly. (The line fragment of #612 goes the opposite way — offsets travel
there, alongside the key signature and `elementSpacingRatio` it already carries — but that is a
separate downstream plan and is not touched here.)

**Zero them unconditionally.** Do not add a rule keyed on how *similar* the destination context is
— same neighbours, same header width, same key, a paste back into the line it came from. It is a
tempting refinement and it is explicitly rejected: predictability beats salvaging nudges on a
near-miss paste, and a rule that sometimes preserves offsets makes the outcome impossible for a
user to predict. This is the deliberate mirror of the accidental rule, which goes the opposite way
for a reason: pitch is semantic, so its context-dependence is the problem to solve; a nudge is
*purely* contextual, so its context-dependence means it must not travel at all.

### Tasks

1. Add a third record component to `Fragment`:
   `List<StaffElement.@Nullable Accidental> priorAccidentals`, parallel to `elements`. In the
   compact constructor, `List.copyOf` it and throw `IllegalArgumentException` when its size does
   not match `elements`.
2. In `capture(Line line, int begin, int end)`, build `priorAccidentals` inside the **same** loop
   that builds `elements` (lines 100-110), so the range trimming for a trailing breath mark
   (`effectiveDeleteEnd`) and for an orphan paired grace note keeps the two lists aligned for
   free. For each `original` at index `i`: store `original.getAccidental()` when non-null; else
   `original.findEffectiveAccidental(line, i)` when `original.getType().isPitchedNote()`; else
   `null`. Resolve against `original`, never against `clone` — add a comment recording the trap
   above.
3. In `instantiate()`, pass `priorAccidentals` through unchanged (the clone loop maps 1:1 in
   order, so alignment is preserved for free), and call `clone.setXOffsetPx(0)` on every clone,
   with a comment giving the reason from **Context** — a fragment carries semantic content, not
   layout corrections.
4. Update the class javadoc, including the ASCII copy/paste diagram (lines 41-53), to show the
   parallel accidental list and the offset zeroing.
5. Fix every construction site of `Fragment` that the new component breaks. Locate them with
   `jet_brains_find_referencing_symbols` on `Fragment` rather than grep. Note that
   `src/test/java/songscribe/ui/clipboard/FragmentTest.java` contains **no** assertions on
   `xOffset`, so the offset zeroing breaks nothing there; only the added record component can
   force edits, and those are mechanical.
6. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm
   green.

---

## ✅ Phase 6: Call Site 3 — Paste

**Status:** Complete  <br>
**BlockedBy:** 4, 5  <br>
**Recommended model/effort:** Opus 4.8, high effort — a hard ordering constraint interacting with
the existing "nothing is mutated on LINE_FULL" contract, inside a method with several ordering
constraints already documented in place.

### Context

`ScoreViewController.tryInsertFragment(Line, int, DeletedRange)`
(`src/main/java/songscribe/ui/component/ScoreViewController.java:756`) is the single commit path
for both paste-replace (`handlePaste`, same file line 897) and paste-mode placement
(`PasteModeManager.placeAtTarget`, `src/main/java/songscribe/ui/edit/PasteModeManager.java:409`).
Paste is the n-element case of insertion, not a separate concern.

Its current order is: fit gate (`InsertionSpacingCalculator.calculateFragmentInsertion` over
`fragment.elements()`) → LINE_FULL refusal → `fragment.instantiate()` → ending confirms → span
reconciliation → delete → insert clones → trailing shift → add spans.

Phase 4 added `songscribe.layout.AccidentalReconciliation` with
`reconcile(InsertionRegion)` returning `List<Materialization>` — pure and pre-mutation. Phase 5
added `Fragment.priorAccidentals`, a list parallel to `Fragment.elements()` holding each element's
effective accidental in its source context, carried through `instantiate()` unchanged.

**Ordering is mandatory:** accidentals must be materialized *before* the projected column chain is
built, because `ElementColumnBuilder` derives extents including accidental width and `LayoutEngine`
treats accidental widths as a layout input. With that ordering, the fit gate and the committed
layout are both automatically correct and no per-position shift machinery is needed.

**Two constraints that pull against each other, and the decided resolution:**

- **C1** — on `LINE_FULL` the line must be left exactly as it was and the caller's modification
  bracket must record nothing. This is the method's documented contract ("on LINE_FULL nothing has
  been mutated: ... a caller-opened bracket stays empty, and no notification is posted"), and both
  callers rely on it — `handlePaste` keeps the selection intact, `placeAtTarget` stays in paste
  mode for another try.
- **C2** — the materialized accidentals must be visible to the fit gate's column projection.

**Resolution.** Materializations on the fragment's clones are free — the clones are detached and
carry no line back-reference until `addElement`, so setting their accidental mutates nothing
recordable. Materializations on **surviving destination notes** are the problem. Apply them with
plain `element.setAccidental(...)`, saving each note's prior accidental first, then run the fit
gate. On refusal, restore the saved priors with plain `setAccidental` and return `LINE_FULL` —
nothing recorded, line unchanged. On acceptance, restore the priors and immediately re-apply each
through `line.modifyElement(index, EnumSet.of(ElementField.ACCIDENTAL), () -> note.setAccidental(a))`
so undo records them.

The four facts this rests on are confirmed in the code — do not re-derive them, and do not weaken
the sequence without re-checking them:

- `StaffElement.setAccidental` (`src/main/java/songscribe/dom/StaffElement.java:483`) is a plain
  field setter — mutation recording happens only through `Line.modifyElement` /
  `Line.applyChange`. Its one side effect is clearing `isAccidentalInParentheses` when the
  accidental becomes null, which cannot lose information here: a materialization is always
  non-null, and restoring a null prior means the flag was already false.
- `Line.modifyElement` (`src/main/java/songscribe/dom/Line.java:342-361`) captures `beforeClone`,
  runs the mutator, then captures `afterClone`. So restore-then-`modifyElement` produces exactly
  the right before/after pair.
- `ElementField.ACCIDENTAL` is **not** in `ElementField.DURATION_AFFECTING` (which holds only
  `DOT_COUNT`, `src/main/java/songscribe/message/mutation/ElementField.java:85`), so
  `modifyElement`'s `removeOverlappingTuplets` call does not fire.
- `UndoController` replays a step's mutations in **reverse** order on undo
  (`src/main/java/songscribe/undo/UndoController.java:242`) and forward on redo (line 277). That
  is why the materializations must be recorded **before** `deleteElementRange` in this method:
  undo then processes them last, after the deletion has been undone and the surviving notes are
  back at their pre-delete indices, which is where `modifyElement` recorded them.

**When the materialized accidentals no longer fit: warn and reject** through the existing
`LINE_FULL` path and the existing `Strings.ERROR_LINE_FULL_PASTE` string. Fit is already
unpredictable to a user because of compress-to-fit, so this adds no new class of surprise, and the
existing strings cover it unchanged. Do not add a string.

### Tasks

1. Read `ScoreViewController.tryInsertFragment` in full
   (`src/main/java/songscribe/ui/component/ScoreViewController.java:756-895`), including the
   in-place comments about the hard ordering constraint around `addPastedRangeElement` and about
   why clones are instantiated before any mutation. Nothing in this phase may weaken those.
2. Move `fragment.instantiate()` above the fit gate. It touches nothing (the clones carry no line
   back-reference until `addElement`), and the gate must measure the clones that will actually be
   inserted. Pass `instantiated.elements()` — not `fragment.elements()` — to
   `InsertionSpacingCalculator.calculateFragmentInsertion`.
3. Between `instantiate()` and the fit gate, call
   `AccidentalReconciliation.reconcile(new AccidentalReconciliation.InsertionRegion(line,
   insertIndex, deleteRange, instantiated.elements(), instantiated.priorAccidentals(),
   instantiated.spans()))`. Pass the **`spacingInsertIndex`/`spacingDeleteRange` pair**, not the
   caller's raw `insertIndex`/`deleteRange`. That pair (built at `ScoreViewController.java:772-776`)
   widens the range by one when `line.isHostOfPairedGraceNote(deleteRange.begin())`, because
   `deleteElementRange` also removes the paired grace note immediately before the range. The
   reconciliation needs the same widening for the same reason spacing does — that grace note does
   not survive, so an explicit accidental on it is removed content and changes the context arriving
   at the boundary. Add a comment saying the two share one reason.
4. Apply the returned materializations per the **Resolution** above: clones directly; surviving
   destination notes with plain `setAccidental` after saving their priors. Then run the fit gate.
5. On `LINE_FULL`, restore the saved priors before showing `Strings.ERROR_LINE_FULL_PASTE` and
   returning, so C1 holds. Add a comment naming C1.
6. On acceptance, restore the priors and re-apply each surviving-note materialization through
   `line.modifyElement(index, EnumSet.of(ElementField.ACCIDENTAL), ...)`, placed **before** the
   `deleteElementRange` call so the recorded indices are pre-deletion — which is what the
   reverse-order undo replay needs, per the fourth confirmed fact above. Add a comment naming both
   that ordering and the mandatory materialize-before-the-column-chain ordering.
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm
   green.

---

## ✅ Phase 7: Call Sites 1 and 2 — Single Insert and Delete

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Recommended model/effort:** Opus 4.8, high effort — two distinct commit paths, one of which
(delete) has two branches, plus a fit-gate argument that has to be stated correctly rather than
assumed.

### Context

Phase 4 added `songscribe.layout.AccidentalReconciliation.reconcile(InsertionRegion)` — pure,
pre-mutation, returning `List<Materialization>` (a note plus the accidental to give it). Phase 6
wired it into the paste path and established the apply/gate/restore sequence described there.
Single insert and single/range delete are the same call-site shape as paste, with `inserted` of
size 1 and size 0 respectively.

**Call site 1 — single insert.** Two entry points, both in
`src/main/java/songscribe/ui/component/score/PreviewElementManager.java`:
`insertElement(LineComponent, int xIndex, Line)` (line 1773) and `addPreviewElement(LineComponent,
Line)` (line 1723, the append-at-end case). Both go through
`calculateInsertionOrShowError(...)` (line 1697), which calls
`InsertionSpacingCalculator.calculateInsertion` and shows `Strings.ERROR_LINE_FULL_ELEMENT` on
refusal.

**The inserted note itself is never materialized.** It is a note the user is creating, so it has no
pitch it "had" and the invariant ("every note keeps the pitch it had, unless the user changed that
note") does not reach it. Pass an **empty** `insertedPriorAccidentals` list, which is the encoding
Phase 4 defined for "no source context — never materialize these elements themselves".

`StaffElement` used to carry `getPreviewElementPitch(Line)` and `getPreviewElementAccidental(Line)`,
which looked like a source of a "prior" value for the inserted note. Both were dead and have
**already been deleted** on this branch; do not reintroduce them. Using them would have been wrong
on the facts: the note the user hears on insertion is played by
`EditModeManager.previewElementDidChange`
(`src/main/java/songscribe/ui/edit/EditModeManager.java:337-362`) at line 360, as
`line.getElement(elementIndex).getPitch()`, *after* `line.addElement(...)` has run. That is the
committed element resolved through the real backward scan, so what sounds already matches what the
note is. There is no preview/commit mismatch to correct, and materializing against a "preview
pitch" would change an inserted note from the accidental it correctly inherits to an explicit
natural — audibly wrong and contrary to notation convention.

**What call site 1 actually reconciles** is the other direction: when the inserted element carries
an explicit accidental (the accidental button was active, so the element arrives with one set), it
changes the context arriving at following notes at the same staff position, and those need
materializing. That is what "including a preview-carried accidental" means in the source plan's
call-site table. An inserted element with no explicit accidental changes nothing for anyone and
yields an empty result.

**Call site 2 — single / range delete.** `ScoreViewController.deleteElementRange(Line, int, int,
@Nullable String)` (`src/main/java/songscribe/ui/component/ScoreViewController.java:655`) is the
single range path, with two branches: a per-element fallback via `deleteSelection` → `deleteNote`
when `line.isHostOfPairedGraceNote(begin)`, and a contiguous batch `line.removeRange(begin,
rangeEnd)` otherwise. Its callers are cut (line 518) and delete (line 590); paste-replace also
routes through it (line 836) but Phase 6 already reconciled that mutation as a whole, so
`tryInsertFragment`'s call must **not** reconcile again. `deleteElementRange` is already private
with a 4-argument signature, so add a **private 5-argument overload** taking a
`boolean reconcileAccidentals` and have the existing 4-argument form delegate with `true`;
`tryInsertFragment` calls the overload with `false`. An added parameter on the existing signature
would force every caller to state a flag none of them care about.

**Where reconciliation goes, decided.** Reconcile exactly once, in `deleteElementRange`, covering
both of its branches. `deleteNote` (line 981) must **never** reconcile: it is the per-element
worker the paired-grace branch loops over via `deleteSelection`, and it also recurses into itself
(line 1051) to cascade-delete a trailing breath mark. A breath mark is not a pitched note and
carries no accidental, so that cascade can never change accidental context; reconciling inside
`deleteNote` would instead re-reconcile, per element, a range `deleteElementRange` already covered
as a whole.

The one wrinkle is the paired-grace branch's extra element: when
`line.isHostOfPairedGraceNote(begin)` is true, `deleteNote` also removes the grace note at
`begin - 1`, so the reconciled range must start at `begin - 1` — otherwise an explicit accidental
on that grace note is missed. This is the same compensation `tryInsertFragment` already performs
for spacing at `ScoreViewController.java:772-776`, for the same reason: the grace note does not
survive.

**Why delete needs no fit gate.** Deletion is not fit-gated today and must not become so. A
materialization can only arise from a staff position carrying an explicit accidental in the
removed content, and each such position yields at most one materialization (only the first
following note lacking its own accidental needs fixing). So removing `k` accidental-carrying notes
frees `k` noteheads plus `k` accidental glyphs and adds back at most `k` accidental glyphs — the
line can never get wider. Record that argument as a comment; do not add a gate.

### Tasks

1. In `PreviewElementManager.insertElement` (line 1773), before
   `calculateInsertionOrShowError`, call `AccidentalReconciliation.reconcile` with an
   `InsertionRegion` of `line`, `insertIndex = xIndex`, `deleteRange = null`,
   `inserted = List.of(previewElement)`, `insertedPriorAccidentals = List.of()` (empty — the
   inserted note has no source context and is never materialized against itself), and
   `insertedSpans = List.of()`. Skip the call entirely when
   `previewElement.getAccidental() == null`, since an element carrying no explicit accidental
   changes no following note's context; add a comment saying so.
2. Apply the materializations with the same sequence Phase 6 established: the inserted element
   directly (it is not yet in the line); surviving notes with plain `setAccidental` after saving
   priors, then the fit gate, then restore-and-re-apply through
   `line.modifyElement(index, EnumSet.of(ElementField.ACCIDENTAL), ...)` on acceptance, or restore
   and return on refusal so nothing is mutated when `Strings.ERROR_LINE_FULL_ELEMENT` is shown.
   **Factor that sequence out** — this phase is its third and fourth use, and it is the one place
   the "nothing is mutated on refusal" contract is enforced. Put it in a **new** class
   `songscribe.layout.AccidentalMaterializer`
   (`src/main/java/songscribe/layout/AccidentalMaterializer.java`), as a static method taking the
   materialization list and a `BooleanSupplier` fit gate and returning whether it committed. Do
   **not** put it on `AccidentalReconciliation`, whose stated contract is that it is pure and
   mutates nothing. Migrate Phase 6's `tryInsertFragment` onto it in this phase as well, so the
   contract has exactly one implementation. The delete path passes a supplier returning `true`,
   since deletion has no fit gate.
3. Do the same in `addPreviewElement` (line 1723) for the append-at-end case, with `insertIndex =
   line.elementCount()`.
4. Nothing to do for the preview helpers — `StaffElement.getPreviewElementPitch` and
   `getPreviewElementAccidental` were already deleted as dead code before this plan began. Confirm
   they are still absent and do not reintroduce either; see **Context** for why they are the wrong
   basis for an inserted note's prior accidental.
5. In `ScoreViewController.deleteElementRange` (line 655), reconcile once, before either branch
   mutates anything: `insertIndex` and `deleteRange.begin()` are
   `line.isHostOfPairedGraceNote(begin) ? begin - 1 : begin`, `deleteRange.end()` is
   `line.effectiveDeleteEnd(end)`, and `inserted`, `insertedSpans` and `insertedPriorAccidentals`
   are all empty. Apply the materializations through `line.modifyElement(index,
   EnumSet.of(ElementField.ACCIDENTAL), ...)` inside the same modification bracket the method
   already opens via the private `withModification(line, label, body)` helper (line 706), recording
   them **before** the removal so the reverse-order undo replay
   (`src/main/java/songscribe/undo/UndoController.java:242`) restores them after the elements are
   back. Add the no-fit-gate argument from **Context** as a comment.
6. Add the reconciliation-skip parameter (or private overload) to `deleteElementRange` and use it
   from `ScoreViewController.tryInsertFragment` (line 836), so a paste-replace reconciles exactly
   once. Leave `deleteNote` (line 981) and its self-recursion (line 1051) with no reconciliation at
   all, per **Where reconciliation goes, decided**; add a one-line comment on `deleteNote` saying
   its caller owns the reconciliation.
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm
   green.

---

## ✅ Phase 8: Modification Fit Gate + Call Site 4

**Status:** Complete  <br>
**BlockedBy:** 7  <br>
**Recommended model/effort:** Opus 4.8, high effort — a new projection API in the spacing
calculator plus a gate on the one un-gated mutation path, with the projected post-change element
list built from clones.

### Context

**The gap.** `SelectionCoordinator.applyActionToSelection`
(`src/main/java/songscribe/ui/selection/SelectionCoordinator.java`) is the single un-gated path for
**every** in-place element modification. The routed actions that change horizontal extent are
`AccidentalAction` (`src/main/java/songscribe/ui/action/AccidentalAction.java`),
`AccidentalInParensAction`, `DotAction`, and `UIAction.ElementReplaceable` duration swaps. Fermata
and dynamics stack independently of the note column and do not.

An infeasible line is not refused at mutation time — it surfaces later as `LINE_TOO_FULL_ERROR` and
a **null** `LayoutResult`, so the line does not render at all. `calculateInsertion` already carries
a comment naming exactly this failure as the reason the insertion gate exists; the modification
path never got one.

**The fix.** Add `calculateModification` beside `calculateInsertion` and
`calculateFragmentInsertion` in `src/main/java/songscribe/layout/InsertionSpacingCalculator.java` —
the same projection with a column **replaced** rather than spliced — and gate
`applyActionToSelection` with it, refusing with the existing `Strings.ERROR_LINE_FULL_ELEMENT`
(`error.line.full.element`). Do not add a string.

Because a modification never changes the element count, indices are preserved 1:1, which makes the
projection simpler than `calculateFragmentInsertion`'s: take the full post-change element list
(detached stand-ins at the changed positions, live elements everywhere else), build one column per
position via the same path `calculateFragmentInsertion` uses, append the terminal barline with
`appendTerminalIfPresent`, build springs with `HorizontalSpacingCalculator.buildSprings` +
`LyricLift.applyLyricLift`, anchor with `HorizontalSpacingCalculator.calculateAnchorXSs`, and
return a result exposing `fitsWithinLine(double)`. Lyric context comes from the same index on the
real line while the extents come from the projected element — mirror how
`buildSurroundingColumn(element, line, i, columnBuilder)` is already called.

**Call site 4 — the accidental toggle — adds *and* removes.** Removing an explicit accidental is
exactly as context-changing as adding one: a later note at the same staff position that inherited
it silently changes pitch. Phase 4 added
`AccidentalReconciliation.reconcileModification(Line, List<IntendedChange>)` for precisely this
shape — `IntendedChange(index, accidental, staffPosition)` describes each touched note's post-change
state, and the touched notes are excluded from the result (the user changed those deliberately).

The materialized accidentals must be part of the projected element list the gate measures, for the
same reason as every other call site: accidental width is a layout input.

**The ordering problem with the ending confirms, and its resolution.** As written today,
`applyActionToSelection` opens `song.withModification(...)` and then, *inside* that bracket and
*inside* the per-element loop, resolves `LineEndingSupport.findEndingReplacementEffect` and shows
`EndingConfirms.confirmInvalidation` / `confirmCompensateEnd` / `confirmCompensateSplit`. Declining
any of them `continue`s, so that element does **not** change. A gate built before the loop
therefore cannot know which elements actually change, and would over-refuse — refusing the whole
action, dialogs never shown, in cases the user could legitimately perform.

Restructure into three passes. This is the decided shape; do not gate the loop as it stands.

1. **Decide** (outside any bracket, mutating nothing): for each index the action `appliesTo`,
   compute the post-change stand-in and, for an `ElementReplaceable`, resolve the ending effect and
   show its confirm. Record per index: the stand-in, whether it proceeds, and which
   `Ending.EndingEffect` compensation to apply later. Both action interfaces are safe to run
   against a detached clone — `ElementModifiable.applyToElement(StaffElement, boolean)` and
   `ElementReplaceable.createReplacement(StaffElement, boolean)`
   (`src/main/java/songscribe/ui/action/UIAction.java:190-219`) take the element as a parameter and
   touch nothing else; `AccidentalAction.applyToElement` is a bare `element.setAccidental(...)`.
2. **Reconcile and gate** (still mutating nothing), over only the indices that proceed.
3. **Apply**, inside `song.withModification(...)`: the recorded compensations
   (`EndingConfirms.applyCompensatingEndChange` / `applyCompensatingSplitChange` mutate the line, so
   they belong here, not in pass 1), then `line.setElement` / `line.modifyElement` per index, then
   the materializations, then the existing `validateSpans` and cache invalidation.

Moving the confirms out of the bracket is an improvement in its own right — a dialog should not be
open while a modification bracket is.

### Tasks

1. Read `InsertionSpacingCalculator.calculateInsertion` (line 252) and
   `calculateFragmentInsertion` (line ~420) in full, plus `projectLine`,
   `buildSurroundingColumn`, `appendTerminalIfPresent` and `createLightweightColumn`, so
   `calculateModification` mirrors their structure rather than inventing one.
2. Add `public static ModificationResult calculateModification(Line line, List<StaffElement>
   projectedElements, @Nullable LayoutResult layout, @Nullable LyricRenderMetrics
   lyricRenderMetrics)` to `InsertionSpacingCalculator`, where `projectedElements.size()` must
   equal `line.effectiveElementCount()` (throw `IllegalArgumentException` otherwise). Return a
   record exposing `fitsWithinLine(double lineWidthSs)` computed from the same fit springs,
   anchor X and trailing reservation the other two methods use. Javadoc it as the replace-a-column
   analogue, and name the `LINE_TOO_FULL_ERROR` / null-`LayoutResult` failure it exists to prevent.
3. Restructure `SelectionCoordinator.applyActionToSelection` into the three passes described under
   **The ordering problem with the ending confirms**. Pass 1 runs the existing per-element logic
   with the mutating calls removed: `createReplacement(element, true)` on an `ElementReplaceable`,
   or `element.clone()` + `applyToElement(clone, selected)` on an `ElementModifiable`, plus
   `LineEndingSupport.findEndingReplacementEffect` and its confirm. It records, per index, the
   stand-in, whether the index proceeds, and any `Ending.EndingEffect` compensation to apply in
   pass 3. It mutates nothing and opens no bracket.
4. Build the projected element list from pass 1: `line.effectiveElementCount()` entries, the
   stand-in at every proceeding index and the live element everywhere else. Run
   `AccidentalReconciliation.reconcileModification(line, changes)` over the proceeding indices,
   each `IntendedChange` carrying that index, its post-change accidental and its unchanged staff
   position. Replace each materialized note's entry in the projected list with a clone carrying
   the materialized accidental (the live element must not be touched before the gate runs), so the
   gate measures the accidental widths.
5. Gate on `calculateModification(...).fitsWithinLine(line.getSong().getLineWidthSs())`. On
   refusal, show `Strings.ALERT_TITLE_INSERT_ERROR` / `Strings.ERROR_LINE_FULL_ELEMENT` via
   `OptionDialogs.showErrorMessage` (mirroring
   `PreviewElementManager.calculateInsertionOrShowError`) and return **without opening the
   modification bracket**, so nothing is mutated and no undo step is created.
6. Gate only the extent-changing actions: `AccidentalAction`, `AccidentalInParensAction`,
   `DotAction`, and `ElementReplaceable` duration swaps. Leave fermata and dynamics ungated, with
   a comment saying they stack independently of the note column. On acceptance, apply the
   materializations inside the existing bracket through `line.modifyElement(index,
   EnumSet.of(ElementField.ACCIDENTAL), ...)` so the toggle and its reconciliation are one undo
   step.
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm
   green.

---

## ✅ Phase 9: Call Site 5 — Pitch Shift

**Status:** Complete  <br>
**BlockedBy:** 8  <br>
**Recommended model/effort:** Opus 4.8, high effort — the drag mutates live on every mouse step, so
the reconciliation has a different lifecycle here (capture once at drag start, reconcile once at
finalize) than at every other call site.

### Context

Pitch shift has two entry points that share one implementation:

- **arrow keys** — `PitchShifter.shiftPitch(Line, int begin, int end, int deltaSp)`
  (`src/main/java/songscribe/ui/component/score/PitchShifter.java:84`), which runs
  `moveGroupAndPlayAnchor` once then `commitPitchShift`;
- **mouse drag** — `NoteDragHandler`
  (`src/main/java/songscribe/ui/component/score/NoteDragHandler.java`), whose `handlePress`
  (line 95) builds the drag group, whose `handleDrag` (line 177) calls
  `PitchShifter.moveGroupAndPlayAnchor` live on **every** mouse step, and whose `handleRelease`
  (line 218) calls `PitchShifter.commitPitchShift`.

`moveGroupAndPlayAnchor(Line, List<PitchShiftEntry>, int anchorIndex, int clampedDelta)`
(`PitchShifter.java:155`) is the single definition of what happens on one pitch move: it sets each
group member's staff position to `originalStaffPositionSp + clampedDelta` and re-derives the stem
direction.

**Two settled decisions to implement:**

1. **Adopt MuseScore's behaviour: clear any explicit accidental as soon as the note's staff
   position changes during a drag.** Undo already restores it through the existing mutation
   records, and this is confirmed, not assumed: `commitPitchShift`
   (`src/main/java/songscribe/ui/component/score/PitchShifter.java:271-281`) records an
   `ElementModification` carrying `entry.beforeClone()` (captured pre-mutation) and a post-move
   clone; undo restores that snapshot **whole** via `StaffElement.copyStateFrom`
   (`src/main/java/songscribe/message/mutation/ElementModification.java:28-37`), and
   `copyStateFrom` copies `accidental` (`src/main/java/songscribe/dom/StaffElement.java:179`). The
   `EnumSet<ElementField> fields` component is only a **filter hint for subscribers** — "it lets
   subscribers filter without inspecting `beforeElement` field-by-field" — not a restriction on
   what undo restores. So no widening is required for correctness. Still add `ACCIDENTAL` to
   `commitPitchShift`'s `EnumSet.of(ElementField.PITCH)`, because the field genuinely changes now
   and the set is documented as identifying which fields changed; a subscriber filtering on
   `ACCIDENTAL` would otherwise miss it. Document the clearing in `moveGroupAndPlayAnchor`'s
   javadoc as intended behaviour, not an accident.
2. That covers the moved note but **not the position it vacated**, where a later note that
   inherited the departing accidental still changes silently — structurally identical to the
   toggle-off case of Phase 8. So this path also needs
   `AccidentalReconciliation.reconcileModification(Line, List<IntendedChange>)`, in a different
   shape from every other call site: **the drag mutates live on every mouse step, so it must
   capture pre-drag state once at drag start and reconcile once at finalize.**

For the arrow-key path the lifecycle is simpler — `shiftPitch` still holds the pre-mutation state
when it builds the group, so reconcile there before `moveGroupAndPlayAnchor` runs.

`IntendedChange(int index, @Nullable Accidental accidental, int staffPosition)` describes a touched
note's post-change state; with decision 1 the accidental is always `null` and the staff position is
`originalStaffPositionSp + clampedDelta`. `reconcileModification` excludes the touched notes from
its result, which is correct — the user moved those deliberately.

No fit gate is needed: a pitch shift changes no element's horizontal extent, and clearing an
accidental only ever narrows the moved note. A materialization on a following note can widen the
line, but the same argument as the delete path applies — each vacated staff position yields at most
one materialization, and the moved note gave up its own accidental glyph. Record that as a comment.

### Tasks

1. Read `PitchShifter.java` in full (`shiftPitch`, `buildPitchShiftGroup`, `clampDelta`,
   `moveGroupAndPlayAnchor`, `commitPitchShift`, the `PitchShiftEntry` record) and
   `NoteDragHandler.handlePress` / `handleDrag` / `handleRelease`.
2. In `moveGroupAndPlayAnchor`, clear each moved note's explicit accidental
   (`note.setAccidental(null)`) alongside the staff-position set, and extend the method javadoc to
   record this as intended MuseScore-matching behaviour with undo restoring it through the
   existing mutation records.
3. Add `ElementField.ACCIDENTAL` to `commitPitchShift`'s
   `EnumSet.of(ElementField.PITCH)` (`PitchShifter.java:277`) so the recorded field set names every
   field that changed. Undo correctness does not depend on this — see decision 1 — so do not
   restructure anything else around it.
4. In `PitchShifter.shiftPitch`, call
   `AccidentalReconciliation.reconcileModification(line, changes)` **before**
   `moveGroupAndPlayAnchor` (line 102), building one `IntendedChange(entry.index(), null,
   entry.originalStaffPositionSp() + clampedDelta)` per group entry. Apply the returned
   materializations through `line.modifyElement(index, EnumSet.of(ElementField.ACCIDENTAL), ...)`
   so they land in the same undo step as the shift. Add the no-fit-gate comment from **Context**.
5. For the drag path, capture the reconciliation input once in `NoteDragHandler.handlePress` (the
   pre-drag line state is intact there, and `dragGroup` already holds each entry's
   `originalStaffPositionSp` and `beforeClone`), and run the reconciliation once in
   `handleRelease` — after `commitPitchShift`, using the group's final staff positions to build
   the `IntendedChange` list. Reconciliation must not run per mouse step. Add a comment stating
   that lifecycle and why it differs from every other call site.
6. Confirm both paths produce a single undo step covering the shift plus its materializations —
   `shiftPitch` through the bracket `commitPitchShift` already uses, the drag through
   `handleRelease`'s.
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm
   green. `src/test/java/songscribe/ui/component/score/PitchShifterTest.java` contains **no**
   assertions mentioning accidentals, so nothing there encodes the old behaviour and no existing
   test should need editing. Coverage for the clearing rule is added in Phase 11.

---

## ⏳ Phase 10: Manual UI Verification

**Status:** Pending  <br>
**BlockedBy:** 1, 9  <br>
**Recommended model/effort:** n/a — the user drives the application; no model does this work.

### Context

Every behaviour change in Phases 1–9 is user-visible. Tests for the reconciliation unit and the
fit gate are deliberately held until after this verification (Phase 11), so that tests are written
against confirmed behaviour rather than assumed behaviour.

Build these two lines once, in a song with **no key signature**, and reuse them for steps 4–7. They
are the same fixtures Phase 11 automates, taken from #676, and each is four notes:

```
Fixture A:   F   G   A   F        (all natural)
Fixture B:   F♯  G   A   F        (the last F inherits the sharp — it sounds sharp today)
```

The check in every case is the same: **play the line and confirm the last F sounds exactly as it
did before the mutation.** Before this work it does not.

`./scripts/run.sh` must **never** be executed without the user's explicit permission. Ask; do not
launch it unprompted. Useful flags: `--log-level=debug`, `--truncate-log`; `DEBUG=1` prefix for UI
debug features.

### Tasks

1. Ask the user for permission to launch the app, then run `./scripts/run.sh` and hand over.
2. Have the user verify the paste-mode lockout: Cmd+V with no selection to enter paste mode, then
   click on the staff lines in the clef/key-signature header. Expected: nothing is selected, the
   mode does not flip EDIT→SELECT, paste mode stays active, and a subsequent click on a valid
   insertion point still places the fragment. Escape still cancels.
3. Have the user verify the resolver: on a line with a sharpened note followed by a barline and
   then the same staff position with no accidental, the second note now plays *unaltered*. With a
   tie drawn from the first note across the barline to the second, it plays *sharpened* again.
4. Have the user verify paste, twice. On **Fixture A**, paste a fragment containing an explicit F♯
   so it lands at index 1: the final F must still sound natural, now with a ♮ drawn on it. On
   **Fixture B**, paste-replace over `[0..1]`: the final F must still sound sharp, now with a ♯
   drawn on it. Then the cross-key case: copy a run containing a note that inherits an accidental
   and paste it into a passage in a different key — every pasted note sounds as it did in the
   source, including a natural drawn where the source note was unaltered but the destination key
   alters that pitch. Also confirm no pasted note carries a stale horizontal nudge.
5. Have the user verify single insert and delete. Insert: a note inserted after an explicit
   accidental at the same staff position, with no accidental button active, still **inherits** it —
   no accidental is drawn on it and it sounds altered, exactly as before this work. Inserting a
   note **with** an accidental active instead materializes one on the next note at that staff
   position that had been inheriting the older accidental. Delete: deleting a note that carried an
   accidental leaves the following note at that staff position sounding unchanged, with an
   accidental now drawn on it.
6. Have the user verify the accidental toggle and the modification fit gate. On **Fixture B**,
   toggle the sharp off index 0: the final F must still sound sharp, now with a ♯ drawn on it.
   Separately, adding accidentals or dots to a selection on a nearly-full line is now **refused**
   with the "line full" error instead of leaving the line unrendered.
7. Have the user verify pitch shift. On **Fixture B**, drag index 0 away from its staff position:
   its own ♯ clears as soon as the position changes, and the final F must still sound sharp with a
   ♯ now drawn on it. One Cmd+Z restores everything — both the dragged note's accidental and the
   materialized one. Repeat with arrow-key pitch changes.
8. Record the user's verdict per scenario in this file before Phase 11 starts. Anything the user
   rejects is fixed before test-writing begins.

---

## ⏸️ Phase 11: Reconciliation and Fit-Gate Tests

**Status:** Pending  <br>
**BlockedBy:** 10  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical test authoring against
behaviour the user has already confirmed; existing test classes supply the fixture idiom.

### Context

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first (neither is
auto-loaded). Unit tests only — any e2e test requires the user's approval, so do not add one
without asking.

Classes and files under test:

- `songscribe.layout.AccidentalReconciliation` (`src/main/java/songscribe/layout/AccidentalReconciliation.java`)
  — `reconcile(InsertionRegion)` and `reconcileModification(Line, List<IntendedChange>)`, both pure
  and pre-mutation.
- `InsertionSpacingCalculator.calculateModification` — existing tests live in
  `src/test/java/songscribe/layout/InsertionSpacingCalculatorTest.java`.
- `songscribe.ui.clipboard.Fragment` — existing tests in
  `src/test/java/songscribe/ui/clipboard/FragmentTest.java`.

Every staff position, key count and expected accidental must be a named constant, not a raw
literal — the no-magic-numbers rule applies to tests, with only `0`, `1`, `-1` and `*2`/`/2`
exempt.

**Two canonical fixtures, from #676.** Build both; between them they cover every call site's
failure mode in four notes, in a single key with no key signature (`keyType` left unset, so the
key-signature fallback yields null for every pitch class).

```
Fixture A — key = none.   F(0)  G(1)  A(2)  F(3)      ← both F natural
```
Paste a fragment containing an explicit F♯ so it lands at index 1. Without reconciliation the F at
index 3 scans back, finds the pasted sharp, and sounds sharp — **one pasted note changed a
different note the user never touched.** Expected: a materialization on index 3 (a NATURAL, since
its `before` was null), leaving it sounding natural.

```
Fixture B — key = none.   F♯(0)  G(1)  A(2)  F(3)     ← index 3 inherits, sounds sharp
```
Three mutations must each leave index 3 sounding **sharp**, each via a materialization of SHARP on
it: paste-replace over `[0..1]`; toggling the sharp off index 0; and pitch-shifting index 0 away
from that staff position. Without reconciliation all three silently turn index 3 natural.

### Tasks

1. Create `src/test/java/songscribe/layout/AccidentalReconciliationTest.java`. Start with
   **Fixture A** above, then generalise: the first following note at that staff position gets a
   materialization; a later one does not; a following note that already has its own explicit
   accidental gets none.
2. Cover **Fixture B**'s three mutations — paste-replace over `[0..1]`, toggle-off, and pitch shift
   — each asserting a SHARP materialization on index 3. Add a pure delete that removes an explicit
   accidental, and a paste-replace that both removes and adds one at the same staff position.
3. Cover the two comparison rules explicitly: `null` before → `NATURAL` materialized when the
   destination key alters that pitch class (the cross-key paste case), and **no** materialization
   when `before` and `after` differ by enum identity but not by adjustment (`null` vs `NATURAL`,
   `FLAT` vs `NATURAL_FLAT`) — assert on `StaffElement.getPitchAdjustment`.
4. Cover the barrier and tie interaction inside the projected scan: a barline between the mutation
   point and the candidate note stops the effect (no materialization), and a tie crossing that
   barline reinstates it.
5. Cover `reconcileModification`: an accidental toggled **off** materializes on the first
   following note at that staff position; an accidental toggled **on** does the same; the touched
   note itself is never in the result; and a staff position vacated by a pitch shift materializes
   on the note that had inherited from it.
6. Add `calculateModification` cases to `InsertionSpacingCalculatorTest`: a line with slack accepts
   an accidental added to a selection; a nearly-full line refuses it; the element count is
   preserved and a size mismatch throws `IllegalArgumentException`. Also add a test for
   `songscribe.layout.AccidentalMaterializer`'s contract — the one that every call site depends on:
   with a fit gate that refuses, every note's accidental is exactly what it was beforehand and no
   mutation was recorded; with a gate that accepts, each materialization is applied and recorded
   once.
7. Add `FragmentTest` cases for the reshape: `capture` stores an inherited accidental resolved
   against the live original (not the key alone) for a note with no explicit accidental of its
   own; the `priorAccidentals` list stays aligned when capture trims an orphan paired grace note
   or extends past a trailing breath mark; `instantiate` carries the list through unchanged and
   zeroes every clone's `xOffset`; and a size mismatch in the constructor throws.
8. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`, and confirm green. Do not weaken an
   assertion to make a test pass.

---

## ⏸️ Phase 12: Documentation

**Status:** Pending  <br>
**BlockedBy:** 11  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — prose updates to existing docs against
finished, verified code.

### Context

`docs/clipboard.md` documents the cut/copy/paste architecture (#65). Its `## 6. Deliberately out
of scope` section does **not** list accidental-context reconciliation — it was an unrecognized
hole, not a known one — and now that the hole is closed the document must describe the mechanism
rather than omit it.

The following remain deliberately out of scope and must be recorded as such, not silently dropped:

- **#612 (cut/copy/paste entire line)** — separate downstream plan; it needs the `Fragment` reshape
  from Phase 5, and its line fragment carries offsets (the opposite of the element fragment's
  rule).
- **#53 (mid-line key changes)** — separate. The resolver now accepts it via `keyInEffectAt`;
  nothing here implements it. It also breaks the one-key-per-line assumption in
  `HorizontalSpacingCalculator.isWithinHeaderXSs`.
- **#11 (ABC import)** — separate. Listed only as a future call site of the shared reconciliation
  unit, needed in two cases: ABC's default applies an accidental to the pitch class in *all*
  octaves within the bar while SongScribe matches same-octave only, and ABC does not reset at a
  line break while SongScribe does. Both apply to default-directive files, so #11 is a
  materialization call site by default.
- **Line-reset revisited** — kept as house convention; the private traversal seam added to
  `StaffElement` in Phase 2 is where it would change.
- **The `xOffset` dual meaning** — the field is documented and exported as a nudge but serves as an
  absolute position store for the insert/delete/paste arithmetic and `HorizontalAdjustment`. Both
  cannot hold once a real nudge exists, and pasted notes plausibly export a spurious `relative-x`
  today. A prerequisite for the manual-offset feature, not for this work; its own issue.
- **Storing pitch on `StaffElement`** — rejected actively, not deferred. Revisit only if
  transposition becomes a feature, a pitch-based source becomes a primary import path, or the
  call-site set stops being finite.

### Tasks

1. Add a section to `docs/clipboard.md` describing the shared reconciliation unit
   (`songscribe.layout.AccidentalReconciliation`): the invariant, the materialization rule
   including `null` → `NATURAL` and adjustment-not-identity comparison, the two bounds, and the
   mandatory ordering (materialize before the projected column chain is built).
2. In the same document, record the `Fragment` reshape: the parallel `priorAccidentals` list, the
   capture trap (resolve against the live original — a clone's `getElementIndex` returns −1), and
   the offset-zeroing rule with its "semantic content, not layout corrections" rationale.
3. Rewrite `docs/clipboard.md` `## 6. Deliberately out of scope` to list the six items in
   **Context**, each with one sentence saying why it is out of scope and what it depends on.
4. Add a short section on the modification fit gate: `calculateModification`, which actions it
   gates and which it deliberately does not (fermata and dynamics stack independently of the note
   column), and the `LINE_TOO_FULL_ERROR` / null-`LayoutResult` failure it prevents.
5. Update `plans/accidental-context.md`'s `## Status` section to point at this plan and record
   which phases are done.
6. Run `./scripts/compile.sh` and confirm SUCCESS (no code changed, but the phase must leave the
   tree building).

---

## Verification (whole plan)

- `./scripts/compile.sh` reports SUCCESS.
- `./scripts/test.sh unit` is green, including the new
  `AccidentalReconciliationTest` and the additions to `StaffElementTest`,
  `InsertionSpacingCalculatorTest`, `FragmentTest` and `PitchShifterTest`.
- The user has signed off on every scenario in Phase 10.
- No new user-facing string was added — `Strings.ERROR_LINE_FULL_PASTE` and
  `Strings.ERROR_LINE_FULL_ELEMENT` cover both refusal paths unchanged.
- No file-format migration step, version bump or rewrite was added: `NoteAccumulator` ignores
  `<alter>` on read, so existing files pick up the corrected reading on next open.
