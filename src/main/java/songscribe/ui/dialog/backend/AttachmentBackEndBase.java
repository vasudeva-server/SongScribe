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
package songscribe.ui.dialog.backend;

import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;
import songscribe.ui.dialog.AttachmentBackEnd;

/**
 * What every attachment back end does the same way: commit through exactly one modification
 * bracket on the target element, labelled for the operation being performed.
 *
 * <p>The bracket mechanics live here rather than in each subclass because getting them wrong is
 * invisible until someone presses Undo. A subclass supplies only the three things that genuinely
 * differ — which field the attachment occupies, what the operation is called in its own noun, and
 * what the mutation actually does — and cannot accidentally commit outside a bracket, because
 * {@link #modifyTarget} is the only route to the element that this class offers.
 *
 * @param <C> the attachment's value type, as {@link AttachmentBackEnd} defines it
 */
public abstract class AttachmentBackEndBase<C> implements AttachmentBackEnd<C> {

    /**
     * Which of the three commits is being performed, so each subclass can name the undo step with
     * its own noun — {@code Add Tempo Change} against {@code Add Annotation}.
     */
    public enum AttachmentOp { ADD, CHANGE, REMOVE }

    private final AttachmentTarget target;

    protected AttachmentBackEndBase(AttachmentTarget target) {
        this.target = target;
    }

    /**
     * @return the element this back end edits, for a subclass to read its attachments from
     */
    protected final StaffElement element() {
        return target.element();
    }

    /**
     * The undo-step name for {@code op}, in this attachment's own noun.
     *
     * @param op the operation about to be committed
     * @return the resolved label, as it will read in the Edit menu after {@code Undo}
     */
    protected abstract String opLabel(AttachmentOp op);

    /**
     * @return the element field this back end's attachment occupies, which the recorded
     *         {@code ElementModification} names as the changed field
     */
    protected abstract ElementField elementField();

    /**
     * Takes this back end's attachment off the element, or does nothing when there is none.
     *
     * <p>Called by {@link #remove()} from inside an open modification bracket — never call it
     * directly, or the change goes unrecorded and Undo cannot reverse it.
     */
    protected abstract void removeAttachment();

    /**
     * Runs {@code mutator} against the target element inside one modification bracket named for
     * {@code op}.
     *
     * <p>The element's index is resolved when this is called rather than at binding time, so the
     * recorded mutation always names the element's current position. Nesting is safe: a
     * {@code mutator} that opens further brackets still produces one notification and one undo
     * step, per {@code docs/mutations.md}.
     *
     * @param op      the operation being committed, which names the undo step
     * @param mutator the change to make to the element
     */
    protected final void modifyTarget(AttachmentOp op, Runnable mutator) {
        var line = target.line();
        var elementIndex = target.elementIndex();
        var field = elementField();

        line.withModification(opLabel(op), () -> line.modifyElement(elementIndex, field, mutator));
    }

    /**
     * The operation a commit of gathered values performs, read from the document as it stands.
     *
     * @return {@link AttachmentOp#ADD} when the element carries no change yet, otherwise
     *         {@link AttachmentOp#CHANGE}
     */
    protected final AttachmentOp commitOp() {
        return existingChange() == null ? AttachmentOp.ADD : AttachmentOp.CHANGE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Final, so that no subclass can remove an attachment outside a bracket: the removal itself
     * goes in {@link #removeAttachment()}, which this wraps.
     */
    @Override
    public final void remove() {
        modifyTarget(AttachmentOp.REMOVE, this::removeAttachment);
    }
}
