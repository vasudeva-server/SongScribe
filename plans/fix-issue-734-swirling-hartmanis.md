# Fix #734 — annotations dropped on REPEAT_LEFT / REPEAT_LEFT_RIGHT

## Context

Issue #734: `MusicXmlWriter.writeLineDrivenMeasures` never emits an annotation
`<direction>` for a barline element whose type is `REPEAT_LEFT` or
`REPEAT_LEFT_RIGHT`. Both types take dedicated early branches that close one
measure and open another; neither looks for an `AnnotationAttachment`. The user
types an annotation, sees it render, saves, reopens, and it is gone — silently.
Issue #713 fixed the general barline arm (`writeElementAnnotation` before
`writeBarline`) and deliberately left these two branches alone.

A **second defect in the same mechanism** is fixed here as well.
`BarlineParser` defers a `REPEAT_RIGHT` (it may turn out to be the backward half
of a straddling `REPEAT_LEFT_RIGHT` pair), so the element is not appended when
its `<barline>` is parsed. `MusicXmlReader.appendToCurrentLine` resolves the
pending annotation onto whatever it appends, and `finishNote` calls
`barlines.flushPendingRepeatRight()` **before** binding the note's own
attachments. Consequences today:

- `note, REPEAT_RIGHT, annotated-note` → the deferred `REPEAT_RIGHT` is appended
  at the next `</note>` and **steals that note's annotation**.
- annotated `REPEAT_RIGHT` followed by an annotated note → the barline's
  annotation is overwritten in `AnnotationResolver`'s single pending slot and
  **lost outright**; the note's annotation lands on the barline.

Intended outcome: an annotation on any element round-trips onto that same
element, with no special cases and no silent loss.

## Design

### Writer placement — forced, not chosen

A `<barline location="left">` must be the **first child** of its `<measure>`
(the barline-location schema rule already relied on by
`MusicXmlMeasureWriter.writeInvisibleLeftBarline`). Nothing can be emitted
between the closing barline of one measure and the forward-left barline of the
next. So for both types the annotation `<direction>` must go in the **preceding**
measure:

```
 REPEAT_LEFT branch                    REPEAT_LEFT_RIGHT branch
 ─────────────────────                 ────────────────────────────
 writeElementAnnotation        [NEW]   writeElementAnnotation    [NEW]
 writeInvisibleRightBarline            writeBackwardRepeatRightBarline
 openForwardRepeatMeasure              openForwardRepeatMeasure
        │  (closes + opens)                   │  (closes + opens)
        ▼                                     ▼
 <direction> lands in the PRECEDING    <direction> lands in the PRECEDING
 measure, ahead of the invisible       measure, ahead of the backward-right
 right barline that closes it          barline it is held with
```

For `REPEAT_LEFT` the direction stays pending across the invisible barline
(which appends nothing) and binds when the forward-left barline appends the
element. For `REPEAT_LEFT_RIGHT` the direction is parsed *before* the
backward-right barline, so it is taken into the hold and attached at the merge.

### Reader — the hold carries the annotation

The deferred `REPEAT_RIGHT` already carries its `<ending>` markers with it
(`pendingRepeatRightEndings`). Make it carry its annotation the same way:
capture the pending annotation at *hold* time, attach it at *flush/merge* time.
Annotation resolution stops being an implicit side effect of appending and
becomes each caller's explicit decision.

```
              <direction placement=…>  ──▶ AnnotationResolver.pending
                                                    │
      ┌─────────────────────┬──────────────────────┴───────────────┐
      │                     │                                       │
 <barline> = REPEAT_RIGHT   <barline> = other                  </note>
      │                     │                                       │
 appendOrHold: HOLD    appendOrHold: APPEND              finishNote
  ├ pendingRepeatRight=T     ├ append element             ├ flushPendingRepeatRight
  ├ take endings             └ resolveAnnotation  [NEW]   │   ├ append REPEAT_RIGHT
  └ take annotation  [NEW]      (was implicit inside      │   └ attachHeldRepeatRight
      │                          appendToCurrentLine)     ├ append note
      │                                                   └ resolveAnnotation(note)
 next <barline> = REPEAT_LEFT@left
      │
 MERGE: append REPEAT_LEFT_RIGHT
  ├ attachHeldRepeatRight (backward half: held endings + held annotation)
  └ attach current endings (forward half)
```

**Only the held annotation is ever attached at flush/merge time.** There is
deliberately no fallback to `resolveAnnotation`. An annotation still pending at
a flush belongs to an element that has not been appended yet — the
`REPEAT_LEFT` whose forward-left barline is still ahead — and a fallback would
steal it. This is the case reachable through clipboard paste, which can place a
`REPEAT_RIGHT` directly before a `REPEAT_LEFT`
(`EditModeManager.elementWasModified` merges the adjacent pair only on the
pen-insertion path, so the model can legitimately hold both).

---

## Phase 1 — Reader: split resolution and make the hold carry the annotation

**Files:** `AnnotationResolver.java`, `BarlineParser.java`, `MusicXmlReader.java`

1. In `AnnotationResolver`, split binding into take + attach and express the
   existing `resolveAnnotation` in terms of them, so there is no duplicated
   attach logic:
   - `@Nullable Annotation takePendingAnnotation()` — returns the pending
     annotation and clears the slot.
   - `void attachAnnotation(StaffElement element, @Nullable Annotation annotation)`
     — attaches a new `AnnotationAttachment`; no-op when `annotation` is null.
   - `void resolveAnnotation(StaffElement element)` — becomes
     `attachAnnotation(element, takePendingAnnotation())`.

2. In `MusicXmlReader`, drop `annotations.resolveAnnotation(element)` from
   `appendToCurrentLine`. Replace the Javadoc paragraph that justified it with
   an explicit statement of the new contract: **callers own annotation
   resolution.** Name the four call sites and what each does —
   `BarlineParser.appendOrHold` (append path) resolves; `appendOrHold` (hold
   path) takes into the hold; `flushPendingRepeatRight` and the
   `REPEAT_LEFT_RIGHT` merge attach the held annotation; the `BREATH_MARK`
   append in `finishNote` deliberately resolves nothing, because `finishNote`
   has already bound the annotation to the note itself.

3. In `MusicXmlReader:136`, pass `annotations` to `new BarlineParser(...)`.
   The `annotations` field at `:128` is already initialized before `barlines`,
   so no field reordering is needed.

4. In `BarlineParser`, take the `AnnotationResolver` as a third constructor
   parameter and store it in a `private final AnnotationResolver annotations`
   field. Add `@Nullable private Annotation pendingRepeatRightAnnotation = null`
   directly below `pendingRepeatRightEndings`, and extend that field's comment
   to cover both pieces of held state.

5. In `BarlineParser`, extract a private `attachHeldRepeatRight(StaffElement)`
   that releases the whole hold in one place — take held endings, take the held
   annotation, clear `pendingRepeatRight`, then attach both to `element`. Its
   Javadoc must state why only the held annotation is attached (a still-pending
   annotation belongs to an element not yet appended). Held endings must attach
   before any caller's `currentBarlineEndings`, preserving today's order.

6. Rewrite the three call sites against that helper:
   - `appendOrHold` hold path: also set
     `pendingRepeatRightAnnotation = annotations.takePendingAnnotation()`.
   - `appendOrHold` append path: add `annotations.resolveAnnotation(element)`
     after `endings.attachBarlineEndings(...)`.
   - `flushPendingRepeatRight` and the `REPEAT_LEFT_RIGHT` merge branch of
     `processBarline`: replace their inline take-and-clear blocks with
     `attachHeldRepeatRight(element)`. The merge branch keeps its trailing
     `endings.attachBarlineEndings(element, currentBarlineEndings)`.

No other flush site needs changing: `startNewLine:674` and the `PART`-end flush
at `:577` both go through `flushPendingRepeatRight`, which now attaches only its
own held annotation — so `annotations.flushPendingAnnotation()` at `:583`
correctly warns-and-drops a genuinely unbound trailing annotation.

**Verify:** `./scripts/compile.sh` prints SUCCESS.

---

## Phase 2 — Writer and reader documentation

**Files:** `MusicXmlWriter.java`, `docs/musicxml-reader.md`

1. `REPEAT_LEFT` branch of `writeLineDrivenMeasures`: call
   `writeElementAnnotation(pw, element)` before
   `MusicXmlMeasureWriter.writeInvisibleRightBarline(pw)`.

2. `REPEAT_LEFT_RIGHT` branch: call `writeElementAnnotation(pw, element)` before
   `MusicXmlMeasureWriter.writeBackwardRepeatRightBarline(...)`.

3. Delete the stale comment in the general barline arm stating that these two
   branches drop annotations and pointing at #734. In its place, add a one-line
   note in each of the two branches explaining that the direction precedes the
   closing barline because a forward-left barline must be its measure's first
   child. `writeElementAnnotation` is reused as-is — no new helper.

4. Add a fourth section to `docs/musicxml-reader.md`, after the `<note>` subtree
   section, titled **"Pending annotation and the deferred `REPEAT_RIGHT` hold"**.
   Reproduce the reader state diagram from the Design section above, and state
   the two invariants it encodes: `AnnotationResolver` has exactly one pending
   slot, and flush/merge attach only the held annotation, never the pending one.

**Verify:** `./scripts/compile.sh` prints SUCCESS.

---

## Phase 3 — Tests: the annotated element keeps its annotation

**File:** `src/test/java/songscribe/io/musicxml/MusicXmlAnnotationRoundTripTest.java`

Reuse `buildSong` / `roundTrip` / `writeToString` from `MusicXmlRoundTripSupport`
and the file's existing `AnnotationCase`, `attachAnnotation`,
`assertAnnotationEquals` helpers. Name every element index as a constant,
following `ORDINARY_BARLINE_INDEX`. Each test's assertion must check both that
the element at the index still has the expected `ElementType` and that it
carries the expected annotation.

1. **`testAnnotationOnRepeatLeftRoundTrips`** — `note, REPEAT_LEFT(annotated),
   note`. Fails today: the annotation is dropped.

2. **`testAnnotationOnRepeatLeftRightRoundTrips`** — same shape with
   `REPEAT_LEFT_RIGHT`. Fails today.

3. **`testAnnotationOnRepeatLeftAtLineStartRoundTrips`** — a two-line song via
   `buildSong(lineOne, lineTwo)` where line 2's **first** element is an
   annotated `REPEAT_LEFT`. This is the case where the pending annotation must
   survive `startNewLine`, which calls `flushPendingRepeatRight` before the new
   line becomes current. Assert the annotation lands on line 2's first element,
   not on anything in line 1.

4. **`testRepeatAnnotationDirectionsValidateAgainstSchema`** — a song containing
   annotated `REPEAT_LEFT` and `REPEAT_LEFT_RIGHT` elements validated with
   `MusicXmlSchemaValidator`, mirroring
   `testAnnotationDirectionsValidateAgainstSchema`.

**Verify:** `./scripts/compile.sh`, then
`./scripts/test.sh MusicXmlAnnotationRoundTripTest`.

---

## Phase 4 — Tests: no element steals another element's annotation

**File:** `src/test/java/songscribe/io/musicxml/MusicXmlAnnotationRoundTripTest.java`

Same helpers and index-constant convention as Phase 3. Tests 3 and 4 below build
a `REPEAT_RIGHT` immediately followed by a forward repeat — a shape the model
reaches through clipboard paste, since the adjacent-pair merge in
`EditModeManager.elementWasModified` runs only on the pen-insertion path.

1. **`testAnnotationOnNoteAfterRepeatRightStaysOnTheNote`** — `note,
   REPEAT_RIGHT, annotated-note`. Assert the note holds the annotation **and**
   the `REPEAT_RIGHT` holds none. Fails today: the barline steals it.

2. **`testAnnotatedRepeatRightAndAnnotatedNextNoteBothSurvive`** — distinct texts
   on the `REPEAT_RIGHT` and on the following note; assert each lands on its own
   element. Fails today: one is lost.

3. **`testStrayRepeatRightBeforeAnnotatedRepeatLeftDoesNotStealIt`** — `note,
   REPEAT_RIGHT, REPEAT_LEFT(annotated), note`. Assert the annotation is on the
   `REPEAT_LEFT` and the `REPEAT_RIGHT` carries none.

4. **`testStrayRepeatRightBeforeAnnotatedRepeatLeftRightDoesNotStealIt`** — same
   shape with `REPEAT_LEFT_RIGHT` in place of `REPEAT_LEFT`.

5. **`testAnnotatedRepeatLeftRightKeepsBothHalvesEndingMarkers`** — an annotated
   `REPEAT_LEFT_RIGHT` that also carries volta markers on both halves (follow
   the fixture style in `MusicXmlEndingRoundTripTest`). Assert the annotation
   and both ending spans survive together, proving `attachHeldRepeatRight`
   releases every piece of held state.

**Verify:** `./scripts/compile.sh`, then
`./scripts/test.sh MusicXmlAnnotationRoundTripTest`.

---

## Verification

1. `./scripts/compile.sh` — must print SUCCESS.
2. `./scripts/test.sh MusicXmlAnnotationRoundTripTest` — all nine new tests pass,
   and the pre-existing `testAnnotationOnRepeatRightTerminalRoundTrips` still
   passes. That existing test is the canary for the hold change: it is the only
   one that exercises flush-at-line-end with an annotation.
3. `./scripts/test.sh MusicXmlBarlineRoundTripTest MusicXmlEndingRoundTripTest MusicXmlCorpusLosslessnessTest`
   — the deferred-`REPEAT_RIGHT` and ending-marker paths are the ones being
   touched, so these are the regression risk.
4. `./scripts/test.sh` — full unit suite.
5. Optional manual check (needs the user's permission to run the app): create a
   song with a `|:` repeat, attach an annotation to it, save, reopen, confirm the
   annotation is still on the repeat barline; repeat for `:||:` and for the first
   note after a `:|`.
