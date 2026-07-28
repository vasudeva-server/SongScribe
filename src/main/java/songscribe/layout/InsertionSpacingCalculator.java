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
     * @param insertedElementXSs             X position where the inserted element should be placed
     * @param shiftForSubsequentElementsSs   Amount to shift all elements after the insertion point (always >= 0)
     * @param newLineWidthSs                 Projected line width after the insertion
     * @param projectedSprings               The lyric-stretched spring chain of the line as LayoutEngine
     *                                       will build it after the commit — every column with the new
     *                                       one spliced in, including the terminal barline — what
     *                                       {@link #fitsWithinLine} solves
     * @param projectedFirstXSs              X the projected chain is anchored at
     *                                       ({@link HorizontalSpacingCalculator#calculateAnchorXSs})
     * @param projectedTrailingReservationSs The span the solve keeps clear past the projected chain's
     *                                       last column origin — its right extent, plus a full line
     *                                       rest when that column is not the auto-maintained terminal
     *                                       ({@link HorizontalSpacingCalculator#trailingReservationSs})
     */
    public record InsertionResult(
        double insertedElementXSs,
        double shiftForSubsequentElementsSs,
        double newLineWidthSs,
        List<Spring> projectedSprings,
        double projectedFirstXSs,
        double projectedTrailingReservationSs
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
                projectedSprings, projectedFirstXSs, projectedTrailingReservationSs, staffRightMarginSs)
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
     * An element range {@code begin} through {@code end} (both inclusive) that a
     * paste-replace will delete before inserting a fragment at {@code begin}.
     * Callers pass the <em>effective</em> range (already extended past a trailing
     * breath mark) so the fit check sees exactly what the deletion will remove.
     *
     * @param begin The index of the first element to be deleted
     * @param end   The index of the last element to be deleted
     */
    public record DeletedRange(int begin, int end) {
    }

    /**
     * Result of a fragment insertion spacing calculation: the X position of every fragment clone,
     * the single shift to apply to all surviving elements after the fragment, and the projected
     * spring chain its fit gate solves. The multi-element analogue of {@link InsertionResult}.
     *
     * @param cloneXPositionsSs              X position for each fragment clone, in fragment order
     * @param shiftForSubsequentElementsSs   Amount to shift every surviving element after the
     *                                       fragment (negative when a paste-replace pulls the
     *                                       tail left)
     * @param projectedSprings               The lyric-stretched spring chain of the line as LayoutEngine
     *                                       will build it after the operation — the surviving columns
     *                                       with the fragment's clones spliced in, including the terminal
     *                                       barline — what {@link #fitsWithinLine} solves
     * @param projectedFirstXSs              X the projected chain is anchored at
     *                                       ({@link HorizontalSpacingCalculator#calculateAnchorXSs})
     * @param projectedTrailingReservationSs The span the solve keeps clear past the projected chain's
     *                                       last column origin — its right extent, plus a full line
     *                                       rest when that column is not the auto-maintained terminal
     *                                       ({@link HorizontalSpacingCalculator#trailingReservationSs})
     */
    public record FragmentInsertionResult(
        List<Double> cloneXPositionsSs,
        double shiftForSubsequentElementsSs,
        List<Spring> projectedSprings,
        double projectedFirstXSs,
        double projectedTrailingReservationSs
    ) {
        public FragmentInsertionResult {
            cloneXPositionsSs = List.copyOf(cloneXPositionsSs);
            projectedSprings = List.copyOf(projectedSprings);
        }

        /**
         * Returns whether the fragment insertion fits within the given right margin — compress-to-fit,
         * the identical gate single-element insertion uses: the line is free to give up slack to
         * absorb the clones, and only a chain that overflows with every gap frozen at its strut is
         * rejected.
         *
         * @param staffRightMarginSs The maximum allowed line width in staff spaces
         * @return {@code true} unless the projected spring chain solves INFEASIBLE
         */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !HorizontalSpacingCalculator.solveChain(
                projectedSprings, projectedFirstXSs, projectedTrailingReservationSs, staffRightMarginSs)
                .isInfeasible();
        }
    }

    /**
     * Result of a modification spacing calculation: the projected spring chain of the line as it
     * will read once an in-place modification is applied, and the fit gate over it.
     * <p>
     * The replace-a-column analogue of {@link InsertionResult} and
     * {@link FragmentInsertionResult}. A modification changes no element count, so nothing has to
     * be positioned — every index keeps the element it had — and the result carries only what the
     * gate needs.
     *
     * @param projectedSprings               The lyric-stretched spring chain of the line as
     *                                       {@link LayoutEngine} will build it after the
     *                                       modification — every column with the changed ones
     *                                       swapped out, including the terminal barline — what
     *                                       {@link #fitsWithinLine} solves
     * @param projectedFirstXSs              X the projected chain is anchored at
     *                                       ({@link HorizontalSpacingCalculator#calculateAnchorXSs})
     * @param projectedTrailingReservationSs The span the solve keeps clear past the projected chain's
     *                                       last column origin — its right extent, plus a full line
     *                                       rest when that column is not the auto-maintained terminal
     *                                       ({@link HorizontalSpacingCalculator#trailingReservationSs})
     */
    public record ModificationResult(
        List<Spring> projectedSprings,
        double projectedFirstXSs,
        double projectedTrailingReservationSs
    ) {
        public ModificationResult {
            projectedSprings = List.copyOf(projectedSprings);
        }

        /**
         * Returns whether the modified line fits within the given right margin — compress-to-fit,
         * the identical gate the insertion paths use: the line is free to give up slack to absorb
         * the widened columns, and only a chain that overflows with every gap frozen at its strut
         * is rejected.
         *
         * @param staffRightMarginSs The maximum allowed line width in staff spaces
         * @return {@code true} unless the projected spring chain solves INFEASIBLE
         */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !HorizontalSpacingCalculator.solveChain(
                projectedSprings, projectedFirstXSs, projectedTrailingReservationSs, staffRightMarginSs)
                .isInfeasible();
        }
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
            insertedElementXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
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
        appendTerminalIfPresent(
            fitColumns, line, lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null);

        var fitSprings = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(fitColumns, line), fitColumns);
        var fitFirstXSs = HorizontalSpacingCalculator.calculateAnchorXSs(fitColumns.getFirst(), line);

        return new InsertionResult(
            insertedElementXSs,
            shiftSs,
            newLineWidthSs,
            fitSprings,
            fitFirstXSs,
            HorizontalSpacingCalculator.trailingReservationSs(fitColumns.getLast(), line));
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
     * Appends the line's auto-maintained terminal barline column to a projected chain, if the line
     * has one. The positioning chains exclude the terminal — layout pins it flush-right rather than
     * shifting it — but the fit solve must still include it, because it consumes the span the rest
     * of the chain has to fit into. Without it a nearly-full line could pass the gate, commit, then
     * fail to lay out.
     */
    private static void appendTerminalIfPresent(
        List<ElementColumn> columns, Line line, @Nullable ElementColumnBuilder columnBuilder) {

        var elementCount = line.elementCount();

        if (line.effectiveElementCount() < elementCount) {
            var terminalIndex = elementCount - 1;
            columns.add(buildSurroundingColumn(line.getElement(terminalIndex), line, terminalIndex, columnBuilder));
        }
    }

    /**
     * Calculates positions and fit for inserting a multi-element fragment at {@code insertIndex},
     * optionally replacing a deleted range (paste-replace). Pure measurement — the line and the
     * fragment elements are never mutated; all positioning happens on lightweight columns.
     * <p>
     * The multi-element analogue of {@link #calculateInsertion}: it builds the projected line the
     * commit will produce — surviving predecessors, the fragment's clones, then the surviving
     * successors — spaces the clones by the natural spring lengths from the predecessor's current
     * position, and derives the single trailing shift for the tail. Fit is the same compress-to-fit
     * solve over the same chain the layout engine runs, including the terminal barline, so an insert
     * the gate accepts always lays out.
     * <p>
     * The surrounding columns and the fragment's own clones carry their real syllable widths
     * whenever a {@link LyricRenderMetrics} is supplied, mirroring {@link #calculateInsertion}.
     * Callers with no lyric metrics to hand may pass {@code null}, which falls back to
     * syllable-less columns for both.
     *
     * @param fragment           The elements to be inserted, in order (must be non-empty)
     * @param insertIndex        The index where the fragment's first element will land
     *                           (must equal {@code deleteRange.begin()} when a range is given)
     * @param deleteRange        The effective range a paste-replace deletes first, or null for pure insertion
     * @param layout             Layout result for position lookup; null falls back to {@code xOffset}
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return A {@link FragmentInsertionResult} with per-clone X positions, the trailing shift,
     *         and the projected spring chain its fit gate solves
     */
    public static FragmentInsertionResult calculateFragmentInsertion(
        Line line,
        List<StaffElement> fragment,
        int insertIndex,
        @Nullable DeletedRange deleteRange,
        @Nullable LayoutResult layout,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (fragment.isEmpty()) {
            throw new IllegalArgumentException("fragment must not be empty");
        }

        var effectiveCount = line.effectiveElementCount();

        if (insertIndex < 0 || insertIndex > effectiveCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + effectiveCount + ']');
        }

        if (deleteRange != null) {
            if (deleteRange.begin() < 0
                    || deleteRange.begin() > deleteRange.end()
                    || deleteRange.end() >= effectiveCount) {
                throw new IllegalArgumentException("invalid deleteRange " + deleteRange);
            }

            if (insertIndex != deleteRange.begin()) {
                throw new IllegalArgumentException(
                    "insertIndex " + insertIndex + " must equal deleteRange.begin() " + deleteRange.begin());
            }
        }

        // The positioning chain, terminal barline excluded (layout pins it flush-right rather
        // than shifting it): surviving predecessors, the fragment's clones, then the surviving
        // successors — the line as it will read after delete+insert. A paste-replace drops the
        // deleted range by skipping straight from the predecessors to the element past the range.
        var successorIndex = deleteRange == null ? insertIndex : deleteRange.end() + 1;
        var columns = new ArrayList<ElementColumn>();
        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;

        for (var i = 0; i < insertIndex; i++) {
            columns.add(buildSurroundingColumn(line.getElement(i), line, i, columnBuilder));
        }

        var fragmentStart = columns.size();

        for (var element : fragment) {
            columns.add(columnBuilder != null
                ? columnBuilder.buildDetachedColumn(element, line.getSong().getActiveVerse())
                : createLightweightColumn(element));
        }

        for (var i = successorIndex; i < effectiveCount; i++) {
            columns.add(buildSurroundingColumn(line.getElement(i), line, i, columnBuilder));
        }

        // Natural spring lengths drive where each clone lands, measured from the predecessor's
        // CURRENT position so only what follows the insertion point moves — the same rule
        // calculateInsertion uses for a single element, chained across the fragment's clones.
        var springs = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(columns, line), columns);
        var cloneXPositionsSs = new ArrayList<Double>(fragment.size());
        double cloneXSs;

        if (fragmentStart == 0) {
            cloneXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
        } else {
            var predecessor = columns.get(fragmentStart - 1).getElement();
            cloneXSs = elementXSs(predecessor, layout) + springs.get(fragmentStart - 1).naturalLengthSs();
        }

        cloneXPositionsSs.add(cloneXSs);

        for (var k = 1; k < fragment.size(); k++) {
            cloneXSs += springs.get(fragmentStart + k - 1).naturalLengthSs();
            cloneXPositionsSs.add(cloneXSs);
        }

        // The single trailing shift for every surviving successor: where the first survivor must
        // sit (last clone position + its spring) minus where it currently sits. A pure insertion
        // only ever pushes the tail right; a paste-replace may pull it left when the fragment is
        // narrower than the deleted range.
        var lastCloneIndex = fragmentStart + fragment.size() - 1;
        var shiftSs = 0.0;

        if (lastCloneIndex + 1 < columns.size()) {
            var successor = columns.get(lastCloneIndex + 1).getElement();
            var requiredSuccessorXSs = cloneXSs + springs.get(lastCloneIndex).naturalLengthSs();
            shiftSs = requiredSuccessorXSs - elementXSs(successor, layout);

            if (deleteRange == null) {
                shiftSs = Math.max(0, shiftSs);
            }
        }

        // The fit gate solves the SAME chain LayoutEngine will after the commit — the positioning
        // columns PLUS the auto-maintained terminal barline (excluded above because layout pins it
        // flush-right, not shifted). Anchoring with the shared calculateAnchorXSs and lyric-lifting
        // the springs keeps the pre-check and the committed layout from disagreeing, exactly as
        // calculateInsertion does for a single element.
        var fitColumns = new ArrayList<>(columns);
        appendTerminalIfPresent(fitColumns, line, columnBuilder);

        var fitSprings = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(fitColumns, line), fitColumns);
        var fitFirstXSs = HorizontalSpacingCalculator.calculateAnchorXSs(fitColumns.getFirst(), line);

        return new FragmentInsertionResult(
            cloneXPositionsSs,
            shiftSs,
            fitSprings,
            fitFirstXSs,
            HorizontalSpacingCalculator.trailingReservationSs(fitColumns.getLast(), line));
    }

    /**
     * Calculates whether the line still fits once an in-place modification — an accidental, a dot,
     * a duration swap — is applied. Pure measurement: the line and the projected elements are
     * never mutated.
     * <p>
     * The replace-a-column analogue of {@link #calculateInsertion} and
     * {@link #calculateFragmentInsertion}: the same projection with columns <em>replaced</em>
     * rather than spliced. Because a modification never changes the element count, indices are
     * preserved 1:1 and there is nothing to position — {@code projectedElements} is simply the
     * line as it will read, detached stand-ins at the changed positions and the live elements
     * everywhere else. Each column takes its extents from the projected element and its lyric and
     * beam context from the same index on the real line, then the auto-maintained terminal barline
     * is appended, exactly as the insertion projections do.
     * <p>
     * Without this gate an infeasible modification is not refused at mutation time: it surfaces
     * later as {@code LINE_TOO_FULL_ERROR} and a <b>null</b> {@code LayoutResult}, so the line does
     * not render at all.
     *
     * @param line               The line being modified, in its pre-modification state
     * @param projectedElements  The line's effective elements as they will read after the
     *                           modification, in element order; must have exactly
     *                           {@code line.effectiveElementCount()} entries, and at least one
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return A {@link ModificationResult} carrying the projected spring chain its fit gate solves
     */
    public static ModificationResult calculateModification(
        Line line,
        List<StaffElement> projectedElements,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var effectiveCount = line.effectiveElementCount();

        if (projectedElements.size() != effectiveCount) {
            throw new IllegalArgumentException(
                "projectedElements has " + projectedElements.size()
                    + " entries for " + effectiveCount + " effective elements");
        }

        if (projectedElements.isEmpty()) {
            throw new IllegalArgumentException("projectedElements must not be empty");
        }

        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;
        var columns = new ArrayList<ElementColumn>(line.elementCount());

        for (var i = 0; i < effectiveCount; i++) {
            columns.add(buildSurroundingColumn(projectedElements.get(i), line, i, columnBuilder));
        }

        appendTerminalIfPresent(columns, line, columnBuilder);

        var springs = LyricLift.applyLyricLift(
            HorizontalSpacingCalculator.buildSprings(columns, line), columns);

        return new ModificationResult(
            springs,
            HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line),
            HorizontalSpacingCalculator.trailingReservationSs(columns.getLast(), line));
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
     * A fall widens the source element's column rather than adding one, so the line it produces is
     * the current line with that one column swapped out. This gate builds exactly that projection —
     * every column including the auto-maintained terminal barline, with the fall-carrying clone in
     * the source's place — and runs the same compress-to-fit solve
     * ({@link InsertionResult#fitsWithinLine}) that the insertion gates and the committed layout
     * run. Solving rather than measuring is what lets a full line with slack in its springs accept
     * a fall: the projection is derived from the chain, never from the elements' current X
     * positions, which still carry the pre-fall (and possibly stale) spacing (refs #617).
     *
     * @param line               The line to check
     * @param sourceIndex        The index of the element the fall would be applied to. Must name an
     *                           effective element ({@code < line.effectiveElementCount()}) — the
     *                           auto-maintained terminal cannot carry a fall, and an index naming it
     *                           would leave the fall out of the projection entirely
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} if the fall fits on the line
     */
    public static boolean hasRoomForFall(
        Line line, int sourceIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var sourceElement = line.getElement(sourceIndex);

        // Fall already present — no change in right extent, always fits
        if (sourceElement.hasFall()) {
            return true;
        }

        // Measure a clone carrying the fall so the live model element is never mutated,
        // mirroring the read-only geometry contract of hasRoomForGraceNote.
        var sourceWithFall = sourceElement.clone();
        sourceWithFall.setFall();

        var effectiveCount = line.effectiveElementCount();
        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;
        var columns = new ArrayList<ElementColumn>(line.elementCount());

        for (var i = 0; i < effectiveCount; i++) {
            var element = i == sourceIndex ? sourceWithFall : line.getElement(i);
            columns.add(buildSurroundingColumn(element, line, i, columnBuilder));
        }

        appendTerminalIfPresent(columns, line, columnBuilder);

        return !HorizontalSpacingCalculator.solveLine(columns, line, line.getSong().getLineWidthSs())
            .isInfeasible();
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
