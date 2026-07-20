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
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Spacing calculations for element insertion operations.
 * <p>
 * This class answers two questions for interactive editing (adding or inserting an element)
 * without committing to a full relayout:
 * <ul>
 *   <li><b>Where does the element go?</b> — the natural (uncompressed) length of the
 *       {@link Spring} on either side of the insertion point, measured from the neighbours'
 *       <em>current</em> positions, so only the elements after the insertion point move.</li>
 *   <li><b>Does it still fit?</b> — {@link InsertionResult#fitsWithinLine} runs the identical
 *       {@link HorizontalSpacingCalculator#solveChain solve} the layout engine runs, over the same
 *       projected columns {@link LayoutEngine} would build after the commit — every column with the
 *       new one spliced in, <em>including the auto-maintained terminal barline</em>, anchored by the
 *       shared {@link HorizontalSpacingCalculator#calculateAnchorXSs}. Solving the exact chain the
 *       layout will solve is what guarantees the two agree: an insert the gate accepts always lays
 *       out. An insert is refused only when even full compression to every gap's strut overflows the
 *       margin; a line with slack simply compresses to absorb the new element.</li>
 * </ul>
 * <p>
 * The columns surrounding the insertion point carry their real syllable widths whenever a
 * {@link LyricRenderMetrics} is supplied, so previews are spaced by the same lyric rules as the
 * committed layout. Callers with no lyric metrics to hand (file load, where the positions are
 * bootstrap values the first layout overwrites) may pass {@code null}, which falls back to
 * syllable-less columns.
 */
public final class InsertionSpacingCalculator {

    /**
     * Result of an insertion spacing calculation, providing the X position for the
     * inserted element, the shift amount for subsequent elements, and the projected
     * line width after the insertion.
     *
     * @param insertedElementXSs           X position where the inserted element should be placed
     * @param shiftForSubsequentElementsSs Amount to shift all elements after the insertion point (always >= 0)
     * @param newLineWidthSs               Projected line width after the insertion
     * @param projectedSprings             The lyric-stretched spring chain of the line as LayoutEngine
     *                                     will build it after the commit — every column with the new
     *                                     one spliced in, including the terminal barline — what
     *                                     {@link #fitsWithinLine} solves
     * @param projectedFirstXSs            X the projected chain is anchored at
     *                                     ({@link HorizontalSpacingCalculator#calculateAnchorXSs})
     * @param projectedLastRightExtentSs   Right extent of the projected chain's last column, so the
     *                                     solve leaves room for its glyph inside the margin
     */
    public record InsertionResult(
        double insertedElementXSs,
        double shiftForSubsequentElementsSs,
        double newLineWidthSs,
        List<Spring> projectedSprings,
        double projectedFirstXSs,
        double projectedLastRightExtentSs
    ) {
        public InsertionResult {
            projectedSprings = List.copyOf(projectedSprings);
        }

        /**
         * Returns whether the insertion fits within the given right margin — compress-to-fit:
         * the line is free to give up slack to absorb the new element, and only a chain that
         * overflows with every gap frozen at its strut is rejected.
         *
         * @param staffRightMarginSs The maximum allowed line width in staff spaces
         * @return {@code true} unless the projected spring chain solves INFEASIBLE
         */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !HorizontalSpacingCalculator.solveChain(
                projectedSprings, projectedFirstXSs, projectedLastRightExtentSs, staffRightMarginSs)
                .isInfeasible();
        }
    }

    /**
     * The <em>positioning</em> chain for an insertion: every effective column (the terminal barline
     * excluded, since it is pinned flush-right rather than shifted) in element order with the
     * inserted column spliced in, plus the lyric-stretched spring chain over them. Drives where the
     * inserted element lands and how far its successors shift. The separate <em>fit</em> chain that
     * {@link InsertionResult#fitsWithinLine} solves is built in {@link #calculateInsertion} and does
     * include the terminal, so it matches the committed layout exactly.
     *
     * @param columns     Projected columns in element order (never empty — it holds at least the
     *                    inserted column)
     * @param springs     One lyric-stretched spring per adjacent pair of {@code columns}
     * @param insertIndex Index of the inserted column within {@code columns}
     */
    private record ProjectedLine(List<ElementColumn> columns, List<Spring> springs, int insertIndex) {}

    /**
     * The fall-fit rule: a projected width fits when it leaves at least the default column gap
     * before the right margin, so the fall glyph doesn't butt up against the line end. Insertion
     * uses the compress-to-fit gate in {@link InsertionResult#fitsWithinLine} instead; a fall
     * widens a column rather than adding one, so it has no spring chain of its own to solve.
     */
    private static boolean fitsWithinMarginSs(double projectedWidthSs, double staffRightMarginSs) {
        return projectedWidthSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS <= staffRightMarginSs;
    }

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
    }

    private static double elementXSs(StaffElement element, @Nullable LayoutResult layout) {
        return layout != null
            ? layout.getElementXSs(element)
            : ScaleContext.pxToSs(element.getXOffsetPx());
    }

    /**
     * Calculates the X position for appending an element to the end of a line.
     *
     * @param line               The line to append to
     * @param elementToAppend    The element being appended
     * @param layout             Layout result for position lookup; null falls back to {@code xOffset}
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return X position in staff spaces where the element should be placed
     */
    public static double calculateAppendPositionSs(
        Line line,
        StaffElement elementToAppend,
        @Nullable LayoutResult layout,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        return calculateInsertion(line, elementToAppend, line.elementCount(), layout, lyricRenderMetrics)
            .insertedElementXSs();
    }

    /**
     * Calculates the X position for {@code nextElement} when placed after {@code currentElement},
     * using the current X position of {@code currentElement}.
     * <p>
     * This is useful for paste operations where elements have already been positioned and
     * you need to determine where the next element in sequence should go. Neither element is
     * looked up on a line, so the pair is spaced with no lyric context.
     *
     * @param currentElement An element with its X position already set
     * @param nextElement    The element to be placed after currentElement
     * @return X position where nextElement should be placed
     */
    public static double calculateNextElementXSs(StaffElement currentElement, StaffElement nextElement) {
        var currentColumn = createLightweightColumn(currentElement);
        currentColumn.setXSs(elementXSs(currentElement, null));
        var nextColumn = createLightweightColumn(nextElement);

        // No line is involved in this paste-position lookup, so there is no song line rest to read;
        // the pair is spaced against the default line rest.
        return currentColumn.getXSs()
            + HorizontalSpacingCalculator.buildSpring(
                currentColumn, nextColumn, Song.DEFAULT_REST_LENGTH_SS).naturalLengthSs();
    }

    /**
     * Calculates both the X position for an inserted element and the shift amount for subsequent elements.
     * <p>
     * This method must be called before the element is added to the line, since it examines the
     * line's current state to determine proper spacing.
     *
     * @param line               The line being modified (before insertion)
     * @param insertedElement    The element being inserted
     * @param insertIndex        The index where the element will be inserted (0-based)
     * @param layout             Layout result for position lookup; null falls back to {@code xOffset}
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return An {@link InsertionResult} with the element's X position, the shift for subsequent
     *         elements, and the projected spring chain its fit gate solves
     */
    public static InsertionResult calculateInsertion(
        Line line,
        StaffElement insertedElement,
        int insertIndex,
        @Nullable LayoutResult layout,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var elementCount = line.elementCount();

        if (insertIndex < 0 || insertIndex > elementCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + elementCount + ']');
        }

        var insertedColumn = createLightweightColumn(insertedElement);

        if (lyricRenderMetrics != null) {
            // A freshly inserted element carries no lyric, so ElementColumnBuilder would space it as
            // a non-hyphenated, lyric-less column — one space width to the next syllable. Mirror that
            // here so the fit solve below agrees with the layout that follows the commit.
            insertedColumn.setMinGapToNextSyllableSs(lyricRenderMetrics.spaceWidthSs());
            insertedColumn.setMinCollisionGapToNextSyllableSs(lyricRenderMetrics.spaceWidthSs());
        }

        var projected = projectLine(line, insertedColumn, insertIndex, lyricRenderMetrics);
        var columns = projected.columns();
        var springs = projected.springs();
        var splicedIndex = projected.insertIndex();

        // The element lands where its left neighbour's spring wants it, measured from that
        // neighbour's *current* position — only what follows the insertion point moves.
        double insertedElementXSs;

        if (splicedIndex == 0) {
            insertedElementXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
        } else {
            var prevElement = columns.get(splicedIndex - 1).getElement();
            insertedElementXSs = elementXSs(prevElement, layout) + springs.get(splicedIndex - 1).naturalLengthSs();
        }

        insertedColumn.setXSs(insertedElementXSs);

        var shiftSs = 0.0;

        if (splicedIndex + 1 < columns.size()) {
            var nextElement = columns.get(splicedIndex + 1).getElement();
            var requiredNextXSs = insertedElementXSs + springs.get(splicedIndex).naturalLengthSs();
            // Shift = (where next needs to be) - (where it currently is), never negative:
            // a gap that is already wide enough stays as it is.
            shiftSs = Math.max(0, requiredNextXSs - elementXSs(nextElement, layout));
        }

        // Projected line width: max of the inserted element's right edge and the last real
        // element's right edge shifted by the insertion shift.
        var newLineWidthSs = projectedWidthWithLastShiftSs(line, insertedColumn.getRightEdgeXSs(), shiftSs, layout);

        // The fit gate must solve the SAME chain LayoutEngine will after the commit — including the
        // auto-maintained terminal barline, which the positioning chain above excludes (the terminal
        // is pinned flush-right, not shifted). Appending it here, and anchoring with the shared
        // calculateAnchorXSs, is what keeps the pre-check and the committed layout from disagreeing:
        // without it a nearly-full line could pass the gate, commit, then fail to lay out.
        var fitColumns = new ArrayList<>(columns);

        if (line.effectiveElementCount() < elementCount) {
            var terminalIndex = elementCount - 1;
            var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;
            fitColumns.add(
                buildSurroundingColumn(line.getElement(terminalIndex), line, terminalIndex, columnBuilder));
        }

        var fitSprings = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(fitColumns, line), fitColumns);
        var fitFirstXSs = HorizontalSpacingCalculator.calculateAnchorXSs(fitColumns.getFirst(), line);

        return new InsertionResult(
            insertedElementXSs,
            shiftSs,
            newLineWidthSs,
            fitSprings,
            fitFirstXSs,
            fitColumns.getLast().getRightExtentSs());
    }

    /**
     * Builds the line as it would be after the insertion — the same column list and
     * lyric-stretched spring chain {@link LayoutEngine} would build — so the fit gate asks the
     * solver the identical question the committed layout will ask.
     * <p>
     * The auto-maintained terminal is excluded: layout pins it, so it is not the insertion's to
     * space. An {@code insertIndex} past the last effective element appends.
     */
    private static ProjectedLine projectLine(
        Line line,
        ElementColumn insertedColumn,
        int insertIndex,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var effectiveCount = line.effectiveElementCount();
        var splicedIndex = Math.min(insertIndex, effectiveCount);
        var columns = new ArrayList<ElementColumn>(effectiveCount + 1);
        // One builder for the whole projection, and each element's index passed down, so this pass
        // stays linear — it runs on every insertion, including per-keystroke note entry.
        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;

        for (var i = 0; i < effectiveCount; i++) {
            if (i == splicedIndex) {
                columns.add(insertedColumn);
            }

            columns.add(buildSurroundingColumn(line.getElement(i), line, i, columnBuilder));
        }

        if (splicedIndex == effectiveCount) {
            columns.add(insertedColumn);
        }

        var springs = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(columns, line), columns);

        return new ProjectedLine(columns, springs, splicedIndex);
    }

    /**
     * Builds the column for an element already on the line, carrying its real syllable width and
     * beam membership so a preview is spaced by the same lyric rules as the committed layout.
     * Falls back to a syllable-less column when the caller has no lyric metrics.
     */
    private static ElementColumn buildSurroundingColumn(
        StaffElement element, Line line, int elementIndex, @Nullable ElementColumnBuilder columnBuilder) {

        if (columnBuilder == null) {
            return createLightweightColumn(element);
        }

        return columnBuilder.buildColumn(element, line, elementIndex);
    }

    /**
     * Determines whether a grace note will fit on a line when inserted at the given index.
     *
     * @param line               The line to check (before insertion)
     * @param atIndex            The index where the grace note would be inserted
     * @param layout             Layout result for position lookup; null falls back to {@code xOffset}
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} if the grace note fits on the line
     */
    public static boolean hasRoomForGraceNote(
        Line line, int atIndex, @Nullable LayoutResult layout, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var staffRightMarginSs = line.getSong().getLineWidthSs();
        // Shared singleton is safe: calculateInsertion only reads geometry from the element.
        var graceNote = ElementType.GRACE_QUAVER.getInstance();
        return calculateInsertion(line, graceNote, atIndex, layout, lyricRenderMetrics)
            .fitsWithinLine(staffRightMarginSs);
    }

    /**
     * Determines whether a host note (crotchet) will fit on a line immediately after the grace
     * note at the given index. The grace note must already be in the line.
     * <p>
     * Uses element xOffset values since the layout may be stale after grace note insertion.
     *
     * @param line               The line containing the grace note (after insertion)
     * @param graceNoteIndex     The index of the grace note already in the line
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} if a host note fits after the grace note
     */
    public static boolean hasRoomForHostNoteAfterGrace(
        Line line, int graceNoteIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var staffRightMarginSs = line.getSong().getLineWidthSs();
        // Shared singleton is safe: calculateInsertion only reads geometry from the element.
        var hostNote = ElementType.CROTCHET.getInstance();
        return calculateInsertion(line, hostNote, graceNoteIndex + 1, null, lyricRenderMetrics)
            .fitsWithinLine(staffRightMarginSs);
    }

    /**
     * Determines whether a fall will fit on a line when applied to the element at the given index.
     * <p>
     * A fall extends the source element's right extent. When that extension is large enough to push
     * subsequent elements right, this method checks whether the last element on the line would
     * overflow the right margin.
     *
     * @param line        The line to check
     * @param sourceIndex The index of the element the fall would be applied to
     * @param layout      Layout result for position lookup; null falls back to {@code xOffset}
     * @return {@code true} if the fall fits on the line
     */
    public static boolean hasRoomForFall(Line line, int sourceIndex, @Nullable LayoutResult layout) {
        var sourceElement = line.getElement(sourceIndex);

        // Fall already present — no change in right extent, always fits
        if (sourceElement.hasFall()) {
            return true;
        }

        // Measure a clone carrying the fall so the live model element is never mutated,
        // mirroring the read-only geometry contract of hasRoomForGraceNote.
        var sourceWithFall = sourceElement.clone();
        sourceWithFall.setFall();
        var sourceColumnWithFall = createLightweightColumn(sourceWithFall);

        // The clone is absent from the layout map, so look up its X by the original element.
        sourceColumnWithFall.setXSs(elementXSs(sourceElement, layout));

        var effectiveCount = line.effectiveElementCount();
        var projectedWidthSs = sourceColumnWithFall.getRightEdgeXSs();

        // If there are effective elements after the source, the fall's larger right extent
        // may push them right — check whether the last element would overflow the margin.
        if (sourceIndex + 1 < effectiveCount) {
            var nextElement = line.getElement(sourceIndex + 1);
            var nextColumn = createLightweightColumn(nextElement);
            var currentNextXSs = elementXSs(nextElement, layout);
            // Space the pair through the spring engine the committed layout uses, so the fall-fit
            // gate honours the song's line rest instead of a fixed default gap.
            var requiredNextXSs = sourceColumnWithFall.getXSs()
                + HorizontalSpacingCalculator.buildSpring(
                    sourceColumnWithFall, nextColumn, line.getSong().getDefaultRestLengthSs()).naturalLengthSs();
            var shiftSs = Math.max(0, requiredNextXSs - currentNextXSs);

            if (shiftSs > 0) {
                projectedWidthSs = projectedWidthWithLastShiftSs(line, projectedWidthSs, shiftSs, layout);
            }
        }

        return fitsWithinMarginSs(projectedWidthSs, line.getSong().getLineWidthSs());
    }

    /**
     * Projects the line width after shifting the last effective element right by {@code shiftSs},
     * taking the larger of {@code baseWidthSs} and that element's shifted right edge. Returns
     * {@code baseWidthSs} unchanged when the line has no effective elements.
     */
    private static double projectedWidthWithLastShiftSs(
        Line line, double baseWidthSs, double shiftSs, @Nullable LayoutResult layout) {

        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount == 0) {
            return baseWidthSs;
        }

        var lastElement = line.getElement(effectiveCount - 1);
        var lastColumn = createLightweightColumn(lastElement);
        lastColumn.setXSs(elementXSs(lastElement, layout) + shiftSs);

        return Math.max(baseWidthSs, lastColumn.getRightEdgeXSs());
    }

    /**
     * Creates a lightweight ElementColumn for spacing calculations.
     * <p>
     * This column has accurate geometric extents but no syllable information — correct for the
     * element being inserted, which carries no lyric yet, and for an element that is not on a line
     * at all. Columns already on the line are built by {@link #buildSurroundingColumn} instead, so
     * they bring their real syllable widths to the spacing.
     *
     * @param element The element to create a column for
     * @return An ElementColumn with geometric extents but no syllable data
     */
    private static ElementColumn createLightweightColumn(StaffElement element) {
        // Calculate geometric extents using ElementColumnBuilder's static methods
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.getDirection());
        var rightExtentExcludingAugmentationSs = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(element, false, element.getDirection());

        // For insertion operations, we don't need stem positions or beam group info
        // since we're only calculating horizontal spacing
        double stemTopSs = 0;
        double stemBottomSs = 0;

        // No syllable information for lightweight columns
        String syllable = null;
        double syllableWidthSs = 0;

        //noinspection ConstantValue
        return new ElementColumn(
            element,
            Collections.emptyList(),  // No grace notes
            leftExtentSs,
            rightExtentSs,
            rightExtentExcludingAugmentationSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            false
        );
    }
}
