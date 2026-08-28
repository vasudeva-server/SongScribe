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

package songscribe.font;

import songscribe.layout.LyricRenderMetrics;

/**
 * Visual layout metrics for a lyric box, all in staff-space units.
 *
 * @param advanceSs     cursor-advance width (the layout width used for column placement
 *                      and centering math)
 * @param leftBearingSs offset from the advance origin to the leftmost painted pixel;
 *                      typically 0 or slightly negative for glyphs that overhang to
 *                      the left of their advance origin
 * @param rightExtentSs offset from the advance origin to the rightmost painted pixel;
 *                      may exceed {@code advanceSs} for glyphs that overhang past their
 *                      advance width
 */
public record LyricBoxMetrics(double advanceSs, double leftBearingSs, double rightExtentSs) {
    public static final LyricBoxMetrics EMPTY = new LyricBoxMetrics(0.0, 0.0, 0.0);
}
