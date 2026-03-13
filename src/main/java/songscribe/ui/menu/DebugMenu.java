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

package songscribe.ui.menu;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.*;

import songscribe.ui.component.Score;

/**
 * Debug menu for Chrome DevTools-style inspector.
 * Only shown when DEBUG environment variable is set.
 */
public class DebugMenu extends JMenu {

    private final Score score;

    public DebugMenu(Score score) {
        super("Debug");
        this.score = score;

        var inspectorItem = new JCheckBoxMenuItem("Enable Inspector");
        inspectorItem.setAccelerator(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_I,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | InputEvent.SHIFT_DOWN_MASK
            )
        );
        inspectorItem.addActionListener(e -> {
            DebugState.setInspectorEnabled(inspectorItem.isSelected());
            score.repaint();
        });
        add(inspectorItem);
    }
}
