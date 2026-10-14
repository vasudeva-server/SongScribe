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
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * What a rubber-band drag on one line may select, and how far across the staff it may reach.
 * Both answers come from here so a painted band never lies over notation that refuses to
 * highlight; neither limit nor membership is derived anywhere else.
 * <p>
 * The song's auto-maintained terminal is excluded by where {@link #of} stops collecting columns.
 * <p>
 * An instance is a snapshot of one layout. Build a new one per drag event, since a line's content
 * can change under a live gesture.
 * <p>
 * The left limit never exceeds the right limit.
 */
public final class SweepRange {

    /**
     * Document pixels rather than view pixels, so the reachable range is the same music at every
     * zoom.
     */
    private static final double HEADER_GAP_PX = 1.0;

    private final List<ElementColumn> columns;
    private final double leftLimitSs;
    private final double rightLimitSs;

    private SweepRange(List<ElementColumn> columns, double leftLimitSs, double rightLimitSs) {
        this.columns = columns;
        this.leftLimitSs = leftLimitSs;
        this.rightLimitSs = rightLimitSs;
    }

    /**
     * @param layoutResult must be the one that laid {@code line} out
     * @return {@code null} when the line holds nothing a drag could select (empty, or only the
     *         terminal), or when the layout has no column for one of the line's elements because
     *         it went stale under a live gesture
     */
    public static @Nullable SweepRange of(Line line, LayoutResult layoutResult) {
        var sweepableCount = line.effectiveElementCount();

        if (sweepableCount == 0) {
            return null;
        }

        var columns = new ArrayList<ElementColumn>(sweepableCount);

        for (var elementIndex = 0; elementIndex < sweepableCount; elementIndex++) {
            var column = layoutResult.getElementColumn(line.getElement(elementIndex));

            if (column == null) {
                return null;
            }

            columns.add(column);
        }

        var rightLimitSs = rightLimitSs(line, layoutResult, sweepableCount);

        if (rightLimitSs == null) {
            return null;
        }

        var headerRightEdgeSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line);
        var leftLimitSs = headerRightEdgeSs + DocumentScale.pxToSs(HEADER_GAP_PX);

        return new SweepRange(columns, leftLimitSs, rightLimitSs);
    }

    /**
     * The limits are the <i>staff</i> rather than the music on it. Both ends of a band go through
     * this, so no press position can put an edge where a drag could not have taken it.
     */
    public double clamp(double xSs) {
        return Math.clamp(xSs, leftLimitSs, rightLimitSs);
    }

    /**
     * A column is touched the moment the interval reaches its ink, which includes a leading
     * accidental and trailing augmentation dots. Vertical position is not consulted.
     *
     * @param rightSs must not be less than {@code leftSs}
     * @return {@code null} when the interval touches no column
     */
    public @Nullable ElementRange overlapping(double leftSs, double rightSs) {
        @Nullable Integer begin = null;
        @Nullable Integer end = null;

        for (var elementIndex = 0; elementIndex < columns.size(); elementIndex++) {
            var column = columns.get(elementIndex);

            if (column.getLeftEdgeXSs() <= rightSs && column.getRightEdgeXSs() >= leftSs) {
                if (begin == null) {
                    begin = elementIndex;
                }

                end = elementIndex;
            }
        }

        return begin == null || end == null ? null : new ElementRange(begin, end);
    }

    /**
     * @return {@code null} when the line carries a terminal the layout has no column for
     */
    private static @Nullable Double rightLimitSs(
        Line line, LayoutResult layoutResult, int sweepableCount) {

        if (sweepableCount == line.elementCount()) {
            return line.getSong().getLineWidthSs().value();
        }

        var terminalColumn = layoutResult.getElementColumn(line.getElement(sweepableCount));
        return terminalColumn == null ? null : terminalColumn.getLeftEdgeXSs();
    }
}
