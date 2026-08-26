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

import java.awt.Insets;

import songscribe.dom.DocumentScale;

/** Insets in staff spaces: left, top, right, bottom. */
public record InsetsSs(double top, double left, double bottom, double right) {

    public Insets toInsetsPx() {
        return new Insets(
            DocumentScale.ssToRoundedPx(top),
            DocumentScale.ssToRoundedPx(left),
            DocumentScale.ssToRoundedPx(bottom),
            DocumentScale.ssToRoundedPx(right)
        );
    }
}
