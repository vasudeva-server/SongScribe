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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;

/**
 * Computes lyric box and connector geometry from per-element {@link Lyric} records.
 * <p>
 * Span rules (authoritative):
 * <ul>
 *   <li>note-with-Lyric(relation = SYLLABLE/COMPOUND_WORD) → HYPHEN span to next syllable</li>
 *   <li>note-with-Lyric(extend = START) → EXTENDER span from end of syllable onward</li>
 *   <li>note-with-Lyric(extend = STOP) → active EXTENDER ends at
 *       {@code max(syllable end + }{@value MIN_MELISMA_LENGTH_SS}{@code , note's notehead right edge)},
 *       matching LilyPond's {@code LyricExtender} minimum-length rule; emits no lyric box (the
 *       lyric is a melisma terminator)</li>
 *   <li>note-with-Lyric(extend = CONTINUE) → continues current EXTENDER silently (cross-line
 *       carrier); emits no lyric box</li>
 *   <li>note-with-no-Lyric + active EXTENDER → continues current EXTENDER</li>
 *   <li>rest-with-no-Lyric + active EXTENDER → breaks EXTENDER (ends at rest left edge)</li>
 *   <li>rest-with-Lyric(extend = START/CONTINUE) + active EXTENDER → continues EXTENDER through
 *       rest</li>
 *   <li>rest-with-Lyric(extend = STOP) + active EXTENDER → ends EXTENDER the same way as the
 *       note case above, anchored to the rest's notehead-equivalent right edge</li>
 *   <li>note-with-Lyric(extend = NONE, text) + active EXTENDER → EXTENDER ends at start of this
 *       syllable; a new span (if any) begins after this syllable</li>
 *   <li>host of a paired grace note → emits no lyric box (the syllable belongs to the grace); a
 *       STOP on it ends the active EXTENDER exactly as the note case above, closing the automatic
 *       grace-host melisma, while any other lyric state passes through so a hyphen or extender
 *       from the grace reaches the next lyric-bearing element. The one exception is a grace
 *       syllable that already spans the grace-host union
 *       ({@link HorizontalSpacingCalculator#graceSyllableSpansUnion}): the syllable itself already
 *       reaches the host, so the melisma — real in the model — is not drawn at all</li>
 * </ul>
 * <p>
 * Spans that extend past the last column produce a {@link LyricConnectorLayout.Kind#DANGLING_EXTENDER}
 * anchored to the right edge of the last eligible element on the line; the caller threads
 * {@link Result#hasTrailingContinuation()} into the next line's {@code hasLeadingContinuation}
 * so that line emits a matching leading stub from x = 0 to its first lyric-bearing element
 * (or to the first rest that breaks the continuation).
 */
public final class LyricLayoutBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LyricLayoutBuilder.class);

    // LilyPond's LyricExtender minimum-length (scm/define-grobs.scm): the extender's total length,
    // measured from the end of the syllable, so a melisma spanning few/close notes still reads as a
    // visible line. Longer melismas are unaffected: the extender is clamped to end no earlier than
    // the terminating note's notehead right edge (see ElementColumn#getNoteheadRightEdgeXSs), never
    // the column's right edge, so a stem, flag, or augmentation dot never shifts where it ends.
    static final double MIN_MELISMA_LENGTH_SS = 1.5;

    private LyricLayoutBuilder() {}

    public record Result(
        Map<StaffElement, List<LyricBoxLayout>> boxes,
        List<LyricConnectorLayout> connectors,
        boolean hasTrailingContinuation
    ) {}

    /**
     * Builds lyric boxes and connectors for a single line.
     * <p>
     * Only {@code activeVerse} is laid out. A song's other verses are the same lyrics in other
     * languages, held in the document but never shown alongside the one the user picked, so they
     * produce no boxes, no connectors and no second row to make space for.
     * <p>
     * {@code activeVerse} stamps the boxes and connectors this emits. The lyrics themselves come
     * off the columns, which {@link ElementColumnBuilder} already resolved for that verse and
     * measured the cached syllable widths against, so text and widths cannot come from different
     * verses.
     *
     * @param columns                per-element columns in line order (X positions already finalized)
     * @param activeVerse            the verse to lay out, from {@link songscribe.dom.Song#getActiveVerse()}
     * @param lyricRenderMetrics     metrics used to measure syllable text widths
     * @param hasLeadingContinuation true if the previous line ended with an active extender
     *                               that should continue from x = 0 on this line
     * @param lineWidthSs            width of the line in staff spaces (reserved for future use)
     * @return the boxes and connectors for that verse, and whether an extender runs off the line
     */
    public static Result build(
        List<ElementColumn> columns,
        int activeVerse,
        LyricRenderMetrics lyricRenderMetrics,
        boolean hasLeadingContinuation,
        double lineWidthSs) {

        var boxesByElement = new HashMap<StaffElement, List<LyricBoxLayout>>(columns.size());
        var connectors = new ArrayList<LyricConnectorLayout>(columns.size());
        var state = new ExtenderState(hasLeadingContinuation);

        for (var columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            var column = columns.get(columnIndex);
            var element = column.getElement();

            var lyric = column.getLyric();
            var extend = lyric != null ? lyric.extend() : null;

            // The host of a paired grace note never carries a syllable of its own, so it emits no
            // lyric box. A STOP carrier on it closes the automatic melisma that the grace started,
            // ending past the host's notehead like any other STOP; anything else passes through so
            // hyphens and extenders originating from the grace reach the next lyric-bearing element.
            if (isHostOfPairedGraceColumn(columns, columnIndex)) {
                if (extend == Lyric.Extend.STOP) {
                    state.closeGraceHostExtender(
                        connectors, activeVerse, column.getNoteheadRightEdgeXSs(), columnIndex);
                }

                continue;
            }

            if (column.isRest()) {
                // Rest with extending lyric (START/CONTINUE): extender flows through.
                // Rest with STOP lyric: extender ends at the rest's notehead-equivalent right edge
                // (LilyPond LyricExtender minimum-length rule, see MIN_MELISMA_LENGTH_SS).
                // Rest without extending lyric: extender (if active) ends at rest's left edge.
                if (extend == Lyric.Extend.CONTINUE || extend == Lyric.Extend.START) {
                    continue;
                }

                if (extend == Lyric.Extend.STOP) {
                    state.closeExtenderPastHead(connectors, activeVerse, column.getNoteheadRightEdgeXSs());
                    continue;
                }

                state.closeExtender(connectors, activeVerse, column.getLeftEdgeXSs());
                continue;
            }

            if (lyric == null) {
                // Note with no lyric: extender (if active) continues silently through this column.
                continue;
            }

            if (extend == Lyric.Extend.CONTINUE) {
                // CONTINUE carrier: extender continues silently through this column.
                continue;
            }

            if (extend == Lyric.Extend.STOP) {
                // STOP carrier: ends active extender past this note's notehead, no box.
                state.closeExtenderPastHead(connectors, activeVerse, column.getNoteheadRightEdgeXSs());
                continue;
            }

            var text = lyric.text();
            // ElementColumnBuilder measured this syllable's width when it resolved the lyric above;
            // reuse the cached value to avoid a redundant TextLayout allocation per layout pass.
            var widthSs = column.getSyllableWidthSs();
            // A grace's host is the column immediately after it, resolved once here so the union the
            // syllable is placed on and the melisma that shares that placement are read off the same
            // column. Null for anything but a grace, and for a grace ending the line.
            var hostColumn = column.isGraceNote() && columnIndex + 1 < columns.size()
                ? columns.get(columnIndex + 1)
                : null;
            var hostLyric = hostColumn != null ? hostColumn.getLyric() : null;
            var graceUnionWidthSs = column.isGraceNote()
                ? spacedGraceHostUnionWidthSs(columns, columnIndex, column, hostColumn)
                : 0;
            var boxXSs = computeLyricBoxLeftXSs(
                column, lyric, hostLyric, widthSs, graceUnionWidthSs, lyricRenderMetrics);
            var box = new LyricBoxLayout(boxXSs, widthSs, activeVerse, text);
            boxesByElement.computeIfAbsent(element, e -> new ArrayList<>()).add(box);

            // Close any pending hyphen at the start of this syllable.
            if (state.pendingHyphenStartXSs >= 0) {
                connectors.add(new LyricConnectorLayout(
                    state.pendingHyphenStartXSs,
                    boxXSs,
                    activeVerse,
                    LyricConnectorLayout.Kind.HYPHEN,
                    state.pendingHyphenColumnIndex));
                state.pendingHyphenStartXSs = -1;
                state.pendingHyphenColumnIndex = LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;
            }

            // Close any active extender at the start of this syllable.
            state.closeExtender(connectors, activeVerse, boxXSs);

            var syllableEndXSs = boxXSs + widthSs;
            var opensHyphen = Lyric.syllabicContinues(lyric.syllabic());

            if (opensHyphen) {
                state.pendingHyphenStartXSs = syllableEndXSs;
                state.pendingHyphenColumnIndex = columnIndex;
            }

            if (extend == Lyric.Extend.START && !opensHyphen) {
                state.extenderActive = true;
                state.extenderStartXSs = syllableEndXSs;
                state.extenderColumnIndex = columnIndex;
                state.syllableSpansGraceHostUnion = column.isGraceNote()
                    && HorizontalSpacingCalculator.graceSyllableSpansUnion(widthSs, graceUnionWidthSs);
            }
        }

        var hasTrailingContinuation = false;

        // Any START that reaches the end of the line without being closed by a STOP or a
        // text-bearing note continues the melisma onto the next line.
        if (state.extenderActive) {
            emitDanglingExtender(connectors, columns, state, activeVerse);
            hasTrailingContinuation = true;
        }

        if (state.pendingHyphenStartXSs >= 0) {
            emitDanglingHyphen(connectors, columns, state, activeVerse);
        }

        // Always the lyric space width: a melisma is sung on a single (non-hyphenated) syllable,
        // so the gap before the following word is a word space, never a hyphen cell.
        clampExtendersToFollowingSyllable(connectors, boxesByElement, lyricRenderMetrics.spaceWidthSs());

        return new Result(boxesByElement, connectors, hasTrailingContinuation);
    }

    /**
     * Pulls back any extender that would otherwise run within {@code gapSs} of the syllable that
     * follows it, so a melisma keeps the same gap from the next syllable that separates adjacent
     * words. An extender closed by a STOP carrier ends past that carrier per the
     * {@value #MIN_MELISMA_LENGTH_SS}-ss minimum-length rule, and the next syllable's box is not
     * known at that point, so the clamp has to run once the whole verse is laid out.
     * DANGLING_EXTENDER connectors have no following syllable on this line and are left untouched.
     */
    private static void clampExtendersToFollowingSyllable(
        List<LyricConnectorLayout> connectors,
        Map<StaffElement, ? extends List<LyricBoxLayout>> boxesByElement,
        double gapSs) {

        for (var i = 0; i < connectors.size(); i++) {
            var connector = connectors.get(i);

            if (connector.kind() != LyricConnectorLayout.Kind.EXTENDER) {
                continue;
            }

            var nextSyllableLeftXSs = firstBoxLeftXSsAfter(boxesByElement, connector.startXSs());

            if (Double.isNaN(nextSyllableLeftXSs)) {
                continue;
            }

            var maxEndXSs = nextSyllableLeftXSs - gapSs;

            if (connector.endXSs() > maxEndXSs) {
                connectors.set(i, new LyricConnectorLayout(
                    connector.startXSs(),
                    maxEndXSs,
                    connector.verseIndex(),
                    connector.kind(),
                    connector.sourceElementIndex()));
            }
        }
    }

    /**
     * Returns the left edge of the leftmost lyric box that starts to the right of {@code xSs},
     * or {@link Double#NaN} if there is none. The extender's own source syllable sits to the left
     * of its start, so it is naturally excluded.
     */
    private static double firstBoxLeftXSsAfter(
        Map<StaffElement, ? extends List<LyricBoxLayout>> boxesByElement,
        double xSs) {

        var nextLeftXSs = Double.NaN;

        for (var boxes : boxesByElement.values()) {
            for (var box : boxes) {
                var boxLeftXSs = box.xSs();

                if (boxLeftXSs > xSs && (Double.isNaN(nextLeftXSs) || boxLeftXSs < nextLeftXSs)) {
                    nextLeftXSs = boxLeftXSs;
                }
            }
        }

        return nextLeftXSs;
    }

    /**
     * Returns the left-edge X (staff spaces) at which to place a syllable's lyric box.
     * <p>
     * Normal notes / rests: center the entire syllable's advance width on the notehead center,
     * excluding the flag and augmentation dots (the established Gould/Ross rule — neither the flag
     * nor the dots are part of the notehead and must not shift the lyric position).
     * <p>
     * Grace notes: the grace and its host are treated as one unioned column for lyric layout — the
     * grace carries the lyric, the host never does. The offset from the grace's origin comes from
     * {@link HorizontalSpacingCalculator#graceLyricLeftOffsetSs}, which the spacing calculator also
     * reads to reserve the neighbour space, so the box is drawn where space was reserved. The pair's
     * own melisma is part of what that offset places, so whether the pair carries one is derived from
     * {@code hostLyric} — the host's lyric for this verse — and passed along, as is
     * {@code graceUnionWidthSs}, the union as this line was actually spaced, which is the one input
     * the reservation cannot share (see {@link #spacedGraceHostUnionWidthSs}).
     */
    private static double computeLyricBoxLeftXSs(
        ElementColumn column,
        Lyric lyric,
        @Nullable Lyric hostLyric,
        double widthSs,
        double graceUnionWidthSs,
        LyricRenderMetrics lyricRenderMetrics) {

        if (column.isGraceNote()) {
            return column.getXSs() + HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
                widthSs,
                lyricRenderMetrics.firstGraphemeWidthSs(lyric.text()),
                graceUnionWidthSs,
                column,
                HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(lyric, hostLyric));
        }

        // Center the syllable on the notehead, which excludes the flag and augmentation dots
        // (getNoteheadCenterXSs) so neither shifts the lyric.
        return column.getNoteheadCenterXSs() - widthSs / 2.0;
    }

    /**
     * Returns the width of the grace–host lyric union this grace's syllable is laid out on, measured
     * from the two columns' final X positions rather than rebuilt from the ideal grace→host gap.
     * <p>
     * {@link HorizontalSpacingCalculator#idealGraceHostUnionWidthSs} has to assume that gap is exactly
     * {@link HorizontalSpacingCalculator#GRACE_HOST_REST_SS}, because it is asked for while the line
     * is still being solved. The gap the solver produced also carries the {@link OpticalSpacing}
     * stem correction, any strut that clamped it, and any compression. Lyric layout runs after the
     * solve, so it can read the union off the notes themselves — and it has to: centering a syllable
     * and its melisma on the ideal width when a different width was drawn leaves them off the pair by
     * half the difference, which is a third of a staff space for a stem-up grace against a stem-down
     * host.
     * <p>
     * Measuring needs a host the union really ends at, which is what pairing establishes: an
     * unpaired grace's neighbour is an ordinary note carrying a lyric of its own, not the far edge of
     * a union. An unpaired grace therefore keeps the ideal width — which is also the width
     * {@link HorizontalSpacingCalculator} reserved against, since the reservation path treats the
     * next column as the grace's host whether or not the two are paired, so the box still lands where
     * space was made for it.
     */
    private static double spacedGraceHostUnionWidthSs(
        List<ElementColumn> columns,
        int graceIndex,
        ElementColumn grace,
        @Nullable ElementColumn hostColumn) {

        if (hostColumn != null && isHostOfPairedGraceColumn(columns, graceIndex + 1)) {
            return hostColumn.getNoteheadRightEdgeXSs() - grace.getXSs();
        }

        return HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, hostColumn);
    }

    private static boolean isHostOfPairedGraceColumn(List<ElementColumn> columns, int index) {
        var line = columns.getFirst().getElement().getLine();
        return line.isHostOfPairedGraceNote(index);
    }

    /**
     * Emits a {@link LyricConnectorLayout.Kind#DANGLING_EXTENDER} starting at the syllable end
     * and walking forward from the START column, extending only through elements that explicitly
     * carry {@link Lyric.Extend#CONTINUE} or {@link Lyric.Extend#STOP}. The extender ends at the
     * right edge of the last such element, or at the START element's own right edge if none
     * follow. A leading continuation (extender carried in from the previous line, with no START
     * column on this line) extends from x = 0 through the leading run of CONTINUE/STOP markers.
     */
    private static void emitDanglingExtender(
        List<? super LyricConnectorLayout> connectors,
        List<ElementColumn> columns,
        ExtenderState state,
        int verse
    ) {
        var startColumnIndex = state.extenderColumnIndex;
        var endXSs = startColumnIndex >= 0
            ? columns.get(startColumnIndex).getRightEdgeXSs()
            : state.extenderStartXSs;

        for (var i = startColumnIndex + 1; i < columns.size(); i++) {
            // Pass through the host of a paired grace; the host never carries a lyric, but
            // the extender's silent run must not be broken by it.
            if (isHostOfPairedGraceColumn(columns, i)) {
                continue;
            }

            var column = columns.get(i);
            var lyric = column.getLyric();
            var extend = lyric != null ? lyric.extend() : null;

            if (extend != Lyric.Extend.CONTINUE && extend != Lyric.Extend.STOP) {
                break;
            }

            endXSs = column.getRightEdgeXSs();
        }

        connectors.add(new LyricConnectorLayout(
            state.extenderStartXSs,
            endXSs,
            verse,
            LyricConnectorLayout.Kind.DANGLING_EXTENDER,
            state.extenderColumnIndex));
    }

    /**
     * Emits a {@link LyricConnectorLayout.Kind#DANGLING_HYPHEN} centered between the
     * syllable end and the next eligible element's left edge. The lyric editor prevents
     * a hyphen-opening syllable from being entered without a following eligible element
     * on the line, so finding none here indicates a layout invariant violation.
     */
    private static void emitDanglingHyphen(
        List<? super LyricConnectorLayout> connectors,
        List<ElementColumn> columns,
        ExtenderState state,
        int verse
    ) {
        for (var i = state.pendingHyphenColumnIndex + 1; i < columns.size(); i++) {
            var column = columns.get(i);

            if (column.getElement().isEligibleForLyric(verse)) {
                connectors.add(new LyricConnectorLayout(
                    state.pendingHyphenStartXSs,
                    column.getLeftEdgeXSs(),
                    verse,
                    LyricConnectorLayout.Kind.DANGLING_HYPHEN,
                    state.pendingHyphenColumnIndex));
                return;
            }
        }

        LOG.error("Dangling hyphen at column {} in verse {} has no following eligible element on line",
            state.pendingHyphenColumnIndex, verse);
    }

    /** Mutable scratch state for tracking the active extender and pending hyphen during one verse pass. */
    private static final class ExtenderState {
        boolean extenderActive;
        double extenderStartXSs;
        int extenderColumnIndex = LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;
        // Set when the active extender starts at a grace whose syllable already spans the grace-host
        // union; such an extender is dropped rather than drawn (see closeGraceHostExtender).
        boolean syllableSpansGraceHostUnion;
        double pendingHyphenStartXSs = -1.0;
        int pendingHyphenColumnIndex = LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;

        ExtenderState(boolean hasLeadingContinuation) {
            extenderActive = hasLeadingContinuation;
            extenderStartXSs = 0.0;
        }

        /** If an extender is active, emit it ending at endXSs and clear the active flag. */
        void closeExtender(List<? super LyricConnectorLayout> connectors, int verse, double endXSs) {
            if (extenderActive) {
                connectors.add(new LyricConnectorLayout(
                    extenderStartXSs,
                    endXSs,
                    verse,
                    LyricConnectorLayout.Kind.EXTENDER,
                    extenderColumnIndex));
                extenderActive = false;
            }
        }

        /**
         * If an extender is active, emit it ending at
         * {@code max(extenderStartXSs + MIN_MELISMA_LENGTH_SS, headRightEdgeXSs)} — LilyPond's
         * LyricExtender minimum-length rule — and clear the active flag. For a leading continuation
         * (extender carried in from the previous line), {@code extenderStartXSs} is still the {@code 0.0}
         * placeholder from the constructor, so the first term collapses to {@code MIN_MELISMA_LENGTH_SS};
         * {@code headRightEdgeXSs}, an absolute X position on this line, always dominates that {@code max()}.
         */
        void closeExtenderPastHead(List<? super LyricConnectorLayout> connectors, int verse, double headRightEdgeXSs) {
            closeExtender(connectors, verse, Math.max(extenderStartXSs + MIN_MELISMA_LENGTH_SS, headRightEdgeXSs));
        }

        /**
         * Ends at {@code hostColumnIndex}'s host the melisma that reaches it. Normally that is
         * {@link #closeExtenderPastHead}, but when this host's <em>own</em> grace started the
         * melisma and that grace's syllable already spans the pair
         * ({@link HorizontalSpacingCalculator#graceSyllableSpansUnion}) the extender is dropped
         * instead of emitted: it would start where it was going to end, so there is nothing left for
         * a reader to see. The melisma stays in the model either way — this decides only whether it
         * is drawn.
         * <p>
         * The melisma reaching this host need not be this pair's own: {@code Line.syncGraceHostMelisma}
         * leaves a host alone when it already carries an extender onward, so a melisma can start at
         * one grace, run through its host and several notes, and stop at a second grace's host.
         * {@link #syllableSpansGraceHostUnion} describes the grace the melisma <em>started</em> at, so
         * it only licenses dropping the line when that grace is this host's own — the column
         * immediately before it. Applied to any other melisma it would erase a line spanning many
         * notes.
         */
        void closeGraceHostExtender(
            List<? super LyricConnectorLayout> connectors,
            int verse,
            double headRightEdgeXSs,
            int hostColumnIndex) {

            if (extenderActive
                && syllableSpansGraceHostUnion
                && extenderColumnIndex == hostColumnIndex - 1) {

                extenderActive = false;
                return;
            }

            closeExtenderPastHead(connectors, verse, headRightEdgeXSs);
        }
    }
}
