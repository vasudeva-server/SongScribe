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
import java.util.HashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Slide;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;

/**
 * Spacing calculations for element insertion operations.
 * <p>
 * This class answers two questions for interactive editing (adding or inserting an element)
 * without committing to a full relayout:
 * <ul>
 *   <li><b>Where does the element go?</b> — the natural (uncompressed) length of the
 *       {@link Spring} on either side of the insertion point, measured from the neighbours'
 *       <em>current</em> positions, so only the elements after the insertion point move.</li>
 *   <li><b>Does it still fit?</b> — {@link InsertionResult#fitsWithinLine} hands the projected
 *       columns {@link LayoutEngine} would build after the commit — every column with the new one
 *       spliced in, <em>including the auto-maintained terminal barline</em> — to the same
 *       {@link HorizontalSpacingCalculator#solveLine} the layout engine calls. Running the one
 *       solve rather than reassembling its steps is what guarantees the two agree: an insert the
 *       gate accepts always lays out, and a step added to the solve reaches the gate on its own. An
 *       insert is refused only when even full compression to every gap's strut overflows the
 *       margin; a line with slack simply compresses to absorb the new element.</li>
 * </ul>
 * <p>
 * The columns surrounding the insertion point carry their real syllable widths whenever a
 * {@link LyricRenderMetrics} is supplied, so previews are spaced by the same lyric rules as the
 * committed layout. Callers with no lyric metrics to hand may pass {@code null}, which falls
 * back to syllable-less columns.
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
     * @param line                         The line the insertion lands on
     * @param projectedColumns             The line's columns as {@link LayoutEngine} will build them
     *                                     after the commit — every column with the new one spliced
     *                                     in, including the terminal barline — what {@link #solve}
     *                                     solves
     */
    public record InsertionResult(
        double insertedElementXSs,
        double shiftForSubsequentElementsSs,
        double newLineWidthSs,
        Line line,
        List<ElementColumn> projectedColumns
    ) {
        public InsertionResult {
            projectedColumns = List.copyOf(projectedColumns);
        }

        /**
         * Runs the projected line through {@link HorizontalSpacingCalculator#solveLine} — the whole
         * solve the layout engine runs, over the columns the commit will produce.
         *
         * @param staffRightMarginSs The maximum allowed line width in staff spaces
         * @return the anchor, the spring chain and the solver's verdict
         */
        public HorizontalSpacingCalculator.LineSolution solve(double staffRightMarginSs) {
            return HorizontalSpacingCalculator.solveLine(projectedColumns, line, staffRightMarginSs);
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
            return !solve(staffRightMarginSs).isInfeasible();
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
     * Result of a fragment insertion spacing calculation: the projected spring chain its fit gate
     * solves. The multi-element analogue of {@link InsertionResult}.
     *
     * @param line             The line the fragment lands on
     * @param projectedColumns The line's columns as {@link LayoutEngine} will build them after the
     *                         operation — the surviving columns with the fragment's clones spliced
     *                         in, including the terminal barline — what {@link #fitsWithinLine}
     *                         solves
     */
    public record FragmentInsertionResult(Line line, List<ElementColumn> projectedColumns) {
        public FragmentInsertionResult {
            projectedColumns = List.copyOf(projectedColumns);
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
            return !HorizontalSpacingCalculator
                .solveLine(projectedColumns, line, staffRightMarginSs).isInfeasible();
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
     * @param line             The line being modified
     * @param projectedColumns The line's columns as {@link LayoutEngine} will build them after the
     *                         modification — every column with the changed ones swapped out,
     *                         including the terminal barline — what {@link #fitsWithinLine} solves
     */
    public record ModificationResult(Line line, List<ElementColumn> projectedColumns) {
        public ModificationResult {
            projectedColumns = List.copyOf(projectedColumns);
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
            return !HorizontalSpacingCalculator
                .solveLine(projectedColumns, line, staffRightMarginSs).isInfeasible();
        }
    }

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
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
     * @param layout             The line's current layout, which the neighbours' positions are
     *                           read from
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return An {@link InsertionResult} with the element's X position, the shift for subsequent
     *         elements, and the projected spring chain its fit gate solves
     */
    public static InsertionResult calculateInsertion(
        Line line,
        StaffElement insertedElement,
        int insertIndex,
        LayoutResult layout,
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
            insertedElementXSs = layout.getElementXSs(prevElement) + springs.get(splicedIndex - 1).naturalLengthSs();
        }

        insertedColumn.setXSs(insertedElementXSs);

        var shiftSs = 0.0;

        if (splicedIndex + 1 < columns.size()) {
            var nextElement = columns.get(splicedIndex + 1).getElement();
            var requiredNextXSs = insertedElementXSs + springs.get(splicedIndex).naturalLengthSs();
            // Shift = (where next needs to be) - (where it currently is), never negative:
            // a gap that is already wide enough stays as it is.
            shiftSs = Math.max(0, requiredNextXSs - layout.getElementXSs(nextElement));
        }

        // Projected line width: max of the inserted element's right edge and the last real
        // element's right edge shifted by the insertion shift.
        var newLineWidthSs = projectedWidthWithLastShiftSs(line, insertedColumn.getRightEdgeXSs(), shiftSs, layout);

        // The fit gate must solve the SAME chain LayoutEngine will after the commit — including the
        // auto-maintained terminal barline, which the positioning chain above excludes (the terminal
        // is pinned flush-right, not shifted). Handing the columns to the shared
        // HorizontalSpacingCalculator.solveLine, rather than assembling a chain here, is what keeps
        // the pre-check and the committed layout from disagreeing: without the terminal a nearly-full
        // line could pass the gate, commit, then fail to lay out, and a chain assembled here would
        // drift from solveLine the moment a step is added to it.
        var fitColumns = new ArrayList<ElementColumn>(columns);
        appendTerminalIfPresent(
            fitColumns, line, lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null);

        return new InsertionResult(insertedElementXSs, shiftSs, newLineWidthSs, line, fitColumns);
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
        List<? super ElementColumn> columns, Line line, @Nullable ElementColumnBuilder columnBuilder) {

        var elementCount = line.elementCount();

        if (line.effectiveElementCount() < elementCount) {
            var terminalIndex = elementCount - 1;
            columns.add(buildSurroundingColumn(line.getElement(terminalIndex), line, terminalIndex, columnBuilder));
        }
    }

    /**
     * Calculates the fit for inserting a multi-element fragment at {@code target}, optionally
     * replacing a deleted range first (paste-replace). Pure measurement — the line and the
     * fragment elements are never mutated; the projection is built on lightweight columns.
     * <p>
     * The multi-element analogue of {@link #calculateInsertion}: it builds the projected line the
     * commit will produce — surviving predecessors, the fragment's clones, then the surviving
     * successors. Fit is the same compress-to-fit solve over the same chain the layout engine
     * runs, including the terminal barline, so an insert the gate accepts always lays out.
     * <p>
     * The surrounding columns and the fragment's own clones carry their real syllable widths
     * whenever a {@link LyricRenderMetrics} is supplied, mirroring {@link #calculateInsertion}.
     * Callers with no lyric metrics to hand may pass {@code null}, which falls back to
     * syllable-less columns for both.
     *
     * @param fragment           The elements to be inserted, in order (must be non-empty)
     * @param target             Where the fragment lands: a plain index, or the effective range
     *                           this paste-replace deletes first and the index it leaves the
     *                           fragment at
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return A {@link FragmentInsertionResult} with the projected spring chain its fit gate solves
     */
    public static FragmentInsertionResult calculateFragmentInsertion(
        Line line,
        List<? extends StaffElement> fragment,
        StaffElementRun.InsertionTarget target,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (fragment.isEmpty()) {
            throw new IllegalArgumentException("fragment must not be empty");
        }

        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange() == null ? null : target.deleteRange().range();
        var effectiveCount = line.effectiveElementCount();

        if (insertIndex < 0 || insertIndex > effectiveCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + effectiveCount + ']');
        }

        if (deleteRange != null && (deleteRange.begin() < 0 || deleteRange.end() >= effectiveCount)) {
            throw new IllegalArgumentException("invalid deleteRange " + deleteRange);
        }

        // The projected chain: surviving predecessors, the fragment's clones, then the surviving
        // successors — the line as it will read after delete+insert. A paste-replace drops the
        // deleted range by skipping straight from the predecessors to the element past the range.
        var successorIndex = deleteRange == null ? insertIndex : deleteRange.end() + 1;
        var columns = new ArrayList<ElementColumn>();
        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;

        for (var i = 0; i < insertIndex; i++) {
            columns.add(buildSurroundingColumn(line.getElement(i), line, i, columnBuilder));
        }

        var activeVerse = line.getSong().getActiveVerse();

        // A key signature's width depends on the key it cancels as well as the key it establishes,
        // and a fragment element sits on no line to read that from. The projected sequence is what
        // knows it: the key in effect just before the insertion point, moved on by each key
        // change the fragment itself carries. Each one is measured through a stand-in told that
        // key, so a pasted or inserted signature reserves the room its naturals actually need.
        var runningKey = line.keyBefore(insertIndex);

        for (var element : fragment) {
            var measured = element;

            if (element instanceof KeyChangeElement keyChange) {
                measured = KeyChangeElement.forMeasurement(keyChange.getKey(), runningKey);
                runningKey = keyChange.getKey();
            }

            columns.add(columnBuilder != null
                ? columnBuilder.buildDetachedColumn(measured, activeVerse)
                : createLightweightColumn(measured));
        }

        for (var i = successorIndex; i < effectiveCount; i++) {
            columns.add(buildSurroundingColumn(line.getElement(i), line, i, columnBuilder));
        }

        // The fit gate solves the SAME chain LayoutEngine will after the commit — the projected
        // columns PLUS the auto-maintained terminal barline — through the shared solveLine,
        // exactly as calculateInsertion does for a single element.
        appendTerminalIfPresent(columns, line, columnBuilder);

        return new FragmentInsertionResult(line, columns);
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
     * later as a line laid out on its collision floors, whose tail runs past the staff and is
     * clipped, drawn in red (refs #696) — the modified element may itself be the part cut off.
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
        List<? extends StaffElement> projectedElements,
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

        return new ModificationResult(line, columns);
    }

    /**
     * Determines whether a grace note will fit on a line when inserted at the given index.
     *
     * @param line               The line to check (before insertion)
     * @param atIndex            The index where the grace note would be inserted
     * @param layout             The line's current layout, which the neighbours' positions are
     *                           read from
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} if the grace note fits on the line
     */
    public static boolean hasRoomForGraceNote(
        Line line, int atIndex, LayoutResult layout, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var staffRightMarginSs = line.getSong().getLineWidthSs().value();
        var graceNote = ElementType.GRACE_QUAVER.newInstance();
        return calculateInsertion(line, graceNote, atIndex, layout, lyricRenderMetrics)
            .fitsWithinLine(staffRightMarginSs);
    }

    /**
     * Determines whether the slides of the given kind will fit on a line when applied to the
     * elements at the given indices.
     * <p>
     * A slide claims horizontal space without adding a column: a fall widens its own source's
     * column, and a connecting glissando puts a floor under the spring reaching the following
     * column so the line it draws stays visible. Either way the line it produces is the current
     * line with those columns swapped out — the same shape as an accidental, a dot or a duration
     * swap, so this projects the line's effective elements with a slide-carrying clone at each
     * source index and asks {@link #calculateModification} the identical projection-and-solve
     * question those modifications do ({@link ModificationResult#fitsWithinLine}). Solving rather
     * than measuring is what lets a full line with slack in its springs accept a slide: the
     * projection is derived from the chain, never from the elements' current X positions, which
     * still carry the pre-slide (and possibly stale) spacing (refs #617).
     * <p>
     * The whole batch is projected in one solve, so the verdict accounts for the slides' cumulative
     * demand. Asking about each candidate separately against the unchanged line would accept N
     * slides that each fit alone but jointly overrun it.
     *
     * @param line               The line to check
     * @param sourceIndices      The indices of the elements the slides would be applied to. Each
     *                           must name an effective element
     *                           ({@code < line.effectiveElementCount()}) — the auto-maintained
     *                           terminal cannot carry a slide, and an index naming it would leave
     *                           that slide out of the projection entirely, so one that does refuses
     *                           the whole batch
     * @param slide              The kind of slide being applied
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} if the slides fit on the line
     */
    public static boolean hasRoomForSlides(
        Line line, int[] sourceIndices, Slide slide, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var effectiveCount = line.effectiveElementCount();

        // Measure clones carrying the slide so the live model elements are never mutated,
        // mirroring the read-only geometry contract of hasRoomForGraceNote.
        var sourcesWithSlide = new HashMap<Integer, StaffElement>();

        for (var sourceIndex : sourceIndices) {
            if (sourceIndex < 0 || sourceIndex >= effectiveCount) {
                return false;
            }

            var sourceElement = line.noteAt(sourceIndex);

            // Slide already present — it claims no further space, so the live element measures it.
            if (sourceElement.getSlide() != slide) {
                var sourceWithSlide = sourceElement.clone();
                sourceWithSlide.setSlide(slide);
                sourcesWithSlide.put(sourceIndex, sourceWithSlide);
            }
        }

        // Nothing claims space — the projection is the line as it already stands, which already fits.
        if (sourcesWithSlide.isEmpty()) {
            return true;
        }

        var projectedElements = new ArrayList<StaffElement>(effectiveCount);

        for (var i = 0; i < effectiveCount; i++) {
            projectedElements.add(sourcesWithSlide.getOrDefault(i, line.getElement(i)));
        }

        return fitsInPlace(line, projectedElements, lyricRenderMetrics);
    }

    /**
     * Returns whether {@code line} still fits its song's line width once it reads as
     * {@code projectedElements}: the in-place gate every modification that widens a column asks —
     * a slide, a dot, a duration swap, or a chain walk stamping an accidental on a note that drew
     * none. Pure measurement — neither the line nor the projected elements are mutated.
     * <p>
     * The verdict is {@link #calculateModification}'s
     * {@link ModificationResult#fitsWithinLine compress-to-fit} solve at the song's line width, so a
     * modification this accepts always lays out, and a step added to the solve reaches this gate on
     * its own.
     *
     * @param line               The line being modified, in its pre-modification state
     * @param projectedElements  The line's effective elements as they will read after the
     *                           modification, in element order: detached stand-ins at the changed
     *                           positions and the live elements everywhere else, or the live
     *                           elements throughout to measure the line as it stands
     * @param lyricRenderMetrics Lyric metrics for measuring the line's syllables; null spaces the
     *                           line as if it had no lyrics
     * @return {@code true} unless the projected line overflows the song's line width with every
     *         gap frozen at its strut
     * @throws IllegalArgumentException when {@code projectedElements} is empty, or its size differs
     *                                  from {@code line.effectiveElementCount()}
     */
    public static boolean fitsInPlace(
        Line line,
        List<? extends StaffElement> projectedElements,
        @Nullable LyricRenderMetrics lyricRenderMetrics) {

        return calculateModification(line, projectedElements, lyricRenderMetrics)
            .fitsWithinLine(line.getSong().getLineWidthSs().value());
    }

    /**
     * Projects the line width after shifting the last effective element right by {@code shiftSs},
     * taking the larger of {@code baseWidthSs} and that element's shifted right edge. Returns
     * {@code baseWidthSs} unchanged when the line has no effective elements.
     */
    private static double projectedWidthWithLastShiftSs(
        Line line, double baseWidthSs, double shiftSs, LayoutResult layout) {

        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount == 0) {
            return baseWidthSs;
        }

        var lastElement = line.getElement(effectiveCount - 1);
        var lastColumn = createLightweightColumn(lastElement);
        lastColumn.setXSs(layout.getElementXSs(lastElement) + shiftSs);

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
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false);
        var rightExtentExcludingAugmentationSs = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(element, false);

        // For insertion operations, we don't need stem positions or beam group info
        // since we're only calculating horizontal spacing
        double stemTopSs = 0;
        double stemBottomSs = 0;

        // No syllable information for lightweight columns
        Lyric lyric = null;
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
            lyric,
            syllableWidthSs,
            false
        );
    }
}
