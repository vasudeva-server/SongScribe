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

import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.EngravingConstants;
import songscribe.engraving.HairpinMetrics;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.LogUtils;

/**
 * Calculates horizontal X positions for note columns following Gould/Ross engraving principles:
 * spacing is non-proportional (rhythmic value does not determine distance), syllable widths
 * determine column gaps, and a beam group packs tightly unless lyrics force expansion.
 */
public final class HorizontalSpacingCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(HorizontalSpacingCalculator.class);

    /**
     * The single minimum gap between adjacent note columns, applied wherever a gap can be squeezed.
     * Measured to the leftmost glyph, so a note shifts right only when its accidental would come
     * closer than this to the previous element. Lyric spacing may require more.
     */
    public static final double MIN_COLUMN_GAP_SS = 1.0;  // 8px
    public static final double DEFAULT_COLUMN_GAP_SS = 2.5;  // 20px
    /**
     * Ideal gap between a grace note and its host note: independent of the song's line rest and of
     * the line's syllables, and the one gap the lyric lift never widens. Shared by the spring path
     * ({@link #buildSpring}) and the grace-insertion lock preview.
     */
    public static final double GRACE_HOST_REST_SS = 2.0;  // 16px
    /** How far a compressed line may tighten a grace→host gap below {@link #GRACE_HOST_REST_SS}. */
    public static final double GRACE_HOST_COMPRESSION_ALLOWANCE_SS = 0.5;  // 4px
    /**
     * Reducing factor applied to the line rest for a gap internal to a beam group whose two notes
     * are both shorter than an eighth note. A pair touching an eighth note (or longer) packs at
     * the full line rest. Beam groups still widen if lyrics require it.
     */
    public static final double BEAM_GROUP_INTERNAL_REST_FACTOR = 0.8;

    private HorizontalSpacingCalculator() {
    }

    public static double calculateFirstElementXSs(Line line) {
        return calculateFirstElementXSs(line.getRunningKey());
    }

    /**
     * LilyPond measures this from a different edge depending on what the note follows: a key
     * signature's right edge, or, for a bare clef, a floor on the span from the clef's <em>left</em>
     * edge.
     */
    public static double calculateFirstElementXSs(Key key) {
        if (key.signatureWidthSs() == 0) {
            return LayoutEngine.CLEF_X_POSITION_SS
                + Math.max(SMuFLMetadata.advanceWidthSs(SMuFLGlyph.G_CLEF), StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS);
        }

        return calculateHeaderRightEdgeSs(key)
            + StaffHeaderMetrics.KEY_SIGNATURE_FIRST_NOTE_GAP_SS;
    }

    /** The only place {@link StaffHeaderMetrics#CLEF_GAP_SS} is applied; {@link StaffHeader} does not carry it. */
    public static double calculateKeySignatureXSs() {
        return LayoutEngine.CLEF_X_POSITION_SS
            + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.G_CLEF)
            + StaffHeaderMetrics.CLEF_GAP_SS;
    }

    /**
     * @param line a null line has no key signature
     */
    public static double calculateHeaderRightEdgeSs(@Nullable Line line) {
        if (line == null) {
            return calculateHeaderRightEdgeSs(Key.NO_ACCIDENTALS);
        }

        return calculateHeaderRightEdgeSs(line.getRunningKey());
    }

    public static double calculateHeaderRightEdgeSs(Key key) {
        var keySignatureWidthSs = key.signatureWidthSs();

        if (keySignatureWidthSs == 0) {
            return LayoutEngine.CLEF_X_POSITION_SS + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.G_CLEF);
        }

        return calculateKeySignatureXSs() + keySignatureWidthSs;
    }

    /**
     * A point exactly on the header's right edge counts as inside, so this agrees with the
     * staff-line hit region.
     *
     * @param line a null line has no key signature
     */
    public static boolean isWithinHeaderXSs(double xSs, @Nullable Line line) {
        return xSs <= calculateHeaderRightEdgeSs(line);
    }

    // ==========================================================================
    // Column-to-Column Spacing
    // ==========================================================================

    /**
     * The result is an origin-to-origin delta that guarantees a constant amount of clear space
     * between the two columns' facing ink, whatever either holds — see {@link #minimumInkGapSs}.
     *
     * <p>Ahead of a mid-line {@link KeyChangeElement} the floor is the key-signature padding,
     * which is also that gap's rest. Behind it the ordinary floor applies, so the last accidental
     * clears the following note's leftmost ink by {@link #MIN_COLUMN_GAP_SS}. The key signature's
     * contribution is its <em>drawn</em> width, so a wider change pushes the note further.
     */
    private static double calculateMinimumColumnSpacingSs(
        ElementColumn prevColumn,
        ElementColumn currColumn) {

        // Distance from previous column's center to current column's center
        // = previous column's right extent + minimum ink gap + abs(current column's left extent)
        return rightExtentFacingSs(prevColumn, currColumn)
            + GapKind.of(prevColumn, currColumn).minimumInkGapSs()
            + Math.abs(currColumn.getLeftExtentSs());
    }

    /**
     * What kind of gap sits between two adjacent columns, and everything that kind decides: how far
     * the pair rests, how close their ink may come, whether the gap takes lyric lift, and how it
     * compresses. Classified once, so a new kind cannot be added to three of the answers and missed
     * in the fourth; the switches are exhaustive.
     */
    private enum GapKind {

        /** A fixed absolute gap taking no lyric lift; it still compresses, bounded by {@link #graceCompressionFloorSs}. */
        GRACE_TO_HOST,

        /**
         * A barline and the mid-line key signature behind it, one typeset unit: the accidentals
         * stand a fixed padding behind their barline, which is this gap's floor as well as its
         * rest, so the gap is frozen.
         */
        BARLINE_TO_KEY_SIGNATURE,

        /** Internal to one beam group, both notes shorter than an eighth; keeps its reduction as its solver weight. */
        TIGHT_BEAM,

        /** Every other pair: a full line rest, freely compressible, taking its share of the lift. */
        NORMAL;

        /**
         * A key change's position invariant guarantees a barline or repeat in front of it,
         * so the pair is named by {@code curr} alone.
         */
        static GapKind of(ElementColumn prev, ElementColumn curr) {
            if (prev.isGraceNote()) {
                return GRACE_TO_HOST;
            }

            if (curr.isKeyChange()) {
                return BARLINE_TO_KEY_SIGNATURE;
            }

            if (isTightBeamGap(prev, curr)) {
                return TIGHT_BEAM;
            }

            return NORMAL;
        }

        /** Excludes the left column's ink. */
        double restSs(double lineRestSs) {
            return switch (this) {
                case GRACE_TO_HOST -> GRACE_HOST_REST_SS;
                case BARLINE_TO_KEY_SIGNATURE -> EngravingConstants.KEY_SIGNATURE_PADDING_SS;
                case TIGHT_BEAM -> BEAM_GROUP_INTERNAL_REST_FACTOR * lineRestSs;
                case NORMAL -> lineRestSs;
            };
        }

        double minimumInkGapSs() {
            return switch (this) {
                case BARLINE_TO_KEY_SIGNATURE -> EngravingConstants.KEY_SIGNATURE_PADDING_SS;
                case GRACE_TO_HOST, TIGHT_BEAM, NORMAL -> MIN_COLUMN_GAP_SS;
            };
        }

        boolean isLiftExempt() {
            return switch (this) {
                case GRACE_TO_HOST, BARLINE_TO_KEY_SIGNATURE -> true;
                case TIGHT_BEAM, NORMAL -> false;
            };
        }

        /** Also the factor the lyric lift scales its share by. */
        double weight() {
            return switch (this) {
                case TIGHT_BEAM -> BEAM_GROUP_INTERNAL_REST_FACTOR;
                case GRACE_TO_HOST, BARLINE_TO_KEY_SIGNATURE, NORMAL -> Spring.NORMAL_WEIGHT;
            };
        }
    }

    /**
     * How far {@code prev}'s ink reaches right of its origin as {@code curr} meets it — the single
     * source of truth for the left-hand ink of every gap, so the ideal rest, the collision strut,
     * the glissando reservation and the grace–host lyric union never disagree.
     *
     * <p>For a grace note this is {@link ElementColumn#getRightExtentFacingSs} against
     * {@code curr}'s <em>left-facing</em> band, so a flag hanging well above the host is not
     * charged. Every other column is charged its full {@link ElementColumn#getRightExtentSs()};
     * generalising the discount wants LilyPond's skyline machinery rather than a one-glyph special
     * case.
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
     * The single source of truth for per-pair horizontal spacing, shared by full layout and
     * insertion: a delta-X (Ss) between the two columns' origins. Grace, key signature and
     * glissando are folded in here, so a {@code Spring} needs no post-processing.
     *
     * <p>See {@code docs/layout-geometry.md} for the full recipe with each floor's formula.
     */
    public static Spring buildSpring(ElementColumn prev, ElementColumn curr, double lineRestSs) {
        return buildSpring(prev, curr, lineRestSs, null, null, null);
    }

    /**
     * @param line {@code null} when the pair is not a pair of that line's own adjacent elements — a
     *             paste-position lookup, an insertion preview, or the flush-right terminal. Only
     *             the hairpin minimum-length reservation consults it.
     */
    public static Spring buildSpring(
        ElementColumn prev,
        ElementColumn curr,
        double lineRestSs,
        @Nullable Line line,
        @Nullable ElementColumn beforePrev,
        @Nullable ElementColumn afterCurr) {

        var gapKind = GapKind.of(prev, curr);
        var restSs = leftInkSs(prev, curr) + gapKind.restSs(lineRestSs);
        var strutSs = Math.max(
            calculateMinimumColumnSpacingSs(prev, curr),
            Math.max(
                syllableCollisionFloorSs(prev, curr, beforePrev, afterCurr),
                Math.max(
                    glissandoReservationFloorSs(prev, curr),
                    Math.max(
                        graceCompressionFloorSs(prev, restSs),
                        hairpinReservationFloorSs(line, prev, curr)))));

        // The gap's glyph-ink component becomes the spring's level offset, so the whitespace-aware
        // solver levels the visual gap (rest minus ink) rather than the origin-to-origin delta. The
        // weight matches the reducing factor applied to the line rest in the base rest, so
        // levelOffset + weight × lineRest reproduces the base rest exactly — whitespace levelling is
        // a strict generalisation of the rest model, not a second spacing scheme.
        return Spring.of(
            restSs, strutSs, gapKind.weight(), gapKind.isLiftExempt(), leftInkSs(prev, curr));
    }

    /**
     * Allows {@link #GRACE_HOST_COMPRESSION_ALLOWANCE_SS} of give, so a fixed grace gap cannot turn
     * an otherwise-fittable line infeasible. Measured from the <em>uncorrected</em> ideal
     * {@code baseRestSs}, since {@link OpticalSpacing} shifts rests without touching struts.
     */
    private static double graceCompressionFloorSs(ElementColumn prev, double baseRestSs) {
        if (!prev.isGraceNote()) {
            return 0;
        }

        return baseRestSs - GRACE_HOST_COMPRESSION_ALLOWANCE_SS;
    }

    /**
     * Beam handling ends here: every downstream stage can stay beam-unaware.
     *
     * @param columns These need not be the line's own elements in the line's own order — an
     *                insertion preview splices a not-yet-added column in — so any line-scoped
     *                floor must key off the pair's elements rather than off their position in
     *                this list.
     */
    public static List<Spring> buildSprings(List<ElementColumn> columns, Line line) {
        var lineRestSs = line.getSong().getDefaultRestLengthSs();
        var springs = new ArrayList<Spring>();

        for (var i = 1; i < columns.size(); i++) {
            var beforePrev = i >= 2 ? columns.get(i - 2) : null;
            var afterCurr = i + 1 < columns.size() ? columns.get(i + 1) : null;
            springs.add(
                buildSpring(columns.get(i - 1), columns.get(i), lineRestSs, line, beforePrev, afterCurr));
        }

        return springs;
    }

    // ==========================================================================
    // Line solve — the shared anchor + fit, so full layout and the insertion
    // pre-check always agree on whether a line fits.
    // ==========================================================================

    /**
     * The horizontal solve for one line. Both {@link LayoutEngine} and the insertion pre-check
     * ({@link InsertionSpacingCalculator}) run the identical solve through {@link #solveLine} /
     * {@link #solveChain}, so they can never disagree.
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
     * An accidental does not move the notehead; LilyPond measures to the notehead's left edge. A
     * wide first syllable does still push the origin right, so the lyric does not crowd the clef
     * or key signature.
     */
    public static double calculateAnchorXSs(ElementColumn firstColumn, Line line) {
        return calculateAnchorXSs(firstColumn, line.getRunningKey());
    }

    /** The same anchor against a stated header key, so a re-header can be measured before commit ({@link KeyEditFitCalculator}). */
    public static double calculateAnchorXSs(ElementColumn firstColumn, Key headerKey) {
        var firstElementXSs = calculateFirstElementXSs(headerKey);
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
     * The span the gaps may consume leaves the last glyph's right edge, not its origin, at or
     * before the margin. Shared by full layout and the insertion pre-check.
     */
    public static SpringSolveResult solveChain(
        List<Spring> springs,
        double firstXSs,
        double trailingReservationSs,
        double staffRightMarginSs) {

        var availableSpanSs = staffRightMarginSs - firstXSs - trailingReservationSs;
        return SpringSpacer.solve(springs, availableSpanSs);
    }

    public static double trailingReservationSs(ElementColumn lastColumn, Line line) {
        return trailingReservationSs(lastColumn, line, LineKeys.of(line));
    }

    /**
     * The column's own right extent plus a trailing gap: zero when {@code lastColumn} carries the
     * auto-maintained terminal barline, which is pinned flush-right, and a full line rest otherwise.
     *
     * <p>A line that leads into a {@link CautionaryKeySignature} takes that cautionary's
     * {@link CautionaryKeySignature#reservationSs() run} as its whole trailing gap instead; taking
     * the larger of the two would leave it further from its barline than
     * {@link EngravingConstants#KEY_SIGNATURE_PADDING_SS}.
     */
    private static double trailingReservationSs(ElementColumn lastColumn, Line line, LineKeys keys) {
        var song = line.getSong();
        var lineRestSs = song.getDefaultRestLengthSs();
        var lastElement = lastColumn.getElement();
        var cautionary = CautionaryKeySignature.of(keys, lastElement, lineRestSs);

        if (cautionary != null) {
            return lastColumn.getRightExtentSs() + cautionary.reservationSs();
        }

        // The song's own terminal-identity test, not a bare type check: a barline that merely looks
        // like a terminal — one ending a non-last line, or a projected clone that is not the line's
        // element — is not pinned flush-right, so it still owes the trailing rest.
        var trailingGapSs = song.isTerminal(lastElement, line) ? 0.0 : lineRestSs;

        return lastColumn.getRightExtentSs() + trailingGapSs;
    }

    /**
     * The caller supplies the final ordered column list, so full layout and the insertion
     * pre-check get the same anchor, span and solve, and their verdicts agree.
     *
     * @param columns never empty
     */
    public static LineSolution solveLine(List<ElementColumn> columns, Line line, double staffRightMarginSs) {
        return solveLine(columns, line, staffRightMarginSs, LineKeys.of(line));
    }

    /**
     * The same solve against <em>projected</em> keys rather than the ones the song currently holds,
     * so an edit that re-keys a line can be measured before it is committed
     * ({@link KeyEditFitCalculator}).
     *
     * @param columns never empty
     * @param keys    {@link LineKeys#of(Line)} is what the committed layout uses
     */
    public static LineSolution solveLine(
        List<ElementColumn> columns, Line line, double staffRightMarginSs, LineKeys keys) {

        var lifted = LyricLift.applyLyricLift(buildSprings(columns, line), columns);
        var springs = OpticalSpacing.applyCorrections(lifted, columns);
        var firstXSs = calculateAnchorXSs(columns.getFirst(), keys.headerKey());
        var reservationSs = trailingReservationSs(columns.getLast(), line, keys);
        var result = solveChain(springs, firstXSs, reservationSs, staffRightMarginSs);

        if (LogUtils.isTracingSpacing(LOG)) {
            logLineSolve(columns, line, lifted, springs, result, staffRightMarginSs - firstXSs - reservationSs);
        }

        return new LineSolution(firstXSs, springs, result);
    }

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
            line.index(), availableSpanSs, naturalSpanSs, verdict));

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

    private static double whitespaceSs(double placedSs, ElementColumn prev, ElementColumn curr) {
        return placedSs - rightExtentFacingSs(prev, curr) + curr.getLeftExtentSs();
    }

    private static void appendCorrectionBreakdown(StringBuilder sb, ElementColumn prev, ElementColumn curr) {
        var oppositeSs = OpticalSpacing.oppositeStemCorrectionSs(prev, curr);
        var sameSs = OpticalSpacing.sameDirectionCorrectionSs(prev, curr);
        var barlineSs = OpticalSpacing.downstemAfterBarlineCorrectionSs(prev, curr);

        if (oppositeSs == 0.0 && sameSs == 0.0 && barlineSs == 0.0) {
            return;
        }

        sb.append(String.format(
            "%n         corr: opposite=%+.2f same=%+.2f barline=%+.2f", oppositeSs, sameSs, barlineSs));

        if (prev.stemDirection() != null && curr.stemDirection() != null) {
            sb.append(String.format(
                " (overlap=%.2f deltaPos=%+.2f)",
                OpticalSpacing.verticalOverlapSs(prev, curr),
                curr.getPositionSs() - prev.getPositionSs()));
        }
    }

    private static String describeForLog(ElementColumn column) {
        var type = column.getElement().getType();
        var direction = column.stemDirection();

        if (direction == null) {
            return type.name();
        }

        return String.format(
            "%s(%s@%.1f %.1f..%.1f)",
            type.name(),
            direction,
            column.getPositionSs(),
            column.getAbsoluteTopYSs(),
            column.getAbsoluteBottomYSs());
    }

    /**
     * Negative when the syllable starts left of the notehead. Single source of truth for where a
     * grace lyric sits: {@link LyricLayoutBuilder} adds it to the grace's X to place the box, and
     * the extent methods below derive the neighbour reservations from the same formula. The
     * reservation passes the ideal {@link #idealGraceHostUnionWidthSs}; the placement runs after
     * the solve and passes the union actually produced.
     * <p>
     * The grace and its host are one unioned column for lyric layout, the union width wide. A pair
     * that carries its own melisma ({@link PairMelisma#CARRIED}) draws an extender at least
     * {@link LyricLayoutBuilder#MIN_MELISMA_LENGTH_SS} long past the syllable.
     * <ul>
     *   <li>syllable narrower than the grace notehead → center the whole syllable on the notehead;
     *   <li>syllable alone spanning the union → the melisma is not drawn at all
     *       ({@link #graceSyllableSpansUnion}), so center the syllable on the union;
     *   <li>syllable plus melisma within the union → center the syllable's <em>first grapheme</em> on
     *       the notehead;
     *   <li>syllable plus melisma wider than the union → center the two together on the union.
     * </ul>
     */
    public static double graceLyricLeftOffsetSs(GraceSyllable syllable, ElementColumn grace) {
        var syllableWidthSs = syllable.syllableWidthSs();
        var unionWidthSs = syllable.unionWidthSs();
        var noteheadWidthSs = grace.getNoteheadWidthSs();

        if (syllableWidthSs < noteheadWidthSs) {
            return (noteheadWidthSs - syllableWidthSs) / 2;
        }

        if (graceSyllableSpansUnion(syllableWidthSs, unionWidthSs)) {
            return -(syllableWidthSs - unionWidthSs) / 2;
        }

        var contentWidthSs = syllable.pairMelisma() == PairMelisma.CARRIED
            ? syllableWidthSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS
            : syllableWidthSs;

        if (contentWidthSs <= unionWidthSs) {
            return (noteheadWidthSs - syllable.firstGraphemeWidthSs()) / 2;
        }

        return -(contentWidthSs - unionWidthSs) / 2;
    }

    /**
     * Measured from the grace's origin, so the union's right edge is the host's notehead right
     * edge; neither the host's flag nor its dots widen it.
     *
     * <p>This is the union <em>before the line is solved</em>, which the reservation path needs. It
     * is not the union that ends up on the page ({@link OpticalSpacing}, struts and compression all
     * change it): code that places lyric ink after the solve measures from the final X positions;
     * see {@code LyricLayoutBuilder.spacedGraceHostUnionWidthSs}.
     *
     * <p>An unpaired grace (the line's last column) has no host span to face, so its union is its
     * own full extent plus the gap the host will sit past.
     */
    public static double idealGraceHostUnionWidthSs(ElementColumn grace, @Nullable ElementColumn host) {
        if (host == null) {
            return grace.getRightExtentSs() + GRACE_HOST_REST_SS;
        }

        return rightExtentFacingSs(grace, host) + GRACE_HOST_REST_SS + host.getNoteheadWidthSs();
    }

    /**
     * A syllable at least as wide as the whole grace–host union already ends at or past the host's
     * notehead right edge, so the pair's melisma is not drawn. The melisma stays in the model.
     *
     * <p>{@code unionWidthSs} is the caller's, judged against the union that gets drawn, not the
     * ideal one (see {@link #graceLyricLeftOffsetSs}).
     */
    public static boolean graceSyllableSpansUnion(double syllableWidthSs, double unionWidthSs) {
        return syllableWidthSs >= unionWidthSs;
    }

    /**
     * Whether a grace–host pair carries the melisma of its own that
     * {@code LyricRun.syncGraceHostMelisma} maintains: the grace starts one and the host stops it.
     *
     * @param graceLyric null if the grace has none for the script being laid out
     * @param hostLyric  null if the host has none for the same script
     */
    public static PairMelisma pairCarriesGraceHostMelisma(
        @Nullable Lyric graceLyric, @Nullable Lyric hostLyric) {

        var carried = graceLyric != null
            && graceLyric.extend() == Lyric.Extend.START
            && !Lyric.syllabicContinues(graceLyric.syllabic())
            && hostLyric != null
            && hostLyric.extend() == Lyric.Extend.STOP;

        return carried ? PairMelisma.CARRIED : PairMelisma.NONE;
    }

    public enum PairMelisma {
        CARRIED,
        NONE
    }

    /**
     * The widths are given rather than read off the grace so a caller can supply a per-script box
     * width.
     *
     * @param firstGraphemeWidthSs measured in the font the syllable is drawn in
     */
    public record GraceSyllable(
        double syllableWidthSs,
        double firstGraphemeWidthSs,
        double unionWidthSs,
        PairMelisma pairMelisma
    ) {}

    /**
     * The non-whitespace part of the gap's base rest, which {@link SpringSpacer} excludes from
     * whitespace levelling. Augmentation is excluded so dots and falls never push the next column
     * beyond the comfortable gap; the collision strut, which uses the full extents, keeps the
     * glyphs apart.
     */
    private static double leftInkSs(ElementColumn prev, ElementColumn curr) {
        return prev.isGraceNote()
            ? rightExtentFacingSs(prev, curr)
            : prev.getRightExtentExcludingAugmentationSs();
    }

    /**
     * This is {@link GapKind#weight()} — the same value the solver uses, so the reduction is
     * applied identically at rest and under compression. It is also the factor the lyric lift
     * scales each gap's share by.
     */
    public static double restFactorFor(ElementColumn prev, ElementColumn curr) {
        return GapKind.of(prev, curr).weight();
    }

    private static boolean isBeamInternalGap(ElementColumn prev, ElementColumn curr) {
        return prev.sharesBeamGroupWith(curr);
    }

    private static boolean isTightBeamGap(ElementColumn prev, ElementColumn curr) {
        return isBeamInternalGap(prev, curr) && bothShorterThanEighth(prev, curr);
    }

    /**
     * Each column contributes the half of its own syllable that overhangs toward the gap,
     * separated by the floor {@code prev} carries as its
     * {@link ElementColumn#getMinCollisionGapToNextSyllableSs()}. A lone syllable still reserves
     * its footprint against an unlyriced neighbour.
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
     * Each side contributes the part of its syllable that overhangs toward the gap, measured from
     * the notehead. {@code gapAccessor} selects the inter-syllable floor: the collision floor for
     * a strut, or the comfortable gap for a lifted rest.
     *
     * <p>When {@code prev} hosts a grace, the floor comes from the grace column, since the grace
     * and its host behave as one unioned column for lyric layout.
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
     * A grace column reaches nothing (its lyric is deferred to the host). A host of a grace
     * ({@code beforeColumn} is a grace) reaches however far the grace lyric's ink — syllable and
     * melisma alike — lands past the host's own origin.
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

    private static double graceLyricLeftOffsetSs(ElementColumn grace, ElementColumn host) {
        return graceLyricLeftOffsetSs(idealGraceSyllable(grace, host), grace);
    }

    private static GraceSyllable idealGraceSyllable(ElementColumn grace, ElementColumn host) {
        return new GraceSyllable(
            grace.getSyllableWidthSs(),
            grace.getSyllableFirstGraphemeWidthSs(),
            idealGraceHostUnionWidthSs(grace, host),
            graceHostPairMelisma(grace, host));
    }

    /**
     * The reservation is the melisma's <em>minimum</em> length, not the host notehead it prefers
     * to end at, since {@link LyricLayoutBuilder} clamps an extender back off a following syllable.
     */
    private static double graceLyricRightInkSs(ElementColumn grace, ElementColumn host) {
        var syllable = idealGraceSyllable(grace, host);
        var syllableWidthSs = syllable.syllableWidthSs();
        var syllableEndSs = graceLyricLeftOffsetSs(syllable, grace) + syllableWidthSs;

        if (syllable.pairMelisma() == PairMelisma.NONE
            || graceSyllableSpansUnion(syllableWidthSs, syllable.unionWidthSs())) {
            return syllableEndSs;
        }

        return syllableEndSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS;
    }

    /** Read off the columns, so the melisma belongs to the script the syllable widths were measured for. */
    private static PairMelisma graceHostPairMelisma(ElementColumn grace, ElementColumn host) {
        return pairCarriesGraceHostMelisma(grace.getLyric(), host.getLyric());
    }

    private static double glissandoReservationFloorSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.hasGlissando()) {
            return 0;
        }

        return rightExtentFacingSs(prev, curr)
            - curr.getLeftExtentSs()
            + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
    }

    /**
     * Reserving for a text dynamic on a bound, which moves that bound's tip inward, is what stops an
     * {@code f<} from being squashed on a compressed line (see {@link HairpinMetrics#MINIMUM_LENGTH_SS}).
     * <p>
     * The question is asked about the two <em>elements</em> being spaced, not their position in
     * the column list, since an insertion preview splices a not-yet-added column between a
     * hairpin's anchor and end. A pair with an element the line does not contain reserves nothing.
     * <p>
     * This only constrains a hairpin whose anchor and end are adjacent columns. A dynamic on the
     * element <em>outside</em> the span, and the back-to-back rule, also move a tip inward and are
     * not reserved for.
     */
    private static double hairpinReservationFloorSs(
        @Nullable Line line, ElementColumn prev, ElementColumn curr) {

        if (line == null) {
            return 0;
        }

        var anchorElement = prev.getElement();
        var endElement = curr.getElement();

        if (!line.contains(anchorElement) || !line.contains(endElement)) {
            return 0;
        }

        var anchorIndex = line.getElementIndex(anchorElement);
        var endIndex = line.getElementIndex(endElement);

        if (!line.spanRegistry().hasSpan(Hairpin.class, Span.exactly(new ElementRange(anchorIndex, endIndex)))) {
            return 0;
        }

        return HairpinMetrics.MINIMUM_LENGTH_SS
            + hairpinLeftTipOffsetSs(anchorElement)
            - hairpinRightTipOffsetSs(endElement, curr.getNoteheadWidthSs());
    }

    private static double hairpinLeftTipOffsetSs(StaffElement anchorElement) {
        var dynamic = anchorElement.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return 0;
        }

        return HairpinEndpoints.dynamicLeftTipOffsetSs(anchorElement, dynamic);
    }

    private static double hairpinRightTipOffsetSs(
        StaffElement endElement, double noteheadWidthSs) {

        var dynamic = endElement.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return noteheadWidthSs;
        }

        return HairpinEndpoints.dynamicRightTipOffsetSs(endElement, dynamic);
    }

    // ==========================================================================
    // Beam Group Spacing
    // ==========================================================================

    private static boolean bothShorterThanEighth(ElementColumn prev, ElementColumn curr) {
        return prev.getElement().getType().beamCount() > 1
            && curr.getElement().getType().beamCount() > 1;
    }

}
