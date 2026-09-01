# Engraving Restructure — Design Pass 3, Steps 3–5

**Type:** Plan  <br>
**Created:** 2026-08-28  <br>
**Status:** Complete

The settled class design is `plans/design-pass-3/findings-1-2.md`. It is the
source of truth for what is built and is not re-argued here. The pass record is
`plans/design-pass-3/record.md`.

Test *triage* is design pass step 6 and is not in this plan. The tests in Phase 1
are a regression baseline captured before anything moves.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Regression Baseline](#-phase-1-regression-baseline) | ✅ Complete | — |
| 2 | [New Constant Classes](#-phase-2-new-constant-classes) | ✅ Complete | — |
| 3 | [StaffPosition and Staff](#-phase-3-staffposition-and-staff) | ✅ Complete | — |
| 4 | [Engraving Teardown](#-phase-4-engraving-teardown) | ✅ Complete | — |
| 5 | [SMuFL Engraving Defaults](#-phase-5-smufl-engraving-defaults) | ✅ Complete | — |
| 6 | [Drawn Accidental](#-phase-6-drawn-accidental) | ✅ Complete | — |
| 7 | [Dom Call Sites](#-phase-7-dom-call-sites) | ✅ Complete | — |
| 8 | [Layout Call Sites](#-phase-8-layout-call-sites) | ✅ Complete | — |
| 9 | [Stacking Call Sites](#-phase-9-stacking-call-sites) | ✅ Complete | — |
| 10 | [Renderer Call Sites](#-phase-10-renderer-call-sites) | ✅ Complete | — |
| 11 | [Score Component Call Sites](#-phase-11-score-component-call-sites) | ✅ Complete | — |
| 12 | [Test Tree Repoint](#-phase-12-test-tree-repoint) | ✅ Complete | — |
| 13 | [Gate](#-phase-13-gate) | ✅ Complete | — |

## Constant mapping

Every phase that touches a call site uses this table. Left column is what the
code says today; right column is what it must say afterwards.

### Moved to `songscribe.engraving.EngravingConstants`

| Today | Afterwards |
|---|---|
| `LineThickness.STAFF_LINE_SS` | `EngravingConstants.STAFF_LINE_SS` |
| `LineThickness.THIN_BARLINE_SS` | `EngravingConstants.THIN_BARLINE_SS` |
| `LineThickness.THICK_BARLINE_SS` | `EngravingConstants.THICK_BARLINE_SS` |
| `LineThickness.HAIRPIN_SS` | `EngravingConstants.HAIRPIN_SS` |
| `LineThickness.VOLTA_BRACKET_SS` | `EngravingConstants.VOLTA_BRACKET_SS` |
| `LineThickness.TUPLET_BRACKET_SS` | `EngravingConstants.TUPLET_BRACKET_SS` |
| `LineThickness.GLISSANDO_SS` | `EngravingConstants.GLISSANDO_SS` |
| `StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS` | `EngravingConstants.KEY_SIGNATURE_PADDING_SS` |
| `LineThickness.STAFF_LINE_SS / 2.0` in `StackingUtils` | `EngravingConstants.STAFF_LINE_HALF_THICKNESS_SS` |

### Moved to per-thing classes

| Today | Afterwards |
|---|---|
| `LineThickness.STEM_SS` | `StemMetrics.STEM_SS` |
| `SMuFLConstants.STEM_LENGTH_SS` | `StemMetrics.STEM_LENGTH_SS` |
| `SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS` | `StemMetrics.GRACE_NOTE_STEM_LENGTH_SS` |
| `LineThickness.BEAM_THICKNESS_SS` | `BeamMetrics.BEAM_THICKNESS_SS` |
| `LineThickness.BEAM_TRANSLATION_SS` | `BeamMetrics.BEAM_TRANSLATION_SS` |
| `LineThickness.BEAM_BLOT_DIAMETER_SS` | `BeamMetrics.BEAM_BLOT_DIAMETER_SS` |
| `LineThickness.beamTranslationSs(t)` | `BeamMetrics.beamTranslationSs(t)` |
| `LineThickness.beamStackHeightSs(n)` | `BeamMetrics.beamStackHeightSs(n)` |
| `LineThickness.LEDGER_LINE_SS` | `LedgerLine.LEDGER_LINE_SS` |
| `SMuFLConstants.LEDGER_LINE_LENGTH_FRACTION` | `LedgerLine.LENGTH_FRACTION` |
| `LineThickness.BARLINE_SEPARATION_SS` | `BarStroke.SEPARATION_SS` |

### Glyph constants become direct queries

`SMuFLMetadata` and `SMuFLGlyph` are in `songscribe.smufl`.

| Today | Afterwards |
|---|---|
| `SMuFLConstants.G_CLEF_ADVANCE_WIDTH_SS` | `SMuFLMetadata.advanceWidthSs(SMuFLGlyph.G_CLEF)` |
| `SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS` | `SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT)` |
| `SMuFLConstants.NOTE_HEAD_INK_WIDTH_SS` | `SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).widthSs()` |
| `StaffHeaderMetrics.accidentalInkBboxSs(g)` | `Key.DrawnAccidental.inkWidthSs(g)` |

### The staff-position grid

`Staff.STAFF_POSITION_OFFSET_SS` is deleted. It is `0.5` used for three different
things, and each gets its own name. **Classify every site before changing it** —
all three read the same today and produce the same number, so a site put in the
wrong row compiles and is silently wrong.

| What the site means | Today | Afterwards |
|---|---|---|
| a staff **position** → staff spaces | `Staff.spToSs(p)` or `p * Staff.STAFF_POSITION_OFFSET_SS` | `StaffPosition.toSs(p)` |
| staff spaces → a staff **position** | `Staff.ssToSp(y)` or `y / Staff.STAFF_POSITION_OFFSET_SS` | `StaffPosition.fromSs(y)` |
| a **count of half staff spaces** (a difference, an offset) → staff spaces | `Staff.spToSs(delta)` | `Staff.halfSpacesToSs(delta)` |
| the **length** "half a staff space" | `Staff.STAFF_POSITION_OFFSET_SS` | `Staff.HALF_SPACE_SS` |
| `Staff.MIN_STAFF_POSITION_SP` | — | `StaffPosition.MIN_SP` |
| `Staff.MAX_STAFF_POSITION_SP` | — | `StaffPosition.MAX_SP` |

The delta sites, in full — there are exactly three, and every other `spToSs` site
is a position:

- `ui/renderer/RenderingUtils.forEachLedgerLineYSs` — `Staff.spToSs(i - staffPosition)`
- `ui/renderer/RestRenderer` — `Staff.spToSs(SEMIBREVE_REST_Y_OFFSET)`
- `ui/renderer/RestRenderer` — `Staff.spToSs(MINIM_REST_Y_OFFSET)`

The length sites, in full:

- `layout/LayoutEngine:1056` — `seatSs > Staff.STAFF_POSITION_OFFSET_SS`
- `layout/LayoutEngine:1279, 1294, 1297` — `Staff.STAFF_POSITION_OFFSET_SS + …`
- `layout/stacking/StackingUtils:103` — `Staff.STAFF_HALF_SS + Staff.STAFF_POSITION_OFFSET_SS`

## Constant source attribution

Wherever a constant lands, its Javadoc names where its value comes from. These
four are wrong or missing today:

| Constant | Source to state |
|---|---|
| `STEM_LENGTH_SS` | LilyPond `Stem.details.lengths`, first entry — `scm/define-grobs.scm:3453`, commented there "3.5 (or 3 measured from note head) is standard length". Its current "SMuFL standard stem length" is **wrong**; SMuFL declares no stem length |
| `GRACE_NOTE_STEM_LENGTH_SS` | a SongScribe decision, not a port — say so, so nobody hunts for a LilyPond original |
| `LedgerLine.LENGTH_FRACTION` | LilyPond `LedgerLineSpanner.length-fraction` |
| `STEM_MULTIPLIER` | the same LilyPond `Stem` grob's `thickness`, `scm/define-grobs.scm:3474` |

---

## ✅ Phase 1: Regression Baseline

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/test/java/songscribe/dom/KeySignatureExtentTest.java, src/test/java/songscribe/layout/CautionaryKeySignatureTest.java, src/test/java/songscribe/dom/BarAppearanceTest.java, src/test/java/songscribe/layout/EndingBracketGeometryTest.java, src/test/java/songscribe/engraving/StaffGeometryRegressionTest.java, src/test/java/songscribe/engraving/package-info.java, src/test/java/songscribe/ui/renderer/LedgerLineOffsetTest.java, src/test/java/songscribe/ui/renderer/package-info.java  <br>
**Recommended model/effort:** Opus, high — the assertions must capture what the code does now, and an expected value guessed rather than derived turns the baseline into a false alarm or a blind spot

This phase changes **no production code**. It runs against the tree exactly as it
stands and must be green before any other phase starts.

### Tasks

1. Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md`
   before writing anything. They govern case-table shape, naming, and what an
   assertion may say.
2. **Before writing each test method, check whether it will sit beside a
   same-shape sibling.** If it will, both are rows in one `record` case table
   driven by `@ParameterizedTest` + `@MethodSource`, from the first case — not two
   methods to be merged later. A varying lambda does not disqualify a case; only a
   varying assertion does.
3. Derive every expected value **from the current implementation**, by reading it
   and computing, not by guessing a plausible number. A wrong expectation here
   either fails immediately or hides the regression it was written to catch.
4. Create `src/test/java/songscribe/engraving/package-info.java` carrying
   `@NullMarked` and the package declaration, matching
   `src/test/java/songscribe/dom/package-info.java`.
5. Create `src/test/java/songscribe/engraving/StaffGeometryRegressionTest.java`
   extending `songscribe.UnitTest`, covering:
   - `Staff.ssToSp` — that it rounds half away from zero and returns positions
     outside `MIN_STAFF_POSITION_SP..MAX_STAFF_POSITION_SP` unchanged. Include at
     least one input either side of both bounds. **Phase 3 makes this method clamp**,
     so this test records the behaviour that is about to change deliberately; write
     it as the current contract and expect Phase 12 to update it.
   - `LineThickness.beamStackHeightSs` for beam counts 1, 2 and 3, and
     `LineThickness.beamTranslationSs` for thickening 0 and one non-zero value.
   - `RenderingUtils.forEachLedgerLineYSs` — the full sequence of Y offsets for a
     note at each staff position that needs ledger lines, above and below the
     staff. It is package-private in `songscribe.ui.renderer`, so this coverage
     goes in a test class in that package instead; create
     `src/test/java/songscribe/ui/renderer/LedgerLineOffsetTest.java` and a
     `package-info.java` beside it if the package has none.
6. Extend `src/test/java/songscribe/dom/KeySignatureExtentTest.java` with:
   - Natural kerning: `StaffHeaderMetrics.naturalKerningSs(a, b)` over a case
     table covering a pair that clears, a pair that touches at the corners, and a
     pair that overlaps — in both orders, since the function is not symmetric.
   - Accidental advances: for every `Key` constant, the sequence of
     `DrawnAccidental.advanceSs()` and `leadingGapSs()` values that
     `Key.accidentalsFrom` produces, both from `Key.NO_ACCIDENTALS` and from a
     previous key that forces a cancellation. Drive the key list from
     `Key.values()` so a new key fails the test rather than being skipped.
   - `ElementType.computeKeySignatureBoundsSs` for a signature of sharps, one of
     flats, and one with a cancellation.
7. Extend `src/test/java/songscribe/layout/CautionaryKeySignatureTest.java` with
   `reservationSs()` and `placeIn()` for a line that fits and a line that
   overflows, and for a cautionary that draws its own barline and one that does
   not — four combinations, as one case table.
8. Extend `src/test/java/songscribe/dom/BarAppearanceTest.java` with the stroke
   sequence and total width for every `ElementType` that is a barline or repeat.
   **Drive the cases from `ElementType.values()` filtered by the predicates the
   production code uses** (`isBarLine()`, `isRepeat()`), so a new bar type fails
   the test rather than going unnoticed. A hand-written list does not satisfy this
   task.
9. Extend `src/test/java/songscribe/layout/EndingBracketGeometryTest.java` with
   `Ending.getSpanWidthSs` for a two-element bracket and a many-element bracket.
   Do **not** add a case for a single-element bracket: that state cannot occur —
   an ending cannot be created from one element, and shrinking one to a single
   element deletes it — and Phase 7 deletes the `Math.max` that guards it.
10. Gate this phase: run `./scripts/compile.sh --test`, then
    `./scripts/test.sh KeySignatureExtentTest CautionaryKeySignatureTest BarAppearanceTest EndingBracketGeometryTest`
    and `./scripts/test.sh StaffGeometryRegressionTest LedgerLineOffsetTest`.
    Both must report SUCCESS with every test passing. Never `./gradlew`, `gradle`,
    `javac`, or `java -cp`.

---

## ✅ Phase 2: New Constant Classes

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/engraving/EngravingConstants.java, src/main/java/songscribe/engraving/StemMetrics.java, src/main/java/songscribe/engraving/BeamMetrics.java, src/main/java/songscribe/engraving/LedgerLine.java  <br>
**Recommended model/effort:** Sonnet, medium — the membership and the values are fully specified; the care is in carrying the Javadoc across intact

All four are `public final class` with a `private` no-arg constructor, in package
`songscribe.engraving`. Copy each constant's existing Javadoc across unchanged
except where the *Constant source attribution* table above corrects it. Values are
taken verbatim from `LineThickness` and `SMuFLConstants` as they stand — this
phase changes no number.

### Tasks

1. Create `EngravingConstants` holding `LILYPOND_BASE_THICKNESS_SS = 0.1` and,
   derived from it, `STAFF_LINE_SS`, `THIN_BARLINE_SS`, `THICK_BARLINE_SS`,
   `HAIRPIN_SS`, `VOLTA_BRACKET_SS`, `TUPLET_BRACKET_SS`, `GLISSANDO_SS`, each
   with its `private static final double *_MULTIPLIER` beside it. Also
   `STAFF_LINE_HALF_THICKNESS_SS` (`STAFF_LINE_SS / 2.0`) and
   `KEY_SIGNATURE_PADDING_SS = 0.75`.
2. Write `STAFF_LINE_SS` as `LILYPOND_BASE_THICKNESS_SS * STAFF_LINE_MULTIPLIER`
   with `private static final double STAFF_LINE_MULTIPLIER = 1.0`, so every width
   in the class is base × named multiplier and none reads as an alias of the base.
3. Give `EngravingConstants` class Javadoc stating the invariant every member
   obeys: each stroke width is a fixed multiple of one base thickness, so the
   visual weight hierarchy holds at any resolution. Carry across
   `LineThickness`'s existing explanation of why LilyPond's base (0.1) is used
   rather than Bravura's (0.13), and why the multipliers matter at screen
   resolution. Do **not** state that every engraving constant in the program lives
   here — it does not.
4. Document `STAFF_LINE_HALF_THICKNESS_SS` as what it is: half the staff line
   width, the distance from a staff line's center to its edge. It is undocumented
   at its current home in `layout/stacking/StackingUtils`.
5. Document `KEY_SIGNATURE_PADDING_SS` by carrying across its Javadoc from
   `StaffHeaderMetrics`, including the sentence explaining that the staff header's
   own signature is not one of the cases it covers.
6. Create `StemMetrics` holding `STEM_SS` (base × `STEM_MULTIPLIER = 1.3`),
   `STEM_LENGTH_SS = 3.5` and `GRACE_NOTE_STEM_LENGTH_SS = 2.5`. `STEM_SS`
   multiplies `EngravingConstants.LILYPOND_BASE_THICKNESS_SS`, which must
   therefore be `public`. Apply the source attributions from the table above:
   `STEM_LENGTH_SS` is LilyPond's, not SMuFL's, and `GRACE_NOTE_STEM_LENGTH_SS` is
   a SongScribe decision.
7. Create `BeamMetrics` holding `BEAM_THICKNESS_SS = 0.48`, `BEAM_TRANSLATION_SS`,
   `BEAM_BLOT_DIAMETER_SS = 0.08`, `beamTranslationSs(double)` and
   `beamStackHeightSs(int)`, with the private `STAFF_SPACE_SS = 1.0` that
   `BEAM_TRANSLATION_SS`'s ported formula reads. Carry across every existing
   Javadoc, including the note that beam thickness deliberately does not come from
   the font's `engravingDefaults`.
8. Create `LedgerLine` holding `LEDGER_LINE_SS` (base × `LEDGER_LINE_MULTIPLIER = 2.0`)
   and `LENGTH_FRACTION = 0.25`. Name `LENGTH_FRACTION`'s source per the table.
   It is a dimensionless multiplier on notehead width, so it takes no unit suffix.

---

## ✅ Phase 3: StaffPosition and Staff

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/engraving/StaffPosition.java, src/main/java/songscribe/engraving/Staff.java  <br>
**Recommended model/effort:** Opus, high — splits one constant that serves three purposes, and adds clamping where there was none

`StaffPosition` is **not** a value type. It is a `public final class` with a
`private` no-arg constructor holding the pitch grid's bounds and its conversions
as statics. Callers pass and receive `int`.

### Tasks

1. Write the contract for `StaffPosition.toSs` and `StaffPosition.fromSs` before
   implementing them. `toSs` has 18 call sites and `fromSs` four, so both earn
   preconditions, postconditions and boundary semantics. State for `fromSs` what
   it does at each bound and that it never returns a position outside
   `MIN_SP..MAX_SP`; state for `toSs` that it accepts any position and does not
   itself range-check, so a caller holding an unvalidated `int` gets an answer
   rather than an error.
2. Create `songscribe.engraving.StaffPosition` with:
   - `public static final int MIN_SP` and `MAX_SP`, moved from `Staff` with their
     Javadoc. Move `STAFF_LINES_ABOVE` and `STAFF_LINES_BELOW` here too, as
     `private`, since they exist only to derive the bounds.
   - `public static double toSs(int staffPositionSp)` — the position-to-staff-space
     conversion, replacing `Staff.spToSs`.
   - `public static int fromSs(double ss)` — replacing `Staff.ssToSp`. It rounds
     the way `Staff.ssToSp` does today (`Math.round(ss / 0.5)`) **and then clamps
     the result into `MIN_SP..MAX_SP`**. Clamping is new behaviour, deliberate,
     and is why Phase 1 recorded the unclamped behaviour first.
3. Name both magic literals in the bounds. `MIN_STAFF_POSITION_SP` is
   `-(STAFF_LINES_ABOVE + 2) * 2` today: the `* 2` converts a count of staff lines
   into half-staff-space positions, and the `+ 2` is the two further positions past
   the outermost ledger line that a note may occupy. Give each a named constant so
   neither literal appears in the expression, and do the same in
   `MAX_STAFF_POSITION_SP`, which repeats the pair.
4. In `Staff`, rename `STAFF_POSITION_OFFSET_SS` to `HALF_SPACE_SS` and document
   it as a **length** — half of one staff space — not as a conversion factor. It
   keeps the value `0.5`.
5. Add `public static double halfSpacesToSs(int halfSpaces)` to `Staff`, for
   converting a count of half staff spaces — a difference between two positions,
   or an offset expressed in them — into staff spaces. Its Javadoc must say that
   it takes a distance, not a position, and that a position goes through
   `StaffPosition.toSs` instead; the two are the same arithmetic and only the name
   distinguishes them.
6. Delete `Staff.spToSs`, `Staff.ssToSp`, `Staff.MIN_STAFF_POSITION_SP`,
   `Staff.MAX_STAFF_POSITION_SP`, `Staff.STAFF_LINES_ABOVE` and
   `Staff.STAFF_LINES_BELOW`.
7. Keep `STAFF_HEIGHT_SS`, `STAFF_HALF_SS`, `MIN_ABOVE_STAFF_SS` and
   `MIN_BELOW_STAFF_SS` on `Staff`, repointing the latter two at
   `StaffPosition.MIN_SP` / `MAX_SP` and `Staff.HALF_SPACE_SS`.
8. Update `Staff`'s class Javadoc: it holds staff geometry and the half-staff-space
   unit; the pitch grid and its bounds are `StaffPosition`'s.

---

## ✅ Phase 4: Engraving Teardown

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/engraving/BarStroke.java, src/main/java/songscribe/engraving/StaffHeaderMetrics.java, src/main/java/songscribe/engraving/LineThickness.java, src/main/java/songscribe/engraving/SMuFLConstants.java, src/main/java/songscribe/engraving/package-info.java  <br>
**Recommended model/effort:** Sonnet, medium — deletions and two contained edits, all fully specified

### Tasks

1. In `BarStroke`, replace the constructor-assigned `widthSs` field with a
   `widthSs()` that computes its answer on call. `THIN` and `THICK` return
   `EngravingConstants.THIN_BARLINE_SS` and `THICK_BARLINE_SS`; `DOTS` returns
   `SMuFLMetadata.advanceWidthSs(SMuFLGlyph.REPEAT_DOTS)`. This removes the font
   load from `BarStroke`'s class initializer, and so from `dom/ElementType`, which
   reaches `BarStroke`'s constants by static import — state that reason in the
   Javadoc of whatever holds the `DOTS` lookup, because it is the constraint that
   would otherwise be undone by someone hoisting the value back into a field.
2. Add `public static final double SEPARATION_SS` to `BarStroke`, moved from
   `LineThickness.BARLINE_SEPARATION_SS` with its Javadoc, computed as
   `EngravingConstants.LILYPOND_BASE_THICKNESS_SS * 3.0` with the multiplier named
   as `LineThickness` names it today.
3. Strip `StaffHeaderMetrics` to the three gaps that are genuinely the staff
   header's: `CLEF_GAP_SS`, `KEY_SIGNATURE_FIRST_NOTE_GAP_SS`,
   `CLEF_FIRST_NOTE_SPAN_SS`. Delete `KEY_SIGNATURE_PADDING_SS`,
   `CANCELLATION_TO_KEY_GAP_SS`, `accidentalInkBboxSs`, `naturalKerningSs`,
   `NATURAL_OVERLAP_KERNING_SS`, `NATURAL_TOUCHING_KERNING_SS`,
   `RIGHT_EDGE_DESCENDER`, `RIGHT_EDGE_TOP_CORNER` and `LEFT_EDGE_SHIFT` — Phase 6
   recreates them on `Key.DrawnAccidental`, and Phase 2 recreates
   `KEY_SIGNATURE_PADDING_SS` on `EngravingConstants`.
4. Rewrite `StaffHeaderMetrics`'s class Javadoc so it describes only what remains.
   Its current text explains accidental stacking and natural kerning, which no
   longer live there. Keep the explanation of LilyPond's `space-alist` and the
   directional table, which is what the three surviving gaps come from.
5. Delete `src/main/java/songscribe/engraving/LineThickness.java` and
   `src/main/java/songscribe/engraving/SMuFLConstants.java`.
6. Update `src/main/java/songscribe/engraving/package-info.java` to describe the
   package: staff geometry, the pitch grid, and the engraving measurements the
   program draws with. Keep the `@NullMarked` annotation.

---

## ✅ Phase 5: SMuFL Engraving Defaults

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/smufl/EngravingDefaults.java, src/main/java/songscribe/smufl/SMuFLMetadata.java, src/main/java/songscribe/smufl/package-info.java  <br>
**Recommended model/effort:** Sonnet, low — a dead path with a single consumer, verified by symbol lookup

`songscribe.smufl` was completed by an earlier design pass. This phase removes a
path that is dead: the three constants that read it are deleted in Phase 4, and
symbol lookup confirms nothing else references any of it.

### Tasks

1. Delete `src/main/java/songscribe/smufl/EngravingDefaults.java`.
2. In `SMuFLMetadata`, delete the `engravingDefaults` field, the public static
   `engravingDefaults()` accessor, the `parseEngravingDefaults` method, and the
   constructor line that assigns the field. Leave `bboxSs`, `advanceWidthSs`,
   `stemAnchors` and their parsing untouched.
3. If the `engravingDefault` helper (singular) is left with no caller after step 2,
   delete it too. Verify with `jet_brains_find_referencing_symbols` rather than by
   reading.
4. Add a sentence to `src/main/java/songscribe/smufl/package-info.java` stating
   what is now true: the application reads glyph bounding boxes, advance widths and
   stem anchors from the font, and takes **no** engraving default from it — every
   stroke width, gap and beam measurement is LilyPond's or this program's own.
   Place it near the existing paragraph about every lookup being total.

---

## ✅ Phase 6: Drawn Accidental

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/Key.java  <br>
**Recommended model/effort:** Opus, high — moves a domain rule and turns a documented precondition into a total function

A key signature is drawn in the staff header, at a mid-line key change, and as a
cautionary. The advance rule serves all three, which is why it cannot stay on a
class named for one of them.

### Tasks

1. Write the contract for `DrawnAccidental.advanceSs` before implementing it. It
   is the promise "how far the pen moves after drawing me, given what follows",
   and it must be **total**: it answers the plain ink width when this accidental's
   glyph is not `SMuFLGlyph.ACCIDENTAL_NATURAL`, when `next` is `null`, or when
   `next` carries a non-zero `leadingGapSs`. State why kerning stays within a
   group — a non-zero leading gap on the following accidental already holds the two
   apart, so the run ends there.
2. Move onto `Key.DrawnAccidental`, as `private static final` members, the five
   constants deleted from `StaffHeaderMetrics` in Phase 4: `RIGHT_EDGE_DESCENDER = -6`,
   `RIGHT_EDGE_TOP_CORNER = 3`, `LEFT_EDGE_SHIFT = 3`,
   `NATURAL_OVERLAP_KERNING_SS = 0.3`, `NATURAL_TOUCHING_KERNING_SS = 0.15`, with
   the comment explaining that LilyPond models an accidental's silhouette on a grid
   of half staff positions.
3. Fix the direction the two kerning constants are documented with. They currently
   read "Extra space **before** a natural…". The kerning is added to the natural's
   own advance, so it lands **after** it, ahead of the accidental to its right —
   which is what `naturalKerningSs`'s summary and `DrawnAccidental`'s `advanceSs`
   component doc both already say.
4. Move `naturalKerningSs`'s body onto `DrawnAccidental` as a `private` helper.
   Keep the coordinate-system comment ("SongScribe staff positions grow downward,
   LilyPond's grow upward") — it is the reason for the negation and nothing else
   records it.
5. Add `public static double inkWidthSs(SMuFLGlyph glyph)` to `DrawnAccidental`,
   replacing `StaffHeaderMetrics.accidentalInkBboxSs`. It returns
   `SMuFLMetadata.bboxSs(glyph).widthSs()`. Carry across the existing Javadoc
   explaining that this is deliberately the ink extent rather than the advance
   width `SMuFLMetadata.advanceWidthSs` reports, because LilyPond stacks key
   signature accidentals ink edge to ink edge. The name says width, not bbox — the
   old name said bbox and returned a width.
6. Add `public double advanceSs(@Nullable DrawnAccidental next)` implementing the
   contract from task 1.
7. Move `CANCELLATION_TO_KEY_GAP_SS = 0.5` from `StaffHeaderMetrics` onto
   `DrawnAccidental` with its Javadoc. It is the gap between a cancellation's run
   of naturals and the key signature that follows, and it becomes a
   `leadingGapSs` — the same field `advanceSs` tests. Repoint `Key`'s one use of
   it, at the `groupGapSs` local.
8. Collapse `Key.withAdvances` to a map over the accidentals that calls
   `advanceSs(next)`, with no `if`. The three conditions it tests today move
   inside `advanceSs` as part of making it total.
9. Repoint `Key`'s remaining use of `StaffHeaderMetrics.accidentalInkBboxSs` to
   `DrawnAccidental.inkWidthSs`.

---

## ✅ Phase 7: Dom Call Sites

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/ElementType.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/BarAppearance.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical repointing plus two contained deletions

### Tasks

1. In `ElementType`, repoint every constant per the *Constant mapping* tables:
   `SMuFLConstants.STEM_LENGTH_SS` and `GRACE_NOTE_STEM_LENGTH_SS` to
   `StemMetrics`; `Staff.STAFF_HEIGHT_SS` and `STAFF_HALF_SS` are unchanged; the
   two `StaffHeaderMetrics.accidentalInkBboxSs` calls in
   `computeKeySignatureBoundsSs` become `Key.DrawnAccidental.inkWidthSs`.
   `BarStroke`'s constants keep their names and their static imports.
2. In `BarAppearance`, repoint `LineThickness.THIN_BARLINE_SS` and
   `VOLTA_BRACKET_SS` to `EngravingConstants`, and
   `LineThickness.BARLINE_SEPARATION_SS` to `BarStroke.SEPARATION_SS`.
3. In `Ending`, delete the `Math.max` in `getSpanWidthSs`, leaving
   `endXSs - anchorXSs + getEndElementWidthSs()`. The clamp guards a
   single-element bracket, and that state cannot occur: an ending cannot be
   created from one element, and shrinking one to a single element deletes it.
   `Ending.findRepeatSplitIndex` scans `anchorIndex + 1` to `endIndex` for the
   repeat that splits the two brackets, so a valid ending is at least three
   elements wide. Delete the `SMuFLConstants` import and the comment above the
   deleted line.
4. In `Span`, remove the nullability from `endElement`: drop `@Nullable` from the
   field, from `setEndElement`'s parameter, and from `getEndElement`'s return.
   Delete the `if (end == null)` branch in `getEndElementWidthSs` so it is
   `endElement.getType().getElementWidthSs()`, and remove the paragraph of its
   Javadoc describing the fallback. The field is assigned only by the constructor,
   whose parameter is a non-null `StaffElement`, and by `setEndElement`, whose only
   callers are `Line.setElement` and `Line.mergeOverlappingSpans` — both passing
   non-null.
5. Check `Span.anchorElement` the same way, with
   `jet_brains_find_referencing_symbols` on the field and on `setAnchorElement`. If
   nothing can assign null, remove its nullability too. If something can, leave it
   and **report what assigns null** rather than changing it.

---

## ✅ Phase 8: Layout Call Sites

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/BeamMath.java, src/main/java/songscribe/layout/BeamScoring.java, src/main/java/songscribe/layout/NoteGeometry.java, src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/layout/CautionaryKeySignature.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/layout/ElementColumn.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical, but the staff-position table requires classifying each site before changing it

### Tasks

1. Repoint every constant in these files per the *Constant mapping* tables.
2. For each `Staff.spToSs` and `Staff.STAFF_POSITION_OFFSET_SS` site in these
   files, **classify it first** using *The staff-position grid* table: a position,
   a length, or a count of half spaces. Every `spToSs` site in `layout/` passes a
   position — `element.getStaffPosition()`, `notePositionSp`, `topStaffPosition`,
   `staffPositionSp` — so all become `StaffPosition.toSs`. The
   `STAFF_POSITION_OFFSET_SS` sites are mixed: `LayoutEngine:1056, 1279, 1294, 1297`
   are the **length** and become `Staff.HALF_SPACE_SS`.
3. In `LayoutEngine`, delete `TIE_LINE_THICKNESS_SS` and point its readers at
   `EngravingConstants.LILYPOND_BASE_THICKNESS_SS`. It is a third private copy of
   `0.1`, documented as "LilyPond default layout line-thickness", and it is private
   and feeds only `TIE_MID_THICKNESS_SS`, so the change stays inside the file.
4. In `LayoutEngine`, repoint `Staff.ssToSp` to `StaffPosition.fromSs`. **This
   changes behaviour**: `fromSs` clamps into `MIN_SP..MAX_SP` where `ssToSp` did
   not. Read the call site and confirm a clamped result is what it wants; if it is
   not, stop and report rather than working around it.
5. In `NoteGeometry`, delete `public static final double STEM_WIDTH_SS`, a pure
   public re-export of `LineThickness.STEM_SS` that gives the value a second public
   name. Point its readers at `StemMetrics.STEM_SS`. Repoint
   `SMuFLConstants.LEDGER_LINE_LENGTH_FRACTION` to `LedgerLine.LENGTH_FRACTION`
   and `AUGMENTATION_DOT_WIDTH_SS` to the direct `SMuFLMetadata` query.
6. In `BeamScoring`, repoint only the **initializers** of `STAFF_RADIUS_SS`,
   `STAFF_LINE_THICKNESS_SS`, `BEAM_THICKNESS_SS` and `BEAM_TRANSLATION_SS`. Keep
   the four constants themselves. They are deliberate: this class is a
   line-by-line port of LilyPond's `beam-quanting.cc`, and each names the LilyPond
   term it stands for so the ported formulas read like their originals. They are
   package-private, so they add no reachable second name. Do not delete them.
7. In `HorizontalSpacingCalculator`, repoint `SMuFLConstants.G_CLEF_ADVANCE_WIDTH_SS`
   to the direct `SMuFLMetadata` query and
   `StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS` to `EngravingConstants`. Leave
   `CLEF_GAP_SS`, `KEY_SIGNATURE_FIRST_NOTE_GAP_SS` and `CLEF_FIRST_NOTE_SPAN_SS`
   pointing at `StaffHeaderMetrics`, which keeps them.

---

## ✅ Phase 9: Stacking Call Sites

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/layout/stacking/StackingUtils.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/stacking/SystemStacker.java, src/main/java/songscribe/layout/stacking/VerticalStackingCalculator.java, src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java  <br>
**Recommended model/effort:** Sonnet, medium — `StackingUtils` holds nine sites of one constant serving three different purposes

### Tasks

1. Repoint every constant in these files per the *Constant mapping* tables.
2. In `StackingUtils`, delete `STAFF_LINE_HALF_THICKNESS_SS` and point its readers
   at `EngravingConstants.STAFF_LINE_HALF_THICKNESS_SS`.
3. Classify each of `StackingUtils`'s nine `STAFF_POSITION_OFFSET_SS` sites before
   changing it, using *The staff-position grid* table:
   - `:60`, `:67`, `:149`, `:183`, `:201`, `:219`, `:602` multiply a **position**
     and become `StaffPosition.toSs(…)`.
   - `:103` — `Staff.STAFF_HALF_SS + Staff.STAFF_POSITION_OFFSET_SS` — is a
     **length** and becomes `Staff.HALF_SPACE_SS`.
   - `:593` — `centerYSs / Staff.STAFF_POSITION_OFFSET_SS` — is a staff-space to
     position conversion. **It does not round today**, and `:602` multiplies the
     result back. `StaffPosition.fromSs` both rounds and clamps, so substituting it
     changes the answer. Read `:590`–`:605` and decide whether the rounding is what
     the code wants; if it is not, leave the division expressed against
     `Staff.HALF_SPACE_SS` and report the site rather than forcing it through
     `fromSs`.
4. In `NoteAttachedStacker`, `:371` and `:389` multiply a **position** and become
   `StaffPosition.toSs`. Its `Staff.spToSs` site is also a position.
5. In `SystemStacker.stackTempoMark`, repoint `SMuFLConstants.NOTE_HEAD_INK_WIDTH_SS`
   to `SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).widthSs()`. The method's
   Javadoc already says the mark "begins one notehead width right of the header's
   right edge", so the direct query says what the code means.

---

## ✅ Phase 10: Renderer Call Sites

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/renderer/BarRenderer.java, src/main/java/songscribe/ui/renderer/StaffElementRenderer.java, src/main/java/songscribe/ui/renderer/TupletRenderer.java, src/main/java/songscribe/ui/renderer/HairpinRenderer.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/ui/renderer/SlideRenderer.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/ui/renderer/RestRenderer.java, src/main/java/songscribe/ui/renderer/KeySignatureRenderer.java  <br>
**Recommended model/effort:** Sonnet, medium — contains all three delta sites, where a wrong conversion is a silently misplaced glyph

### Tasks

1. Repoint every constant in these files per the *Constant mapping* tables.
2. `BeamGroupRenderer` and `KeySignatureRenderer` each have one `Staff.spToSs`
   site passing a **position** — `element.getStaffPosition()` and
   `accidental.staffPositionSp()` — which become `StaffPosition.toSs`.
3. In `RenderingUtils.forEachLedgerLineYSs`, `Staff.spToSs(i - staffPosition)`
   converts a **difference** of two staff positions, not a position. The method's
   own Javadoc calls it "the Y offsets … relative to the note's staff position".
   It becomes `Staff.halfSpacesToSs(i - staffPosition)`.
4. In `RestRenderer`, change the two rest offsets from half-staff-space `int`s to
   staff-space `double`s and delete their conversions:
   `SEMIBREVE_REST_Y_OFFSET = -2` becomes `SEMIBREVE_REST_Y_OFFSET_SS = -1.0`, and
   `MINIM_REST_Y_OFFSET = 0` becomes `MINIM_REST_Y_OFFSET_SS = 0.0`. Both are
   offsets from the middle line, so stating them in the unit the surrounding code
   works in removes a conversion rather than relocating it. Keep the existing
   comments ("Above the middle line", "On the middle line"). The third
   `Staff.spToSs` site in this file passes `note.getStaffPosition()`, a
   **position**, and becomes `StaffPosition.toSs`.
5. `RestRenderer`'s two constants are the one change in this phase that alters a
   drawn position if it is done wrong: `-2` half-spaces is `-1.0` staff spaces, not
   `-2.0`. Verify against `Staff.HALF_SPACE_SS` rather than by eye.

---

## ✅ Phase 11: Score Component Call Sites

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/score/PitchShifter.java, src/main/java/songscribe/ui/component/score/PreviewElementManager.java, src/main/java/songscribe/ui/component/score/InsertionMarkerOverlay.java, src/main/java/songscribe/ui/component/score/NoteDragHandler.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical, with two behaviour-bearing `ssToSp` sites

### Tasks

1. Repoint every constant in these files per the *Constant mapping* tables.
   `Staff.MIN_STAFF_POSITION_SP` and `MAX_STAFF_POSITION_SP` become
   `StaffPosition.MIN_SP` and `MAX_SP` in `PitchShifter`,
   `PreviewElementManager` and `InsertionMarkerOverlay`, including in `{@link}`
   references in their Javadoc.
2. Every `Staff.spToSs` site in these files passes a **position** and becomes
   `StaffPosition.toSs`.
3. `PreviewElementManager` and `NoteDragHandler` each call `Staff.ssToSp`, which
   becomes `StaffPosition.fromSs`. **This changes behaviour**: `fromSs` clamps into
   `MIN_SP..MAX_SP` where `ssToSp` did not. Both callers currently range-check or
   clamp afterwards, so read each and say in the task's report whether the
   downstream check is now redundant — do not delete it in this phase.
4. `PreviewElementManager.isValidStaffPosition` tests
   `staffPosition >= MIN && staffPosition <= MAX`, and `PitchShifter.clampDelta`
   clamps a delta against both bounds. Leave both in place and repoint their
   constants only. Whether either becomes unnecessary is a question for design pass
   step 6, not this plan.

---

## ✅ Phase 12: Test Tree Repoint

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/test/java/songscribe/dom/KeySignatureExtentTest.java, src/test/java/songscribe/layout/CautionaryKeySignatureTest.java, src/test/java/songscribe/dom/BarAppearanceTest.java, src/test/java/songscribe/layout/EndingBracketGeometryTest.java, src/test/java/songscribe/engraving/StaffGeometryRegressionTest.java, src/test/java/songscribe/ui/renderer/LedgerLineOffsetTest.java  <br>
**Recommended model/effort:** Sonnet, medium — repointing, plus one expectation that must change because the behaviour did

### Tasks

1. Repoint every reference in these test files per the *Constant mapping* tables,
   the same way production call sites are repointed.
2. In `StaffGeometryRegressionTest`, `Staff.ssToSp` becomes
   `StaffPosition.fromSs`, and the cases asserting that an out-of-range input is
   returned unchanged must now expect it **clamped** to `StaffPosition.MIN_SP` or
   `MAX_SP`. This is the one expectation in the suite that changes because the
   contract changed, not because a value moved. Rewrite those cases against the
   new contract rather than deleting them.
3. In `StaffGeometryRegressionTest`, `LineThickness.beamStackHeightSs` and
   `beamTranslationSs` become `BeamMetrics.…`.
4. In `KeySignatureExtentTest`, the natural-kerning cases call
   `StaffHeaderMetrics.naturalKerningSs`, which no longer exists. Rewrite them
   against `Key.DrawnAccidental.advanceSs(next)`, which now returns ink width plus
   kerning: assert the difference between an accidental's advance with and without
   a kerning neighbour, so the case still isolates the kerning amount. Expected
   values do not change.
5. In `LedgerLineOffsetTest`, no repointing is needed — it calls
   `RenderingUtils.forEachLedgerLineYSs`, whose signature does not change. Confirm
   the expected offsets are unchanged; if they are not, the delta conversion in
   Phase 10 was done wrong and that is the finding.

---

## ✅ Phase 13: Gate

**Status:** Complete  <br>
**BlockedBy:** 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, medium — runs the build and reads failures; a failure here is information about the design, not noise

### Tasks

1. Run `./scripts/compile.sh --test`. It builds both trees. Never `./gradlew`,
   `gradle`, `javac`, or `java -cp`. Fix every error before proceeding.
2. Run `./scripts/test.sh KeySignatureExtentTest CautionaryKeySignatureTest BarAppearanceTest EndingBracketGeometryTest`.
3. Run `./scripts/test.sh StaffGeometryRegressionTest LedgerLineOffsetTest`. The
   hook `.claude/hooks/no-full-test-suite.sh` denies a run naming more than four
   classes, which is why this is two commands rather than one.
4. Both runs must report SUCCESS with every test passing. Never rerun a failure
   with extra flags, and never assume a failure is pre-existing.
5. A failing test means one of three things — the code is wrong, the test is
   wrong, or the contract is wrong. Weakening a contract to reach green is
   legitimate only when the contract was wrong about the domain, and then it is
   stated explicitly, never done silently. Report which of the three each failure
   was.
6. Report the outcome plainly: what passed, what failed, and what was changed to
   fix it. A green build proves integration, never correctness — do not report it
   as evidence the restructure is right.
