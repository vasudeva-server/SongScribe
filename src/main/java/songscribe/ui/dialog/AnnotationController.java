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
package songscribe.ui.dialog;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.AttachmentRemoval;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;

/**
 * Reads and writes the annotation on one element, for {@link AnnotationDialog}.
 *
 * <p>Nothing is refused, so {@link DialogController#validate} keeps its default: the UI
 * guarantees blank text never arrives.
 */
public final class AnnotationController extends AttachmentDialogController<Annotation> {

    public AnnotationController(AttachmentTarget target) {
        super(target);
    }

    @Override
    protected ElementField elementField() {
        return ElementField.ANNOTATION;
    }

    @Override
    protected String opLabel(AttachmentOp op) {
        return Strings.get(switch (op) {
            case ADD -> Strings.ACTION_EDIT_OP_ADD_ANNOTATION;
            case CHANGE -> Strings.ACTION_EDIT_OP_CHANGE_ANNOTATION;
            case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION;
        });
    }

    @Override
    protected @Nullable Annotation read() {
        var attachment = element().findAttachment(AnnotationAttachment.class);

        return attachment != null ? attachment.getAnnotation() : null;
    }

    /**
     * Removal is {@link #removal()}'s job; blank text is never read as a deletion request.
     */
    @Override
    protected void commit(Annotation change) {
        var element = element();
        var existing = element.findAttachment(AnnotationAttachment.class);

        modifyTarget(commitOp(), () -> {
            if (existing != null) {
                existing.setAnnotation(change);
            } else {
                element.addAttachment(new AnnotationAttachment(change));
            }
        });
    }

    @Override
    protected void removeAttachment() {
        AttachmentRemoval.removeAnnotation(element());
    }
}
