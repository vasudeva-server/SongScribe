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

package songscribe.dom;

/**
 * A span whose two endpoints are always notes: a tie joins two notes, and a beam runs from one
 * note to another.
 *
 * <p>This is the one place the endpoint narrowing {@link Span} describes is written. The
 * constructor admits only notes, {@link #acceptsEndpoint} refuses anything else on the base
 * setters and {@link Span#copy}, and the two covariant getters are therefore the only casts in
 * the narrowing — every reader of a tie's or a beam's endpoint gets a {@link NoteElement}
 * without re-proving it.
 */
public abstract sealed class NoteSpan extends Span permits Tie, Beam {

    /**
     * Creates a span reaching from one note to another.
     *
     * @param anchorElement The first note in the range
     * @param endElement    The last note in the range
     */
    protected NoteSpan(NoteElement anchorElement, NoteElement endElement) {
        super(anchorElement, endElement);
    }

    /** Returns the first note in this range. */
    @Override
    public NoteElement getAnchorElement() {
        return (NoteElement) super.getAnchorElement();
    }

    /** Returns the last note in this range. */
    @Override
    public NoteElement getEndElement() {
        return (NoteElement) super.getEndElement();
    }

    /** Admits a note and nothing else. */
    @Override
    protected final boolean acceptsEndpoint(StaffElement element) {
        return element instanceof NoteElement;
    }

    /**
     * {@link Span#copy} has already put both elements past {@link #acceptsEndpoint}, so they
     * are notes; this hands them on as such.
     */
    @Override
    protected final Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return createNoteCopy((NoteElement) newAnchor, (NoteElement) newEnd);
    }

    /**
     * The note-typed form of {@link Span#createCopy}: creates a new instance of this span's
     * concrete subclass between the given notes, carrying over any subclass-specific state.
     *
     * @param newAnchor The anchor note for the copy
     * @param newEnd    The end note for the copy
     */
    protected abstract NoteSpan createNoteCopy(NoteElement newAnchor, NoteElement newEnd);
}
