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

package songscribe.message.notification;

import songscribe.message.Message;
import songscribe.ui.component.score.LineComponent;

/**
 * Posted when a rubber-band selection drag begins on a line.
 * The SelectionCoordinator uses this to track the dragging line
 * so it can clean up if Swing fails to deliver mouseReleased.
 */
public class SelectionDragDidStartNotification extends Message {

    private final LineComponent lineComponent;

    public SelectionDragDidStartNotification(LineComponent lineComponent) {
        this.lineComponent = lineComponent;
    }

    public LineComponent getLineComponent() {
        return lineComponent;
    }
}
