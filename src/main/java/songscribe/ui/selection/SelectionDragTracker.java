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
 * A drag paints a rectangle on the {@link LineComponent} it started on, and that component
 * clears it on mouseReleased. During a fast drag Swing sometimes never delivers that release
 * to it, leaving the rectangle painted over a drag that is long over. So the drag is recorded
 * here and a global AWT listener catches the release wherever it lands.
 * <p>
 * Nothing about the selection itself: what the drag selected is assigned through
 * {@link SelectionCoordinator} as the drag runs, and a release caught here changes none of it.
 */
public final class SelectionDragTracker {

    /**
     * The LineComponent that currently has an active rubber-band drag, or null.
     * Tracked so the global AWTEventListener can clean up if Swing fails to
     * deliver mouseReleased to the originating LineComponent.
     */
    @Nullable
    private LineComponent draggingLine = null;

    /**
     * Global AWT listener that catches mouseReleased events which Swing sometimes
     * fails to deliver to a LineComponent during fast rubber-band drags.
     * Registered only while a drag is active and removed once the release is caught.
     */
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

    /**
     * Called by {@link LineComponent} when a rubber-band drag begins,
     * so the tracker can install the safety-net AWTEventListener.
     */
    public void dragDidStart(LineComponent lineComponent) {
        draggingLine = lineComponent;
        Toolkit.getDefaultToolkit().addAWTEventListener(
            globalMouseReleasedListener, AWTEvent.MOUSE_EVENT_MASK
        );
    }

    /**
     * Returns the LineComponent that currently has an active rubber-band drag, or null.
     * Package-private for tests that verify drag-cleanup semantics.
     */
    @Nullable
    LineComponent getDraggingLine() {
        return draggingLine;
    }

    AWTEventListener getGlobalMouseReleasedListener() {
        return globalMouseReleasedListener;
    }
}
