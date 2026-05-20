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

package songscribe.ui.renderer;

/**
 * Per-element rendering state, built fresh for each element a {@code LineRenderer}
 * visits (and shared for line-level passes via {@link #LINE_LEVEL}).
 * <p>
 * Together with {@code LineInvariants} this replaces the mutable per-element setters
 * that {@code ElementRenderContext} exposed: an immutable frame cannot carry stale
 * state from a previous element.
 *
 * @param currentElementIndex  index of the element being rendered within the line,
 *                             or {@code -1} during line-level passes
 * @param overrideElementXSs   exact X coordinate in staff spaces to use instead of the
 *                             laid-out position, or {@link Double#NaN} when no override applies
 * @param previewShiftFromIndex first element index (inclusive) to displace for the
 *                             grace-note insert preview, or {@code -1} when no shift applies
 * @param previewShiftSs       preview shift amount in staff spaces
 */
public record ElementFrame(
    int currentElementIndex,
    double overrideElementXSs,
    int previewShiftFromIndex,
    double previewShiftSs
) {

    /** Canonical frame for line-level passes: no element index, no override, no preview shift. */
    public static final ElementFrame LINE_LEVEL = new ElementFrame(-1, Double.NaN, -1, 0.0);

    /**
     * Returns a line-level frame (no element index, no override) carrying the given
     * grace-note insertion preview shift. {@code LineRenderer} threads this shift into
     * every per-element frame it builds for the paint.
     *
     * @param fromIndex first element index (inclusive) to displace
     * @param shiftSs   preview shift amount in staff spaces
     */
    public static ElementFrame lineLevelWithPreviewShift(int fromIndex, double shiftSs) {
        return new ElementFrame(
            LINE_LEVEL.currentElementIndex(),
            LINE_LEVEL.overrideElementXSs(),
            fromIndex,
            shiftSs
        );
    }

    /**
     * Returns a per-element frame for {@code elementIndex} with the given X override,
     * inheriting this frame's preview-shift pair. Pass {@link Double#NaN} for
     * {@code overrideXSs} when the element uses its laid-out position.
     *
     * @param elementIndex index of the element within the line, or {@code -1} for a line-level pass
     * @param overrideXSs  exact X in staff spaces, or {@link Double#NaN} for no override
     */
    public ElementFrame withElement(int elementIndex, double overrideXSs) {
        return new ElementFrame(elementIndex, overrideXSs, previewShiftFromIndex, previewShiftSs);
    }

    /** Returns whether an override element X is active (i.e. {@link #overrideElementXSs} is not NaN). */
    public boolean hasOverrideElementX() {
        return !Double.isNaN(overrideElementXSs);
    }

    /** Returns whether a preview shift is active (i.e. {@link #previewShiftFromIndex} is non-negative). */
    public boolean hasPreviewShift() {
        return previewShiftFromIndex >= 0;
    }
}
