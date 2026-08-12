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

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * A dialog with OK and Cancel buttons.
 *
 * <p><strong>The OK lifecycle, in full:</strong> the focused field's {@link InputVerifier} is
 * consulted, then {@link #commitOnOk()} runs, then the window is dismissed — and it is dismissed
 * only if both of those accepted. Cancel dismisses it without either, which is what makes Cancel
 * "discard everything": nothing this dialog can do to the outside world happens anywhere but in
 * {@link #commitOnOk()}.
 *
 * <p><strong>A commit becomes visible through the notification it posts</strong>, not through
 * anything this class does. A commit that writes the document does so inside a modification
 * bracket, and the bracket's {@code SongDidChangeNotification} is what re-lays out and repaints the
 * score — see {@code docs/mutations.md}. A commit that reaches the screen some other way arranges
 * that itself.
 *
 * <p>A dialog whose OK commits values read from its controls extends {@link CommitDialog} rather
 * than overriding anything here.
 */
public abstract class StandardDialog extends BaseDialog {

    protected JPanel buttonPanel = new JPanel(
        new FlowLayout(FlowLayout.RIGHT, FlatLafProps.getInt(FlatLafKey.DIALOG_BUTTON_HORIZONTAL_GAP), 0)
    );
    protected final JButton okButton;
    protected final JButton cancelButton;
    private boolean buttonPanelAttached = false;

    protected StandardDialog(MainFrame mainFrame, String title) {
        this(mainFrame, title, true);
    }

    protected StandardDialog(MainFrame mainFrame, String title, boolean isModal) {
        this(mainFrame, title, isModal, DialogCategory.OPERATIONAL);
    }

    protected StandardDialog(MainFrame mainFrame, String title, boolean isModal, DialogCategory category) {
        super(mainFrame, title, isModal, category);

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
     * Performs whatever OK does beyond dismissing the dialog, and says whether it may be dismissed.
     *
     * <p>Runs after the focused field's {@link InputVerifier} has accepted its value and before the
     * window is dismissed; it is the whole of the OK lifecycle between those two points, so a
     * dialog has nowhere else to put work that OK is supposed to do. Answering false leaves the
     * dialog up with its controls untouched, and whoever answers false has already told the user
     * why — nothing after this point will.
     *
     * @return {@code true} to dismiss the dialog, {@code false} to leave it up
     * @implSpec Commits nothing and answers true: on a dialog that gathers no values, OK means no
     *           more than "close". {@link CommitDialog} overrides this with the
     *           gather-validate-commit lifecycle and is final there, so a dialog that commits
     *           values extends that class rather than overriding this.
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean commitOnOk() {
        return true;
    }
}
