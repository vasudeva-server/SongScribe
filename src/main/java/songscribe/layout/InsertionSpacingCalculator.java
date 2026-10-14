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
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.Slide;
import songscribe.dom.StaffElement;

/**
 * Spacing calculations for element insertion, answering where the element goes and whether it
 * still fits, without committing to a full relayout.
 * <p>
 * Fit hands the projected columns, including the terminal barline, to the same
 * {@link HorizontalSpacingCalculator#solveLine} the layout engine calls, so an insert the gate
 * accepts always lays out. An insert is refused only when even full compression to every gap's
 * strut overflows the margin.
 * <p>
 * The columns surrounding the insertion point carry their real syllable widths whenever a
 * {@link LyricRenderMetrics} is supplied; {@code null} falls back to syllable-less columns.
 */
public final class InsertionSpacingCalculator {

    /**
     * @param shiftForSubsequentElementsSs always >= 0
     * @param projectedColumns             The line's columns as {@link LayoutEngine} will build them
     *                                     after the commit, including the terminal barline
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

        public HorizontalSpacingCalculator.LineSolution solve(double staffRightMarginSs) {
            return HorizontalSpacingCalculator.solveLine(projectedColumns, line, staffRightMarginSs);
        }

        /** Compress-to-fit: only a chain that overflows with every gap frozen at its strut is rejected. */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !solve(staffRightMarginSs).isInfeasible();
        }
    }

    /**
     * The <em>positioning</em> chain for an insertion, which excludes the terminal barline; the
     * separate <em>fit</em> chain built in {@link #calculateInsertion} includes it.
     *
     * @param columns Projected columns in element order (never empty)
     */
    private record ProjectedLine(List<ElementColumn> columns, List<Spring> springs, int insertIndex) {}

    /**
     * @param projectedColumns The line's columns as {@link LayoutEngine} will build them after the
     *                         operation, including the terminal barline
     * @param projectedKeys    The line's keys as the operation leaves them
     */
    public record FragmentInsertionResult(Line line, List<ElementColumn> projectedColumns, LineKeys projectedKeys) {
        public FragmentInsertionResult {
            projectedColumns = List.copyOf(projectedColumns);
        }

        /** Compress-to-fit, the identical gate single-element insertion uses. */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !HorizontalSpacingCalculator
                .solveLine(projectedColumns, line, staffRightMarginSs, projectedKeys).isInfeasible();
        }
    }

    /**
     * A modification changes no element count, so the result carries only what the gate needs.
     *
     * @param projectedColumns The line's columns as {@link LayoutEngine} will build them after the
     *                         modification, including the terminal barline
     */
    public record ModificationResult(Line line, List<ElementColumn> projectedColumns) {
        public ModificationResult {
            projectedColumns = List.copyOf(projectedColumns);
        }

        /** Compress-to-fit, the identical gate the insertion paths use. */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return !HorizontalSpacingCalculator
                .solveLine(projectedColumns, line, staffRightMarginSs).isInfeasible();
        }
    }

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * Must be called before the element is added to the line, since it examines the line's
     * current state to determine proper spacing.
     *
     * @param lyricRenderMetrics null spaces the line as if it had no lyrics
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

        var insertedColumn = ElementColumnBuilder.buildLightweightColumn(insertedElement);

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

    /** The auto-maintained terminal is excluded: layout pins it. The fit gate's chain includes it. */
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

    private static ElementColumn buildSurroundingColumn(
        StaffElement element, Line line, int elementIndex, @Nullable ElementColumnBuilder columnBuilder) {

        if (columnBuilder == null) {
            return ElementColumnBuilder.buildLightweightColumn(element);
        }

        return columnBuilder.buildColumn(element, line, elementIndex);
    }

    /**
     * The fit solve must include the terminal, because it consumes span the rest of the chain has
     * to fit into; without it a nearly-full line could pass the gate and then fail to lay out.
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
     * Pure measurement: the line and the fragment elements are never mutated. The solve reads the
     * line in the keys the edit leaves it in. A {@code null} {@code lyricRenderMetrics} falls back
     * to syllable-less columns.
     */
    public static FragmentInsertionResult calculateFragmentInsertion(
        ProjectedEdit edit, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var fragment = edit.inserted();

        if (fragment.isEmpty()) {
            throw new IllegalArgumentException("fragment must not be empty");
        }

        var line = edit.line();
        var insertIndex = edit.index();
        var effectiveCount = line.effectiveElementCount();

        // A paste-replace drops the deleted range by skipping straight from the predecessors to
        // the element past the range.
        var successorIndex = insertIndex + edit.removed().size();

        if (successorIndex > effectiveCount) {
            throw new IllegalArgumentException(
                "edit at " + insertIndex + " removing " + edit.removed().size()
                    + " elements runs past the effective end " + effectiveCount);
        }

        // The projected chain: surviving predecessors, the fragment's clones, then the surviving
        // successors — the line as it will read after delete+insert.
        var columns = new ArrayList<ElementColumn>();
        var columnBuilder = lyricRenderMetrics != null ? new ElementColumnBuilder(lyricRenderMetrics) : null;

        // The key in effect at each projected element, starting from the key the edit leaves the
        // line starting in.
        var runningKey = edit.startKey();

        for (var i = 0; i < insertIndex; i++) {
            var element = line.getElement(i);
            runningKey = keyAfter(element, runningKey);
            columns.add(buildSurroundingColumn(element, line, i, columnBuilder));
        }

        var activeScript = line.getSong().getActiveScript();

        // A key signature's width depends on the key it cancels as well as the key it establishes,
        // and a fragment element sits on no line to read that from. The projected sequence is what
        // knows it: the key in effect just before the insertion point, moved on by each key
        // change the fragment itself carries. Each one is measured through a stand-in told that
        // key, so a pasted or inserted signature reserves the room its naturals actually need.
        for (var element : fragment) {
            var measured = element;

            if (element instanceof KeyChangeElement keyChange) {
                measured = KeyChangeElement.forMeasurement(keyChange.getKey(), runningKey);
            }

            runningKey = keyAfter(element, runningKey);

            columns.add(columnBuilder != null
                ? columnBuilder.buildDetachedColumn(measured, activeScript)
                : ElementColumnBuilder.buildLightweightColumn(measured));
        }

        for (var i = successorIndex; i < effectiveCount; i++) {
            var element = line.getElement(i);
            runningKey = keyAfter(element, runningKey);
            columns.add(buildSurroundingColumn(element, line, i, columnBuilder));
        }

        // The fit gate solves the SAME chain LayoutEngine will after the commit — the projected
        // columns PLUS the auto-maintained terminal barline — through the shared solveLine,
        // exactly as calculateInsertion does for a single element.
        appendTerminalIfPresent(columns, line, columnBuilder);

        var keys = new LineKeys(edit.startKey(), runningKey, nextRunningKey(line, runningKey));

        return new FragmentInsertionResult(line, columns, keys);
    }

    private static Key keyAfter(StaffElement element, Key key) {
        return (element instanceof KeyChangeElement keyChange) ? keyChange.getKey() : key;
    }

    /**
     * @return null when {@code line} is its song's last line
     */
    private static @Nullable Key nextRunningKey(Line line, Key keyAtEndOfLine) {
        if (line.isLast()) {
            return null;
        }

        var nextOwnKey = line.getSong().getLine(line.index() + 1).getKey();

        return (nextOwnKey != null) ? nextOwnKey : keyAtEndOfLine;
    }

    /**
     * Pure measurement: the line and the projected elements are never mutated.
     *
     * @param lyricRenderMetrics null spaces the line as if it had no lyrics
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
     * @param lyricRenderMetrics null spaces the line as if it had no lyrics
     */
    public static boolean hasRoomForGraceNote(
        Line line, int atIndex, LayoutResult layout, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var staffRightMarginSs = line.getSong().getLineWidthSs().value();
        var graceNote = ElementType.GRACE_QUAVER.newInstance();
        return calculateInsertion(line, graceNote, atIndex, layout, lyricRenderMetrics)
            .fitsWithinLine(staffRightMarginSs);
    }

    /**
     * The whole batch is projected in one solve, so the verdict accounts for the slides' cumulative
     * demand; asking about each candidate separately would accept N slides that each fit alone but
     * jointly overrun the line.
     *
     * @param lyricRenderMetrics null spaces the line as if it had no lyrics
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
     * Pure measurement: neither the line nor the projected elements are mutated. The verdict is
     * {@link #calculateModification}'s compress-to-fit solve, so a modification this accepts always
     * lays out.
     *
     * @param projectedElements  The line's effective elements as they will read after the
     *                           modification, in element order: detached stand-ins at the changed
     *                           positions and the live elements everywhere else
     * @param lyricRenderMetrics null spaces the line as if it had no lyrics
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

    private static double projectedWidthWithLastShiftSs(
        Line line, double baseWidthSs, double shiftSs, LayoutResult layout) {

        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount == 0) {
            return baseWidthSs;
        }

        var lastElement = line.getElement(effectiveCount - 1);
        var lastColumn = ElementColumnBuilder.buildLightweightColumn(lastElement);
        lastColumn.setXSs(layout.getElementXSs(lastElement) + shiftSs);

        return Math.max(baseWidthSs, lastColumn.getRightEdgeXSs());
    }
}
