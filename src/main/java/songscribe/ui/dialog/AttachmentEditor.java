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

import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.TempoChangeAttachment;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.backend.AnnotationBackEnd;
import songscribe.ui.dialog.backend.AttachmentTarget;
import songscribe.ui.dialog.backend.BeatChangeBackEnd;
import songscribe.ui.dialog.backend.TempoChangeBackEnd;

/**
 * Opens the dialog that edits an attachment, bound to the element it sits on.
 *
 * <p><strong>This is where the document meets the dialog.</strong> An {@link AttachmentDialog}
 * holds no element, no line and no score; it holds an {@link AttachmentBackEnd} that already
 * does. Binding one is this class's whole job, and doing it in one place is what keeps every
 * dialog in the family free of the score — see {@link DialogBackEnd} for why the binding sits on
 * the caller's side rather than inside the dialog.
 *
 * <p>Two ways in, because there are two gestures. Clicking an attachment names its element
 * directly, and {@link #edit} takes it. Invoking a menu action names nothing, so the
 * {@code editXxxOnSelection} methods resolve the element from the score's current selection.
 * Both end at the same bound dialog.
 *
 * <p>It is a class rather than a private method on {@code LineComponent} so the mapping can be
 * tested without Swing, and so the routing in {@code mouseClicked} has a static to observe — the
 * call to {@link #edit} is that gesture's only observable effect.
 */
public final class AttachmentEditor {

    private AttachmentEditor() {}

    /**
     * Opens the dialog that edits {@code attachment}, bound to its owner element.
     *
     * <p>Answers false for a fermata and a dynamic, because neither has editable properties, and
     * false when {@code attachment}'s owner is not an element of {@code line} — including when it
     * has no owner at all. In every false case the caller leaves the attachment selected and does
     * nothing else. The per-line hit registry makes the mismatched-owner case unreachable today;
     * the check is what keeps it unreachable if the registry ever stops being per-line.
     *
     * @param mainFrame  the window the dialog parents itself to
     * @param attachment the attachment the user acted on
     * @param line       the line whose hit registry produced {@code attachment}
     * @return {@code true} when a dialog was opened
     */
    public static boolean edit(MainFrame mainFrame, Attachment attachment, Line line) {
        var target = AttachmentTarget.forElement(line, attachment.getOwnerElement());

        if (target == null) {
            return false;
        }

        // Exhaustive by sealing: a new attachment kind fails to compile here rather than
        // silently answering "not editable".
        @Nullable AttachmentDialog<?> dialog = switch (attachment) {
            case AnnotationAttachment ignored -> annotationDialog(mainFrame, target);
            case BeatChangeAttachment ignored -> beatChangeDialog(mainFrame, target);
            case TempoChangeAttachment ignored -> tempoChangeDialog(mainFrame, target);
            case FermataAttachment ignored -> null;
            case DynamicAttachment ignored -> null;
        };

        if (dialog == null) {
            return false;
        }

        dialog.setVisible(true);
        return true;
    }

    /**
     * Opens the annotation dialog on the score's current selection, or does nothing when there is
     * no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editAnnotationOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentEditor::annotationDialog);
    }

    /**
     * Opens the beat-change dialog on the score's current selection, or does nothing when there is
     * no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editBeatChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentEditor::beatChangeDialog);
    }

    /**
     * Opens the tempo-change dialog on the score's current selection, or does nothing when there
     * is no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editTempoChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentEditor::tempoChangeDialog);
    }

    /**
     * Resolves the selection and opens the dialog {@code dialogFactory} builds for it.
     *
     * <p>Doing nothing when the selection resolves to nothing is deliberate and unreachable in
     * practice: every action that calls this carries {@code REQUIRES_SINGLE_SELECTION} and is
     * disabled without one. It replaces a dialog that used to open against a null element and then
     * fail at OK, which is the worse of the two silences.
     */
    private static void openOnSelection(
        MainFrame mainFrame,
        BiFunction<MainFrame, AttachmentTarget, AttachmentDialog<?>> dialogFactory
    ) {
        var target = selectionTarget(mainFrame);

        if (target == null) {
            return;
        }

        dialogFactory.apply(mainFrame, target).setVisible(true);
    }

    /**
     * @return the selected element and its line, or {@code null} when the selection is not a
     *         single element of the active line — the state a click on a notation object leaves
     *         behind, where {@code getSingleSelectedElement} answers null
     */
    private static @Nullable AttachmentTarget selectionTarget(MainFrame mainFrame) {
        var scoreView = mainFrame.requireScoreView();
        var line = scoreView.getSong().getLine(scoreView.getSelectionCoordinator().getActiveLineIndex());

        return AttachmentTarget.forElement(line, scoreView.getSingleSelectedElement());
    }

    private static AnnotationDialog annotationDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new AnnotationDialog(mainFrame, new AnnotationBackEnd(target));
    }

    private static BeatChangeDialog beatChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new BeatChangeDialog(mainFrame, new BeatChangeBackEnd(target));
    }

    private static TempoChangeDialog tempoChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new TempoChangeDialog(mainFrame, new TempoChangeBackEnd(target));
    }
}
