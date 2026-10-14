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

import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.text.DefaultCaret;

/**
 * Caret that hides itself whenever its text component has a non-empty selection,
 * matching native macOS {@code NSTextField} behavior. Swing's stock {@link DefaultCaret}
 * paints the caret on top of the selection highlight, which looks wrong.
 */
public class SelectionHidingCaret extends DefaultCaret {

    public SelectionHidingCaret() {
        setBlinkRate(500);
    }

    @Override
    public void paint(Graphics g) {
        if (isSelectionActive()) {
            return;
        }

        super.paint(g);
    }

    boolean isSelectionActive() {
        return getComponent() != null && getDot() != getMark();
    }

    @Override
    public void damage(Rectangle r) {
        if (isSelectionActive()) {
            return;
        }

        super.damage(r);
    }
}
