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

package songscribe.engraving;

/**
 * A staff position that is diatonic and musically ordinary, but lies beyond the outermost
 * ledger line the renderer draws, so the staff has no position to put its note head at.
 *
 * <p>It is a type rather than a plain {@link IllegalArgumentException} so that a caller can
 * catch this one refusal without also swallowing an {@code IllegalArgumentException} that
 * means a bug. This is what {@link StaffPosition}'s canonical constructor throws.
 */
public final class PitchOutOfRangeException extends IllegalArgumentException {

    private final String detail;

    PitchOutOfRangeException(int value) {
        this("Staff position out of range: " + value
            + " lies beyond the outermost ledger line the staff draws");
    }

    private PitchOutOfRangeException(String detail) {
        super(detail);
        this.detail = detail;
    }

    /**
     * Returns why the position was refused, naming it.
     *
     * @return the reason; never null, unlike {@link #getMessage()}, so a caller can carry it
     *     into a document-level refusal without a null guard
     */
    public String detail() {
        return detail;
    }
}
