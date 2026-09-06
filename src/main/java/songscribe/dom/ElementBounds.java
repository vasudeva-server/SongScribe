/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

/**
 * The eight measurements of an element type in staff spaces, computed once from the type's
 * appearance and never changed.
 *
 * @param fullWidthSs         the element width including a stemmed note's flag extent
 * @param baseWidthSs         the right edge of the element's own glyph, measured from the origin
 * @param fullElementHeightSs the element height excluding a note's stem
 * @param noteheadTopOffsetSs the Y offset from the element center to the top of its glyph,
 *                            negative
 * @param heightUpSs          the element height with a stem-up
 * @param heightDownSs        the element height with a stem-down
 * @param topOffsetUpSs       the Y offset from the element center to the top of a stem-up element
 * @param topOffsetDownSs     the Y offset from the element center to the top of a stem-down
 *                            element
 */
record ElementBounds(
    double fullWidthSs,
    double baseWidthSs,
    double fullElementHeightSs,
    double noteheadTopOffsetSs,
    double heightUpSs,
    double heightDownSs,
    double topOffsetUpSs,
    double topOffsetDownSs
) {
    /**
     * Bounds for an element with no stem: the same in both stem directions, and the glyph's
     * width is the element's width.
     *
     * @param glyphSize   the glyph's width from the origin to its right edge, and its height
     * @param topOffsetSs the Y offset from the element center to the top of the glyph, negative
     * @return bounds whose full and base widths are both the glyph's width, and whose height and
     *     top offset are the glyph's for either stem direction
     */
    static ElementBounds symmetric(Size glyphSize, double topOffsetSs) {
        var widthSs = glyphSize.widthSs();
        var heightSs = glyphSize.heightSs();

        return new ElementBounds(
            widthSs, widthSs, heightSs, topOffsetSs, heightSs, heightSs, topOffsetSs, topOffsetSs);
    }
}
