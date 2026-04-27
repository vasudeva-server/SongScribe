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

package songscribe.ui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import songscribe.music.Lyric;
import songscribe.music.StaffElement;

/**
 * Computes lyric box and connector geometry from per-element {@link Lyric} records.
 * <p>
 * Span rules (authoritative):
 * <ul>
 *   <li>note-with-Lyric(relation = SYLLABLE/COMPOUND_WORD) → HYPHEN span to next syllable</li>
 *   <li>note-with-Lyric(extend = START) → EXTENDER span from end of syllable onward</li>
 *   <li>note-with-Lyric(extend = STOP) → active EXTENDER ends at this note's right edge;
 *       emits no lyric box (the lyric is a melisma terminator)</li>
 *   <li>note-with-Lyric(extend = CONTINUE) → continues current EXTENDER silently (cross-line
 *       carrier); emits no lyric box</li>
 *   <li>note-with-no-Lyric + active EXTENDER → continues current EXTENDER</li>
 *   <li>rest-with-no-Lyric + active EXTENDER → breaks EXTENDER (ends at rest left edge)</li>
 *   <li>rest-with-Lyric(extend = START/CONTINUE) + active EXTENDER → continues EXTENDER through
 *       rest</li>
 *   <li>rest-with-Lyric(extend = STOP) + active EXTENDER → ends EXTENDER at rest's right edge</li>
 *   <li>note-with-Lyric(extend = NONE, text) + active EXTENDER → EXTENDER ends at start of this
 *       syllable; a new span (if any) begins after this syllable</li>
 * </ul>
 * <p>
 * Spans that extend past the last column produce trailing stubs at {@code lineWidthSs}; the
 * caller threads {@link Result#hasTrailingContinuation()} into the next line's
 * {@code hasLeadingContinuation} so that line emits a matching leading stub from x = 0 to
 * its first lyric-bearing element (or to the first rest that breaks the continuation).
 */
public final class LyricLayoutBuilder {

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
     * @param lineWidthSs            width of the line in staff spaces (used for trailing stubs)
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

        var boxesByElement = new HashMap<StaffElement, List<LyricBoxLayout>>();
        var connectors = new ArrayList<LyricConnectorLayout>();
        var state = new ExtenderState(hasLeadingContinuation);

        for (var column : columns) {
            var element = column.getElement();
            var isRest = column.isRest();
            var lyric = element.getLyricForVerse(verse);

            if (isRest) {
                // Rest with extending lyric (START/CONTINUE): extender flows through.
                // Rest with STOP lyric: extender ends at rest's right edge below.
                if (lyric != null && (lyric.extend() == Lyric.Extend.START
                    || lyric.extend() == Lyric.Extend.CONTINUE)) {
                    continue;
                }

                if (lyric != null && lyric.extend() == Lyric.Extend.STOP) {
                    state.closeExtender(connectors, verse, column.getRightEdgeXSs());
                    continue;
                }

                // Rest without extending lyric: any active extender ends at the rest's left edge.
                state.closeExtender(connectors, verse, column.getLeftEdgeXSs());
                continue;
            }

            if (lyric == null || lyric.extend() == Lyric.Extend.CONTINUE) {
                // Note with no lyric, or with a CONTINUE carrier: extender (if active)
                // continues silently through this column.
                continue;
            }

            if (lyric.extend() == Lyric.Extend.STOP) {
                // STOP carrier: ends active extender at this note's right edge, no box.
                state.closeExtender(connectors, verse, column.getRightEdgeXSs());
                continue;
            }

            var text = lyric.text();
            // For verse 1, ElementColumnBuilder has already measured this syllable's width;
            // reuse the cached value to avoid a redundant TextLayout allocation per layout pass.
            var widthSs = (verse == 1)
                ? column.getSyllableWidthSs()
                : lyricRenderMetrics.lyricBoxWidthSs(text);
            var centerXSs = column.getXSs() + column.getRightExtentSs() / 2.0;
            var boxXSs = centerXSs - widthSs / 2.0;
            var box = new LyricBoxLayout(boxXSs, widthSs, verse, text);
            boxesByElement.computeIfAbsent(element, e -> new ArrayList<>()).add(box);

            // Close any pending hyphen at the start of this syllable.
            if (state.pendingHyphenStartXSs >= 0) {
                connectors.add(new LyricConnectorLayout(
                    state.pendingHyphenStartXSs,
                    boxXSs,
                    verse,
                    LyricConnectorLayout.Kind.HYPHEN));
                state.pendingHyphenStartXSs = -1;
            }

            // Close any active extender at the start of this syllable.
            state.closeExtender(connectors, verse, boxXSs);

            var syllableEndXSs = boxXSs + widthSs;
            var opensHyphen = lyric.relation() != StaffElement.SyllableRelation.NONE;

            if (opensHyphen) {
                state.pendingHyphenStartXSs = syllableEndXSs;
            }

            if (lyric.extend() == Lyric.Extend.START && !opensHyphen) {
                state.extenderActive = true;
                state.extenderStartXSs = syllableEndXSs;
            }
        }

        var hasTrailingContinuation = false;

        if (state.extenderActive) {
            connectors.add(new LyricConnectorLayout(
                state.extenderStartXSs,
                lineWidthSs,
                verse,
                LyricConnectorLayout.Kind.EXTENDER));
            hasTrailingContinuation = true;
        }

        if (state.pendingHyphenStartXSs >= 0) {
            connectors.add(new LyricConnectorLayout(
                state.pendingHyphenStartXSs,
                lineWidthSs,
                verse,
                LyricConnectorLayout.Kind.HYPHEN));
        }

        return new VerseResult(boxesByElement, connectors, hasTrailingContinuation);
    }

    /** Mutable scratch state for tracking the active extender and pending hyphen during one verse pass. */
    private static final class ExtenderState {
        boolean extenderActive;
        double extenderStartXSs;
        double pendingHyphenStartXSs = -1.0;

        ExtenderState(boolean hasLeadingContinuation) {
            this.extenderActive = hasLeadingContinuation;
            this.extenderStartXSs = 0.0;
        }

        /** If an extender is active, emit it ending at endXSs and clear the active flag. */
        void closeExtender(List<LyricConnectorLayout> connectors, int verse, double endXSs) {
            if (extenderActive) {
                connectors.add(new LyricConnectorLayout(
                    extenderStartXSs,
                    endXSs,
                    verse,
                    LyricConnectorLayout.Kind.EXTENDER));
                extenderActive = false;
            }
        }
    }

    private record VerseResult(
        Map<StaffElement, List<LyricBoxLayout>> boxesByElement,
        List<LyricConnectorLayout> connectors,
        boolean hasTrailingContinuation
    ) {}
}
