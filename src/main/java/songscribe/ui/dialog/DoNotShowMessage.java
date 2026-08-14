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
public class DoNotShowMessage extends CommitDialog<Boolean> {

    final JCheckBox dontShowCheck = new JCheckBox(
        "Don’t show this message again."
    );
    private final PrefsKey suppressionKey;

    /**
     * @param mainFrame      the window this dialog parents itself to
     * @param title          the window title
     * @param info           the message text
     * @param suppressionKey the boolean preference recording whether this message has already been
     *                       suppressed — read on every show, and the only thing OK ever writes
     */
    public DoNotShowMessage(
        MainFrame mainFrame,
        String title,
        String info,
        PrefsKey suppressionKey
    ) {
        super(mainFrame, title, true, DialogCategory.INFORMATIONAL);
        this.suppressionKey = suppressionKey;
        contentPanel.add(BorderLayout.NORTH, new JLabel(info));
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
    protected Boolean gather() {
        return dontShowCheck.isSelected();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only ever writes {@code true}. Leaving the box clear means the message keeps appearing,
     * which is what the preference already says, so there is nothing to write back — and writing it
     * anyway would post a change notification for a preference that did not change.
     */
    @Override
    protected void commit(Boolean doNotShowAgain) {
        if (doNotShowAgain) {
            Prefs.put(suppressionKey, true);
        }
    }
}
