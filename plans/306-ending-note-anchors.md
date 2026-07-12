# Plan: Note-anchored ending brackets (issue #306)

Remove the requirement that a volta (1st/2nd) ending begin and end on a barline/repeat.
Each **outer** edge of the bracket anchors independently: to a barline/repeat when one is
present (current behavior), otherwise directly to the boundary note's column.

**Locked scope decisions:**
- The middle `REPEAT_RIGHT`/`REPEAT_LEFT_RIGHT` split stays **required** — an ending is still a
  1st+2nd pair.
- The enclosing-repeat precondition (`hasEnclosingRepeat`, Stage 3) stays **required**.
- Only the two **outer** boundaries relax: the 1st bracket's left start and the 2nd bracket's
  right end may be notes.
- The auto-inserted barline on creation is **removed** — the whole point of #306.
- A `REPEAT_LEFT_RIGHT` **immediately preceding** the ending start anchors the 1st bracket to
  that barline (treated like `REPEAT_LEFT` → `EXTEND_SPAN`). This is a new-but-required allowance:
  `hasEnclosingRepeat` (Stage 3) returns `true` for a preceding `REPEAT_LEFT_RIGHT`, so it is *not*
  filtered upstream and must be handled explicitly rather than left to fall into the `invalid()` tail.
- MusicXML import/export is **out of scope** (volta is always barline-bound in that format);
  Phase 4 only confirms the existing round-trip still passes.

**Note-edge geometry (agreed):** column X is the glyph origin; `getLeftExtentSs()` is ≤ 0
(accidental-inclusive), `getRightExtentSs()` is ≥ head width (augmentation-dot-inclusive).
- **Left edge, note start:** `x1 = col.getXSs() + col.getLeftExtentSs() - NoteGeometry.ACCIDENTAL_PADDING_SS`.
  Left arm still draws normally.
- **Right edge, note end:** `x2 = col.getXSs() + col.getRightExtentSs() + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS`,
  `hasClosingStroke = false` (no trailing arm).

**Decision folded in:** the outer end may be a note for **both** split types. Neither the
`REPEAT_RIGHT`-split terminal requirement nor the `REPEAT_LEFT_RIGHT`-split repeat-end requirement
survives — the whole end-type gate relaxes so a content end is allowed regardless of split type.

**Flagged uncertainties (verify by eye during implementation, do not block):**
1. Bare notehead (no accidental) left edge: `leftExtentSs == 0`, so `x1 = X - ACCIDENTAL_PADDING_SS`.
   Confirm the uniform gap looks right; adjust the bare-head case if not.
2. Right-edge padding: start at `AUGMENTATION_DOT_WIDTH_SS`; the user will tune by eye.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Creation validation relaxation](#-phase-1-creation-validation-relaxation) | ✅ Complete | — |
| 2 | [Edit-time invalidation for note boundaries](#-phase-2-edit-time-invalidation-for-note-boundaries) | ✅ Complete | — |
| 3 | [Note-anchored bracket geometry](#-phase-3-note-anchored-bracket-geometry) | ✅ Complete | — |
| 4 | [Tests & manual verification](#-phase-4-tests--manual-verification) | ✅ Complete | — |

## ✅ Phase 1: Creation validation relaxation

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high effort — relaxes interacting validation stages and removes an auto-mutation path; reasoning about which boundaries may now be notes vs. which structural rules stay is the crux of #306.

Files: `ui/MusicEditOperations.java`, `dom/EndingValidationResult.java`.

```
checkPrecedingElement — element immediately before selection start
  ├─ content, begin NOT a barline ──► NONE @ begin        ★ #306 change (was INSERT_BARLINE)
  ├─ content, begin IS barline/L-rep ► NONE @ begin        (unchanged)
  ├─ SINGLE_BARLINE / REPEAT_LEFT ───► EXTEND_SPAN          (unchanged)
  ├─ REPEAT_LEFT_RIGHT ──────────────► EXTEND_SPAN          ★ added (Stage 3 passes it — must handle)
  └─ REPEAT_RIGHT / DOUBLE / FINAL ──► invalid()            (unchanged; Stage 3 also rejects)
```

### Tasks
1. In `EndingValidationResult`, remove `PrecedingAction.INSERT_BARLINE` from the enum. Leave `NONE` and `EXTEND_SPAN`. Production references: the producer `checkPrecedingElement` and the `makeFirstSecondEnding` switch. **Test references that will break compilation and must be fixed in Phase 4** (the plan's earlier "only consumer is `makeFirstSecondEnding`" was wrong): `ScoreViewControllerCommandHandlerTest` (an `INSERT_BARLINE` case), `MusicEditOperationsMutationTest` (two `INSERT_BARLINE` cases), and `EndingValidationResultTest` (its `@EnumSource(PrecedingAction.class)` now enumerates two constants, not three). Phase 1's `compile.sh` cannot pass until Phase 4's test edits land, so Phase 1 and Phase 4 Task 1 are effectively one compile unit — do not treat Phase 1's SUCCESS gate as reachable without them.
2. Rework `MusicEditOperations.checkPrecedingElement` (lines ~501-559): never insert a barline. Only the content-predecessor branch actually changes; keep the diff minimal.
   - **Content predecessor** (the sole #306 behavior change): where the code today returns `INSERT_BARLINE` (content predecessor, selection begin is not itself a barline/left-repeat), return `NONE` anchoring at `selectionBegin` (note anchor). The existing "content predecessor, begin already a barline/left-repeat" branch keeps returning `NONE`.
   - **Barline / left-repeat predecessor** → `EXTEND_SPAN`. **Add `REPEAT_LEFT_RIGHT` to this set** alongside `SINGLE_BARLINE`/`REPEAT_LEFT`: a `REPEAT_LEFT_RIGHT` immediately before the ending acts as the enclosing left-repeat and must anchor the 1st bracket to that barline.
   - **Leave the `invalid()` tail unchanged** for a preceding `REPEAT_RIGHT` / `DOUBLE_BARLINE` / `FINAL_DOUBLE_BARLINE`. *Verified:* `hasEnclosingRepeat` returns `false` for exactly these three, so creation is already rejected upstream — but do **not** blanket-convert the tail to `NONE`, because `hasEnclosingRepeat` returns `true` for `REPEAT_LEFT_RIGHT` and that case is now caught by the `EXTEND_SPAN` branch above, not the tail.
3. Relax `MusicEditOperations.validateEndingStructure` (lines ~377-389): remove the entire end-type gate — both the `REPEAT_LEFT_RIGHT`-split branch (requiring a right-repeat end) and the `else if (!endType.isTerminal())` branch — so the outer end may be a content element for either split type. `validateEndingRegionContent` already guarantees the second region has content and no interior barline/repeat, so no end-type check is needed. Leave `validateEndingRegionContent` unchanged.
4. Rework `MusicEditOperations.makeFirstSecondEnding` (lines ~561-597): delete the `INSERT_BARLINE` switch case (and its start/end++ shift). `EXTEND_SPAN` and `NONE` keep anchoring at `result.getSpanStart()`/`getSpanEnd()` with no element insertion.
5. Run `./scripts/compile.sh` — must report SUCCESS.

## ✅ Phase 2: Edit-time invalidation for note boundaries

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high effort — the anchor/end replacement matrix (`checkReplacement`) has branching CompensateEnd/CompensateSplit/Invalidate outcomes that must stay correct while now admitting content-element boundaries.

Files: `layout/Ending.java` (`checkReplacement`, `isInvalidatedByInsertion`, `isInvalidatedByDeletion`).

### Tasks
1. `checkReplacement` Condition 1 — anchor replaced (lines ~471-476): today invalidates unless the new type is `SINGLE_BARLINE`/`REPEAT_LEFT`. Relax to `None` when `newType.isContentElement() || newType.isBarLine() || newType.isRepeat()`, invalidating only otherwise (e.g. clef/key sig). Because `isRepeat()` is true for `REPEAT_LEFT_RIGHT`, this automatically admits it as a valid anchor — required so an `EXTEND_SPAN`-anchored ending on a `REPEAT_LEFT_RIGHT` predecessor (Phase 1) does not self-invalidate the moment that barline is edited.
2. `checkReplacement` Condition 3 — end replaced (lines ~505-522): allow a content-element end for **either** split type. If `newType.isContentElement()`, return `None` (a note end needs no split compensation, including when the split is `REPEAT_LEFT_RIGHT`). Keep the existing barline/repeat compensation logic (`CompensateSplit`, split-type checks) only for the cases where the new end is itself a barline/repeat. Only invalidate when `newType` is a non-content, non-barline, non-repeat type.
3. Re-verify `isInvalidatedByInsertion` (interior barline/repeat rule) and `isInvalidatedByDeletion` (split + sub-span content deletion) are still correct with note boundaries — these govern interior structure, not the outer edges, so expect no logic change; confirm the anchor/end-note deletion path is still handled by `RangeElement.isInvalidatedBy` (base class) and add no duplicate check.
4. Run `./scripts/compile.sh` — must report SUCCESS.

## ✅ Phase 3: Note-anchored bracket geometry

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, medium effort — mostly mechanical signature threading, but the extent-sign semantics (`leftExtentSs ≤ 0`, `rightExtentSs` from glyph origin) must be applied exactly; shares `Ending.java` with Phase 2 so it runs after.

Files: `layout/Ending.java` (`computeBracketRanges`), `layout/stacking/StructuralStacker.java` (`stackEndings`).

### Tasks
1. Change `Ending.computeBracketRanges`'s second parameter from `ToDoubleFunction<? super StaffElement> elementXSs` to `Function<? super StaffElement, ElementColumn> columnFn`, so it can read X **and** extents. Derive X via `columnFn.apply(e).getXSs()` wherever the code currently calls `elementXSs.applyAsDouble(e)`. Update the method javadoc.
2. In `StructuralStacker.stackEndings` (lines ~178-189), pass `columnsByElement::get` (with the existing null-guard throwing `IllegalStateException`) instead of the `col.getXSs()` lambda.
3. First-bracket left edge, note start (replace the `else if (start > 0)` halfway block, lines ~236-241): when `startElement` is not a barline/repeat, set `x1 = col.getXSs() + col.getLeftExtentSs() - NoteGeometry.ACCIDENTAL_PADDING_SS` where `col = columnFn.apply(startElement)`. Keep the barline/repeat branch (`endingAnchorXOffsetSs`) and the lines ~211-220 "pull in a preceding barline" block unchanged (per-edge: prefer a real barline anchor when present).
4. Second-bracket right edge, note end (replace the `default ->` branch of the `switch (endType)`, lines ~303-313): set `x2 = col.getXSs() + col.getRightExtentSs() + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS` where `col = columnFn.apply(endElement)`, and `hasClosingStroke = false`. Leave the barline/repeat cases and the lines ~276-285 "extend to next barline/repeat" lookahead unchanged.
5. Run `./scripts/compile.sh` — must report SUCCESS.

## ✅ Phase 4: Tests & manual verification

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — updating/adding cases across the existing ending test suite is largely mechanical; the manual eyeball step tunes the two flagged padding values.

Existing suite to reconcile: `dom/EndingValidationResultTest`, `layout/EndingTest`, `layout/EndingInvalidationTest`, `layout/EndingLineFixture`, `ui/action/FirstSecondEndingActionTest`, `ui/renderer/EndingRendererTest`, `ui/component/ScoreViewControllerCommandHandlerTest`, `dom/MusicEditOperationsMutationTest` (the last two reference `INSERT_BARLINE` and will not compile until updated), plus `io/musicxml/MusicXmlEndingRoundTripTest` (expect no change).

### Tasks
1. Update creation/validation tests for the relaxed rules: a selection that starts on a note (no leading barline) and/or ends on a note now creates a valid ending with **no** auto-inserted barline; `PrecedingAction.INSERT_BARLINE` is gone. Fix compile breaks and assertions in: `EndingValidationResultTest` (its `@EnumSource(PrecedingAction.class)` now yields two values, not three), `FirstSecondEndingActionTest`, `ScoreViewControllerCommandHandlerTest` (remove/rewrite its `INSERT_BARLINE` case), and `MusicEditOperationsMutationTest` (its two `INSERT_BARLINE` cases). **Add** a case: a selection immediately preceded by a `REPEAT_LEFT_RIGHT` yields `EXTEND_SPAN` anchored to that barline (not `NONE`/`invalid()`).
2. Update `EndingInvalidationTest` for the new `checkReplacement` matrix: replacing a note anchor/end with another note is `None`; replacing a note anchor with `REPEAT_LEFT_RIGHT` is `None` (now a valid anchor type); barline/repeat compensation cases still hold; only non-content, non-barline, non-repeat replacements invalidate.
3. Add `computeBracketRanges` geometry cases in `EndingTest` (using `EndingLineFixture`): assert the note-start `x1` and note-end `x2` formulas and `hasClosingStroke == false` for a note end. **Assert against the named constants symbolically** (`NoteGeometry.ACCIDENTAL_PADDING_SS`, `SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS`) — never a hardcoded number: `AUGMENTATION_DOT_WIDTH_SS` is loaded from font metadata in a static initializer (`SMuFLConstants.java:74`), not a compile-time literal, so a literal assertion would be brittle and font-dependent. Update the `computeBracketRanges` call sites in `EndingTest` and `EndingRendererTest` (`makeLineWithEnding`, line ~78) for the new `columnFn` signature — these are the only two test call sites; the sole production caller is `StructuralStacker.stackEndings`. Add explicit coverage for both new outer edges: a 1st bracket that starts on a note (ideally with an accidental) and a 2nd bracket that ends on a note.
4. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` — must be green. Confirm `MusicXmlEndingRoundTripTest` still passes unchanged.
5. Manual: `./scripts/run.sh`, create an ending whose 1st bracket starts on a note (ideally with an accidental) and whose 2nd bracket ends on a note. Eyeball the left edge (clears the accidental by the gap) and the right edge (dot-width padding, flat end, no closing arm). Tune the two flagged padding values by eye.
