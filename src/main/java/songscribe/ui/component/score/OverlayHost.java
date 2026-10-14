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

package songscribe.ui.component.score;

import javax.swing.JComponent;

/**
 * A container that hosts {@link LineOverlayComponent}s as free-floating, absolute-bounds
 * children.
 * <p>
 * Overlays refer to their host through this interface, not by concrete type, so no overlay has
 * to change when the host moves from {@code ScoreView} to {@code PageComponent} under {@code
 * specs/184-pagination.md} §4.
 * <p>
 * Implementors must be {@link JComponent}s and must return {@code false} from
 * {@link JComponent#isOptimizedDrawingEnabled()} — see {@code ScoreView.isOptimizedDrawingEnabled}.
 */
public interface OverlayHost {

    void addOverlay(JComponent overlay);

    void removeOverlay(JComponent overlay);

    /** Implementors return {@code this}. */
    JComponent getHostComponent();
}
