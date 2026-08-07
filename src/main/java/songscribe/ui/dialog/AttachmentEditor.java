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

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.TempoChangeAttachment;
import songscribe.ui.component.MainFrame;

/**
 * Maps an attachment to the dialog that edits it.
 *
 * <p>This is a class rather than a private method on {@code LineComponent} so the five-way
 * mapping can be tested without Swing, and so the routing in {@code mouseClicked} has a static
 * to observe — the call to {@link #edit} is that gesture's only observable effect.
 */
public final class AttachmentEditor {

    private AttachmentEditor() {}

    /**
     * Opens the dialog that edits {@code attachment}, pre-bound to its owner element, and
     * returns whether a dialog was opened.
     *
     * <p>Returns false for a fermata and a dynamic because neither has editable properties, and
     * false when there is no element in {@code line} to bind the dialog to; in every false case
     * the caller leaves the attachment selected and does nothing else.
     */
    public static boolean edit(MainFrame mainFrame, Attachment attachment, Line line) {
        var element = attachment.getOwnerElement();

        // AttachmentDialog.setData calls line.getElementIndex(element) and feeds the answer
        // straight to line.modifyElement(...), so an element that is not in this line would
        // reach modifyElement(-1, ...) on OK — a hard failure at the far end of a modal dialog
        // rather than a no-op at the gesture. The per-line hit registry makes that unreachable
        // today; this guard is what keeps it unreachable if the registry ever stops being
        // per-line.
        if (element == null || line.getElementIndex(element) < 0) {
            return false;
        }

        // Exhaustive by sealing: a new attachment kind fails to compile here rather than
        // silently answering "not editable".
        @Nullable AttachmentDialog<?> dialog = switch (attachment) {
            case AnnotationAttachment ignored -> new AnnotationDialog(mainFrame);
            case BeatChangeAttachment ignored -> new BeatChangeDialog(mainFrame);
            case TempoChangeAttachment ignored -> new TempoChangeDialog(mainFrame);
            case FermataAttachment ignored -> null;
            case DynamicAttachment ignored -> null;
        };

        if (dialog == null) {
            return false;
        }

        dialog.showFor(element, line);
        return true;
    }
}
