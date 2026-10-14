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
import songscribe.ui.binding.Widgets;
import songscribe.ui.component.MainFrame;
import songscribe.util.Copyable;
import songscribe.util.UIUtils;

/**
 * A dialog with OK and Cancel, and a Remove button when its operations offer one. A subclass
 * writes only {@link #populate} and {@link #gather}.
 *
 * <p><strong>The OK lifecycle, in full:</strong> the focused field's {@link InputVerifier} is
 * consulted, then the controls are read exactly once, then those values are validated, then — only
 * if validation accepted them — they are committed and the window is dismissed. <strong>The values
 * validated are the values committed.</strong>
 *
 * <p><strong>Nothing is committed when validation refuses.</strong> The dialog stays up with its
 * controls as the user left them. The user is shown the <strong>first</strong> failure only.
 *
 * <p>Cancel dismisses the window without either step: nothing this dialog does to the outside
 * world happens anywhere but on the OK path. Remove neither gathers nor validates.
 *
 * <p><strong>Cancel and Remove decline the focused field's {@link InputVerifier}</strong>:
 * Swing runs it in the middle of the mouse press, and a verifier that puts a modal alert up there
 * takes the release with it, so the button's action never runs. A dismissing button outside this
 * class exempts itself the same way.
 *
 * <p><strong>Always modal</strong>, because Cancel promises that nothing happened. A dialog that
 * applies each edit as it is made extends {@link BaseDialog} directly.
 *
 * <p><strong>A commit becomes visible through the notification it posts.</strong> A commit that
 * reaches the screen some other way arranges that itself.
 *
 * @param <I> what to show — already a copy, so a subclass may edit it in place without reaching
 *            the document. Its bound permits {@code @Nullable} itself, for a family like
 *            {@code AttachmentDialog} whose input is absent on Add
 * @param <O> what the controls say, gathered on OK. Its bound permits {@code @Nullable} itself,
 *            for a dialog that gathers nothing and names {@code @Nullable Void}
 */
public abstract class StandardDialog<I extends @Nullable Copyable<I>, O extends @Nullable Object> extends BaseDialog {

    protected JPanel buttonPanel = new JPanel(
        new FlowLayout(FlowLayout.RIGHT, FlatLafProps.getInt(FlatLafKey.DIALOG_BUTTON_HORIZONTAL_GAP), 0)
    );
    protected final JButton okButton;
    protected final JButton cancelButton;
    private final DialogOps<? extends I, ? super O> ops;
    private boolean buttonPanelAttached = false;

    protected StandardDialog(MainFrame mainFrame, String title, DialogOps<? extends I, ? super O> ops) {
        super(mainFrame, title, Modality.MODAL);
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

        // OK is available exactly when the dialog's values may be committed, so a rule a
        // tab states with requireValid is one the user sees the moment they break it —
        // rather than one they are told about after pressing OK, with the commit already
        // done. A dialog that states no rule is always valid and its OK never disables.
        bindings().bind(Widgets.enabled(okButton), valid);

        cancelButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_CANCEL));
        cancelButton.addActionListener(_ -> setVisible(false));
        dismissWithoutVerifying(cancelButton);

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
            dismissWithoutVerifying(removeButton);
            buttonPanel.add(removeButton, 0);
        }
    }

    private static void dismissWithoutVerifying(JButton button) {
        button.setVerifyInputWhenFocusTarget(false);
    }

    @Override
    protected JButton getDefaultButton() {
        return okButton;
    }

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
     * Final: reading what to show is not a decision any dialog makes for itself. The values are
     * read once per opening, from the document as it stands at that moment.
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
     * An implementation writes to controls, and decides what the buttons say.
     *
     * @param values a copy belonging to this dialog, so retaining or editing it reaches nothing
     *               outside the window
     */
    protected abstract void populate(I values);

    /**
     * Reads only. It is total over every state the controls can reach through the UI: whether the
     * values are acceptable is {@link DialogOps#validate()}'s question. Called exactly once per
     * OK, before validation.
     */
    protected abstract O gather();

    /**
     * Private, with no hook beside it: the order of the steps, and that all of them see the same
     * values, are this class's promises.
     *
     * <p><strong>"Did anything change?" comes first</strong>, so a rule about a change the
     * notator never made cannot refuse to let them out of the dialog. See
     * {@link DialogController#dataWasModified}.
     */
    private boolean commitOnOk() {
        var values = gather();

        if (!ops.wasModified().test(values)) {
            return true;
        }

        var result = ops.validate().apply(values);

        if (!result.isValid()) {
            showFailure(result.failures().getFirst());
            return false;
        }

        ops.commit().accept(values);
        return true;
    }

    /**
     * Both OK's refusals and a refusal a control reports before OK is pressed come through here,
     * so the user cannot be told two different things about one mistake. Shows exactly one alert;
     * choosing which failure is the OK path's decision.
     *
     * @param failure resolved to text here and nowhere earlier
     */
    protected final void showFailure(ValidationFailure failure) {
        var message = failure.message();
        OptionDialogs.showErrorMessage(
            contentPanel, failure.titleKey(), message.key(), resolveArgs(message)
        );
    }

    /**
     * Nested messages carry no arguments of their own, so one level is the whole of it.
     */
    private static Object[] resolveArgs(LocalizedMessage message) {
        return message
            .args()
            .stream()
            .map(arg -> arg instanceof LocalizedMessage nested ? Strings.get(nested.key()) : arg)
            .toArray();
    }
}
