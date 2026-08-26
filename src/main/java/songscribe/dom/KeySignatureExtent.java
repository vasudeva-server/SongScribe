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

import java.util.List;

/**
 * What a key signature draws, and how much room it takes.
 *
 * <p>A key signature's extent is never a property of the key alone: changing to G major draws one
 * sharp coming out of C major and a cancelling natural plus that sharp coming out of D major. The
 * pair of keys is therefore the smallest thing that can answer either question, and this type is
 * that pair — so the accidentals drawn and the column reserved for them are read off one value and
 * cannot disagree.
 *
 * <p>Every key signature in the program is one of these. A mid-line key signature
 * ({@link KeyChangeElement#extent()}) reads its previous key off the line it sits on; the
 * cautionary at the end of a line ({@code CautionaryKeySignature}) reads it off the key the line
 * leaves in. See {@code docs/key-changes.md} for the cancellation policy the accidentals follow.
 *
 * @param previousKey the key in effect immediately before the change — the one whose accidentals
 *                    the cancellation policy may call for naturals against
 * @param newKey      the key taking effect
 */
public record KeySignatureExtent(Key previousKey, Key newKey) {

    /**
     * Returns the accidentals this key signature draws, in the order they are laid out: the
     * cancelling naturals the policy calls for, if any, followed by the new key's own accidentals.
     *
     * @return the accidentals to draw, left to right; empty exactly when {@code newKey} equals
     *     {@code previousKey}, because re-stating the key already in effect draws nothing
     */
    public List<Key.DrawnAccidental> accidentals() {
        return newKey.accidentalsFrom(previousKey);
    }

    /**
     * Returns how much horizontal room the drawn accidentals take, excluding any padding a caller
     * puts either side of them.
     *
     * @return the width in staff spaces; zero exactly when {@link #accidentals()} is empty, and
     *     never negative
     */
    public double widthSs() {
        return newKey.widthSsFrom(previousKey);
    }
}
