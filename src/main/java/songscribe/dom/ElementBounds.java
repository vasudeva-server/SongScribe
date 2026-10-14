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
    static ElementBounds symmetric(Size glyphSize, double topOffsetSs) {
        var widthSs = glyphSize.widthSs();
        var heightSs = glyphSize.heightSs();

        return new ElementBounds(
            widthSs, widthSs, heightSs, topOffsetSs, heightSs, heightSs, topOffsetSs, topOffsetSs);
    }
}
