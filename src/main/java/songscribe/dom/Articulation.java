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

import org.jspecify.annotations.Nullable;

/**
 * One articulation marking on a note, owned by that {@link NoteElement}.
 * <p>
 * Only {@link NoteElement}'s add, remove and clear methods write {@link #owner()}. The line is
 * its note's line, derived on every ask.
 */
public final class Articulation extends LineElement implements LineContent, IntrinsicHeight {

    private final ArticulationType type;

    private @Nullable NoteElement owner;

    public Articulation(ArticulationType type) {
        this.type = type;
    }

    public Articulation copy() {
        var copy = new Articulation(type);
        copy.copyBaseStateFrom(this);

        return copy;
    }

    /**
     * @return {@code null} while it is on no note
     */
    public @Nullable NoteElement owner() {
        return owner;
    }

    /**
     * Package-private so that only {@link NoteElement}'s add and remove methods, which keep the
     * note's articulation list in step, write it.
     */
    void setOwner(@Nullable NoteElement owner) {
        this.owner = owner;
    }

    /**
     * @return {@code null} when it is on no note or its note is in no line
     */
    public @Nullable Line getParentLine() {
        return StaffElement.parentLineOf(owner);
    }

    public ArticulationType getType() {
        return type;
    }

    public double intrinsicWidthSs() {
        return type.intrinsicWidthSs();
    }

    @Override
    public double intrinsicHeightSs() {
        return type.intrinsicHeightSs();
    }
}
