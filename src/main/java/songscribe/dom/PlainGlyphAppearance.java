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

import songscribe.smufl.SMuFLGlyph;

/**
 * A glyph that stands alone, with nothing an editor can add to it — the breath mark. An
 * element of this appearance is a {@link BreathMarkElement}, which has no dot count to hold.
 *
 * @param glyph  the glyph the element is drawn with
 * @param seatSp the staff position the glyph is drawn at and hit-tested at, in staff-position
 *               units from the middle line
 */
public record PlainGlyphAppearance(SMuFLGlyph glyph, int seatSp) implements GlyphAppearance {}
