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
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.util.UIUtils;

/**
 * A dialog with OK and Cancel buttons that commits changes
 * only when the user clicks OK.
 */
public abstract class StandardDialog extends BaseDialog {

    protected JPanel buttonPanel = new JPanel(
        new FlowLayout(FlowLayout.RIGHT, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_BUTTON_HORIZONTAL_GAP), 0)
    );
    protected final JButton okButton;
    protected final JButton cancelButton;

    protected StandardDialog(String title) {
        this(title, true);
    }

    protected StandardDialog(String title, boolean isModal) {
        this(title, isModal, DialogCategory.OPERATIONAL);
    }

    protected StandardDialog(String title, boolean isModal, DialogCategory category) {
        super(title, isModal, category);

        okButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_OK));
        okButton.addActionListener(_ -> {
            if (!isValidData()) {
                return;
            }

            setData();
            repaintScore();
            setVisible(false);
        });

        cancelButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_CANCEL));
        cancelButton.addActionListener(_ -> setVisible(false));

        buttonPanel.setBorder(UIUtils.spacingBorder(FlatLafKeys.DIALOG_BUTTON_PANEL_PADDING));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
    }

    @Override
    protected JButton getDefaultButton() {
        return okButton;
    }

    @Override
    protected boolean hasButtons() {
        return true;
    }

    private void repaintScore() {
        var scoreView = getScoreView();

        if (scoreView != null) {
            scoreView.repaint();
        }
    }

    /**
     * Returns true if all registered tabs report valid data.
     * Subclasses may override to add dialog-level validation
     * (call {@code super.isValidData()} to run tab iteration).
     * Subclasses with no registered tabs may override without calling {@code super}.
     */
    protected boolean isValidData() {
        for (var tab : getTabs()) {
            if (!tab.isValidData()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Writes control values back to the model by iterating registered
     * tabs. Subclasses may override to add dialog-level commit logic
     * (call {@code super.setData()} to run tab iteration).
     * Subclasses with no registered tabs may override without calling {@code super}.
     */
    protected void setData() {
        for (var tab : getTabs()) {
            tab.setData();
        }
    }
}
