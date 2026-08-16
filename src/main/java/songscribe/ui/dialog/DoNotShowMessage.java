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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.MainFrame;

/**
 * A message carrying a <em>Don't show this message again</em> checkbox, which appears until the
 * user ticks it and then never again.
 *
 * <p><strong>The suppression flag is a preference this dialog does not own.</strong> Which
 * preference it is arrives at construction, so one class serves every suppressible message. Its
 * meaning is fixed and is the caller's responsibility to match: {@code true} means <em>this
 * message has been suppressed</em>. A key meaning the opposite produces a dialog that starts
 * appearing exactly when it should stop.
 *
 * <p>The flag goes through {@link Prefs} rather than being stored by this class, which is what
 * makes a suppression reachable from outside it: {@code Prefs.resetAll()} clears it, and the write
 * posts {@code PrefsDidChangeNotification} like any other. This dialog itself offers no way to
 * unsuppress a message — the checkbox only ever suppresses.
 */
public class DoNotShowMessage extends StandardDialog<String, Boolean> {

    private final JLabel messageLabel = new JLabel();
    final JCheckBox dontShowCheck = new JCheckBox(
        "Don’t show this message again."
    );
    private final PrefsKey suppressionKey;

    /**
     * @param mainFrame      the window this dialog parents itself to
     * @param title          the window title
     * @param suppressionKey the boolean preference recording whether this message has already been
     *                       suppressed — read on every show, and the only thing OK ever writes
     * @param ops            this dialog's operations, carrying the message text as {@code I}
     */
    DoNotShowMessage(
        MainFrame mainFrame,
        String title,
        PrefsKey suppressionKey,
        DialogOps<String, Boolean> ops
    ) {
        super(mainFrame, title, ops, DialogCategory.INFORMATIONAL);
        this.suppressionKey = suppressionKey;
        contentPanel.add(BorderLayout.NORTH, messageLabel);
        contentPanel.add(BorderLayout.CENTER, dontShowCheck);
    }

    @Override
    protected Object modifyButtonPanel() {
        buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        return BorderLayout.SOUTH;
    }

    /**
     * Shows the dialog unless this message has already been suppressed, in which case the request
     * is dropped and nothing appears.
     *
     * <p>Dropped <em>silently</em>: the caller asked for a message to be delivered, not for a
     * window to open, and a suppressed message has been delivered as far as the user is concerned.
     * Hiding is never suppressed — a window already up always closes.
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible && Prefs.getBoolean(suppressionKey)) {
            return;
        }

        super.setVisible(visible);
    }

    @Override
    protected void populate(String message) {
        messageLabel.setText(message);
    }

    @Override
    protected Boolean gather() {
        return dontShowCheck.isSelected();
    }
}
