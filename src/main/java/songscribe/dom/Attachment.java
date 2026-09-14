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
 * Abstract base class for elements that attach to staff elements.
 * <p>
 * Attachments include tempo markings, fermatas, annotations, dynamics, and beat changes.
 * The element owns it: {@link StaffElement#addAttachment} makes the element its parent
 * and {@link StaffElement#removeAttachment} detaches it, so {@link #owner()} is answered from
 * the parent-element pointer every {@link LineElement} carries rather than from a pointer of
 * its own.
 * <p>
 * Vertical placement is determined by the layout calculator, not stored as a property.
 */
public abstract sealed class Attachment extends LineElement
    permits FermataAttachment, DynamicAttachment, AnnotationAttachment, MetronomeAttachment {

    /**
     * Returns the staff element this attachment is on.
     *
     * @return the owning element, or {@code null} while it is on none — before
     *         {@link StaffElement#addAttachment} and after {@link StaffElement#removeAttachment}
     */
    public @Nullable StaffElement owner() {
        return (StaffElement) getParentElement();
    }

    /**
     * Returns a deep copy of this attachment, on no element yet, carrying over all
     * {@link LineElement}-level user state (the two user offsets).
     *
     * <p>Base state travels through {@link LineElement#copyBaseStateFrom}, so a new subclass
     * cannot forget it — the same arrangement {@link Span#copy} uses. Subclasses carry their
     * own state in
     * {@link #createCopy}. Does not copy the children a composite attachment holds, and does not
     * set the parent element or line: {@link StaffElement#addAttachment} owns both, and every
     * caller reaches it immediately.
     *
     * @return an attachment of the same concrete type as this one, sharing no mutable state with
     *         it
     */
    public final Attachment copy() {
        var copy = createCopy();

        copy.copyBaseStateFrom(this);

        return copy;
    }

    /**
     * Creates a new instance of this attachment's concrete subclass, carrying over any
     * subclass-specific state.
     *
     * <p>Called only by {@link #copy}, which layers on the shared {@link LineElement}-level state
     * afterwards. An implementation states its own value and nothing else.
     *
     * @return a new attachment of this concrete type
     */
    protected abstract Attachment createCopy();
}
