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
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;
import songscribe.util.Copyable;

/**
 * The controller family behind every {@link AttachmentDialog}, and the two gestures that open one:
 * {@link #edit} for a click on an attachment, and the {@code editXxxOnSelection} methods for a
 * menu action. This is where the document meets the dialog, which holds no element, line or
 * score.
 *
 * <p><strong>Sealed</strong>, so a fourth attachment controller has to be named here.
 *
 * <p><strong>One value type, not two.</strong> {@link #read()} answers {@code @Nullable T}, null
 * when the element carries none yet; {@link #commit(Object)} writes a non-null {@code T}. Stating
 * the type once is what makes {@link #dataWasModified} a real comparison.
 *
 * @param <T> the attachment's value type
 */
public abstract sealed class AttachmentDialogController<T extends Copyable<T>>
    extends DialogController<@Nullable T, T>
    permits AnnotationController, BeatChangeController, TempoChangeController {

    public enum AttachmentOp { ADD, CHANGE, REMOVE }

    private final AttachmentTarget target;

    protected AttachmentDialogController(AttachmentTarget target) {
        this.target = target;
    }

    protected final StaffElement element() {
        return target.element();
    }

    protected abstract String opLabel(AttachmentOp op);

    protected abstract ElementField elementField();

    /**
     * Does nothing when there is no attachment.
     *
     * <p>Called only from the removal {@link #removal()} returns, inside an open modification
     * bracket — never call it directly, or the change goes unrecorded and Undo cannot reverse it.
     */
    protected abstract void removeAttachment();

    /**
     * The element's index is resolved when this is called, so the recorded mutation names the
     * element's current position.
     */
    protected final void modifyTarget(AttachmentOp op, Runnable mutator) {
        var line = target.line();
        var elementIndex = target.elementIndex();
        var field = elementField();

        line.getSong().withModification(opLabel(op), () -> line.modifyElement(elementIndex, field, mutator));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Answered by comparing {@code values} with the attachment's current value, which the
     * family's single type parameter makes a real comparison rather than one between unrelated
     * types.
     *
     * <p>An element carrying nothing yet reads {@code null}, which no gathered value equals, so an
     * Add always writes.
     *
     * <p>Without this the commit would reach {@link Line#modifyElement}, which records
     * unconditionally: an unchanged OK would add an undo step and dirty the document. For a
     * beat-defining attachment it would also re-validate every tuplet from the element forward
     * under {@code STRICT}, which can drop one that a lenient file load accepted.
     *
     * @implSpec Compares with {@code equals}, which every value type in this family defines.
     */
    @Override
    protected boolean dataWasModified(T values) {
        return !values.equals(read());
    }

    protected final AttachmentOp commitOp() {
        return read() == null ? AttachmentOp.ADD : AttachmentOp.CHANGE;
    }

    /**
     * Final, so no subclass can remove an attachment outside a bracket. {@code null} when the
     * element carries no change of this controller's kind.
     */
    @Override
    protected final @Nullable Runnable removal() {
        return read() == null ? null : () -> modifyTarget(AttachmentOp.REMOVE, this::removeAttachment);
    }

    /**
     * Answers false for a fermata and a dynamic, which have no editable properties, and when
     * {@code attachment}'s owner is not an element of {@code line}, or it has none. In every
     * false case the caller leaves the attachment selected and does nothing else.
     */
    public static boolean edit(MainFrame mainFrame, Attachment attachment, Line line) {
        var target = AttachmentTarget.forElement(line, attachment.owner());

        if (target == null) {
            return false;
        }

        // Exhaustive by sealing: a new attachment kind fails to compile here rather than
        // silently answering "not editable".
        var dialog = switch (attachment) {
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

    public static void editAnnotationOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::annotationDialog);
    }

    public static void editBeatChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::beatChangeDialog);
    }

    public static void editTempoChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::tempoChangeDialog);
    }

    /**
     * Does nothing when the selection resolves to nothing; every caller carries
     * {@code REQUIRES_SINGLE_SELECTION}.
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
     * @return {@code null} when the selection is not a single element of the active line
     */
    private static @Nullable AttachmentTarget selectionTarget(MainFrame mainFrame) {
        var scoreView = mainFrame.requireScoreView();
        var activeLineIndex = scoreView.getSelectionCoordinator().getActiveLineIndex();

        if (activeLineIndex == null) {
            return null;
        }

        var line = scoreView.getSong().getLine(activeLineIndex);

        return AttachmentTarget.forElement(line, scoreView.getSingleSelectedElement());
    }

    private static AnnotationDialog annotationDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new AnnotationDialog(mainFrame, new AnnotationController(target).ops());
    }

    private static BeatChangeDialog beatChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new BeatChangeDialog(mainFrame, new BeatChangeController(target).ops());
    }

    private static TempoChangeDialog tempoChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new TempoChangeDialog(mainFrame, new TempoChangeController(target).ops());
    }
}
