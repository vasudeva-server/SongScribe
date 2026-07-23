# Issue #560 — LilyPond-style optical spacing corrections

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Foundation — stem/direction/position accessors](#-phase-1-foundation--stemdirectionposition-accessors) | ✅ Complete | — |
| 2 | [Opposite-stem and same-direction corrections](#-phase-2-opposite-stem-and-same-direction-corrections) | ✅ Complete | — |
| 3 | [Downstem-after-barline correction and wiring](#-phase-3-downstem-after-barline-correction-and-wiring) | ✅ Complete | — |
| 4 | [Manual visual verification](#-phase-4-manual-visual-verification) | ✅ Complete | — |
| 5 | [Whitespace-aware levelling under compression](#-phase-5-whitespace-aware-levelling-under-compression) | ✅ Complete | — |
| 6 | [Automated tests](#-phase-6-automated-tests) | ✅ Complete | — |

## ✅ Phase 1: Foundation — stem/direction/position accessors

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 5, low effort — mechanical accessor additions mirroring an existing method in the same file

### Y convention (applies to all of Phase 1 and beyond)

The Y convention throughout `ElementColumn` is **screen-down**: negative Ss is above the staff middle line, positive Ss is below it (see `ElementColumnBuilder.calculateStemTopSs`/`calculateStemBottomSs`, `src/main/java/songscribe/layout/ElementColumnBuilder.java:349-396`). `getAbsoluteTopYSs()` therefore returns the numerically **smaller** (higher) value for a column and `getAbsoluteBottomYSs()` the numerically **larger** (lower) value.

### Tasks

1. In `src/main/java/songscribe/layout/ElementColumn.java`, in the "Stem Information" section immediately before the existing `getAbsoluteTopYSs()` method (around line 332), add a single source of truth for the element's notehead-center position in Ss:
   ```java
   /**
    * Returns this column's notehead-center staff position converted to ss. Screen-down convention:
    * a more negative value is higher on the staff. Single source of truth for the absolute vertical
    * anchor shared by {@link #getAbsoluteTopYSs()}, {@link #getAbsoluteBottomYSs()}, and the optical
    * spacing corrections.
    */
   public double getPositionSs() {
       return Staff.spToSs(getElement().getStaffPosition());
   }
   ```

2. Refactor the existing `getAbsoluteTopYSs()` to consume `getPositionSs()` instead of recomputing the conversion inline (DRY — the `Staff.spToSs(getElement().getStaffPosition())` expression must live in exactly one place):
   ```java
   public double getAbsoluteTopYSs() {
       return getPositionSs() + getStemTopSs();
   }
   ```

3. Immediately after `getAbsoluteTopYSs()`, add its mirror:
   ```java
   /**
    * Returns the absolute layout-Y bottom of this column: the element's notehead-center position
    * plus the note-local stem bottom extent. Single source of truth for the absolute bottom used
    * when measuring vertical overlap between columns. Screen-down convention: this is the
    * numerically larger (lower) of the column's two vertical extremes.
    */
   public double getAbsoluteBottomYSs() {
       return getPositionSs() + getStemBottomSs();
   }
   ```

4. In the same file's "Primary Element" section (next to the existing `isRest()`/`isBarline()`/`isGraceNote()` methods, `ElementColumn.java:162-178`), add:
   ```java
   /**
    * Returns whether this column's element renders a stem (a pitched or grace note shorter than
    * a whole note).
    */
   public boolean hasStem() {
       return element.getType().isNoteWithStem();
   }
   ```

5. In the same section, add:
   ```java
   /**
    * Returns the stem direction of this column's element. Only meaningful when {@link #hasStem()}
    * is {@code true} — for rests, whole notes, and barlines this returns whatever direction the
    * underlying element happens to hold (default {@code DOWN}), which callers must not treat as a
    * real stem direction.
    */
   public StaffElement.Direction getDirection() {
       return element.getDirection();
   }
   ```
   `StaffElement` is already imported in this file (`import songscribe.dom.StaffElement;`, `ElementColumn.java:27`), so no new import is needed.

6. Before finishing, confirm no existing ASCII diagram comment sits adjacent to the edited "Stem Information" methods; if one does and it now describes stale behavior, update it in the same change (diagram maintenance is part of the edit). The edits here are purely additive/one-line, so this is expected to be a no-op check.

7. Run `./scripts/compile.sh` exactly as-is (no flags, no pipes). Report SUCCESS or FAILURE; fix any errors before finishing this phase.

## ✅ Phase 2: Opposite-stem and same-direction corrections

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 5, medium effort — new file with three small formulas plus two shared helpers; every constant, sign, and guard condition is fully specified below, no design decisions remain

### Context

This phase creates `src/main/java/songscribe/layout/OpticalSpacing.java`, a new horizontal-spacing correction pass modeled directly on `src/main/java/songscribe/layout/LyricLift.java` (read that file first — same package, same shape: `final class` with a private no-arg constructor, a public static entry point taking `List<Spring> springs` and `List<ElementColumn> columns` and returning a new `List<Spring>`, per-gap loop using index `i` for the gap between `columns.get(i)` (prev) and `columns.get(i + 1)` (curr)). There is exactly one fewer spring than columns (`springs.size() == columns.size() - 1`; see `LyricLift.java:81` and `HorizontalSpacingCalculator.buildSprings`), so `columns.get(i + 1)` is always in bounds for `i` in `[0, springs.size())`. Copy the license header verbatim from `LyricLift.java:1-19`.

This pass ports three of LilyPond's optical-spacing corrections (`lily/note-spacing.cc`, `lily/staff-spacing.cc`) — small additive nudges to the ideal (uncompressed) horizontal gap between adjacent note columns, to compensate for optical illusions caused by stem-direction geometry. Corrections only ever adjust a `Spring`'s `restSs` via `Spring.withRestSs(...)` (`src/main/java/songscribe/layout/Spring.java:78`) — never `strutSs` (the hard collision floor), matching LilyPond's own discipline that these corrections are perceptual nudges to the ideal, never collision-safety changes.

> **Amended in Phase 5:** corrections now go through `Spring.withCorrectionSs(...)`, which adjusts `restSs` **and** `levelOffsetSs` together so the correction also survives compression (see Phase 5). Struts remain untouched, exactly as specified here.

**Collision-safety and the one-sided-narrowing property (must be captured in the class Javadoc).** The solver never uses raw `restSs` as a placed gap: it goes through `Spring.naturalLengthSs() = Math.max(restSs, strutSs)` (`Spring.java:65-71`) and uses `strutSs()` directly as the compression floor (`SpringSpacer.java:152,194,223`). Two consequences the implementer and the Phase-4 verifier must understand:
- A **negative** (narrowing) correction that would push `restSs` below `strutSs` is safe — the gap simply pins at `strutSs`; it never breaches the collision floor.
- Because of that same clamp, a narrowing correction has **no visible effect** on a gap that is already at (or below) its strut floor: widening always applies, but narrowing is only observable where the gap has slack above the collision floor. This is expected behavior, not a bug — do not "fix" it by inflating the constants.

**Grace notes are out of scope for these corrections.** LilyPond routes grace columns out of note-spacing entirely — a separate `Grace_spacing_engraver` builds a `GraceSpacing` spanner and grace columns are floated as loose columns (`lily/grace-spacing-engraver.cc`, `lily/spacing-loose-columns.cc`), so `note-spacing.cc`/`staff-spacing.cc` never see a grace column. SongScribe has no separate grace pass, so every correction below must explicitly early-return `0.0` when either column is a grace note (`ElementColumn.isGraceNote()`), reproducing LilyPond's structural exclusion with a guard.

A "knee" (a beam that changes stem direction mid-group) is also explicitly out of scope — LilyPond has a separate `knee_correction` for it that this port does not implement.

### Correction geometry and decision table (include this as a class-level ASCII comment in `OpticalSpacing.java`)

```
Screen-down Ss axis (negative = higher on the staff):

  −Ss ─── stem tip (UP stem)
        │
        │       prev (UP)        curr (DOWN)
  top ──┤       ┌───┐             ┌───┐   ← getAbsoluteTopYSs  (smaller / higher)
        │  ●════│   │       ●═════│   │
    0 ──┼──── staff middle line ─────────
        │       │   │notehead    │   │
  bot ──┤       └───┘             └───┘   ← getAbsoluteBottomYSs (larger / lower)
        │                          │
  +Ss ──                           └── stem tip (DOWN stem)

  verticalOverlapSs = min(bottoms) − max(tops)   (> 0 only where the spans intersect)

  prev       curr        fires when                          correction (Ss)
  ─────────────────────────────────────────────────────────────────────────────
  stem UP    stem DOWN   overlap>0, not knee, not grace      +ramp·0.5   (widen)
  stem DOWN  stem UP      overlap>0, not knee, not grace      −ramp·0.5   (narrow)
  stem X     stem X       |Δpos|>0.5, not grace               ±0.25   (widen if curr higher)
  barline    stem DOWN    overlap(staff span, curr)>0,        +ramp·0.4   (widen)
                          not grace
  otherwise                                                   0
      ramp = min(overlap / 3.5, 1.0)      (reference the named constants, not literals,
                                           in the actual code comment)
```

### Tasks

1. Create `src/main/java/songscribe/layout/OpticalSpacing.java`: license header (copied from `LyricLift.java:1-19`), `package songscribe.layout;`, imports `java.util.ArrayList`, `java.util.List`, `songscribe.dom.StaffElement`, `songscribe.engraving.Staff`. Declare `public final class OpticalSpacing` with a private no-arg constructor (mirroring `LyricLift`'s `private LyricLift() {}`). Put the ASCII geometry/decision comment above (adapted to reference the named constants) as a class-level comment. In the class Javadoc, state the collision-safety and one-sided-narrowing property described in Context. Add these named constants at the top of the class, each with a one-line comment noting it is a hand-tuned value ported from LilyPond's engraving defaults, not derived from anything:
   ```java
   /** Ported from LilyPond's NoteSpacing.stem-spacing-correction default (scm/define-grobs.scm). */
   private static final double OPPOSITE_STEM_MAX_CORRECTION_SS = 0.5;

   /** Ported from LilyPond's NoteSpacing.same-direction-correction default (scm/define-grobs.scm). */
   private static final double SAME_DIRECTION_MAX_CORRECTION_SS = 0.25;

   /**
    * Vertical overlap (Ss) at which the opposite-stem and downstem-after-barline corrections reach
    * full strength. LilyPond derives an equivalent saturation point from a hardcoded constant
    * applied inconsistently across two different internal unit scales; this is a single unified
    * value used by both corrections here instead.
    */
   private static final double STEM_OVERLAP_SATURATION_SS = 3.5;

   /** Minimum vertical gap (Ss) between two same-direction notes before the correction applies. */
   private static final double SAME_DIRECTION_THRESHOLD_SS = 0.5;
   ```

2. Implement two shared geometry helpers. `verticalOverlapSs` is overloaded — a primitive taking four explicit span bounds (so the barline correction in Phase 3 can pass a synthetic staff span) and a column-pair convenience overload that delegates to it. `saturatedMagnitudeSs` factors out the ramp shared by the opposite-stem and downstem-after-barline corrections:
   ```java
   /**
    * Length of the 1-D overlap between two vertical spans [topA, bottomA] and [topB, bottomB] in the
    * screen-down Ss convention (smaller = higher). Non-positive when the spans do not intersect.
    */
   private static double verticalOverlapSs(double topA, double bottomA, double topB, double bottomB) {
       return Math.min(bottomA, bottomB) - Math.max(topA, topB);
   }

   private static double verticalOverlapSs(ElementColumn a, ElementColumn b) {
       return verticalOverlapSs(
           a.getAbsoluteTopYSs(), a.getAbsoluteBottomYSs(),
           b.getAbsoluteTopYSs(), b.getAbsoluteBottomYSs());
   }

   /**
    * Ramps a correction from 0 up to {@code maxSs} as {@code overlapSs} grows from 0 to
    * {@link #STEM_OVERLAP_SATURATION_SS}, saturating at {@code maxSs} beyond that.
    */
   private static double saturatedMagnitudeSs(double overlapSs, double maxSs) {
       return Math.min(overlapSs / STEM_OVERLAP_SATURATION_SS, 1.0) * maxSs;
   }
   ```

3. Implement the opposite-stem correction:
   ```java
   private static double oppositeStemCorrectionSs(ElementColumn prev, ElementColumn curr) {
       if (!prev.hasStem() || !curr.hasStem()) {
           return 0.0;
       }

       // Grace-note gaps are governed by grace spacing, not these optical corrections — LilyPond
       // routes grace columns out of note-spacing entirely, so exclude them here.
       if (prev.isGraceNote() || curr.isGraceNote()) {
           return 0.0;
       }

       if (prev.getDirection() == curr.getDirection()) {
           return 0.0;
       }

       // Opposite-direction stems sharing one beam group is a "knee" — out of scope, so no
       // correction is applied for that case.
       if (prev.getBeamGroupId() != ElementColumn.NO_BEAM_GROUP
           && prev.getBeamGroupId() == curr.getBeamGroupId()) {
           return 0.0;
       }

       var overlapSs = verticalOverlapSs(prev, curr);

       if (overlapSs <= 0.0) {
           return 0.0;
       }

       return saturatedMagnitudeSs(overlapSs, OPPOSITE_STEM_MAX_CORRECTION_SS) * prev.getDirection().sign();
   }
   ```
   `StaffElement.Direction.sign()` (`src/main/java/songscribe/dom/StaffElement.java:808-810`) returns `+1` for `UP`, `-1` for `DOWN`. An up-then-down pair (`prev` UP) therefore widens (positive correction); a down-then-up pair (`prev` DOWN) narrows (negative correction) — matching the essay's rule that up-stem+down-stem gets extra space and down-stem+up-stem gets less.

4. Implement the same-direction correction (using `getPositionSs()` rather than reaching through `getElement().getStaffPosition()`):
   ```java
   private static double sameDirectionCorrectionSs(ElementColumn prev, ElementColumn curr) {
       if (!prev.hasStem() || !curr.hasStem()) {
           return 0.0;
       }

       if (prev.isGraceNote() || curr.isGraceNote()) {
           return 0.0;
       }

       if (prev.getDirection() != curr.getDirection()) {
           return 0.0;
       }

       var deltaSs = Math.abs(curr.getPositionSs() - prev.getPositionSs());

       if (deltaSs <= SAME_DIRECTION_THRESHOLD_SS) {
           return 0.0;
       }

       // Ss is screen-down here: a larger (more positive) staff position is a LOWER pitch.
       var currIsLower = curr.getPositionSs() > prev.getPositionSs();

       return currIsLower ? -SAME_DIRECTION_MAX_CORRECTION_SS : SAME_DIRECTION_MAX_CORRECTION_SS;
   }
   ```
   This is a simplified port of LilyPond's `same_direction_correction` (`lily/note-spacing.cc`): LilyPond compares chord-extremal notehead intervals, but SongScribe's domain model is confirmed monophonic (`StructuralElement` has a single `int staffPosition`, no chord concept), so the comparison collapses to a plain position delta between the two columns. The magnitude is intentionally a fixed step (not ramped), so the correction is discontinuous at the threshold — acceptable at this small magnitude for a monophonic model.

5. Implement the public entry point:
   ```java
   public static List<Spring> applyCorrections(List<Spring> springs, List<ElementColumn> columns) {
       var corrected = new ArrayList<Spring>(springs.size());

       for (var i = 0; i < springs.size(); i++) {
           var spring = springs.get(i);

           // A rigid gap (grace→host) packs at a fixed distance and never changes — same
           // convention as LyricLift.applyLyricLift.
           if (spring.rigid()) {
               corrected.add(spring);
               continue;
           }

           var prev = columns.get(i);
           var curr = columns.get(i + 1);
           // downstemAfterBarlineCorrectionSs is added to this sum in Phase 3.
           var correctionSs = oppositeStemCorrectionSs(prev, curr) + sameDirectionCorrectionSs(prev, curr);

           corrected.add(correctionSs == 0.0 ? spring : spring.withRestSs(spring.restSs() + correctionSs));
       }

       return corrected;
   }
   ```
   `oppositeStemCorrectionSs` and `sameDirectionCorrectionSs` are mutually exclusive (one requires differing directions, the other requires equal directions), so summing them is safe — never both nonzero for the same gap.

6. Run `./scripts/compile.sh` exactly as-is. Report SUCCESS or FAILURE; fix any errors before finishing this phase.

## ✅ Phase 3: Downstem-after-barline correction and wiring

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Sonnet 5, medium effort — one more formula (reusing the Phase-2 helpers) plus a one-line call-site edit, fully specified below

### Tasks

1. In `src/main/java/songscribe/layout/OpticalSpacing.java`, add one more named constant alongside the others from Phase 2:
   ```java
   /** Ported from LilyPond's StaffSpacing.stem-spacing-correction default (scm/define-grobs.scm). */
   private static final double DOWNSTEM_BARLINE_MAX_CORRECTION_SS = 0.4;
   ```

2. Implement the correction, reusing the `verticalOverlapSs` primitive (four-bound overload) and `saturatedMagnitudeSs` helper from Phase 2 — do **not** re-inline the overlap or ramp math:
   ```java
   private static double downstemAfterBarlineCorrectionSs(ElementColumn prev, ElementColumn curr) {
       if (!prev.isBarline() || !curr.hasStem() || curr.getDirection() != StaffElement.Direction.DOWN) {
           return 0.0;
       }

       // A barline is not a grace note, so only curr can be one; exclude it for the same reason as
       // the other two corrections.
       if (curr.isGraceNote()) {
           return 0.0;
       }

       var overlapSs = verticalOverlapSs(
           -Staff.STAFF_HALF_SS, Staff.STAFF_HALF_SS, curr.getAbsoluteTopYSs(), curr.getAbsoluteBottomYSs());

       if (overlapSs <= 0.0) {
           return 0.0;
       }

       return saturatedMagnitudeSs(overlapSs, DOWNSTEM_BARLINE_MAX_CORRECTION_SS);
   }
   ```
   `Staff.STAFF_HALF_SS` (`src/main/java/songscribe/engraving/Staff.java:34`) is half the 5-line staff height (2.0 Ss), so `[-STAFF_HALF_SS, +STAFF_HALF_SS]` is the barline's full vertical span, symmetric about the middle line in the same screen-down Ss convention as `getAbsoluteTopYSs()`/`getAbsoluteBottomYSs()`. This correction is unconditionally additive (never negative) — a downstem right after a barline always gets a little more room, never less, matching LilyPond's `Staff_spacing::optical_correction`.

3. In `OpticalSpacing.applyCorrections` (written in Phase 2), extend the per-gap correction sum to include this third correction:
   ```java
   var correctionSs = oppositeStemCorrectionSs(prev, curr)
       + sameDirectionCorrectionSs(prev, curr)
       + downstemAfterBarlineCorrectionSs(prev, curr);
   ```
   Remove the `// downstemAfterBarlineCorrectionSs is added to this sum in Phase 3.` comment left in Phase 2. All three corrections are mutually exclusive for any given gap (`downstemAfterBarlineCorrectionSs` requires `prev.isBarline()`, which implies `!prev.hasStem()`, which zeroes out the other two), so summing remains safe.

4. In `src/main/java/songscribe/layout/HorizontalSpacingCalculator.java`, in `solveLine` (around line 385), change:
   ```java
   var springs = LyricLift.applyLyricLift(buildSprings(columns, line), columns);
   ```
   to:
   ```java
   var springs = OpticalSpacing.applyCorrections(LyricLift.applyLyricLift(buildSprings(columns, line), columns), columns);
   ```
   `OpticalSpacing` lives in the same `songscribe.layout` package as `HorizontalSpacingCalculator`, so no new import is needed. Confirm no adjacent ASCII diagram comment in `solveLine` is invalidated by the wrap (it should not be — behavior is only post-processed).

5. Run `./scripts/compile.sh` exactly as-is. Report SUCCESS or FAILURE; fix any errors before finishing this phase.

## ✅ Phase 4: Manual visual verification

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Sonnet 5, low effort — requires user interaction, not autonomous implementation work

### Tasks

1. Ask the user for explicit permission before running `./scripts/run.sh` (never execute it without permission, per project convention).
2. Once permitted, launch the app and open or construct a short test song containing at least four cases: (a) two adjacent notes with opposite stem directions (e.g. a note below the staff middle line immediately followed by a note above it, both short enough to have plain unbeamed stems), (b) two adjacent same-direction notes with a large pitch leap between them, (c) a barline immediately followed by a note above the middle line (downward stem), (d) a nearly-full line where several corrections fire, to observe the compounding/compression behavior described below.
3. Compare the spacing around each case against the same song's spacing with `OpticalSpacing.applyCorrections` temporarily removed from `HorizontalSpacingCalculator.solveLine` (e.g. by commenting out the wrapper added in Phase 3, task 4, and reverting after comparison). Confirm the correction is a small, subtle nudge — wider for the up-then-down pair, narrower for the down-then-up pair, extra room for the downstem-after-barline case — with no jarring artifacts (colliding glyphs, visibly lopsided gaps).
4. Because corrections are additive per gap and the solver compresses the whole line when the natural width no longer fits (`solveLine` runs once per line; there is no re-solve loop), a line near its width limit can accumulate enough widening that **other, uncorrected gaps tighten to pay for it**. Explicitly check case (d): does any line that fit before now fail to fit, and did neighboring gaps visibly tighten? Report either way.
5. Expect narrowing corrections to be **invisible on gaps already near their collision floor** (the `Spring.naturalLengthSs = max(restSs, strutSs)` clamp — see Phase 2 Context). Seeing "the narrowing did nothing here" on a tight gap is correct behavior, not a defect; do not treat it as a reason to change the constants.
6. Report findings to the user and get explicit sign-off before Phase 5 proceeds. If a correction looks wrong (wrong sign, too strong/weak, or a visual artifact), stop and report the specific symptom rather than unilaterally changing the constants from Phase 2/3 — tuning is a decision for the user once real output is visible.

### Outcome

Verified with a two-line test song (`spacing-test.musicxml`: one uncompressed line, the same material repeated until the second line compresses), instrumented with a per-gap debug dump added to `HorizontalSpacingCalculator.solveLine` (`logLineSolve` — kept as permanent debug-level diagnostics: per gap it logs the pre-correction rest, correction delta, level offset, strut, natural, and solved length).

- **Uncompressed: works as designed.** All three corrections fire with the right signs and magnitudes; narrowing is invisible on strut-pinned gaps exactly as documented.
- **Compressed: the anticipated compounding problem (task 4) materialised as visible holes.** Root cause: corrections adjusted only `restSs`, but the compress-only water-fill discards ideals except as caps/floors, so a correction's survival was **binary** — fully preserved on gaps whose natural length sat below the levelled unit `U` (barline→note, the only such gaps), fully erased everywhere else. Net effect of the whole pass on a compressed line: +0.3 after each barline, nothing else. Two further structural artifacts became visible: a thin-left-glyph gap (barline) carries ~1.0 more *whitespace* than a notehead gap at the same origin-delta and was exempt from compression entirely; and a lyric-strut-pinned gap (wide syllables) stood 0.6 proud of the uniform level while its optically-crowded neighbour (up→down stems, widening erased) read even tighter — maximal adjacent contrast.
- Resolution designed and implemented in Phase 5; user signed off on the compressed rendering after the fix.

## ✅ Phase 5: Whitespace-aware levelling under compression

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Recommended model/effort:** — (implemented interactively during Phase 4 follow-up)

### Design

Phase 4 showed that additive `restSs` corrections cannot survive the water-fill: under compression the solver levels every free gap to `weight × U`, so the ideal is irrelevant unless it becomes a cap or floor.

**Fix: whitespace-aware levelling.** The water-fill stays, but each spring carries a `levelOffsetSs` — its non-whitespace component — and compression levels `levelOffset + weight × U` instead of `weight × U`. The offset is the previous column's glyph ink (so a thin-barline gap compresses like its notehead neighbours in *visual* whitespace terms, and participates instead of being exempted by its small natural), plus any optical correction (so corrections survive compression as relative offsets at every compression level). Because `baseRestSs = leftInk + factor × lineRest` and the solver weight equals that same factor, `levelOffset + weight × lineRest` reproduces the base rest exactly — whitespace levelling is a strict generalisation of the rest model, not a second spacing scheme. Struts, natural caps, rigid pinning, and the infeasibility check are unchanged, so all Phase 2 collision-safety guarantees carry over.

### What was implemented

1. `Spring` gained a `levelOffsetSs` component; `Spring.of(rest, strut)` and `Spring.of(rest, strut, weight, rigid)` default it to 0 (existing callers and tests unchanged), a new five-arg `of` sets it, `withRestSs` preserves it, and a new `withCorrectionSs(correctionSs)` adds a correction to both `restSs` and `levelOffsetSs`.
2. `HorizontalSpacingCalculator.buildSpring` populates the offset via a new `leftInkSs(prev)` helper (grace: `getRightExtentSs`; otherwise `getRightExtentExcludingAugmentationSs`), now also shared by `baseRestSs` so the ink term cannot drift between the two.
3. `OpticalSpacing.applyCorrections` applies corrections via `withCorrectionSs` instead of `withRestSs`.
4. `SpringSpacer.compress` levels each free gap to `levelOffset + weight × U` (offsets come off the top of the budget; clamp bookkeeping subtracts a clamped gap's offset). A `WHITESPACE_LEVELING` prototype toggle used for the A/B comparison was removed after sign-off — whitespace levelling is now the only compression behavior.
5. The Phase-4 debug dump gained an `off=` column.

Verified: full unit suite green (5828 passed — solver tests build offset-0 springs, so the old path stays covered bit-for-bit); user confirmed visually that the compressed line's holes are gone (barline gaps compress to the common whitespace level + 0.3, the up→down widening survives, and the lyric-pinned gap no longer stands proud of a uniform level).

## ✅ Phase 6: Automated tests

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Recommended model/effort:** Sonnet 5, medium effort — mechanical test-writing against an existing, closely analogous test file

### Tasks

1. Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before writing any tests.
2. Read `src/test/java/songscribe/layout/LyricLiftTest.java` in full as the pattern to follow — same package, same `UnitTest` base class, same style of building synthetic `ElementColumn`/`Spring` instances by hand (see its private `column(...)` helper at line 132) rather than going through the full layout pipeline. Extend or mirror that helper as needed to control `staffPosition`, stem direction, grace-vs-normal type, and barline type per test case — use `StaffElement`'s mutators (`setDirection`, `setStaffPosition`) and the appropriate `ElementType` to configure a synthetic element beyond what `ElementType.newInstance()` gives by default.
3. Create `src/test/java/songscribe/layout/OpticalSpacingTest.java` covering, at minimum:
   - **Accessor sign convention (direct value tests — closes the one critical gap):** for a column at a known staff position, assert `getPositionSs()` equals `Staff.spToSs(position)`, `getAbsoluteBottomYSs()` equals `getPositionSs() + getStemBottomSs()`, and that `getAbsoluteBottomYSs()` is numerically **greater than** `getAbsoluteTopYSs()` (screen-down: bottom is lower/larger). A flipped sign here silently mis-spaces every correction, so it is asserted at the source rather than only indirectly.
   - **Opposite-stem correction:** up-then-down widens (positive delta to `restSs`), down-then-up narrows (negative delta), magnitude scales linearly with vertical overlap below `STEM_OVERLAP_SATURATION_SS` and saturates at `OPPOSITE_STEM_MAX_CORRECTION_SS` above it, zero correction when overlap is zero or negative.
   - **Same-direction correction:** no correction at or below `SAME_DIRECTION_THRESHOLD_SS`, narrows when `curr` is the lower pitch, widens when `curr` is the higher pitch, magnitude always exactly `SAME_DIRECTION_MAX_CORRECTION_SS` once past the threshold.
   - **Downstem-after-barline correction:** zero when `prev` is not a barline, zero when `curr`'s direction is not `DOWN`, **zero when overlap is zero or negative** (explicit boundary case), positive with both a below-saturation (linear) case and an above-saturation (clamped) case.
   - **Grace-note guard (from the grace-exclusion decision):** a grace note in either column position produces zero correction for the opposite-stem and same-direction corrections, and a grace note as `curr` produces zero for the downstem-after-barline correction.
   - **Guard conditions produce zero correction:** either column lacking a stem (a rest or barline paired with a note), and two columns in the same beam group with opposite stem directions (the knee case).
   - **`applyCorrections` behavior:** a `rigid` spring passes through completely unchanged (same `Spring` instance or equal fields); a gap with **zero** net correction also passes through unchanged (identity path, distinct from the rigid case); and an **integration** test that a barline→downstem gap yields exactly the barline correction and is **not double-counted** (the opposite-stem and same-direction terms must be zero because a barline has no stem).
   - **Corrections land in both channels (Phase 5):** a corrected gap's `restSs` and `levelOffsetSs` both shift by the same correction delta, while `strutSs`, `weight`, and `rigid` are untouched.
4. Extend `src/test/java/songscribe/layout/SpringTest.java` for the Phase-5 record changes:
   - `Spring.of(rest, strut)` and `Spring.of(rest, strut, weight, rigid)` default `levelOffsetSs` to 0; the five-arg overload sets it.
   - `withRestSs` preserves `levelOffsetSs` (and still recomputes `complianceSs`).
   - `withCorrectionSs` adds the correction to both `restSs` and `levelOffsetSs`, preserves strut/weight/rigid, and recomputes `complianceSs` from the new rest; a negative correction subtracts from both.
5. Extend `src/test/java/songscribe/layout/HorizontalSpacingCalculatorSpringTest.java`: `buildSpring` sets `levelOffsetSs` to the previous column's left ink — `getRightExtentExcludingAugmentationSs()` for a normal gap, `getRightExtentSs()` for a grace→host gap — and the offset plus the gap's rest factor times the line rest reproduces the base rest (the Phase-5 consistency invariant).
6. Extend `src/test/java/songscribe/layout/SpringSpacerTest.java` for whitespace levelling:
   - Two otherwise-identical free springs where one carries a `levelOffsetSs` of `d`: under compression their solved lengths differ by exactly `d` (the offset survives levelling as a relative shift).
   - The barline scenario from Phase 4: a small-offset, small-natural gap that origin-delta levelling would exempt (natural below the uniform level) **does** compress under whitespace levelling.
   - An all-offset-zero chain solves identically to the pre-Phase-5 behavior (regression guard for the origin-delta path).
   - Solved gap lengths still sum to the available span with mixed offsets (conservation), and strut floors still win over offsets (a gap whose offset-based target falls below its strut pins at the strut).
7. Run `./scripts/compile.sh`, then `./scripts/test.sh unit OpticalSpacingTest SpringTest HorizontalSpacingCalculatorSpringTest SpringSpacerTest`. Report SUCCESS or FAILURE; fix any failures before finishing. Do not rerun with extra flags, and do not assume a failure is pre-existing.

### Outcome

`OpticalSpacing`'s five correction constants were widened from `private` to package-private (per the testability-over-encapsulation convention) so the new test can assert against them directly instead of duplicating literals.

Created `src/test/java/songscribe/layout/OpticalSpacingTest.java` (29 tests) covering the accessor sign convention, all three corrections' ramp/saturation/sign/zero-overlap behavior, the grace-note guard, the shared no-stem/knee guards, `applyCorrections`' rigid/identity/integration paths, and the Phase-5 both-channels invariant — all driven through the public `applyCorrections` entry point rather than the private per-correction methods.

Extended `SpringTest.java` (+6 tests) for `levelOffsetSs` defaulting across the two-, four- and five-arg `of` overloads, `withRestSs` preservation, and `withCorrectionSs`'s dual-channel derivation (including the negative-correction case).

Extended `HorizontalSpacingCalculatorSpringTest.java` (+3 tests) for `buildSpring`'s `levelOffsetSs` — augmentation-excluded ink for a normal gap, full right extent for a grace→host gap — and the Phase-5 consistency invariant (`levelOffset + weight × lineRest == restSs`) for both a normal and a tight-beam gap.

Extended `SpringSpacerTest.java` (+4 tests) for whitespace levelling: an offset surviving compression as an exact relative shift (with span conservation), the Phase-4 barline scenario no longer being exempted from compression, an explicit all-zero-offset regression guard against the origin-delta path, and struts still overriding offset-based targets.

`./scripts/compile.sh`: SUCCESS. `./scripts/test.sh unit OpticalSpacingTest SpringTest HorizontalSpacingCalculatorSpringTest SpringSpacerTest`: **110 passed**, SUCCESS.
