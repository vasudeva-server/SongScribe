# Design Pass 3 — Step 6: Test Triage
Target: `songscribe.engraving`. The floor is _The testing floor_ in `~/.claude/guides/design.md`; the three kinds a test may be are abbreviated below as **algorithm** (real logic worth checking), **invariant** (a promise spanning several calls), and **corpus** (behaviour with a known-correct reference).
## What it found
**The target had no tests at the baseline.** Every row in the table was written or modified by this pass.

**The target's behaviour is eight public methods.** Five of its eight classes hold constants only — `EngravingConstants`, `StemMetrics`, `LedgerLine`, `StaffHeaderMetrics`, and `BarStroke.SEPARATION_SS`. What can be asserted is `Staff.halfSpacesToSs` / `ssToHalfSpaces`, `StaffPosition.toSs` / `fromSs` / `containsSs`, `BeamMetrics.beamTranslationSs` / `beamStackHeightSs`, and `BarStroke.widthSs`.

`BarStroke.widthSs` **has no direct test and needs none.** It is a switch the compiler makes exhaustive over three constants, and `BarAppearanceTest` reaches all three through the stroke sequences `ElementType` declares.

**Seven test methods were written in the plan's Phase 12, whose authorised task was repointing.** Phase 12 task 4 assumed `KeySignatureExtentTest` already held natural-kerning cases that had to be rewritten against the new `Key.DrawnAccidental.advanceSs`; at the baseline that class held two tests and no kerning cases. What landed instead is five new tests there, one in `BarAppearanceTest`, and one in `EndingBracketGeometryTest`. No list was proposed before they were written, so this table is the first gate they reach.

**Four production findings came out of reading the tests against the contracts they are derived from.** They are stated here rather than in the table because they are not test dispositions, and two of them decide what a row's _Kind_ can say.
### F1 — `BeamMetrics.beamStackHeightSs` promises the wrong edge
`src/main/java/songscribe/engraving/BeamMetrics.java:85` says the extent is "measured from the outer edge of the first beam to the outer edge of the last". The body returns `BEAM_THICKNESS_SS + (beamCount - 1) * BEAM_TRANSLATION_SS`, which runs from the outer edge of the first beam to the **inner** edge of the last. Its own test row proves it: outer-to-outer across a single beam is zero, and the row asserts one beam measures `BEAM_THICKNESS_SS`.

The value is right and the sentence is wrong. It reads correctly as _the total extent the stack occupies_, which is also exactly the depth `HitRegionBuilder.beamGroupRectSs` documents as "from the outermost beam's outer edge to the deepest sub-beam's inner edge".

{==**Fix:** correct the sentence to name the inner edge of the last beam==}{>>agree<<}{id="c3" by="user" at="2026-08-28T12:51:12.494Z"}.
### F2 — `HitRegionBuilder` re-derives the beam stack extent
`src/main/java/songscribe/layout/HitRegionBuilder.java:748-749` computes

```java
BeamMetrics.BEAM_THICKNESS_SS + beamLayout.thickeningSs()
    + (levelCount - 1) * BeamMetrics.beamTranslationSs(beamLayout.thickeningSs())
```

which is `BeamMetrics.beamStackHeightSs` generalised to a thickened stack: with `thickeningSs` of zero the two expressions are identical. The stack's extent is `BeamMetrics`' to own, and it is currently owned in two places that must agree by inspection.

{==**Fix:** add `BeamMetrics.beamStackHeightSs`==}{>>agree<<}{id="c4" by="user" at="2026-08-28T12:51:44.946Z"}`(int beamCount, double thickeningSs)` returning `BEAM_THICKNESS_SS + thickeningSs + (beamCount - 1) * beamTranslationSs(thickeningSs)`, have the existing one-argument form delegate to it with zero, and call it from `HitRegionBuilder`. Behaviour-preserving: `BeamMath` and `BeamScoring` keep the unthickened form they use today.

This is also the answer to what a test could pin here. The three call sites that step by this distance — `HitRegionBuilder`, `BeamGroupRenderer.drawBeam`, `StaffElementRenderer.renderStem` — must agree, and removing the duplication enforces that where a test could only observe it.
### F3 — the ledger-line grid is stated twice, in neither place by the type that owns it
Two sites hold the same staff-grid fact, and `StaffPosition` — which exists to own the grid — holds neither:

- `src/main/java/songscribe/ui/renderer/RenderingUtils.java`, `forEachLedgerLineYSs` — a bare `5` in `Math.abs(i) > 5`, plus `% 2` and `step = ±2`, which are `StaffPosition.POSITIONS_PER_STAFF_LINE`.
  
- `src/main/java/songscribe/layout/NoteGeometry.java:592` — `OUTERMOST_STAFF_LINE_POSITION = 5`, whose name and Javadoc are both wrong: the outermost staff _line_ is position 4, and 5 is the space adjacent to it, which is the outermost position needing no ledger line.
  

The sequence itself — which ledger lines a note at position _p_ needs, and where each sits relative to the note — is domain-defined geometry, statable without naming a caller, and it currently lives in a renderer's package-private static bag while `LedgerLine` in the target owns ledger-line thickness and length.

{==**Fix**==}{>>agreed<<}{id="c5" by="user" at="2026-08-28T12:53:04.116Z"}**:** name the threshold once on `StaffPosition` (`OUTERMOST_UNLEDGERED_SP`, or a `needsLedgerLines(int)` query), and move the sequence to `LedgerLine.forEachOffsetSs(int staffPositionSp, DoubleConsumer)` in `engraving`, with `NoteGeometry.noteNeedsLedgerLines` and `RenderingUtils` both reading it from there.

This decides where `LedgerLineOffsetTest` lives; see the table.
### F4 — `StaffPosition`'s bound constants are misnamed and their Javadoc is false
`src/main/java/songscribe/engraving/StaffPosition.java:39-60`:

```java
MIN_SP = -(STAFF_LINES_ABOVE + POSITIONS_PAST_OUTERMOST_LEDGER_LINE) * POSITIONS_PER_STAFF_LINE
```

`-(3 + 2) * 2` is `-10`, and `-10` **is** the outermost ledger line above — a note at `MIN_SP` sits on it, as `LedgerLineOffsetTest`'s row for position −10 shows by drawing lines at −10, −8 and −6. Nothing lies past it. So the `2` is not "positions past the outermost ledger line"; the arithmetic multiplies it by `POSITIONS_PER_STAFF_LINE`, which makes it a count of _lines_ — the two staff lines between the middle line and the staff's outer line. `STAFF_LINES_ABOVE` and `STAFF_LINES_BELOW` are likewise ledger lines, not staff lines: the staff's own lines sit at −4, −2, 0, 2, 4 and bear no ledger lines.

The values `MIN_SP = -10` and `MAX_SP = 12` are correct and unchanged from the baseline. What is wrong is what the decomposition claims to mean.

{==**Fix:** rename to `LEDGER_LINES_ABOVE`, `LEDGER_LINES_BELOW` and `STAFF_LINES_FROM_MIDDLE_TO_OUTER`, and restate the two Javadoc lines to match==}{>>agreed<<}{id="c6" by="user" at="2026-08-28T12:53:16.816Z"}.
## What it proposes
One row per test method, each separately decidable.

| Class | Test | Kind | Disposition | Why |
|---|---|---|---|---|
| `StaffGeometryRegressionTest` | a measurement from the middle line resolves to the nearest position, clamped into the grid | invariant | keep | The round-half-up-in-Y-down rule and the clamp are the two things `fromSs` promises and neither is visible in one drawing. |
| `StaffGeometryRegressionTest` | a measurement is on the grid exactly when it rounds onto a valid position | invariant | keep | Spans `containsSs` and `fromSs`; it is why `containsSs` exists as a separate question. |
| `StaffGeometryRegressionTest` | a measurement off the grid resolves to the bound it lies past | invariant | keep | The cost of converting without asking — the pairing of the two methods, which neither row above states. |
| `StaffGeometryRegressionTest` | a distance resolves to the nearest whole count of half spaces | invariant | keep | `Staff.ssToHalfSpaces` and `StaffPosition.fromSs` must round alike and clamp differently; that divergence is observable only with both tables side by side. |
| `StaffGeometryRegressionTest` | a count of half spaces survives the round trip through staff spaces | invariant | keep | Runs past both bounds, which is the promise a position conversion cannot make. |
| `StaffGeometryRegressionTest` | a beam stack grows by one center-to-center step per beam | algorithm | keep | Row 1 pins the count-versus-level boundary the contract warns about. Its *Kind* holds only once F1 corrects which edge the contract names. |
| `StaffGeometryRegressionTest` | thickening a beam widens the gap between beams by the same amount | — | **discard** | `BEAM_TRANSLATION_SS + thickeningSs` restated: no boundary, no off-by-one, nothing spanning two calls. What needs guarding is the agreement among the three callers that step by it, and F2 removes that by structure. |
| `LedgerLineOffsetTest` | a note gets one ledger line per staff line between it and the staff | algorithm | keep | The sequence changes shape at every position and is drawn nowhere else. Moves to `songscribe.engraving` with the production code if F3 is taken. |
| `LedgerLineOffsetTest` | the cases cover every staff position a note may take | algorithm | keep | The assertion that makes the row above's completeness claim checkable when the grid grows. Moves with it. |
| `BarAppearanceTest` | every bar and repeat type is measured | algorithm | keep | Drives the table's coverage off `isBarLine()`/`isRepeat()`, so a bar type added later fails rather than passing unmeasured. |
| `BarAppearanceTest` | declared bar geometry follows its stroke sequence | algorithm | keep | The added `expectedStrokes` column is what `BarStroke` now owns; without it a reordered sequence shows only as a width that moved. |
| `KeySignatureExtentTest` | the three kerning bands widen as the neighbour closes in | algorithm | keep | Asserts the bands against each other rather than as numbers, so it survives a metric change and still fails on a band boundary that moved. |
| `KeySignatureExtentTest` | a natural pushes its neighbour away by the band their edges fall in | algorithm | keep | Each pair appears in both orders, which is the asymmetry a single-order table would miss. |
| `KeySignatureExtentTest` | each accidental advances by its own ink and the kerning its neighbour needs | invariant | keep | Its closing assertion — the reserved column equals the run laid out — spans the extent and every accidental in it. See *Open questions* on the per-index assertions. |
| `KeySignatureExtentTest` | the `KEY_CHANGE` floor is one no drawn signature falls under | invariant | keep | A floor that must not over-reserve is a promise about two things at once. |
| `KeySignatureExtentTest` | `KEY_CHANGE` spans the staff the way a barline does | — | **discard** | Two stored fields compared to the two constants a single line of the static initialiser sets them from. |
| `CautionaryKeySignatureTest` | placement leaves the padding either side of the accidentals | invariant | keep | Rewritten this pass: the barline relation now runs through `assertBarLinePlacement`. |
| `CautionaryKeySignatureTest` | placement on an overflowing line starts one lead-in past the content | invariant | keep | Rewritten this pass: gained the barline check it did not make, from the same helper. |
| `EndingBracketGeometryTest` | the collision span runs from the anchor past the end element's head | algorithm | **rewrite** | Both elements are crotchets, so the one promise — that it is the *end* element's width and not the anchor's — cannot fail. Rewrite to vary the anchor's type and assert the span does not move, then vary the end element's and assert it does. |
| `KeySignatureExtentTest` | a change that cancels draws wider than the same key out of no accidentals | — | **discard** | Pre-existing, and now a duplicate: it asserts a relation over three rows that *each accidental advances by its own ink…* asserts exactly over thirty, its own three pairs among them. Reached by reading the two together in answering c2. |

**No cases are added.** Nothing in the target's eight contracts is unasserted that the design does not already enforce, which is what the earlier steps were for.
## Open questions
1. **Was the Phase 12 test writing already agreed?** {==Seven methods were written without the list this table is. If they were approved in conversation, say so==}{>>They were approved<<}{id="c1" by="user" at="2026-08-28T12:50:10.008Z"} and the _keep_ rows stand as recorded; if not, this is the veto that was owed.
  
2. {==**Does** `testEachAccidentalAdvancesByItsOwnInkAndTheKerningItsNeighbourNeeds` **assert too much**==}{>>You figure it out<<}{id="c2" by="user" at="2026-08-28T12:50:34.921Z"}**?** Its loop reconstructs the run's composition rules — which indices are naturals, which one carries the leading gap, which pairs kern — and those read as the production algorithm transcribed into the test. Two promises in it are not that: the run's glyph composition, and that `KeySignatureExtent.widthSs()` equals the sum of what it lays out. Collapsing the method to those two, or keeping the per-index assertions, is a judgement I cannot make from the contract alone.
  
3. **F3 decides where two tests live.** Settled by the agreement on F3 (c5): the sequence moves, so `LedgerLineOffsetTest` becomes `LedgerLineTest` in `songscribe.engraving` and stops reaching into a renderer's package-private helper. Say so if that reading is wrong.
  
4. **F1, F2 and F4 are production changes proposed from a test-triage step.** F1 and F4 are defects in what steps 3 and 5 of this pass wrote; F2 and F3 are design findings against `layout` and `ui/renderer`. {==Whether they are made here or carried==}{>>here<<}{id="c7" by="user" at="2026-08-28T12:53:46.454Z"} to the register at step 12 is yours — but F1 and F4 are false statements standing beside the code they describe, and carrying a false sentence forward is what makes the next reader trust it.
  
5. **One row was added in this round.** Answering c2 meant reading `testEachAccidentalAdvancesByItsOwnInkAndTheKerningItsNeighbourNeeds` beside the two tests `KeySignatureExtentTest` already had, and it subsumes one of them. The last table row proposes discarding that one. It is pre-existing rather than this pass's work, so it is yours to decide rather than mine.
  

* * *

comments: c8: body: >- Noted — the seven Phase 12 methods were approved, so every keep row for them stands as recorded and nothing is vetoed retroactively. by: AI at: "2026-08-28T13:10:00.000Z" re: c1 c9: body: >- Not applied. "ggu" lands mid-word inside "the list this table is" and reads as a stray keystroke. Say the word if it was meant to be something. by: AI at: "2026-08-28T13:10:00.000Z" re: s1 c10: body: >- Keep it unchanged — my reservation does not survive reading it against Key.DrawnAccidental. Each per-index assertion maps to a named contract clause, and none is read off a body. The glyph sequence is the order accidentals() states (the cancelling naturals, then the new key's own). The leadingGapSs expectation is DrawnAccidental's "zero for every accidental except the first of a group that follows another group". The advanceSs expectation is the three totality cases advanceSs(next) enumerates, the third of which is exactly the "kerning stays within a group" rule the loop encodes as index + 1 < naturalCount. The kerning oracle is not a transcription either: kerningSs(a, b) is advanceSs(neighbour) minus advanceSs(null) on a synthetic pair, so that assertion reduces to "the accidental in the real run advances as a synthetic natural at the same two staff positions does" — which is what fails if the run kerns against the wrong neighbour or across the group boundary. One caveat that is not a reason to change it: cancels() reimplements the cancellation policy to derive naturalCount, so a policy change fails this test as a stale copy rather than naming the policy. Deriving naturalCount from the production result instead would make the glyph assertion tautological, so the copy is the lesser of the two. by: AI at: "2026-08-28T13:10:00.000Z" re: c2 c11: body: Applying — the sentence will name the inner edge of the last beam. by: AI at: "2026-08-28T13:10:00.000Z" re: c3 c12: body: >- Applying — the two-argument beamStackHeightSs, the one-argument form delegating with zero, and HitRegionBuilder calling it. by: AI at: "2026-08-28T13:10:00.000Z" re: c4 c13: body: >- Applying. This also settles open question 3: the sequence moves to LedgerLine, so LedgerLineOffsetTest becomes LedgerLineTest in songscribe.engraving. by: AI at: "2026-08-28T13:10:00.000Z" re: c5 c14: body: >- Applying — LEDGER_LINES_ABOVE, LEDGER_LINES_BELOW, STAFF_LINES_FROM_MIDDLE_TO_OUTER, and the two Javadoc lines restated. The values do not change. by: AI at: "2026-08-28T13:10:00.000Z" re: c6 c15: body: >- All four made in this pass. Nothing from F1 to F4 goes to the register at step 12. by: AI at: "2026-08-28T13:10:00.000Z" re: c7
