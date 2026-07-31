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
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.engraving.SMuFLConstants;
import songscribe.util.LogUtils;


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
 *       {@link #calculateAnchorXSs} and lays the solved gaps out from there.</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var springs = HorizontalSpacingCalculator.buildSprings(columns, line);
 * springs = LyricLift.applyLyricLift(springs, columns);
 * var result = SpringSpacer.solve(springs, availableSpanSs);
 * }</pre>
 */
public final class HorizontalSpacingCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(HorizontalSpacingCalculator.class);

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
     * Ideal gap between a grace note and its host note. The grace note packs against its host at this
     * distance independent of the song's line rest and of the line's syllables — the one gap the
     * lyric lift never widens. {@link OpticalSpacing} nudges it for the host's stem geometry, and a
     * compressed line may tighten it by up to {@link #GRACE_HOST_COMPRESSION_ALLOWANCE_SS}, so the
     * distance is optical rather than mechanical. Shared by the spring path ({@link #buildSpring})
     * and the grace-insertion lock preview.
     */
    public static final double GRACE_HOST_REST_SS = 2.0;  // 16px
    /**
     * How far a compressed line may tighten a grace→host gap below {@link #GRACE_HOST_REST_SS}. Wide
     * enough that the grace tightens visibly along with its neighbours rather than standing proud of
     * them, narrow enough that the grace never reads as belonging to the note on its other side.
     */
    public static final double GRACE_HOST_COMPRESSION_ALLOWANCE_SS = 0.5;  // 4px
    /**
     * Reducing factor applied to the line rest for a gap internal to a beam group whose two notes
     * are both shorter than an eighth note (sixteenths and faster). A pair touching an eighth note
     * (or longer) instead packs at the full line rest, the same gap as unbeamed notes; the longer
     * note of the pair governs (refs #418). At the {@code 2.5} default line rest this yields
     * {@code 0.8 × 2.5 = 2.0}. Beam groups still widen if lyrics require it.
     */
    public static final double BEAM_GROUP_INTERNAL_REST_FACTOR = 0.8;

    private HorizontalSpacingCalculator() {
    }

    /**
     * Calculates the X position of the first note in a line, in staff-space units.
     *
     * @param line The line, for its key signature
     * @return X position in staff-space units where the first note should be placed
     */
    public static double calculateFirstElementXSs(Line line) {
        return calculateFirstElementXSs(line.getKeyType(), line.getKeyAccidentalCount());
    }

    /**
     * Calculates the X position of the first note in a line, in staff-space units.
     * <p>
     * LilyPond measures this from a different edge depending on what the note follows.
     * A key signature pushes the note {@link StaffHeaderMetrics#KEY_SIGNATURE_FIRST_NOTE_GAP_SS}
     * past its right edge; a bare clef instead sets a floor of
     * {@link StaffHeaderMetrics#CLEF_FIRST_NOTE_SPAN_SS} on the span from the clef's <em>left</em>
     * edge, which the clef alone does not fill.
     *
     * @param keyType            Type of accidentals in the key signature, may be null
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units where the first note should be placed
     */
    public static double calculateFirstElementXSs(@Nullable KeyType keyType, int keyAccidentalCount) {
        if (KeySignature.widthSs(keyType, keyAccidentalCount) == 0) {
            return LayoutEngine.CLEF_X_POSITION_SS
                + Math.max(SMuFLConstants.G_CLEF_WIDTH_SS, StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS);
        }

        return calculateHeaderRightEdgeSs(keyType, keyAccidentalCount)
            + StaffHeaderMetrics.KEY_SIGNATURE_FIRST_NOTE_GAP_SS;
    }

    /**
     * Returns the X position of the key signature's left edge, in staff-space units —
     * one clef width past the clef, plus {@link StaffHeaderMetrics#CLEF_GAP_SS}. This is the
     * only place that gap is applied; the {@code Clef} object does not carry it.
     */
    public static double calculateKeySignatureXSs() {
        return LayoutEngine.CLEF_X_POSITION_SS
            + SMuFLConstants.G_CLEF_WIDTH_SS
            + StaffHeaderMetrics.CLEF_GAP_SS;
    }

    /**
     * Returns the X position of the right edge of the staff header
     * (clef + optional key signature), in staff-space units.
     *
     * @param line The line, for its key signature; a null line has no key signature
     * @return X position in staff-space units of the header's right edge
     */
    public static double calculateHeaderRightEdgeSs(@Nullable Line line) {
        if (line == null) {
            return calculateHeaderRightEdgeSs(null, 0);
        }

        return calculateHeaderRightEdgeSs(line.getKeyType(), line.getKeyAccidentalCount());
    }

    /**
     * Returns the X position of the right edge of the staff header
     * (clef + optional key signature), in staff-space units.
     *
     * @param keyType            Type of accidentals in the key signature, may be null
     * @param keyAccidentalCount Number of accidentals in the key signature
     * @return X position in staff-space units of the header's right edge
     */
    public static double calculateHeaderRightEdgeSs(@Nullable KeyType keyType, int keyAccidentalCount) {
        var keySignatureWidthSs = KeySignature.widthSs(keyType, keyAccidentalCount);

        if (keySignatureWidthSs == 0) {
            return LayoutEngine.CLEF_X_POSITION_SS + SMuFLConstants.G_CLEF_WIDTH_SS;
        }

        return calculateKeySignatureXSs() + keySignatureWidthSs;
    }

    /**
     * Returns whether the given X, in staff-space units, falls within the staff header
     * (clef + optional key signature). A point exactly on the header's right edge counts
     * as inside, so this agrees with the staff-line hit region.
     *
     * @param xSs  X position in staff-space units
     * @param line The line, for its key signature; a null line has no key signature
     * @return whether the X is within the header
     */
    public static boolean isWithinHeaderXSs(double xSs, @Nullable Line line) {
        return xSs <= calculateHeaderRightEdgeSs(line);
    }

    // ==========================================================================
    // Column-to-Column Spacing
    // ==========================================================================

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
        return rightExtentFacingSs(prevColumn, currColumn)
            + MIN_COLUMN_GAP_SS
            + Math.abs(currColumn.getLeftExtentSs());
    }

    /**
     * Returns how far {@code prev}'s ink reaches right of its origin as {@code curr} meets it — the
     * single source of truth for the left-hand ink of every gap, so the ideal rest, the collision
     * strut, the glissando reservation and the grace–host lyric union can never disagree about how
     * wide the column before the gap is.
     *
     * <p>For a grace note this is {@link ElementColumn#getRightExtentFacingSs} against {@code curr}'s
     * <em>left-facing</em> band ({@link ElementColumn#getLeftFacingTopYSs}) — not its whole span,
     * which would include an up-stem standing a column-width clear on the far side of the notehead
     * and so would report an overlap with the grace's flag that no reader can see. A grace's flag is
     * over a third of its right extent, and it packs against its host at a fixed
     * {@link #GRACE_HOST_REST_SS} where that inflation is the difference between a grace that reads
     * as attached and one that floats. Charging the flag when it hangs well above the host is what
     * made a high grace sit visibly further from its host than a low one (refs #560).
     *
     * <p>Every other column is charged its full {@link ElementColumn#getRightExtentSs()}. An ordinary
     * flagged note has the same two-level ink profile, but its gap is a full line rest that dwarfs the
     * flag, so the same discount would buy invisible tightening in exchange for loosening the
     * collision floor of every unbeamed eighth on the page. Generalising it is a separate decision,
     * and wants LilyPond's skyline machinery rather than a one-glyph special case.
     */
    private static double rightExtentFacingSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.isGraceNote()) {
            return prev.getRightExtentSs();
        }

        return prev.getRightExtentFacingSs(curr.getLeftFacingTopYSs(), curr.getLeftFacingBottomYSs());
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
     *   base rest  ┌ grace note prev ──▶ prevRight + GRACE_HOST_REST_SS     (fixed, never scales)
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
     *                                                                 (prev has a glissando)
     *              , grace compression floor  rest − GRACE_HOST_COMPRESSION_ALLOWANCE_SS
     *                                                                 (prev is a grace note) )
     *
     *   compliance = max(0, rest − strut)     ← rest ≤ strut ⇒ the gap starts frozen
     *
     *   prevRight = rightExtentFacingSs(prev, curr) — prev's full right extent, except that a grace
     *               note's flag is not charged when it hangs clear of curr's left-facing band
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
     * {@code curr} is itself a grace). The note-collision strut and the glissando reservation ignore
     * both outer neighbours — they are measured from the pair's own extents alone. See
     * {@link #buildSpring(ElementColumn, ElementColumn, double)} for the base rest / strut /
     * compliance model.
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
                Math.max(
                    glissandoReservationFloorSs(prev, curr),
                    graceCompressionFloorSs(prev, restSs))));

        // A grace→host gap takes no lyric lift: its ideal is a fixed distance that must not grow
        // with the line's syllables. It still compresses, bounded by the floor above. A tight
        // beam-internal gap (both notes shorter than an eighth) keeps its reduction factor as the
        // solver weight, so it stays proportionally tighter than a normal gap under compression, not
        // only at rest.
        var liftExempt = prev.isGraceNote();
        var weight = isTightBeamGap(prev, curr) ? BEAM_GROUP_INTERNAL_REST_FACTOR : Spring.NORMAL_WEIGHT;

        // The gap's glyph-ink component becomes the spring's level offset, so the whitespace-aware
        // solver levels the visual gap (rest minus ink) rather than the origin-to-origin delta. The
        // weight matches the reducing factor applied to the line rest in the base rest, so
        // levelOffset + weight × lineRest reproduces the base rest exactly — whitespace levelling is
        // a strict generalisation of the rest model, not a second spacing scheme.
        return Spring.of(restSs, strutSs, weight, liftExempt, leftInkSs(prev, curr));
    }

    /**
     * Returns how far a grace→host gap may compress below its ideal, expressed as a strut floor, or
     * 0 for any other gap. The grace sits at a fixed distance from its host, but a fixed distance
     * that could never give would make an optical widening able to turn an otherwise-fittable line
     * infeasible, and would leave the grace standing proud of the whitespace level on a compressed
     * line. Allowing {@link #GRACE_HOST_COMPRESSION_ALLOWANCE_SS} of give lets the gap tighten along
     * with its neighbours while staying recognisably the grace gap.
     *
     * <p>The allowance is measured from the <em>uncorrected</em> ideal {@code baseRestSs}, since
     * {@link OpticalSpacing} shifts rests without touching struts; a corrected gap therefore ends up
     * with slightly more or less give than the allowance, which is immaterial at these magnitudes.
     */
    private static double graceCompressionFloorSs(ElementColumn prev, double baseRestSs) {
        if (!prev.isGraceNote()) {
            return 0;
        }

        return baseRestSs - GRACE_HOST_COMPRESSION_ALLOWANCE_SS;
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
     * Returns the X the solved spring chain grows from: the notehead's left edge pinned at
     * {@link #calculateFirstElementXSs(Line)}.
     * <p>
     * An accidental does not move the notehead. LilyPond measures this distance to the note
     * column's reference point, which is the notehead's left edge, and lets the accidental hang
     * left of it into the gap; the accidental reaches the spacing only through
     * {@code Paper_column::minimum_distance}, a collision floor that cannot bind at the gaps
     * {@link StaffHeaderMetrics} leaves here (refs #684, reverses #121).
     * <p>
     * A wide first syllable does still push the origin right, so the lyric does not crowd the
     * clef or key signature. Lyrics center on the notehead center, so the syllable's left edge
     * sits at {@code origin + rightExtentExclAug/2 − syllableWidth/2} (refs #330).
     */
    public static double calculateAnchorXSs(ElementColumn firstColumn, Line line) {
        var firstElementXSs = calculateFirstElementXSs(line);
        var firstXSs = firstElementXSs;

        if (firstColumn.hasSyllable()) {
            var syllableAnchorXSs = firstElementXSs
                - firstColumn.getRightExtentExcludingAugmentationSs() / 2
                + firstColumn.getSyllableWidthSs() / 2;
            firstXSs = Math.max(firstXSs, syllableAnchorXSs);
        }

        return firstXSs;
    }

    /**
     * Solves a lyric-lifted spring chain against the staff width. The span the gaps may consume is
     * the margin minus the anchor and minus the trailing reservation, so the last glyph lands its
     * right edge — not its origin — at or before the margin. Shared by full layout and the insertion
     * pre-check so both ask the solver the identical fit question.
     *
     * @param springs               the lyric-lifted spring chain
     * @param firstXSs              the anchor the chain grows from ({@link #calculateAnchorXSs})
     * @param trailingReservationSs the span to keep clear past the last column's origin
     *                              ({@link #trailingReservationSs})
     * @param staffRightMarginSs    the maximum allowed line width in staff spaces
     * @return the solver's verdict
     */
    public static SpringSolveResult solveChain(
        List<Spring> springs,
        double firstXSs,
        double trailingReservationSs,
        double staffRightMarginSs) {

        var availableSpanSs = staffRightMarginSs - firstXSs - trailingReservationSs;
        return SpringSpacer.solve(springs, availableSpanSs);
    }

    /**
     * Returns the span to reserve for {@code lastColumn} at the end of the solved chain: its own
     * right extent, plus a full line rest when it does not carry the auto-maintained terminal
     * barline. A terminal is pinned flush-right by {@code LayoutEngine.positionTerminalFlushRight},
     * so its own extent already ends the line; any other last element must stop one line rest short
     * of the margin instead of sitting flush against it (refs #617).
     *
     * @param lastColumn the chain's last column
     * @param line       the line being solved (for the song's line rest)
     * @return the span to pass as {@code trailingReservationSs} to {@link #solveChain}
     */
    public static double trailingReservationSs(ElementColumn lastColumn, Line line) {
        var song = line.getSong();
        // The song's own terminal-identity test, not a bare type check: a barline that merely looks
        // like a terminal — one ending a non-last line, or a projected clone that is not the line's
        // element — is not pinned flush-right, so it still owes the trailing rest.
        var nonTerminalGapSs = song.isAutoMaintainedTerminal(lastColumn.getElement(), line)
            ? 0.0
            : song.getDefaultRestLengthSs();

        return lastColumn.getRightExtentSs() + nonTerminalGapSs;
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
        var lifted = LyricLift.applyLyricLift(buildSprings(columns, line), columns);
        var springs = OpticalSpacing.applyCorrections(lifted, columns);
        var firstXSs = calculateAnchorXSs(columns.getFirst(), line);
        var reservationSs = trailingReservationSs(columns.getLast(), line);
        var result = solveChain(springs, firstXSs, reservationSs, staffRightMarginSs);

        if (LogUtils.isTracingSpacing(LOG)) {
            logLineSolve(columns, line, lifted, springs, result, staffRightMarginSs - firstXSs - reservationSs);
        }

        return new LineSolution(firstXSs, springs, result);
    }

    /**
     * Debug dump of one line solve: a header with the available span, the chain's natural span and
     * the solver's verdict, then one row per gap with the pre-correction rest, the optical
     * correction delta, the strut floor, the resulting natural length, the solved (possibly
     * compressed) length, and the ink-to-ink white that length leaves — so ideal-vs-compressed
     * spacing, and length-vs-perceived-white, can both be compared per gap from the log alone. A gap
     * whose correction was non-zero gets a second line breaking it into its three terms. Only ever
     * called under {@link LogUtils#isTracingSpacing}, i.e. with {@code DEBUG_SPACING} set.
     */
    private static void logLineSolve(
        List<ElementColumn> columns,
        Line line,
        List<Spring> lifted,
        List<Spring> corrected,
        SpringSolveResult result,
        double availableSpanSs) {

        var solvedSs = result.gapLengthsSs();
        var naturalSpanSs = 0.0;

        for (var spring : corrected) {
            naturalSpanSs += spring.naturalLengthSs();
        }

        String verdict;

        if (solvedSs == null) {
            verdict = "INFEASIBLE";
        } else if (naturalSpanSs <= availableSpanSs) {
            verdict = "uncompressed";
        } else {
            verdict = "compressed";
        }

        var sb = new StringBuilder();
        sb.append(String.format(
            "solveLine[line %d]: available=%.2f natural=%.2f -> %s",
            line.getSong().indexOfLine(line), availableSpanSs, naturalSpanSs, verdict));

        for (var i = 0; i < corrected.size(); i++) {
            var before = lifted.get(i);
            var after = corrected.get(i);
            var prev = columns.get(i);
            var curr = columns.get(i + 1);
            var placedSs = solvedSs == null ? null : solvedSs[i];
            sb.append(String.format(
                "%n  gap %2d %-34s -> %-34s rest=%5.2f corr=%+5.2f off=%5.2f strut=%5.2f natural=%5.2f solved=%s white=%s%s",
                i,
                describeForLog(prev),
                describeForLog(curr),
                before.restSs(),
                after.restSs() - before.restSs(),
                after.levelOffsetSs(),
                after.strutSs(),
                after.naturalLengthSs(),
                placedSs == null ? "    -" : String.format("%5.2f", placedSs),
                placedSs == null ? "    -" : String.format("%5.2f", whitespaceSs(placedSs, prev, curr)),
                after.liftExempt() ? " (no lift)" : ""));
            appendCorrectionBreakdown(sb, prev, curr);
        }

        LOG.debug("{}", sb);
    }

    /**
     * The ink-to-ink gap a solved spring actually leaves on screen: the origin-to-origin
     * {@code placedSs} less {@code prev}'s right extent and less {@code curr}'s left extent (which is
     * negative when it has an accidental). This — not the spring length — is the white the eye
     * judges, and it is what makes two gaps with equal solved lengths look unequal when the columns
     * flanking them carry different amounts of ink.
     */
    private static double whitespaceSs(double placedSs, ElementColumn prev, ElementColumn curr) {
        return placedSs - rightExtentFacingSs(prev, curr) + curr.getLeftExtentSs();
    }

    /**
     * Appends the per-term breakdown of a gap's optical correction, plus the two geometric inputs
     * that drive the note-to-note terms, so a correction that fired can be told apart from one that
     * was skipped and from one that cancelled against another. Emits nothing when no term fired.
     * Only ever called under {@link LogUtils#isTracingSpacing}.
     */
    private static void appendCorrectionBreakdown(StringBuilder sb, ElementColumn prev, ElementColumn curr) {
        var oppositeSs = OpticalSpacing.oppositeStemCorrectionSs(prev, curr);
        var sameSs = OpticalSpacing.sameDirectionCorrectionSs(prev, curr);
        var barlineSs = OpticalSpacing.downstemAfterBarlineCorrectionSs(prev, curr);

        if (oppositeSs == 0.0 && sameSs == 0.0 && barlineSs == 0.0) {
            return;
        }

        sb.append(String.format(
            "%n         corr: opposite=%+.2f same=%+.2f barline=%+.2f", oppositeSs, sameSs, barlineSs));

        if (prev.hasStem() && curr.hasStem()) {
            sb.append(String.format(
                " (overlap=%.2f deltaPos=%+.2f)",
                OpticalSpacing.verticalOverlapSs(prev, curr),
                curr.getPositionSs() - prev.getPositionSs()));
        }
    }

    /**
     * Compact per-column label for {@link #logLineSolve}: element type, and when stemmed the stem
     * direction, the notehead-center Ss, and the column's whole vertical span. The span is what the
     * overlap ramp reads, and it is where a grace note differs from a full note of the same value —
     * shorter stem, smaller head — so it has to be visible next to the correction it produced.
     */
    private static String describeForLog(ElementColumn column) {
        var type = column.getElement().getType();

        if (!column.hasStem()) {
            return type.name();
        }

        return String.format(
            "%s(%s@%.1f %.1f..%.1f)",
            type.name(),
            column.getDirection(),
            column.getPositionSs(),
            column.getAbsoluteTopYSs(),
            column.getAbsoluteBottomYSs());
    }

    /**
     * Returns the offset from a grace note's column origin (its notehead left edge) to the left edge
     * of its syllable box — negative when the syllable starts left of the notehead. Single source of
     * truth for where a grace lyric sits: {@link LyricLayoutBuilder} adds it to the grace's X to
     * place the box, and the extent methods below derive the neighbour reservations from the same
     * formula.
     * <p>
     * The one input the two callers cannot share is {@code unionWidthSs}. The reservation runs while
     * the line is still being solved, so it has to pass the ideal {@link #idealGraceHostUnionWidthSs};
     * the placement runs afterwards and passes the union the solver actually produced, which differs
     * from the ideal by the optical correction and by any strut that clamped the gap. Centering on
     * the ideal width while a different width is drawn is what left a grace syllable and its melisma
     * sitting off their own two notes.
     * <p>
     * The grace and its host are one unioned column for lyric layout, {@code unionWidthSs}
     * wide. The syllable is not the whole of what is drawn: a pair that carries its own melisma
     * ({@code hasMelisma}) draws an extender at least
     * {@link LyricLayoutBuilder#MIN_MELISMA_LENGTH_SS} long past the syllable, and that extender is
     * part of the content being placed. The widths are parameters rather than read off the grace so
     * callers can supply a per-verse box width.
     * <ul>
     *   <li>syllable narrower than the grace notehead → center the whole syllable on the notehead;
     *   <li>syllable alone spanning the union → the melisma is not drawn at all
     *       ({@link #graceSyllableSpansUnion}), so center the syllable on the union and let it
     *       overhang each side equally;
     *   <li>syllable plus melisma within the union → center the syllable's <em>first grapheme</em> on
     *       the notehead, so the syllable reads as beginning at the grace note and flows rightward
     *       toward the host;
     *   <li>syllable plus melisma wider than the union → center the two together on the union, so
     *       the pair keeps its syllable and its extender balanced over its own two notes.
     * </ul>
     */
    public static double graceLyricLeftOffsetSs(
        double syllableWidthSs,
        double firstGraphemeWidthSs,
        double unionWidthSs,
        ElementColumn grace,
        boolean hasMelisma) {

        var noteheadWidthSs = grace.getNoteheadWidthSs();

        if (syllableWidthSs < noteheadWidthSs) {
            return (noteheadWidthSs - syllableWidthSs) / 2;
        }

        if (graceSyllableSpansUnion(syllableWidthSs, unionWidthSs)) {
            return -(syllableWidthSs - unionWidthSs) / 2;
        }

        var contentWidthSs = hasMelisma
            ? syllableWidthSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS
            : syllableWidthSs;

        if (contentWidthSs <= unionWidthSs) {
            return (noteheadWidthSs - firstGraphemeWidthSs) / 2;
        }

        return -(contentWidthSs - unionWidthSs) / 2;
    }

    /**
     * Returns the <em>ideal</em> width of the grace–host lyric union: the grace's right extent as the
     * host faces it (the physical grace→host gap includes the grace flag only where the flag is
     * beside the host, matching {@link #rightExtentFacingSs}), the fixed {@link #GRACE_HOST_REST_SS}
     * grace→host gap, and the host's notehead width (neither the host's flag nor its dots widen the
     * union). Measured from the grace's origin, so the union's right edge is the host's notehead
     * right edge.
     *
     * <p>This is the union as it stands <em>before the line is solved</em>, which is what the
     * reservation path needs: it is position-independent, so it can be asked for while the springs
     * are still being built. It is not the union that ends up on the page. {@link OpticalSpacing}
     * corrects the grace→host rest for the two notes' stem geometry, a strut can clamp the gap above
     * or below that rest, and a tight line can compress it — none of which this formula sees. Code
     * that places lyric ink runs after the solve and must measure the union off the final X
     * positions instead; see {@code LyricLayoutBuilder.spacedGraceHostUnionWidthSs}.
     *
     * <p>An unpaired grace (the line's last column, before its host is entered) has no host span to
     * face, so its union is its own full extent plus the gap the host will sit past.
     */
    public static double idealGraceHostUnionWidthSs(ElementColumn grace, @Nullable ElementColumn host) {
        if (host == null) {
            return grace.getRightExtentSs() + GRACE_HOST_REST_SS;
        }

        return rightExtentFacingSs(grace, host) + GRACE_HOST_REST_SS + host.getNoteheadWidthSs();
    }

    /**
     * Returns whether a grace syllable of {@code syllableWidthSs} is at least as wide as the whole
     * grace–host union. Centered on the union ({@link #graceLyricLeftOffsetSs}), such a syllable
     * already ends at or past the host's notehead right edge — where the pair's melisma would end —
     * so the melisma is not drawn. The melisma is still there in the model: it says the syllable is
     * sung across both notes, and an extender that starts where it would have ended shows nothing of
     * that.
     *
     * <p>{@code unionWidthSs} is the caller's, for the reason given on {@link #graceLyricLeftOffsetSs}
     * — whether the syllable covers the pair has to be judged against the union that gets drawn, not
     * the ideal one.
     */
    public static boolean graceSyllableSpansUnion(double syllableWidthSs, double unionWidthSs) {
        return syllableWidthSs >= unionWidthSs;
    }

    /**
     * Returns whether a grace–host pair carries the melisma of its own that
     * {@code Line.syncGraceHostMelisma} maintains: the grace starts one and the host stops it. A
     * hyphenated grace syllable draws a hyphen to the next syllable instead, and a host that carries
     * the melisma onward ({@link Lyric.Extend#CONTINUE}) ends it somewhere past the pair, so neither
     * is the pair's own.
     *
     * @param graceLyric the grace's lyric for the verse being laid out, or null if it has none
     * @param hostLyric  the host's lyric for the same verse, or null if it has none
     */
    public static boolean pairCarriesGraceHostMelisma(
        @Nullable Lyric graceLyric, @Nullable Lyric hostLyric) {

        return graceLyric != null
            && graceLyric.extend() == Lyric.Extend.START
            && !Lyric.syllabicContinues(graceLyric.syllabic())
            && hostLyric != null
            && hostLyric.extend() == Lyric.Extend.STOP;
    }

    /**
     * Returns the ideal (uncompressed) delta-X for a pair: the previous column's right extent plus
     * the pair's share of the line rest ({@code factor × lineRestSs}, see {@link #restFactorFor}).
     * A grace→host pair is the exception — it takes a fixed {@link #GRACE_HOST_REST_SS} gap that
     * never scales with the line rest.
     */
    private static double baseRestSs(ElementColumn prev, ElementColumn curr, double lineRestSs) {
        if (prev.isGraceNote()) {
            // Grace note → host note: a fixed absolute gap that never varies with the song's line
            // rest — the grace note always packs against its host at the same distance (refs #418).
            return leftInkSs(prev, curr) + GRACE_HOST_REST_SS;
        }

        var gapSs = isBeamInternalGap(prev, curr) ? beamInternalGapSs(prev, curr, lineRestSs) : lineRestSs;
        return leftInkSs(prev, curr) + gapSs;
    }

    /**
     * Returns the glyph-ink component a gap starting at {@code prev} carries: the span from
     * {@code prev}'s origin to its right ink edge. This is the non-whitespace part of the gap's base
     * rest, and the part {@link SpringSpacer} excludes from whitespace levelling. A grace
     * gap is measured to the host note head, so the host's accidental does not widen it; for any
     * other gap, augmentation is excluded so dots and falls never push the next column beyond the
     * comfortable gap — in both cases the note-collision strut, which does use the full extents,
     * keeps the glyphs apart (refs #418, #441, #496). A grace's extent is the one {@code curr} faces
     * ({@link #rightExtentFacingSs}), so the ideal gap and the strut discount the grace's flag on the
     * same terms.
     */
    private static double leftInkSs(ElementColumn prev, ElementColumn curr) {
        return prev.isGraceNote()
            ? rightExtentFacingSs(prev, curr)
            : prev.getRightExtentExcludingAugmentationSs();
    }

    /**
     * Returns the reducing factor applied to the line rest for this pair's base rest: a tight-beam
     * gap packs proportionally tighter than a normal gap, which takes the full line rest (factor
     * {@code 1}). Mirrors the branch selection in {@link #baseRestSs}, and is the factor the lyric
     * lift scales each gap's share of the lift by, so tight gaps stay proportionally tight. Grace
     * gaps are excluded from the lift, so they never reach this method through that path.
     */
    public static double restFactorFor(ElementColumn prev, ElementColumn curr) {
        if (isTightBeamGap(prev, curr)) {
            return BEAM_GROUP_INTERNAL_REST_FACTOR;
        }

        return Spring.NORMAL_WEIGHT;
    }

    /**
     * Returns whether the gap between two columns is internal to a single beam group. Adjacent
     * beam groups have distinct ids, so the gap between them is a normal gap (refs #418).
     */
    private static boolean isBeamInternalGap(ElementColumn prev, ElementColumn curr) {
        return prev.sharesBeamGroupWith(curr);
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

        return lyricGapRequirementSs(
            prev, curr, beforePrev, afterCurr, ElementColumn::getMinCollisionGapToNextSyllableSs);
    }

    /**
     * Returns the delta-X this gap needs for the flanking syllables to clear each other by
     * {@code gapAccessor}'s floor, or 0 when neither column's lyric reaches into the gap.
     *
     * <p>Each side contributes the part of its syllable that overhangs toward the gap, measured from
     * the notehead (accidentals and augmentation dots excluded), so a lone syllable still reserves
     * its footprint against an unlyriced neighbour. {@code gapAccessor} selects which inter-syllable
     * floor applies: the collision floor for a strut, or the comfortable gap for a lifted rest.
     *
     * <p>When {@code prev} hosts a grace, the grace column carries the syllable and its hyphen or
     * space, so the floor comes from the grace rather than the empty host — the grace and its host
     * behave as one unioned column for lyric layout.
     */
    static double lyricGapRequirementSs(
        ElementColumn prev,
        ElementColumn curr,
        @Nullable ElementColumn beforePrev,
        @Nullable ElementColumn afterCurr,
        ToDoubleFunction<? super ElementColumn> gapAccessor) {

        var right = lyricRightExtentSs(prev, beforePrev);
        var left = lyricLeftExtentSs(curr, afterCurr);

        if (right <= 0 && left <= 0) {
            return 0;
        }

        var gapSource = beforePrev != null && beforePrev.isGraceNote() ? beforePrev : prev;

        return right + gapAccessor.applyAsDouble(gapSource) + left;
    }

    /**
     * Returns how far {@code column}'s syllable reaches left of the column origin (its notehead's
     * left edge), used when the column is the right side of a gap. A grace column reaches however far
     * {@link #graceLyricLeftOffsetSs} places its box left of the origin; a normal syllable-bearing
     * column reaches half its width minus the notehead-center offset; anything else reaches nothing.
     */
    static double lyricLeftExtentSs(ElementColumn column, @Nullable ElementColumn afterColumn) {
        if (column.isGraceNote() && afterColumn != null) {
            return Math.max(0, -graceLyricLeftOffsetSs(column, afterColumn));
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
     * reaches however far the grace lyric's ink — syllable and melisma alike — lands past the host's
     * own origin. A normal syllable-bearing column reaches half its width plus the notehead-center
     * offset; anything else reaches nothing.
     */
    static double lyricRightExtentSs(ElementColumn column, @Nullable ElementColumn beforeColumn) {
        if (column.isGraceNote()) {
            return 0;
        }

        if (beforeColumn != null && beforeColumn.isGraceNote()) {
            var graceHostGapSs = rightExtentFacingSs(beforeColumn, column) + GRACE_HOST_REST_SS;
            return Math.max(0, graceLyricRightInkSs(beforeColumn, column) - graceHostGapSs);
        }

        if (column.hasSyllable()) {
            return column.getSyllableWidthSs() / 2 + column.getNoteheadWidthSs() / 2;
        }

        return 0;
    }

    /**
     * Returns the offset from {@code grace}'s origin to its syllable box, for the verse the columns
     * were built for — the one {@link ElementColumn#getSyllableWidthSs()} was measured for.
     */
    private static double graceLyricLeftOffsetSs(ElementColumn grace, ElementColumn host) {
        return graceLyricLeftOffsetSs(
            grace.getSyllableWidthSs(),
            grace.getSyllableFirstGraphemeWidthSs(),
            idealGraceHostUnionWidthSs(grace, host),
            grace,
            hasGraceHostMelisma(grace, host));
    }

    /**
     * Returns how far the grace's lyric ink reaches right of the grace's own origin: the syllable's
     * right edge, plus the minimum-length melisma when the pair draws one.
     *
     * <p>The reservation is the melisma's <em>minimum</em> length, not the host notehead it prefers
     * to end at: {@link LyricLayoutBuilder} clamps an extender back off a following syllable by one
     * lyric space, which is narrower than the inter-syllable floor this extent is measured against,
     * so an extender anchored at its host still gives way to a crowding syllable — but never below
     * the minimum length that makes it read as a line at all.
     */
    private static double graceLyricRightInkSs(ElementColumn grace, ElementColumn host) {
        var syllableWidthSs = grace.getSyllableWidthSs();
        var unionWidthSs = idealGraceHostUnionWidthSs(grace, host);
        var hasMelisma = hasGraceHostMelisma(grace, host);
        var syllableEndSs = graceLyricLeftOffsetSs(
            syllableWidthSs,
            grace.getSyllableFirstGraphemeWidthSs(),
            unionWidthSs,
            grace,
            hasMelisma) + syllableWidthSs;

        if (!hasMelisma || graceSyllableSpansUnion(syllableWidthSs, unionWidthSs)) {
            return syllableEndSs;
        }

        return syllableEndSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS;
    }

    /**
     * {@link #pairCarriesGraceHostMelisma} for the verse the columns were built for. Read off the
     * columns rather than their elements so the melisma this reserves space for belongs to the same
     * verse the syllable widths were measured for.
     */
    private static boolean hasGraceHostMelisma(ElementColumn grace, ElementColumn host) {
        return pairCarriesGraceHostMelisma(grace.getLyric(), host.getLyric());
    }

    /**
     * Returns the delta-X a connecting glissando needs to clear the next column's left-side glyphs
     * (accidental included) and still keep its minimum visible length, or 0 when the previous
     * column has no glissando. Inverts the retired greedy path's
     * {@code gap = spacingSs + currLeft - prevRight} so the reservation is a hard floor on the
     * spring's strut rather than a post-applied widening (refs #443).
     */
    private static double glissandoReservationFloorSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.hasGlissando()) {
            return 0;
        }

        return rightExtentFacingSs(prev, curr)
            - curr.getLeftExtentSs()
            + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
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
