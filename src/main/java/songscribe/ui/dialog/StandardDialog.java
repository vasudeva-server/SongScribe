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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * A dialog with OK and Cancel, and a Remove button when its operations offer one.
 *
 * <p><strong>A widget shell over {@code I → O}.</strong> It is handed what to show, it shows it, it
 * reads its controls back, and it hands the result to the operations it was constructed with. It
 * has no collaborator it can query: everything outside the window reaches it as a
 * {@link DialogOps}, and a subclass writes only {@link #populate} and {@link #gather}.
 *
 * <p><strong>The OK lifecycle, in full:</strong> the focused field's {@link InputVerifier} is
 * consulted, then the controls are read exactly once, then those values are validated, then — only
 * if validation accepted them — they are committed and the window is dismissed. <strong>The values
 * validated are the values committed</strong>, because there is one read and both steps are handed
 * its result; a dialog cannot read the controls a second time before committing, because it is not
 * the thing doing the committing.
 *
 * <p><strong>Nothing is committed when validation refuses.</strong> A refused OK leaves the
 * document untouched and the dialog up with its controls exactly as the user left them, so the
 * failure can be corrected and OK pressed again. The user is shown the <strong>first</strong>
 * failure only: stacking one modal alert per failure is worse than under-reporting, and
 * {@link ValidationResult} promises the failures arrive in the order they should be read.
 *
 * <p>Cancel dismisses the window without either step, which is what makes it "discard everything":
 * nothing this dialog can do to the outside world happens anywhere but on the OK path. Remove is
 * the third path, and neither gathers nor validates.
 *
 * <p><strong>Always modal.</strong> Cancel promises that nothing happened, and that promise is only
 * worth anything if nothing could have happened meanwhile. A dialog that applies each edit as it is
 * made has no OK/Cancel cycle to put a boundary across and extends {@link BaseDialog} directly —
 * {@code PreferencesDialog} is the one such window.
 *
 * <p><strong>A commit becomes visible through the notification it posts</strong>, not through
 * anything this class does. A commit that writes the document does so inside a modification
 * bracket, and the bracket's {@code SongDidChangeNotification} is what re-lays out and repaints the
 * score — see {@code docs/mutations.md}. A commit that reaches the screen some other way arranges
 * that itself.
 *
 * @param <I> what to show, supplied by {@link DialogOps#read()} on each opening. Its bound permits
 *            {@code @Nullable} itself, for a family like {@code AttachmentDialog} whose input is
 *            absent on Add
 * @param <O> what the controls say, gathered on OK. Values only — never a handle on the document
 */
public abstract class StandardDialog<I extends @Nullable Object, O> extends BaseDialog {

    protected JPanel buttonPanel = new JPanel(
        new FlowLayout(FlowLayout.RIGHT, FlatLafProps.getInt(FlatLafKey.DIALOG_BUTTON_HORIZONTAL_GAP), 0)
    );
    protected final JButton okButton;
    protected final JButton cancelButton;
    private final DialogOps<? extends I, ? super O> ops;
    private boolean buttonPanelAttached = false;

    protected StandardDialog(MainFrame mainFrame, String title, DialogOps<? extends I, ? super O> ops) {
        this(mainFrame, title, ops, DialogCategory.OPERATIONAL);
    }

    protected StandardDialog(MainFrame mainFrame, String title, DialogOps<? extends I, ? super O> ops, DialogCategory category) {
        super(mainFrame, title, true, category);
        this.ops = ops;

        okButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_OK));
        okButton.addActionListener(_ -> {
            // Pressing Enter fires the default button directly, without the focus
            // traversal that a mouse click performs, so the focused field's
            // InputVerifier is never consulted. Run it explicitly first.
            if (verifyFocusedField() && commitOnOk()) {
                setVisible(false);
            }
        });

        cancelButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_CANCEL));
        cancelButton.addActionListener(_ -> setVisible(false));

        buttonPanel.setBorder(UIUtils.spacingBorder(FlatLafKey.DIALOG_BUTTON_PANEL_PADDING));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        var remove = ops.remove();

        // Built only when there is something behind it, so there is no state in which Remove is
        // on screen and does nothing — and no dialog family has to hide it for itself.
        if (remove != null) {
            var removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
            removeButton.addActionListener(_ -> {
                remove.run();
                setVisible(false);
            });
            buttonPanel.add(removeButton, 0);
        }
    }

    @Override
    protected JButton getDefaultButton() {
        return okButton;
    }

    /**
     * Runs the {@link InputVerifier} of the currently focused field, as a
     * mouse click on OK would. Returns true if there is no focused field, the
     * field has no verifier, or the verifier yields focus; false if the
     * verifier rejects the value (in which case it has already shown the user
     * the reason and focus remains on the field).
     */
    private boolean verifyFocusedField() {
        var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (focusOwner instanceof JComponent component) {
            var verifier = component.getInputVerifier();

            if (verifier != null) {
                return verifier.shouldYieldFocus(component, okButton);
            }
        }

        return true;
    }

    @Override
    protected boolean hasButtons() {
        return true;
    }

    /**
     * Called once, on the first {@code setVisible(true)}, before the dialog is shown.
     * Subclasses may mutate {@code buttonPanel} in place or reassign the field.
     * The return value is the {@code BorderLayout} constraint used to attach the panel.
     */
    protected Object modifyButtonPanel() {
        return BorderLayout.SOUTH;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible && !buttonPanelAttached) {
            getButtonPanelContainer().add(buttonPanel, modifyButtonPanel());
            buttonPanelAttached = true;
        }

        super.setVisible(visible);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Final: reading what to show is not a decision any dialog makes for itself. After the
     * registered tabs have populated themselves, the values to show are read and handed to
     * {@link #populate} — once per opening, from the document as it stands at that moment, so a
     * dialog reached from a cached action never shows a document that has since been closed.
     *
     * @return {@code true} unless a registered tab declined to show, in which case
     *         {@link #populate} is not reached and nothing has been read
     */
    @Override
    protected final boolean getData() {
        if (!super.getData()) {
            return false;
        }

        populate(ops.read().get());
        return true;
    }

    /**
     * Sets the controls to show {@code values}.
     *
     * <p>Called once per opening, before the window appears. An implementation writes to controls
     * and does nothing else — including deciding what the buttons say, which is a presentation
     * decision made from {@code values} and belongs here rather than anywhere that can see the
     * document.
     *
     * @param values what to show, as {@link DialogOps#read()} just answered it
     */
    protected abstract void populate(I values);

    /**
     * Reads the controls and returns the values they currently describe.
     *
     * <p>Reads only: calling it leaves the controls untouched. It is total over every state the
     * controls can reach through the UI — there is no combination of selections for which an
     * implementation may decline to produce a value. Whether the values are acceptable is
     * {@link DialogOps#validate()}'s question, not this one's.
     *
     * <p>Called exactly once per OK, before validation.
     *
     * @return the values described by the controls as they now stand
     */
    protected abstract O gather();

    /**
     * Performs whatever OK does beyond dismissing the dialog, and says whether it may be dismissed.
     *
     * <p>Private, and there is no hook beside it: the order of the three steps, and the fact that
     * validate and commit see the same values, are this class's promises rather than each
     * dialog's. A dialog varies what OK does by varying {@link #gather} and the operations it was
     * constructed with, never by taking over this path.
     *
     * @return {@code true} once the commit has run, {@code false} when validation refused and the
     *         user has been told why
     */
    private boolean commitOnOk() {
        var values = gather();
        var result = ops.validate().apply(values);

        if (!result.isValid()) {
            showFailure(result.failures().getFirst());
            return false;
        }

        ops.commit().accept(values);
        return true;
    }

    /**
     * Tells the user why something was refused, as an error alert over this dialog.
     *
     * <p>OK's own refusals come through here, and so does any refusal a control reports before OK
     * is ever pressed — a field whose {@link java.awt.event.FocusListener} or
     * {@link InputVerifier} checks the same rule the controller will. Both routes present the same
     * {@link ValidationFailure}, so the user cannot be told two different things about one
     * mistake, and the rule stays stated in one place whichever way it is reached.
     *
     * <p>Shows exactly one alert. Where several failures were reported, choosing which is the OK
     * path's decision, not this method's.
     *
     * @param failure the reason to show, resolved to text here and nowhere earlier
     */
    protected final void showFailure(ValidationFailure failure) {
        var message = failure.message();
        OptionDialogs.showErrorMessage(
            contentPanel, failure.titleKey(), message.key(), resolveArgs(message)
        );
    }

    /**
     * Resolves the arguments a message's pattern needs, turning any that is itself a
     * {@link LocalizedMessage} into its text.
     *
     * <p>That nesting is how a failure names a user-facing word — a unit, a role — without
     * whoever decided the failure having resolved it. Nested messages carry no arguments of
     * their own, so one level is the whole of it.
     */
    private static Object[] resolveArgs(LocalizedMessage message) {
        return message
            .args()
            .stream()
            .map(arg -> arg instanceof LocalizedMessage nested ? Strings.get(nested.key()) : arg)
            .toArray();
    }
}
