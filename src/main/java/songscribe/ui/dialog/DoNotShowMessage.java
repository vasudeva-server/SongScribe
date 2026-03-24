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

import java.util.prefs.Preferences;

public class DoNotShowMessage extends StandardDialog {

    private final JCheckBox dontShowCheck = new JCheckBox(
        "Don’t show this message again."
    );
    private final String propName;

    public DoNotShowMessage(
        String title,
        String info,
        String propName
    ) {
        super(title, true, DialogCategory.INFORMATIONAL);
        this.propName = propName;
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        contentPanel.add(BorderLayout.NORTH, new JLabel(info));
        contentPanel.add(BorderLayout.CENTER, dontShowCheck);
        buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            if (
                !Preferences.userRoot().node("songscribe").getBoolean(propName, false)
            ) {
                super.setVisible(true);
            }
        } else {
            super.setVisible(false);
        }
    }

    @Override
    protected boolean getData() { return true; }

    @Override
    protected void setData() {
        if (dontShowCheck.isSelected()) {
            Preferences.userRoot().node("songscribe").putBoolean(propName, true);
        }
    }
}
