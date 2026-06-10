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

package songscribe.dom;

import songscribe.font.FontKey;

/**
 * One rendered line of attribution text: the display text and the font role
 * it should be rendered in.
 * <p>
 * {@link FontKey#ATTRIBUTION} lines carry the main credit (Words/Music/…);
 * {@link FontKey#SUB_ATTRIBUTION} lines carry the date and place.
 */
public record AttributionLine(String text, FontKey font) {}
