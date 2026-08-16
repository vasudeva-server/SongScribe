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

/**
 * The controller family behind every {@link AttachmentDialog}, and the two gestures that open one.
 *
 * <p><strong>This is where the document meets the dialog.</strong> An {@link AttachmentDialog}
 * holds no element, no line and no score; it holds a {@link DialogOps} built by one of this
 * class's subclasses, which already does. Binding a controller to the element it edits is this
 * class's static half's whole job, and doing it in one place is what keeps every dialog in the
 * family free of the score.
 *
 * <p>Two ways in, because there are two gestures. Clicking an attachment names its element
 * directly, and {@link #edit} takes it. Invoking a menu action names nothing, so the
 * {@code editXxxOnSelection} methods resolve the element from the score's current selection. Both
 * end at a controller bound to the resolved {@link AttachmentTarget}, and both hand the dialog
 * {@code controller.ops()}.
 *
 * <p>It carries these static entry points rather than leaving them a private method on
 * {@code LineComponent} because it holds real routing — the attachment-kind switch and the two
 * ways in above — and both gestures need it. It is <em>not</em> a class so that a test has a
 * static to observe; that was the reason its first version gave, and a production class shaped for
 * a test is what {@code plans/test-only-surface.md} bans everywhere else.
 *
 * @param <C> the attachment's value type — the change a bound subclass reads, commits and removes.
 *            A value, not the attachment node that holds it: an attachment belongs to the document
 *            graph and never crosses to the dialog
 */
public abstract class AttachmentDialogController<C> extends DialogController<@Nullable C, C> {

    /**
     * Which of the three commits is being performed, so each subclass can name the undo step with
     * its own noun — {@code Add Tempo Change} against {@code Add Annotation}.
     */
    public enum AttachmentOp { ADD, CHANGE, REMOVE }

    private final AttachmentTarget target;

    protected AttachmentDialogController(MainFrame mainFrame, AttachmentTarget target) {
        super(mainFrame);
        this.target = target;
    }

    /**
     * @return the element this controller edits, for a subclass to read its attachments from
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
     * @return the element field this controller's attachment occupies, which the recorded
     *         {@code ElementModification} names as the changed field
     */
    protected abstract ElementField elementField();

    /**
     * Takes this controller's attachment off the element, or does nothing when there is none.
     *
     * <p>Called only from the removal {@link #removal()} returns, from inside an open modification
     * bracket — never call it directly, or the change goes unrecorded and Undo cannot reverse it.
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
        return read() == null ? AttachmentOp.ADD : AttachmentOp.CHANGE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Final, so that no subclass can remove an attachment outside a bracket, and so that Remove
     * is offered exactly when there is something to take away: {@code null} when the element
     * carries no change of this controller's kind, otherwise a removal that wraps
     * {@link #removeAttachment()} in one modification bracket.
     */
    @Override
    protected final @Nullable Runnable removal() {
        return read() == null ? null : () -> modifyTarget(AttachmentOp.REMOVE, this::removeAttachment);
    }

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

    /**
     * Opens the annotation dialog on the score's current selection, or does nothing when there is
     * no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editAnnotationOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::annotationDialog);
    }

    /**
     * Opens the beat-change dialog on the score's current selection, or does nothing when there is
     * no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editBeatChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::beatChangeDialog);
    }

    /**
     * Opens the tempo-change dialog on the score's current selection, or does nothing when there
     * is no single selected element to attach to.
     *
     * @param mainFrame the window the dialog parents itself to
     */
    public static void editTempoChangeOnSelection(MainFrame mainFrame) {
        openOnSelection(mainFrame, AttachmentDialogController::tempoChangeDialog);
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
        return new AnnotationDialog(mainFrame, new AnnotationController(mainFrame, target).ops());
    }

    private static BeatChangeDialog beatChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new BeatChangeDialog(mainFrame, new BeatChangeController(mainFrame, target).ops());
    }

    private static TempoChangeDialog tempoChangeDialog(MainFrame mainFrame, AttachmentTarget target) {
        return new TempoChangeDialog(mainFrame, new TempoChangeController(mainFrame, target).ops());
    }
}
