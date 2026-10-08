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
 * One articulation marking on a note, owned by that {@link NoteElement}. Its only state of its
 * own is its {@link ArticulationType}, which decides what it draws and how much room it takes.
 * <p>
 * <b>The owner and the line.</b> The note owns it: {@link NoteElement#addArticulation} sets
 * {@link #owner()} and {@link NoteElement#removeArticulation} and
 * {@link NoteElement#clearArticulations} clear it, and nothing else writes it. An articulation's
 * line is its note's line, derived on every ask rather than stored, so it follows the note into
 * and out of a line with nothing to keep in step.
 */
public final class Articulation extends LineElement implements LineContent, IntrinsicHeight {

    private final ArticulationType type;

    /** Written only by {@link NoteElement}'s add and remove methods. */
    private @Nullable NoteElement owner;

    /**
     * Creates an articulation of the given kind, not yet on any note.
     *
     * @param type the articulation kind
     */
    public Articulation(ArticulationType type) {
        this.type = type;
    }

    /**
     * Returns a copy of this articulation, on no note yet, carrying its type and its
     * {@link LineElement}-level state. {@link NoteElement#copyArticulationsFrom} reaches it, and
     * gives the copy its note.
     * <p>
     * The class is final so this is the whole copy: there is no subclass whose state could be
     * left behind, and the base state travels through {@link LineElement#copyBaseStateFrom}
     * rather than being restated here.
     *
     * @return an articulation sharing no mutable state with this one
     */
    public Articulation copy() {
        var copy = new Articulation(type);
        copy.copyBaseStateFrom(this);

        return copy;
    }

    /**
     * Returns the note this articulation is on.
     *
     * @return the owning note, or {@code null} while it is on none — before
     *         {@link NoteElement#addArticulation} and after {@link NoteElement#removeArticulation}
     */
    public @Nullable NoteElement owner() {
        return owner;
    }

    /**
     * Sets the note this articulation is on. Package-private so that only
     * {@link NoteElement}'s add and remove methods, which keep the note's articulation list in
     * step, can write it.
     */
    void setOwner(@Nullable NoteElement owner) {
        this.owner = owner;
    }

    /**
     * Returns the line this articulation is in, which is its note's.
     *
     * @return the note's line, or {@code null} when the articulation is on no note or its note
     *     is in no line
     */
    public @Nullable Line getParentLine() {
        return StaffElement.parentLineOf(owner);
    }

    /**
     * Returns what kind of articulation this is.
     *
     * @return the type; fixed for the articulation's life
     */
    public ArticulationType getType() {
        return type;
    }

    /**
     * Returns the width of this articulation's ink, which is what the stacker reserves for it.
     *
     * @return {@link ArticulationType#intrinsicWidthSs()} for this articulation's type, in
     *     staff spaces
     */
    public double intrinsicWidthSs() {
        return type.intrinsicWidthSs();
    }

    @Override
    public double intrinsicHeightSs() {
        return type.intrinsicHeightSs();
    }
}
