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

package songscribe.ui.component;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import javax.swing.JToggleButton;

import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.component.toolbar.Toolbar;
import songscribe.util.UIUtils;

/**
 * A JToggleButton that does not deselect itself when clicked.
 */
public class StickyToggleButton
    extends JToggleButton
    implements ActionListener {

    // We have to keep track of the action because we don't want to bind to it
    private final SelectableUIAction action;

    public StickyToggleButton(SelectableUIAction action) {
        // We want to manually track changes to the action, but not bind to it
        // so that we can control when the action's actionPerformed method is called.
        this.action = action;
        action.addPropertyChangeListener(this::onActionPropertyChanged);
        UIUtils.initToolbarButton(this, Toolbar.BUTTON_DIMENSION);
        UIUtils.configureButtonFromAction(this, action);
        addActionListener(this);
    }

    private void onActionPropertyChanged(PropertyChangeEvent event) {
        UIUtils.configureButtonFromAction(this, action);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Once the button is selected, it should not be deselected.
        // When we get here, the button has already been toggled.
        if (!isSelected()) {
            setSelected(true);
        } else {
            action.setSelected(true);
            action.actionPerformed(e);
        }
    }
}
