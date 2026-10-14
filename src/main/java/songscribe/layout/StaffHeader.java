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

import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.engraving.StaffHeaderMetrics;

/**
 * The header a staff line opens with: the treble clef and the key signature, each at the X the
 * layout placed it. The signature draws the line's {@linkplain Line#getRunningKey() running key}.
 * The header is not an element of the line: it takes part in no spacing and contributes nothing to
 * the staff's bounds.
 *
 * @param key {@link Key#NO_ACCIDENTALS} draws nothing and measures zero in both dimensions
 */
public record StaffHeader(double clefXSs, double keySignatureXSs, Key key) {}
