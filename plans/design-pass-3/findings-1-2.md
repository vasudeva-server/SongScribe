# Design Pass 3 — `engraving` — Class Design
What steps 3 onward build. Nothing in the tree has changed yet.
## The class set
Constants live with the thing they measure. What belongs to no single engraved thing lives in `EngravingConstants`, which is also where the LilyPond base thickness lives.

| Type | Holds |
|---|---|
| `EngravingConstants` *(new)* | `LILYPOND_BASE_THICKNESS_SS` and every stroke width derived from it that has no per-thing home: staff line, thin and thick barline, hairpin, volta bracket, tuplet bracket, glissando, with their multipliers. Also `STAFF_LINE_HALF_THICKNESS_SS` and `KEY_SIGNATURE_PADDING_SS` |
| `StemMetrics` *(new)* | `STEM_SS`, `STEM_LENGTH_SS`, `GRACE_NOTE_STEM_LENGTH_SS` |
| `BeamMetrics` *(new)* | `BEAM_THICKNESS_SS`, `BEAM_TRANSLATION_SS`, `BEAM_BLOT_DIAMETER_SS`, `beamTranslationSs`, `beamStackHeightSs` |
| `LedgerLine` *(new)* | `LEDGER_LINE_SS`, `LEDGER_LINE_LENGTH_FRACTION` |
| `StaffPosition` *(new)* | the position value, its `MIN`/`MAX` bounds, and the conversions to and from staff spaces |
| `Staff` | staff height, half-height, the position offset, the above/below reservations |
| `BarStroke` | `THIN`, `THICK`, `DOTS`, `widthSs()`, and the barline separation |
| `StaffHeaderMetrics` | the header's own gaps only: `CLEF_GAP_SS`, `KEY_SIGNATURE_FIRST_NOTE_GAP_SS`, `CLEF_FIRST_NOTE_SPAN_SS` |
| `Key.DrawnAccidental` *(dom)* | gains the run's own spacing: `CANCELLATION_TO_KEY_GAP_SS`, the accidental silhouette model, the two natural-kerning amounts, and `advanceSs(next)` |
| `LineThickness` | **dissolves into `EngravingConstants`** |
| `SMuFLConstants` | **dissolves** |
## Deletions

**Dead constants.** `SMuFLConstants.REPEAT_BARLINE_DOT_SEPARATION_SS`, `LEDGER_LINE_THICKNESS_SS` and `TIE_MIDPOINT_THICKNESS_SS` are written by the static initializer and read by nothing. Each has a live LilyPond counterpart that is what actually gets drawn.

**The font's engraving-defaults path**, which exists only to feed those three: the `EngravingDefaults` record, `SMuFLMetadata.engravingDefaults()`, its backing field, and `parseEngravingDefaults`. This reaches into `smufl`, which pass 2 completed.

Then state the resulting fact in `smufl/package-info.java`: the application reads glyph boxes, advance widths and stem anchors from the font and takes no engraving default from it.

`Ending.getSpanWidthSs`**'s** `Math.max`**.** It clamps a single-element volta bracket, and that state cannot occur: an ending cannot be created from one element, and shrinking one to a single element deletes it. The method becomes `endXSs - anchorXSs + getEndElementWidthSs()`.

`Span`**'s nullability.** `endElement` is assigned only by the constructor, whose parameter is non-null, and by `setEndElement`, whose two callers — `Line.setElement` and `Line.mergeOverlappingSpans` — both pass non-null. Drop `@Nullable` from the field, from `setEndElement`'s parameter and from `getEndElement`'s return, and delete the null branch in `getEndElementWidthSs`. Check `anchorElement` the same way and report what it turns out to be.

`LayoutEngine.TIE_LINE_THICKNESS_SS`, a third private copy of `0.1`. Its two readers point at the base thickness in `EngravingConstants`. It is private and feeds only `TIE_MID_THICKNESS_SS`, so the change stays inside `LayoutEngine`.
## Glyph measurements become direct queries
`SMuFLMetadata` is a lazy holder whose lookups are array indexing by enum ordinal, so caching its answers in `static final` fields saves nothing and forces the font to load at class-init time — the hazard `SMuFLMetadata`'s own Javadoc names.

| Constant | Becomes |
|---|---|
| `G_CLEF_ADVANCE_WIDTH_SS` | `advanceWidthSs(G_CLEF)` at its 3 sites, all in `layout/HorizontalSpacingCalculator` |
| `AUGMENTATION_DOT_WIDTH_SS` | `advanceWidthSs(AUGMENTATION_DOT)` at `layout/NoteGeometry` (2) and `layout/EndingBracketGeometry` (1) |
| `NOTE_HEAD_INK_WIDTH_SS` | `bboxSs(NOTEHEAD_BLACK).widthSs()` at `layout/stacking/SystemStacker` and `layout/ElementColumnBuilder`; its other two uses are the deletions above |
| `REPEAT_DOTS_ADVANCE_WIDTH_SS` | folds into `BarStroke.DOTS`, computed in `widthSs()` rather than in the constructor |

Computing `DOTS` on call rather than at enum init removes the font load from `BarStroke`'s class initializer, and so from `dom/ElementType`, which reaches `BarStroke`'s constants by static import.
## Changes to what survives
**Every width is base × named multiplier, with no exception.** `STAFF_LINE_SS` currently assigns the base directly, which reads as an alias:

```java
private static final double STAFF_LINE_MULTIPLIER = 1.0;
public static final double STAFF_LINE_SS = LILYPOND_BASE_THICKNESS_SS * STAFF_LINE_MULTIPLIER;
```

`StaffPosition` **carries its own range.** `MIN`/`MAX` move onto it, `ssToSp` clamps into range, and `spToSs`/`ssToSp` become methods on it rather than statics on `Staff`. The two range checks that exist today — `PreviewElementManager.isValidStaffPosition` and `PitchShifter.clampDelta` — stop checking a raw `int`.

`accidentalInkBboxSs` **→** `accidentalInkWidthSs`**.** It returns a width, not a box. Three call sites, in `dom/ElementType` (2) and `dom/Key` (1). Rename via `jet_brains_rename` with `rename_in_comments: false`.

**Four package-private constants in** `LineThickness` **become private**: `STEM_MULTIPLIER`, `VOLTA_BRACKET_MULTIPLIER`, `TUPLET_BRACKET_MULTIPLIER`, `LILYPOND_BASE_THICKNESS_SS`. Nothing outside the class reads them and their six siblings are already private.

**Name both literals in** `Staff.MIN_STAFF_POSITION_SP` — `-(STAFF_LINES_ABOVE + 2) * 2`. The `* 2` converts staff lines to half-staff-space positions; the `+ 2` is the two further positions past the outermost ledger line. Same pair in `MAX_STAFF_POSITION_SP`.

**Fix the kerning direction in** `StaffHeaderMetrics`**.** The two private constants are documented as space "before" a natural. The kerning is added to the natural's own advance in `Key.withAdvances`, so it lands after it. `naturalKerningSs`'s summary and `Key.DrawnAccidental`'s `advanceSs` doc both say "to its right".

**Name the source of every constant whose source is not obvious.**

| Constant | Source to state |
|---|---|
| `STEM_LENGTH_SS` | LilyPond `Stem.details.lengths`, first entry — `scm/define-grobs.scm:3453`. Its current "SMuFL standard stem length" is wrong; SMuFL declares no stem length |
| `GRACE_NOTE_STEM_LENGTH_SS` | a SongScribe decision, not a port |
| `LEDGER_LINE_LENGTH_FRACTION` | LilyPond `LedgerLineSpanner.length-fraction` |
| `STEM_MULTIPLIER` | the same LilyPond `Stem` grob's `thickness`, `scm/define-grobs.scm:3474` |
## Where natural kerning belongs 
`naturalKerningSs(int, int)` is static on `StaffHeaderMetrics` and total in its signature but partial in its contract: _"Only call this when the left glyph of the pair is a natural; the sharp and flat have no vertical right edge and need no kerning."_ Its one caller enforces that, together with two further conditions:

```java
// Key.withAdvances
var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(accidental.glyph());

if (next != null
    && accidental.glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL
    && next.leadingGapSs() == 0) {

    advanceSs += StaffHeaderMetrics.naturalKerningSs(
        accidental.staffPositionSp(), next.staffPositionSp());
}
```

`Key.DrawnAccidental` already is the type this is about — `glyph`, `staffPositionSp`, `leadingGapSs`, `advanceSs`. "How far the pen advances after me, given what follows" is a function of its value and its neighbour's, which under _Placement_ in `design.md` puts the operation on it.

**The operation moves onto `DrawnAccidental`.** A key signature is drawn in the
staff header, at a mid-line key change and as a cautionary, so the advance rule
serves all three and cannot belong to a class named for one of them.

`DrawnAccidental.advanceSs(@Nullable DrawnAccidental next)` returns the ink width
plus any kerning, and answers the plain ink width when this glyph is not a natural,
`next` is absent, or `next` has a leading gap. It is total, so the prose
precondition disappears rather than moving, and `withAdvances` collapses to a map.

**The silhouette model moves with it**, and so does `CANCELLATION_TO_KEY_GAP_SS`.
`RIGHT_EDGE_DESCENDER`, `RIGHT_EDGE_TOP_CORNER`, `LEFT_EDGE_SHIFT`,
`NATURAL_OVERLAP_KERNING_SS` and `NATURAL_TOUCHING_KERNING_SS` become private to
`DrawnAccidental`, so the whole advance rule reads in one place.

The gap is part of the same rule rather than a neighbour of it: it becomes a
`leadingGapSs`, and `withAdvances` already tests `next.leadingGapSs() == 0` as the
condition for applying kerning, so the two are coupled in one expression today.

This puts a LilyPond port in `dom`, which is the established pattern rather than an
exception — `layout/LayoutEngine` holds the `bezier-bow.cc` tie constants and
`layout/BeamScoring` the whole `beam-quanting.cc` port, each next to the code it
serves. The grid is LilyPond's approximation of an accidental's outline, not
anything the font declares, so `smufl` is not its home either.

`KEY_SIGNATURE_PADDING_SS` does not follow. It positions a signature against a
barline or the staff edge and is read only by layout — five sites in
`layout/CautionaryKeySignature`, two in `HorizontalSpacingCalculator` as the rest
and the minimum ink gap of the `BARLINE_TO_KEY_SIGNATURE` gap kind. It goes to
`EngravingConstants`. It is not header-specific, so `StaffHeaderMetrics` is not its
home; and being one constant, it needs no class of its own.

- **Move the whole operation** onto `DrawnAccidental`, silhouette constants and all. Rejected on sight: `RIGHT_EDGE_DESCENDER`, `RIGHT_EDGE_TOP_CORNER`, `LEFT_EDGE_SHIFT` and the two kerning amounts are engraving geometry, and this would put them in the document model.
  
`Key.DrawnAccidental` is inside `dom/Key`, pass 0's completed target, so this is a fourth package the pass reaches into.
### The local re-aliases
Four classes read an `engraving` constant once and republish it under a local name. Reading them, they are three different things:

**A pure public re-export — delete it.** `layout/NoteGeometry.STEM_WIDTH_SS` is `public static final double STEM_WIDTH_SS = LineThickness.STEM_SS`, documented "Stem width in staff-space units (LilyPond multiplier-derived)". It adds nothing and, being public, gives the value a second public name that callers can reach either way. Delete it; its readers use `StemMetrics.STEM_SS`.

**A named derived value — keep, but give it a home.**`layout/stacking/StackingUtils.STAFF_LINE_HALF_THICKNESS_SS` is `LineThickness.STAFF_LINE_SS / 2.0`, package-private and undocumented. It is half of something rather than an alias for it, so deleting it means inlining `/ 2.0` at each of its nine call sites. Move it to `EngravingConstants` beside the staff line width it derives from, and document it.

**Deliberate LilyPond vocabulary — leave them.** `layout/BeamScoring` is a line-by-line port of `beam-quanting.cc`, and its four package-private constants each name the LilyPond term they stand for so the ported formulas read like their originals: `STAFF_RADIUS_SS` ("LilyPond's staff radius"), `STAFF_LINE_THICKNESS_SS` ("LilyPond's `slt`"), `BEAM_THICKNESS_SS` ("LilyPond's `beam_thickness`"), and `BEAM_TRANSLATION_SS`. This is the same justification `LineThickness.STAFF_SPACE_SS` states for itself. Deleting them would make a careful port diverge from its source for no gain — they are package-private, so they add no reachable second name.

Say if you want the `BeamScoring` four removed anyway.
