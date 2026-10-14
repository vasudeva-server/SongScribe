/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.selection;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.score.LineComponent;

/**
 * Cleans up after a rubber-band drag that Swing failed to finish.
 * <p>
 * During a fast drag Swing sometimes never delivers mouseReleased to the {@link LineComponent}
 * that clears the rectangle, so a global AWT listener catches the release wherever it lands.
 * It never changes the selection.
 */
public final class SelectionDragTracker {

    /** The LineComponent that currently has an active rubber-band drag, or null. */
    @Nullable
    private LineComponent draggingLine = null;

    private final AWTEventListener globalMouseReleasedListener = event -> {
        if (event instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_RELEASED) {
            if (draggingLine != null) {
                if (draggingLine.isDraggingSelection()) {
                    draggingLine.clearSelectionBand();
                }

                draggingLine = null;
            }

            Toolkit.getDefaultToolkit().removeAWTEventListener(this.globalMouseReleasedListener);
        }
    };

    SelectionDragTracker() {}

    public void dragDidStart(LineComponent lineComponent) {
        draggingLine = lineComponent;
        Toolkit.getDefaultToolkit().addAWTEventListener(
            globalMouseReleasedListener, AWTEvent.MOUSE_EVENT_MASK
        );
    }
}
