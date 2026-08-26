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
import songscribe.dom.ScaleContext;

/**
 * What a rubber-band drag on one line may select, and how far across the staff it may reach.
 * <p>
 * A drag asks two questions, and both are answered from here so they cannot disagree: how far a
 * band's edge may travel, and which columns a band covers. Stating the answers separately is what
 * lets a painted band lie over notation that then refuses to highlight, so neither limit nor
 * membership is derived anywhere else.
 * <p>
 * The song's auto-maintained terminal is excluded exactly once, by where {@link #of} stops
 * collecting columns (issue #713). Both answers follow from that one stopping point: the terminal
 * is not a member because it is not in the list, and the right-hand limit is where the list runs
 * out.
 * <p>
 * An instance is a snapshot of one layout. Build a new one per drag event rather than holding one
 * across a drag, since a line's content can change under a live gesture.
 *
 * @invariant the left limit never exceeds the right limit, because an instance exists only for a
 *            line carrying at least one selectable column, and every column of a line is laid out
 *            to the right of its header
 */
public final class SweepRange {

    /**
     * The gap a band's edge keeps from the staff header, so a sweep never touches it.
     * <p>
     * There is no matching gap on the right: the auto-maintained terminal is right-aligned with
     * the end of the staff, so stopping at its left ink edge already leaves no reachable staff
     * beyond it.
     * <p>
     * Document pixels rather than view pixels: the reachable range is then the same music at
     * every zoom, which is the whole reason a band's endpoints are held in staff spaces.
     */
    private static final double HEADER_GAP_PX = 1.0;

    private final List<ElementColumn> columns;
    private final double leftLimitSs;
    private final double rightLimitSs;

    /**
     * An inclusive run of element indices on the line a {@link SweepRange} was built from.
     *
     * @param begin the first index in the run
     * @param end   the last index in the run, never less than {@code begin}
     */
    public record ColumnRange(int begin, int end) {}

    private SweepRange(List<ElementColumn> columns, double leftLimitSs, double rightLimitSs) {
        this.columns = columns;
        this.leftLimitSs = leftLimitSs;
        this.rightLimitSs = rightLimitSs;
    }

    /**
     * The sweep range for {@code line} under {@code layoutResult}.
     * <p>
     * Refusing to build is how a stale layout is reported, and it is the only report: a caller
     * that gets {@code null} has no range to clamp against and no membership to test, so the
     * gesture does nothing this frame rather than proceeding on a substituted answer.
     *
     * @param line         the line a drag is sweeping
     * @param layoutResult the layout the drag is reading, which must be the one that laid
     *                     {@code line} out
     * @return the range, or {@code null} when the line holds nothing a drag could select — an
     *         empty line, or one holding nothing but the terminal — or when the layout no longer
     *         has a column for one of the line's elements, which means it went stale under a
     *         live gesture
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
        var leftLimitSs = headerRightEdgeSs + ScaleContext.pxToSs(HEADER_GAP_PX);

        return new SweepRange(columns, leftLimitSs, rightLimitSs);
    }

    /**
     * {@code xSs} brought inside the stretch of staff a band's edge may reach.
     * <p>
     * The limits are the <i>staff</i> rather than the music on it, so a drag past the last column
     * still reaches the bare staff beyond it. Both ends of a band go through this, so no press
     * position can put an edge somewhere a drag could not have taken it.
     *
     * @param xSs a line-local x, in staff spaces
     * @return {@code xSs}, or the nearer limit when it lies outside them
     */
    public double clamp(double xSs) {
        return Math.clamp(xSs, leftLimitSs, rightLimitSs);
    }

    /**
     * The columns this range holds that the interval {@code [leftSs, rightSs]} touches.
     * <p>
     * A column is touched the moment the interval reaches its ink, not once the interval encloses
     * the whole of it, and a column's ink runs from {@link ElementColumn#getLeftEdgeXSs} to
     * {@link ElementColumn#getRightEdgeXSs} — so a leading accidental and trailing augmentation
     * dots both count. Vertical position is not consulted, here or by any caller: a note four
     * ledger lines up, a rest and a barline are all answered for identically.
     * <p>
     * The answer is a run rather than a set because a selection is a run. Column extents are
     * ordered, so every column between the first and the last match is itself a match.
     *
     * @param leftSs  the interval's left end, in line-local staff spaces
     * @param rightSs the interval's right end, which must not be less than {@code leftSs}
     * @return the inclusive index range of touched columns, or {@code null} when the interval
     *         touches none — it lies wholly in a gap, or wholly off the music
     */
    public @Nullable ColumnRange overlapping(double leftSs, double rightSs) {
        var begin = -1;
        var end = -1;

        for (var elementIndex = 0; elementIndex < columns.size(); elementIndex++) {
            var column = columns.get(elementIndex);

            if (column.getLeftEdgeXSs() <= rightSs && column.getRightEdgeXSs() >= leftSs) {
                if (begin == -1) {
                    begin = elementIndex;
                }

                end = elementIndex;
            }
        }

        return begin == -1 ? null : new ColumnRange(begin, end);
    }

    /**
     * The rightmost x a band's edge may reach: the left ink edge of the auto-maintained
     * terminal's column, or the end of the staff when the line carries no terminal.
     *
     * @param sweepableCount the line's selectable column count, which is also the terminal's own
     *                       element index when the line carries one
     * @return the limit, or {@code null} when the line carries a terminal the layout has no
     *         column for
     */
    private static @Nullable Double rightLimitSs(
        Line line, LayoutResult layoutResult, int sweepableCount) {

        if (sweepableCount == line.elementCount()) {
            return line.getSong().getLineWidthSs();
        }

        var terminalColumn = layoutResult.getElementColumn(line.getElement(sweepableCount));
        return terminalColumn == null ? null : terminalColumn.getLeftEdgeXSs();
    }
}
