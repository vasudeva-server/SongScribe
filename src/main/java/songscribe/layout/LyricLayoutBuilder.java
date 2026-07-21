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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
        int verseCount,
        boolean hasTrailingContinuation
    ) {}

    /**
     * Builds lyric boxes and connectors for a single line.
     *
     * @param columns                per-element columns in line order (X positions already finalized)
     * @param lyricRenderMetrics     metrics used to measure syllable text widths
     * @param hasLeadingContinuation true if the previous line ended with an active extender
     *                               that should continue from x = 0 on this line
     * @param lineWidthSs            width of the line in staff spaces (reserved for future use)
     */
    public static Result build(
        List<ElementColumn> columns,
        LyricRenderMetrics lyricRenderMetrics,
        boolean hasLeadingContinuation,
        double lineWidthSs) {

        var verseSet = collectVerses(columns);
        var boxes = new LinkedHashMap<StaffElement, List<LyricBoxLayout>>();
        var connectors = new ArrayList<LyricConnectorLayout>();
        var verseCount = 0;
        var hasTrailingContinuation = false;

        for (var verse : verseSet) {
            // Only verse 1 is currently populated; cross-line continuation flows through verse 1.
            // Multi-verse continuation threading is a follow-up when multi-verse data lands.
            var leading = (verse == 1) && hasLeadingContinuation;
            var verseResult = buildVerse(verse, columns, lyricRenderMetrics, leading, lineWidthSs);

            for (var entry : verseResult.boxesByElement.entrySet()) {
                boxes.computeIfAbsent(entry.getKey(), e -> new ArrayList<>()).addAll(entry.getValue());
            }

            connectors.addAll(verseResult.connectors);

            if (verse > verseCount) {
                verseCount = verse;
            }

            if (verse == 1 && verseResult.hasTrailingContinuation) {
                hasTrailingContinuation = true;
            }
        }

        return new Result(boxes, connectors, verseCount, hasTrailingContinuation);
    }

    private static TreeSet<Integer> collectVerses(List<ElementColumn> columns) {
        var verses = new TreeSet<Integer>();
        verses.add(1);

        for (var column : columns) {
            for (var lyric : column.getElement().getLyrics()) {
                verses.add(lyric.verse());
            }
        }

        return verses;
    }

    private static VerseResult buildVerse(
        int verse,
        List<ElementColumn> columns,
        LyricRenderMetrics lyricRenderMetrics,
        boolean hasLeadingContinuation,
        double lineWidthSs) {

        var boxesByElement = new HashMap<StaffElement, List<LyricBoxLayout>>(columns.size());
        var connectors = new ArrayList<LyricConnectorLayout>(columns.size());
        var state = new ExtenderState(hasLeadingContinuation);

        for (var columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            var column = columns.get(columnIndex);
            var element = column.getElement();

            // The host of a paired grace note never carries a lyric; skip its column outright
            // so hyphens and extenders originating from the grace pass through to the next
            // lyric-bearing element, never terminating at the host.
            if (isHostOfPairedGraceColumn(columns, columnIndex)) {
                continue;
            }

            var lyric = element.getLyricForVerse(verse);
            var extend = lyric != null ? lyric.extend() : null;

            if (column.isRest()) {
                // Rest with extending lyric (START/CONTINUE): extender flows through.
                // Rest with STOP lyric: extender ends at the rest's notehead-equivalent right edge
                // (LilyPond LyricExtender minimum-length rule, see MIN_MELISMA_LENGTH_SS).
                // Rest without extending lyric: extender (if active) ends at rest's left edge.
                if (extend == Lyric.Extend.CONTINUE || extend == Lyric.Extend.START) {
                    continue;
                }

                if (extend == Lyric.Extend.STOP) {
                    state.closeExtenderPastHead(connectors, verse, column.getNoteheadRightEdgeXSs());
                    continue;
                }

                state.closeExtender(connectors, verse, column.getLeftEdgeXSs());
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
                state.closeExtenderPastHead(connectors, verse, column.getNoteheadRightEdgeXSs());
                continue;
            }

            var text = lyric.text();
            // For verse 1, ElementColumnBuilder has already measured this syllable's width;
            // reuse the cached value to avoid a redundant TextLayout allocation per layout pass.
            var widthSs = (verse == 1)
                ? column.getSyllableWidthSs()
                : lyricRenderMetrics.lyricBoxWidthSs(text);
            var hostColumn = columnIndex + 1 < columns.size() ? columns.get(columnIndex + 1) : null;
            var boxXSs = computeLyricBoxLeftXSs(column, hostColumn, text, widthSs, lyricRenderMetrics);
            var box = new LyricBoxLayout(boxXSs, widthSs, verse, text);
            boxesByElement.computeIfAbsent(element, e -> new ArrayList<>()).add(box);

            // Close any pending hyphen at the start of this syllable.
            if (state.pendingHyphenStartXSs >= 0) {
                connectors.add(new LyricConnectorLayout(
                    state.pendingHyphenStartXSs,
                    boxXSs,
                    verse,
                    LyricConnectorLayout.Kind.HYPHEN,
                    state.pendingHyphenColumnIndex));
                state.pendingHyphenStartXSs = -1;
                state.pendingHyphenColumnIndex = LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX;
            }

            // Close any active extender at the start of this syllable.
            state.closeExtender(connectors, verse, boxXSs);

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
            }
        }

        var hasTrailingContinuation = false;

        // Any START that reaches the end of the line without being closed by a STOP or a
        // text-bearing note continues the melisma onto the next line.
        if (state.extenderActive) {
            emitDanglingExtender(connectors, columns, state, verse);
            hasTrailingContinuation = true;
        }

        if (state.pendingHyphenStartXSs >= 0) {
            emitDanglingHyphen(connectors, columns, state, verse);
        }

        // Always the lyric space width: a melisma is sung on a single (non-hyphenated) syllable,
        // so the gap before the following word is a word space, never a hyphen cell.
        clampExtendersToFollowingSyllable(connectors, boxesByElement, lyricRenderMetrics.spaceWidthSs());

        return new VerseResult(boxesByElement, connectors, hasTrailingContinuation);
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
        Map<StaffElement, List<LyricBoxLayout>> boxesByElement,
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
        Map<StaffElement, List<LyricBoxLayout>> boxesByElement,
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
     * Normal notes / rests: centre the entire syllable's advance width on the notehead centre,
     * excluding the flag and augmentation dots (the established Gould/Ross rule — neither the flag
     * nor the dots are part of the notehead and must not shift the lyric position).
     * <p>
     * Grace notes, by syllable advance width relative to the grace notehead and the grace→host
     * notehead union:
     * <ul>
     *   <li>narrower than the grace notehead → centre on the grace notehead (a lone narrow glyph
     *       such as "I" must sit under the notehead, not left-anchored where it reads off-centre);
     *   <li>at least the notehead width but within the union → left-anchor on the grace notehead's
     *       left edge (the lyric flows rightward toward the host);
     *   <li>wider than the union → centre on the union so it overhangs each side equally (see
     *       {@link HorizontalSpacingCalculator#graceLyricOverhangSs(double, ElementColumn, ElementColumn)}).
     * </ul>
     * The grace and its host are treated as one unioned column for lyric layout: the grace carries
     * the lyric, the host never does, and {@code hostColumn} is the column immediately after the
     * grace. The first two regimes keep the syllable's ink within the notehead→host footprint, so
     * they impose no extra neighbour reservation (the spacing extents stay zero).
     */
    private static double computeLyricBoxLeftXSs(
        ElementColumn column,
        @Nullable ElementColumn hostColumn,
        String text,
        double widthSs,
        LyricRenderMetrics lyricRenderMetrics) {

        var element = column.getElement();

        // A grace lyric at least as wide as the grace notehead spans toward the host: anchor on the
        // grace notehead left edge when it fits the grace→host union, else centre on that union.
        if (column.isGraceNote() && widthSs >= column.getNoteheadWidthSs()) {
            return column.getXSs() - HorizontalSpacingCalculator.graceLyricOverhangSs(widthSs, column, hostColumn);
        }

        // Narrow grace lyric or any normal note/rest: centre the syllable on the notehead, which
        // excludes the flag and augmentation dots (getNoteheadCenterXSs) so neither shifts the lyric.
        return column.getNoteheadCenterXSs() - widthSs / 2.0;
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
            var lyric = column.getElement().getLyricForVerse(verse);
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
    }

    private record VerseResult(
        Map<StaffElement, List<LyricBoxLayout>> boxesByElement,
        List<LyricConnectorLayout> connectors,
        boolean hasTrailingContinuation
    ) {}
}
