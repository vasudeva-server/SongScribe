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

package songscribe.smufl;

/**
 * The closed set of noteheads the application draws.
 *
 * <p>Only {@link StemmedNotehead} carries stem attachment points, which is what lets
 * {@link SMuFLMetadata#stemAnchors} be total over that narrower type instead of answering
 * "none" for {@link WholeNotehead}.
 */
public sealed interface Notehead permits StemmedNotehead, WholeNotehead {

    /** @return The glyph this notehead is drawn with. */
    SMuFLGlyph glyph();
}
