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
 * Each attachment has:
 * <ul>
 *   <li>An owner element it attaches to</li>
 *   <li>Horizontal alignment relative to the element (LEFT, CENTER, RIGHT)</li>
 * </ul>
 * <p>
 * Vertical placement is determined by the layout calculator, not stored as a property.
 */
public abstract sealed class Attachment extends LineElement
    permits FermataAttachment, DynamicAttachment, AnnotationAttachment, MetronomeAttachment {

    /**
     * Horizontal alignment of the attachment relative to the owner element.
     */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /** The staff element this attachment belongs to. */
    private @Nullable StaffElement ownerElement;

    /** Horizontal alignment relative to the owner element. */
    private Alignment alignment = Alignment.CENTER;

    /**
     * Returns the staff element this attachment belongs to.
     */
    public @Nullable StaffElement getOwnerElement() {
        return ownerElement;
    }

    /**
     * Sets the staff element this attachment belongs to.
     */
    public void setOwnerElement(@Nullable StaffElement ownerElement) {
        this.ownerElement = ownerElement;
    }

    /**
     * Returns the horizontal alignment relative to the note.
     */
    public Alignment getAlignment() {
        return alignment;
    }

    /**
     * Sets the horizontal alignment relative to the note.
     */
    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    /**
     * Returns a deep copy of this attachment, re-owned by {@code newOwner}, carrying over all
     * {@link LineElement}-level user state (offsets, margins, position) and the alignment.
     *
     * <p>This is the single place that state is copied, so a new subclass cannot forget it — the
     * same arrangement {@link Span#copy} uses. Subclasses carry their own state in
     * {@link #createCopy}. Does not copy the children a composite attachment holds, and does not
     * set {@code parentLine}: {@link StaffElement#addAttachment} owns both, and every caller
     * reaches it immediately.
     *
     * @param newOwner the staff element the copy attaches to
     * @return an attachment of the same concrete type as this one, sharing no mutable state with
     *         it
     */
    public final Attachment copy(StaffElement newOwner) {
        var copy = createCopy(newOwner);

        copy.setAlignment(getAlignment());
        copy.setUserXOffsetSs(getUserXOffsetSs());
        copy.setUserYOffsetSs(getUserYOffsetSs());
        copy.setMarginSs(getMarginTopSs(), getMarginRightSs(), getMarginBottomSs(), getMarginLeftSs());
        copy.setPosition(getPositionSs());

        return copy;
    }

    /**
     * Creates a new instance of this attachment's concrete subclass, owned by {@code newOwner} and
     * carrying over any subclass-specific state.
     *
     * <p>Called only by {@link #copy}, which layers on the shared {@link LineElement}-level state
     * afterwards. An implementation states its own value and nothing else.
     *
     * @param newOwner the staff element the copy attaches to
     * @return a new attachment of this concrete type
     */
    protected abstract Attachment createCopy(StaffElement newOwner);
}
