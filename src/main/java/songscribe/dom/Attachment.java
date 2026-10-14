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
 * Only {@link StaffElement}'s add and remove methods write {@link #owner()}. The line is its
 * owner's line, derived on every ask. Vertical placement belongs to layout, not to the attachment.
 */
public abstract sealed class Attachment extends LineElement implements LineContent
    permits FermataAttachment, DynamicAttachment, AnnotationAttachment, MetronomeAttachment {

    private @Nullable StaffElement owner;

    /**
     * @return {@code null} while it is on no element
     */
    public @Nullable StaffElement owner() {
        return owner;
    }

    /**
     * Package-private so that only {@link StaffElement}'s add and remove methods, which keep the
     * owner's attachment list in step, write it.
     */
    void setOwner(@Nullable StaffElement owner) {
        this.owner = owner;
    }

    /**
     * @return {@code null} when it is on no element or its owner is in no line
     */
    public @Nullable Line getParentLine() {
        return StaffElement.parentLineOf(owner);
    }

    /**
     * Does not set the owner: {@link StaffElement#addAttachment} does.
     */
    public final Attachment copy() {
        var copy = createCopy();

        copy.copyBaseStateFrom(this);

        return copy;
    }

    /**
     * An implementation copies its own state only; {@link #copy} layers the shared state on.
     */
    protected abstract Attachment createCopy();
}
