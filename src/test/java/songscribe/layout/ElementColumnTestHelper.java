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

import java.util.Collections;

import songscribe.dom.StaffElement;

/**
 * Builds positioned {@link ElementColumn}s for tests outside this package.
 * <p>
 * A column's X is assigned by the spacing calculator through a package-private setter, so a test
 * in another package cannot place a column on its own. Tests within {@code songscribe.layout}
 * have their own local helpers and do not need this one.
 */
public final class ElementColumnTestHelper {

    /** Left extent for a column whose left edge sits exactly at its X. */
    private static final double NO_LEFT_EXTENT_SS = 0.0;

    /** Right extent for a column whose right edge sits exactly at its X (a zero-width point). */
    private static final double NO_RIGHT_EXTENT_SS = 0.0;

    private ElementColumnTestHelper() {
    }

    /** Returns a bare column holding {@code element} at {@code xSs}, with no extents. */
    public static ElementColumn columnAt(StaffElement element, double xSs) {
        return columnAt(element, xSs, NO_LEFT_EXTENT_SS);
    }

    /**
     * Returns a column holding {@code element} at {@code xSs} that reaches {@code leftExtentSs}
     * to the left of that position, as a column with a leading accidental does.
     * <p>
     * Left extents are negative, so the resulting left edge is {@code xSs + leftExtentSs}. Pass a
     * non-zero extent to tell the left edge apart from the column's other edges, which all
     * collapse onto {@code xSs} when every extent is zero.
     * <p>
     * Delegates to {@link #columnAt(StaffElement, double, double, double)} with a zero right
     * extent, which preserves this overload's original zero-width-point behavior.
     */
    public static ElementColumn columnAt(StaffElement element, double xSs, double leftExtentSs) {
        return columnAt(element, xSs, leftExtentSs, NO_RIGHT_EXTENT_SS);
    }

    /**
     * Returns a column holding {@code element} at {@code xSs} that reaches {@code leftExtentSs}
     * to the left of that position and {@code rightExtentSs} to the right, so tests can build a
     * column with real width instead of the zero-width point the other overload returns.
     * <p>
     * Left extents are negative, right extents are non-negative — the resulting bounds are
     * {@code [xSs + leftExtentSs, xSs + rightExtentSs]}.
     */
    public static ElementColumn columnAt(
        StaffElement element, double xSs, double leftExtentSs, double rightExtentSs) {

        var column = new ElementColumn(
            element, Collections.emptyList(), leftExtentSs, rightExtentSs,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }
}
