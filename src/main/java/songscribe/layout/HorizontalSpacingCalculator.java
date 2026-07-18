/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.layout;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.engraving.SMuFLConstants;


/**
 * Calculates horizontal X positions for note columns following Gould/Ross engraving principles.
 * <p>
 * Key principles:
 * <ul>
 *   <li>Non-proportional spacing - rhythmic value does not determine horizontal distance</li>
 *   <li>Lyric-driven spacing - syllable widths determine column gaps</li>
 *   <li>Minimum gap measured to a column's leftmost glyph - a note shifts for an accidental only when needed</li>
 *   <li>Beam group coordination - tight internal spacing unless lyrics force expansion</li>
 * </ul>
 * <p>
 * This class owns the first two stages of a spring-and-strut pipeline. A line is modelled as a
 * chain of {@link Spring}s — one per adjacent column pair — which a solver then lengthens or
 * compresses as a whole, rather than accumulating final positions greedily from left to right:
 * <ol>
 *   <li><b>Build</b> — {@link #buildSprings} emits a spring per gap. Each gap's ideal
 *       {@code rest}, its hard collision {@code strut}, and the {@code compliance} between them
 *       are resolved once, in {@link #buildSpring}; grace, beam-internal, glissando and syllable
 *       floors are all folded in there, so no downstream stage needs to know about them.</li>
 *   <li><b>Lyric lift</b> — {@link LyricLift#applyLyricLift} widens the rests so
 *       syllables clear each other, preferring an even line-wide lift over a local spike.</li>
 *   <li><b>Solve</b> — {@link SpringSpacer#solve} fits the chain to the staff width, compressing
 *       each gap in proportion to its compliance and freezing it once it reaches its strut. The
 *       caller ({@link LayoutEngine}) anchors the first column at
 *       {@link #calculateFirstNoteXSs} and lays the solved gaps out from there.</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var springs = HorizontalSpacingCalculator.buildSprings(columns, line);
 * springs = LyricLift.applyLyricLift(springs, columns);
 * var result = SpringSpacer.solve(springs, availableSpanSs);
 * }</pre>
 */
public class HorizontalSpacingCalculator {

    /**
     * Minimum horizontal gap between adjacent note columns — the single minimum gap
     * applied wherever a gap can be squeezed (normal spacing and justification alike).
     * A column's left extent includes its accidental, so the minimum spacing is measured
     * to the leftmost glyph (the accidental when one is present); this is what makes a
     * note shift right only when the accidental would otherwise come closer than this gap
     * to the previous element. Lyric spacing may require more.
     */
    public static final double MIN_COLUMN_GAP_SS = 1.0;  // 8px
    /**
     * Default horizontal gap between adjacent note columns when no lyrics are present.
     * Provides comfortable spacing for music without lyrics.
     */
    public static final double DEFAULT_COLUMN_GAP_SS = 2.5;  // 20px
    /**
     * Fixed gap between a grace note and its host note. The grace note always packs against its host
     * at this distance, independent of the song's line rest — the one gap that never varies. Shared
     * by every path: the spring path ({@link #buildSpring}), the retired greedy path
     * ({@link #calculateNextColumnXSs}), and the grace-insertion lock preview.
     */
    public static final double GRACE_HOST_REST_SS = 2.0;  // 16px
    /**
     * Reducing factor applied to the line rest for a gap internal to a beam group whose two notes
     * are both shorter than an eighth note (sixteenths and faster). A pair touching an eighth note
     * (or longer) instead packs at the full line rest, the same gap as unbeamed notes; the longer
     * note of the pair governs (refs #418). At the {@code 2.5} default line rest this yields
     * {@code 0.8 × 2.5 = 2.0}. Beam groups still widen if lyrics require it.
     */
    public static final double BEAM_GROUP_INTERNAL_REST_FACTOR = 0.8;
    /**
     * Distance from right extent of clef/key signature to first note column.
     * Per Gould/Ross: provides visual separation between staff beginning and music.
     */
    public static final double FIRST_NOTE_OFFSET_SS = 3.5;  // 28px
    /**
     * Width of each accidental in the key signature.
     */
    public static final double KEY_ACCIDENTAL_WIDTH_SS = 1.0;  // 8px

    /**
     * Calculates the X position of the first note in a line, in staff-space units.
     * <p>
     * Formula: clefWidth + keySignatureWidth + FIRST_NOTE_OFFSET
     *
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units where the first note should be placed
     */
    public static double calculateFirstElementXSs(int keyAccidentalCount) {
        return calculateHeaderRightEdgeSs(keyAccidentalCount) + FIRST_NOTE_OFFSET_SS;
    }

    /**
     * Returns the X position of the right edge of the staff header
     * (clef + optional key signature), in staff-space units.
     *
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units of the header's right edge
     */
    public static double calculateHeaderRightEdgeSs(int keyAccidentalCount) {
        return SMuFLConstants.G_CLEF_WIDTH_SS + keyAccidentalCount * KEY_ACCIDENTAL_WIDTH_SS;
    }

    // ==========================================================================
    // First Note Positioning
    // ==========================================================================

    /**
     * Calculates the X position of the first note in a line — the anchor the solved spring chain
     * grows from.
     *
     * @param line The line (for key signature info)
     * @return X position in ss
     */
    public static double calculateFirstNoteXSs(Line line) {
        return calculateFirstElementXSs(line.getKeyAccidentalCount());
    }

    // ==========================================================================
    // Column-to-Column Spacing
    // ==========================================================================

    /**
     * Calculates the X position for the next column based on the previous column.
     * <p>
     * The retired greedy path that full layout once ran. Only {@link LayoutResult}'s
     * append-position lookup still uses it; every spacing decision that reaches the rendered
     * score goes through {@link #buildSpring}.
     *
     * @param prevColumn Previous column (must have X position already set)
     * @param currColumn Current column
     * @return X position for current column
     */
    public static double calculateNextColumnXSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        double spacingSs;

        if (prevColumn.getElement().getType().isGraceNote()) {
            // Grace note → host note: use tight grace note spacing. The comfortable gap is measured
            // to the host note head — the accidental is excluded so it does not widen the gap — while
            // the geometric minimum, which does use the full left extent, acts as a hard floor to
            // ensure no overlap (refs #418).
            var comfortableSpacingSs = prevColumn.getRightExtentSs() + GRACE_HOST_REST_SS;
            var minimumSpacingSs = calculateMinimumColumnSpacingSs(prevColumn, currColumn);
            spacingSs = Math.max(comfortableSpacingSs, minimumSpacingSs);
        } else {
            // Calculate minimum spacing (from previous column's right extent)
            var minimumSpacingSs = calculateMinimumColumnSpacingSs(prevColumn, currColumn);

            // Calculate lyric-driven spacing requirement
            var lyricSpacingSs = calculateLyricSpacingSs(prevColumn, currColumn);

            // Use default comfortable spacing as a floor, then expand further if lyrics require it.
            // This prevents the case where only one side has a lyric from producing
            // tighter-than-default note-head-to-note-head spacing. The minimum-spacing floor (which
            // uses the full left extent, accidental included) already keeps MIN_COLUMN_GAP to the
            // current column's leftmost glyph, so a note shifts right only when its accidental would
            // otherwise crowd the previous element — no separate accidental push is needed.
            var defaultSpacingSs = calculateDefaultColumnSpacingSs(prevColumn);
            spacingSs = Math.max(minimumSpacingSs, Math.max(defaultSpacingSs, lyricSpacingSs));
        }

        // Ensure enough horizontal room for a connecting glissando. This applies to both grace→host
        // and regular note→note pairs: a glissando on the previous note must clear the next note's
        // left-side glyphs (accidental included) and still keep its minimum visible length (refs #443).
        spacingSs = ensureGlissandoSpacing(prevColumn, currColumn, spacingSs);

        return prevColumn.getXSs() + spacingSs;
    }

    /**
     * Calculates minimum spacing between columns based on geometric extents.
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Minimum spacing in ss
     */
    private static double calculateMinimumColumnSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        // Distance from previous column's center to current column's center
        // = previous column's right extent + MIN_COLUMN_GAP + abs(current column's left extent)
        return prevColumn.getRightExtentSs() + MIN_COLUMN_GAP_SS + Math.abs(currColumn.getLeftExtentSs());
    }

    /**
     * Calculates spacing requirement driven by lyric syllables.
     * <p>
     * Formula: (prevSyllableWidth / 2) + prevColumn.minGapToNextSyllable + (currSyllableWidth / 2)
     *
     * @param prevColumn Previous column
     * @param currColumn Current column
     * @return Lyric spacing in ss, or 0 if no syllables
     */
    private static double calculateLyricSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        if (!prevColumn.hasSyllable() && !currColumn.hasSyllable()) {
            return 0;
        }

        var prevHalfWidthSs = prevColumn.hasSyllable() ? prevColumn.getSyllableWidthSs() / 2.0 : 0;
        var currHalfWidthSs = currColumn.hasSyllable() ? currColumn.getSyllableWidthSs() / 2.0 : 0;
        // Every column reserves a gap to the next syllable (the lyric space width, or the hyphen
        // cell width for hyphenated syllables), so this holds even when the previous element
        // carries no lyric of its own.
        var gapSs = prevColumn.getMinGapToNextSyllableSs();

        return prevHalfWidthSs + gapSs + currHalfWidthSs;
    }

    /**
     * Calculates default spacing for columns when no lyrics are present.
     * <p>
     * Uses DEFAULT_COLUMN_GAP to provide comfortable spacing without lyrics. The comfortable
     * gap is measured to the current note head, not to its accidental or augmentation dots,
     * so neither pushes the next element beyond the comfortable default. The minimum-spacing
     * floor — which uses the full right extent including dots — takes over and shifts the
     * next element only when the dot would otherwise come closer than {@link #MIN_COLUMN_GAP_SS}
     * to it. This mirrors how accidentals on the left are handled (refs #418, #441).
     *
     * @param prevColumn Previous column
     * @return Default spacing in ss
     */
    private static double calculateDefaultColumnSpacingSs(ElementColumn prevColumn) {
        return prevColumn.getRightExtentExcludingAugmentationSs() + DEFAULT_COLUMN_GAP_SS;
    }

    /**
     * Ensures minimum horizontal spacing for a glissando between two columns.
     * Returns the input spacing unchanged if no glissando or if there is already enough room.
     * <p>
     * Computes: {@code gap = spacingSs + currLeft - prevRight}. If
     * {@code gap < }{@link NoteGeometry#MIN_GLISSANDO_RESERVATION_SS}, spacing is widened
     * to close the difference. Ledger lines are excluded from both extents.
     */
    private static double ensureGlissandoSpacing(
        ElementColumn prev, ElementColumn curr, double spacingSs
    ) {
        if (!prev.hasGlissando()) {
            return spacingSs;
        }

        var prevGlissRight = prev.getRightExtentSs();
        var currGlissLeft = curr.getLeftExtentSs();

        var gap = spacingSs + currGlissLeft - prevGlissRight;
        var needed = NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;

        if (gap < needed) {
            spacingSs += (needed - gap);
        }

        return spacingSs;
    }

    // ==========================================================================
    // Spring Construction
    // ==========================================================================

    /**
     * Builds the {@link Spring} for one adjacent column pair — the single source of truth for
     * per-pair horizontal spacing, shared by full layout and insertion. Everything is delta-X
     * (Ss) between the two columns' origins, i.e. the value that would be added to
     * {@code prev.getXSs()}.
     * <p>
     * Grace and glissando are folded in here, so a {@code Spring} needs no post-processing. A
     * normal or beam-internal base rest is derived from the song's line rest by a reducing
     * {@code factor} (see {@link #restFactorFor}); at the {@code 2.5} default line rest the factors
     * reproduce the legacy absolute gaps ({@code 2.5} normal, {@code 1.5} tight beam). The grace
     * gap is a fixed absolute distance that never scales with the line rest.
     * <pre>
     *   prev ──────────────── delta-X ───────────────▶ curr
     *
     *   base rest  ┌ grace note prev ──▶ rightExtent + GRACE_HOST_REST_SS   (fixed, never scales)
     *              ├ same beam group ──▶ rightExtentExclAug + factor × lineRest  (0.6× both ≤16th,
     *              │                                                              else 1.0×)
     *              └ otherwise ────────▶ rightExtentExclAug + lineRest                      (1.0×)
     *
     *   strut = max( note-collision floor    prevRight + MIN_COLUMN_GAP_SS + |currLeft|
     *              , syllable-collision floor prevSyl/2 + prev.minCollisionGapToNextSyllable
     *                                                   + currSyl/2   (either bears a syllable;
     *                                                     floor = 1 space, or bare hyphen if hyphenated)
     *              , glissando reservation    prevRight − currLeft
     *                                                   + MIN_GLISSANDO_RESERVATION_SS
     *                                                                 (prev has a glissando) )
     *
     *   compliance = max(0, rest − strut)     ← rest ≤ strut ⇒ the gap starts frozen
     * </pre>
     *
     * @param prev       Previous column
     * @param curr       Current column
     * @param lineRestSs The song's line rest ({@code Song.getDefaultRestLengthSs}); each base rest
     *                   is a reducing factor of it
     * @return The spring governing the gap between {@code prev} and {@code curr}
     */
    public static Spring buildSpring(ElementColumn prev, ElementColumn curr, double lineRestSs) {
        return buildSpring(prev, curr, lineRestSs, null, null);
    }

    /**
     * Builds the {@link Spring} for one adjacent column pair, threading the pair's outer neighbours
     * so the syllable-collision floor can honour a grace–host lyric union: {@code beforePrev} is the
     * column two before {@code curr} (used to detect that {@code prev} hosts a grace), and
     * {@code afterCurr} is the column just after {@code curr} (used as a grace's host when
     * {@code curr} is itself a grace). Neighbour-independent floors — the note-collision strut and
     * the glissando reservation — stay full-column. See {@link #buildSpring(ElementColumn,
     * ElementColumn, double)} for the base rest / strut / compliance model.
     *
     * @param prev       Previous column
     * @param curr       Current column
     * @param lineRestSs The song's line rest; each base rest is a reducing factor of it
     * @param beforePrev The column before {@code prev}, or {@code null} at the line start
     * @param afterCurr  The column after {@code curr}, or {@code null} at the line end
     * @return The spring governing the gap between {@code prev} and {@code curr}
     */
    public static Spring buildSpring(
        ElementColumn prev,
        ElementColumn curr,
        double lineRestSs,
        @Nullable ElementColumn beforePrev,
        @Nullable ElementColumn afterCurr) {

        var restSs = baseRestSs(prev, curr, lineRestSs);
        var strutSs = Math.max(
            calculateMinimumColumnSpacingSs(prev, curr),
            Math.max(
                syllableCollisionFloorSs(prev, curr, beforePrev, afterCurr),
                glissandoReservationFloorSs(prev, curr)));

        // A grace→host gap is rigid: it packs at a fixed distance and never compresses or lifts. A
        // tight beam-internal gap (both notes shorter than an eighth) keeps its reduction factor as
        // the solver weight, so it stays proportionally tighter than a normal gap under compression,
        // not only at rest.
        var rigid = prev.getElement().getType().isGraceNote();
        var weight = isTightBeamGap(prev, curr) ? BEAM_GROUP_INTERNAL_REST_FACTOR : Spring.NORMAL_WEIGHT;

        return Spring.of(restSs, strutSs, weight, rigid);
    }

    /**
     * Builds one spring per adjacent column pair, so the returned list has
     * {@code columns.size() - 1} entries (empty for a line of 0 or 1 columns).
     * <p>
     * Beam handling ends here: {@link #buildSpring} resolves the beam-internal base rest from the
     * columns' own beam-group membership, so every downstream stage (lyric stretch, solver) can
     * stay beam-unaware. There is no separate even-distribution pass — the retired engine's
     * even distribution across a beam group existed solely to spread <em>lyric</em> expansion,
     * and lyric expansion is now a line-wide additive lift applied over these springs.
     * With lyrics factored out, that pass reduces to the per-pair tight rests below, which keeps
     * non-lyric beam spacing identical to the old engine.
     *
     * @param columns List of columns in note order
     * @param line    The line containing these columns; reserved for line-scoped spacing context
     * @return Springs for gaps {@code (i, i+1)}, in column order
     */
    public static List<Spring> buildSprings(List<ElementColumn> columns, Line line) {
        var lineRestSs = line.getSong().getDefaultRestLengthSs();
        var springs = new ArrayList<Spring>();

        for (var i = 1; i < columns.size(); i++) {
            var beforePrev = i >= 2 ? columns.get(i - 2) : null;
            var afterCurr = i + 1 < columns.size() ? columns.get(i + 1) : null;
            springs.add(buildSpring(columns.get(i - 1), columns.get(i), lineRestSs, beforePrev, afterCurr));
        }

        return springs;
    }

    // ==========================================================================
    // Line solve — the shared anchor + fit, so full layout and the insertion
    // pre-check always agree on whether a line fits.
    // ==========================================================================

    /**
     * The horizontal solve for one line: the anchor X the chain grows from, the lyric-lifted spring
     * chain, and the solver's verdict. Both {@link LayoutEngine} and the insertion pre-check
     * ({@link InsertionSpacingCalculator}) run the identical solve through {@link #solveLine} /
     * {@link #solveChain}, so a line the pre-check accepts is one the layout can always place — the
     * two can never disagree.
     *
     * @param firstXSs the anchor the solved gaps are laid out from
     * @param springs  the lyric-lifted spring chain that was solved
     * @param result   the solver's verdict (solved gap lengths, or infeasible)
     */
    public record LineSolution(double firstXSs, List<Spring> springs, SpringSolveResult result) {
        public LineSolution {
            springs = List.copyOf(springs);
        }

        public boolean isInfeasible() {
            return result.isInfeasible();
        }
    }

    /**
     * Returns the X the solved spring chain grows from: the first column's left edge pinned
     * {@link #FIRST_NOTE_OFFSET_SS} past the header. When the first column bears a wide syllable
     * that would overhang left of the note glyph and crowd the clef/key signature, the origin is
     * pushed right so the syllable's left edge keeps the same separation from the header the note
     * glyph gets. Lyrics centre on the notehead centre, so the syllable's left edge sits at
     * {@code origin + rightExtentExclAug/2 − syllableWidth/2} (refs #330).
     */
    public static double calculateAnchorXSs(ElementColumn firstColumn, Line line) {
        var firstXSs = calculateFirstNoteXSs(line) - firstColumn.getLeftExtentSs();

        if (firstColumn.hasSyllable()) {
            var syllableAnchorXSs = calculateHeaderRightEdgeSs(line.getKeyAccidentalCount())
                + FIRST_NOTE_OFFSET_SS
                - firstColumn.getRightExtentExcludingAugmentationSs() / 2
                + firstColumn.getSyllableWidthSs() / 2;
            firstXSs = Math.max(firstXSs, syllableAnchorXSs);
        }

        return firstXSs;
    }

    /**
     * Solves a lyric-lifted spring chain against the staff width. The span the gaps may consume is
     * the margin minus the anchor and minus the last column's right extent, so the last glyph lands
     * its right edge — not its origin — at the margin. Shared by full layout and the insertion
     * pre-check so both ask the solver the identical fit question.
     *
     * @param springs                  the lyric-lifted spring chain
     * @param firstXSs                 the anchor the chain grows from ({@link #calculateAnchorXSs})
     * @param lastColumnRightExtentSs  the last column's right extent
     * @param staffRightMarginSs       the maximum allowed line width in staff spaces
     * @return the solver's verdict
     */
    public static SpringSolveResult solveChain(
        List<Spring> springs,
        double firstXSs,
        double lastColumnRightExtentSs,
        double staffRightMarginSs) {

        var availableSpanSs = staffRightMarginSs - firstXSs - lastColumnRightExtentSs;
        return SpringSpacer.solve(springs, availableSpanSs);
    }

    /**
     * Builds the springs for {@code columns}, lifts their rests for lyrics, anchors the chain, and
     * solves it against the staff width — the whole horizontal solve for a line in one call. The
     * caller supplies the final ordered column list (full layout builds every column including the
     * terminal; the insertion pre-check splices the new column in), and both get the same anchor,
     * span and solve, so their verdicts agree.
     *
     * @param columns            the line's columns in element order (never empty)
     * @param line               the line being solved (for the song's line rest and key signature)
     * @param staffRightMarginSs the maximum allowed line width in staff spaces
     * @return the anchor, the lifted spring chain, and the solver's verdict
     */
    public static LineSolution solveLine(List<ElementColumn> columns, Line line, double staffRightMarginSs) {
        var springs = LyricLift.applyLyricLift(buildSprings(columns, line), columns);
        var firstXSs = calculateAnchorXSs(columns.getFirst(), line);
        var result = solveChain(springs, firstXSs, columns.getLast().getRightExtentSs(), staffRightMarginSs);

        return new LineSolution(firstXSs, springs, result);
    }

    /**
     * Returns the position-independent grace-lyric overhang: how far a grace note's syllable of
     * width {@code syllableWidthSs} spills past the grace-notehead-left → host-notehead-right union
     * on each side. The union spans the grace's right extent (the physical grace→host gap includes
     * the grace flag), the fixed {@link #GRACE_HOST_REST_SS} grace→host gap, and the host's notehead
     * width (neither the host's flag nor its dots widen the union). The width is a parameter rather
     * than read off the grace so callers can supply a per-verse box width. Returns 0 when the
     * syllable fits the union (it then left-anchors on the grace notehead and imposes no neighbour
     * constraint).
     */
    public static double graceLyricOverhangSs(double syllableWidthSs, ElementColumn grace, ElementColumn host) {
        var unionWidthSs = grace.getRightExtentSs()
            + GRACE_HOST_REST_SS
            + host.getNoteheadWidthSs();
        return Math.max(0, (syllableWidthSs - unionWidthSs) / 2);
    }

    /**
     * Returns the ideal (uncompressed) delta-X for a pair: the previous column's right extent plus
     * the pair's share of the line rest ({@code factor × lineRestSs}, see {@link #restFactorFor}).
     * A grace→host pair is the exception — it takes a fixed {@link #GRACE_HOST_REST_SS} gap that
     * never scales with the line rest.
     */
    private static double baseRestSs(ElementColumn prev, ElementColumn curr, double lineRestSs) {
        if (prev.getElement().getType().isGraceNote()) {
            // Grace note → host note: a fixed absolute gap that never varies with the song's line
            // rest — the grace note always packs against its host at the same distance. The gap is
            // measured to the host note head, so the host's accidental does not widen it; the
            // note-collision strut, which does use the full left extent, keeps the glyphs apart
            // (refs #418).
            return prev.getRightExtentSs() + GRACE_HOST_REST_SS;
        }

        var gapSs = isBeamInternalGap(prev, curr) ? beamInternalGapSs(prev, curr, lineRestSs) : lineRestSs;

        // Augmentation is excluded so dots and falls never push the next column beyond the
        // comfortable gap; the note-collision strut takes over when they would actually collide
        // (refs #441, #496).
        return prev.getRightExtentExcludingAugmentationSs() + gapSs;
    }

    /**
     * Returns the reducing factor applied to the line rest for this pair's base rest: a tight-beam
     * gap packs proportionally tighter than a normal gap, which takes the full line rest (factor
     * {@code 1}). Mirrors the branch selection in {@link #baseRestSs}, and is the factor the lyric
     * lift scales each gap's share of the lift by, so tight gaps stay proportionally tight. Grace
     * gaps are rigid and excluded from the lift, so they never reach this method through that path.
     */
    public static double restFactorFor(ElementColumn prev, ElementColumn curr) {
        if (isTightBeamGap(prev, curr)) {
            return BEAM_GROUP_INTERNAL_REST_FACTOR;
        }

        return 1;
    }

    /**
     * Returns whether the gap between two columns is internal to a single beam group. Adjacent
     * beam groups have distinct ids, so the gap between them is a normal gap (refs #418).
     */
    private static boolean isBeamInternalGap(ElementColumn prev, ElementColumn curr) {
        return prev.isBeamed() && curr.isBeamed() && prev.getBeamGroupId() == curr.getBeamGroupId();
    }

    /**
     * Returns whether the gap takes the tight beam-internal reduction: internal to one beam group
     * and flanked by two notes both shorter than an eighth (sixteenths or faster). This is the single
     * condition behind both the reduced base rest ({@link #restFactorFor}) and the solver weight
     * ({@link #buildSpring}), so the reduction is applied identically at rest and under compression.
     */
    private static boolean isTightBeamGap(ElementColumn prev, ElementColumn curr) {
        return isBeamInternalGap(prev, curr) && bothShorterThanEighth(prev, curr);
    }

    /**
     * Returns the delta-X below which the columns' syllables would collide, or 0 when neither column
     * bears a syllable. Each column contributes the half of its own syllable that overhangs toward
     * the gap, separated by a lyric-appropriate floor gap so the syllables never come closer than a
     * single space (non-hyphenated, including melisma) or the bare hyphen glyph width (hyphenated) —
     * the floor {@code prev} carries as its {@link ElementColumn#getMinCollisionGapToNextSyllableSs()}.
     * A lone syllable still reserves its footprint against an unlyriced neighbour.
     */
    private static double syllableCollisionFloorSs(
        ElementColumn prev,
        ElementColumn curr,
        @Nullable ElementColumn beforePrev,
        @Nullable ElementColumn afterCurr) {

        var right = lyricRightExtentSs(prev, beforePrev);
        var left = lyricLeftExtentSs(curr, afterCurr);

        if (right <= 0 && left <= 0) {
            return 0;
        }

        // When prev hosts a grace, the grace column carries the syllable and its hyphen/space, so the
        // collision gap to the next syllable comes from the grace, not the empty host.
        var gapSource = beforePrev != null && isGraceNote(beforePrev) ? beforePrev : prev;

        return right + gapSource.getMinCollisionGapToNextSyllableSs() + left;
    }

    /**
     * Returns whether a column is a grace note.
     */
    private static boolean isGraceNote(ElementColumn column) {
        return column.getElement().getType().isGraceNote();
    }

    /**
     * Returns how far {@code column}'s syllable reaches left of the column origin (its notehead's
     * left edge), used when the column is the right side of a gap. A grace column defers to its host
     * {@code afterColumn} via the grace–host lyric union overhang; a normal syllable-bearing column
     * reaches half its width minus the notehead-centre offset; anything else reaches nothing.
     */
    static double lyricLeftExtentSs(ElementColumn column, @Nullable ElementColumn afterColumn) {
        if (isGraceNote(column) && afterColumn != null) {
            return graceLyricOverhangSs(column.getSyllableWidthSs(), column, afterColumn);
        }

        if (column.hasSyllable()) {
            return column.getSyllableWidthSs() / 2 - column.getNoteheadWidthSs() / 2;
        }

        return 0;
    }

    /**
     * Returns how far {@code column}'s syllable reaches right of the column origin (its notehead's
     * left edge), used when the column is the left side of a gap. A grace column reaches nothing
     * (its lyric is deferred to the host). A host of a grace ({@code beforeColumn} is a grace)
     * reaches the grace lyric's right spill past the union. A normal syllable-bearing column reaches
     * half its width plus the notehead-centre offset; anything else reaches nothing.
     */
    static double lyricRightExtentSs(ElementColumn column, @Nullable ElementColumn beforeColumn) {
        if (isGraceNote(column)) {
            return 0;
        }

        if (beforeColumn != null && isGraceNote(beforeColumn)) {
            var syllableWidthSs = beforeColumn.getSyllableWidthSs();
            var overhangSs = graceLyricOverhangSs(syllableWidthSs, beforeColumn, column);
            var graceHostGapSs = beforeColumn.getRightExtentSs() + GRACE_HOST_REST_SS;
            return Math.max(0, (syllableWidthSs - overhangSs) - graceHostGapSs);
        }

        if (column.hasSyllable()) {
            return column.getSyllableWidthSs() / 2 + column.getNoteheadWidthSs() / 2;
        }

        return 0;
    }

    /**
     * Returns the delta-X a connecting glissando needs to clear the next column's left-side glyphs
     * (accidental included) and still keep its minimum visible length, or 0 when the previous
     * column has no glissando. Inverts {@link #ensureGlissandoSpacing}'s
     * {@code gap = spacingSs + currLeft - prevRight} so the reservation becomes a hard floor rather
     * than a post-applied widening (refs #443).
     */
    private static double glissandoReservationFloorSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.hasGlissando()) {
            return 0;
        }

        return prev.getRightExtentSs() - curr.getLeftExtentSs() + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
    }

    // ==========================================================================
    // Beam Group Spacing
    // ==========================================================================

    /**
     * Returns the comfortable internal gap between two adjacent beamed columns.
     * The longer note of the pair governs the gap: a pair touching an eighth note (or longer)
     * packs at the default note-to-note gap, while the tighter beam-internal gap applies only
     * when both notes are shorter than an eighth (sixteenths or faster). {@link LayoutEngine#beamCount}
     * returns 1 for an eighth note (quaver) and a larger value for shorter notes (refs #418).
     *
     * @param prev       Previous beamed column
     * @param curr       Current beamed column
     * @param lineRestSs The song's line rest, scaled by the beam-internal factor when both notes
     *                   are shorter than an eighth
     * @return Internal gap in ss
     */
    private static double beamInternalGapSs(ElementColumn prev, ElementColumn curr, double lineRestSs) {
        return bothShorterThanEighth(prev, curr) ? BEAM_GROUP_INTERNAL_REST_FACTOR * lineRestSs : lineRestSs;
    }

    /**
     * Returns whether both beamed columns are shorter than an eighth note (sixteenths or faster),
     * the condition for the tight beam-internal gap. {@link LayoutEngine#beamCount} returns 1 for
     * an eighth note (quaver) and a larger value for shorter notes (refs #418).
     */
    private static boolean bothShorterThanEighth(ElementColumn prev, ElementColumn curr) {
        return LayoutEngine.beamCount(prev.getElement()) > 1
            && LayoutEngine.beamCount(curr.getElement()) > 1;
    }

}
