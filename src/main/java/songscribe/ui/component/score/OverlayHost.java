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
 * Overlays refer to their host through this interface rather than by concrete type: the host
 * is {@code ScoreView} today and becomes {@code PageComponent} under {@code
 * specs/184-pagination.md} §4, and no overlay should have to change when that lands. An overlay
 * computes its bounds in the host's coordinate space, so the host is also the ancestor it
 * converts against.
 * <p>
 * Implementors must be {@link JComponent}s and must return {@code false} from
 * {@link JComponent#isOptimizedDrawingEnabled()} — see {@code ScoreView.isOptimizedDrawingEnabled}.
 */
public interface OverlayHost {

    /**
     * Adds {@code overlay} as a free-floating absolute-bounds child, positioned in this host's
     * z-order according to the host's overlay z-order contract.
     */
    void addOverlay(JComponent overlay);

    /** Removes a previously added overlay and repaints the region it occupied. */
    void removeOverlay(JComponent overlay);

    /**
     * Returns this host as a component, for coordinate conversion and hierarchy tests.
     * Implementors return {@code this}.
     */
    JComponent getHostComponent();
}
