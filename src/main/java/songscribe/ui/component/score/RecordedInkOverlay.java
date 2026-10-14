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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.DisplayList;
import songscribe.ui.renderer.RecordingGraphics2D;
import songscribe.util.GraphicsState;

/**
 * Shared base for the preview overlays that obtain their ink by <b>recording the real renderers</b>
 * rather than composing it by hand: the hover preview element and the two slide previews.
 * <p>
 * Bounds are the union of the recorded {@link DisplayList} and painting is a replay of it, so
 * the two can never disagree.
 *
 * <h2>Staleness</h2>
 * Subclasses record only while {@link #inkIsStale()}, and mark the ink stale via
 * {@link #markInkStale()} when something the renderers read has changed.
 */
public abstract class RecordedInkOverlay extends LineOverlayComponent {

    protected final RecordingGraphics2D recorder = new RecordingGraphics2D();

    private DisplayList displayList = DisplayList.EMPTY;

    @Nullable
    private Rectangle2D displayListInkSs;

    private boolean displayListIsStale = true;

    protected RecordedInkOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Hides the overlay when {@code line} is null. Call whenever anything the renderers read has
     * changed.
     */
    void previewDidChange(@Nullable LineComponent line) {
        markInkStale();
        setTargetLine(line);
        inkDidChange();
    }

    @Override
    public void retarget() {
        // The line components were recreated, so both the target and the recorded ink refer to a
        // layout that no longer exists.
        markInkStale();
        setTargetLine(PreviewElementManager.getCurrentInsertionLine());

        // Not merely setTargetLine: when the manager still points at the same component the
        // setter returns early, and the unchanged-position early-out in updateBounds would then
        // keep the ink recorded against the discarded layout.
        inkDidChange();
    }

    protected final void markInkStale() {
        displayListIsStale = true;
    }

    protected final boolean inkIsStale() {
        return displayListIsStale;
    }

    protected final void setDisplayList(DisplayList newDisplayList) {
        displayList = newDisplayList;
        displayListInkSs = newDisplayList.inkBoundsSs();
        displayListIsStale = false;
    }

    /** Null when nothing was recorded — which for an overlay means there is nothing to show. */
    protected final @Nullable Rectangle2D getDisplayListInkSs() {
        return displayListInkSs;
    }

    protected final void replayInk(Graphics2D g2) {
        displayList.replay(g2);
    }

    @Override
    protected void renderOverlay(Graphics2D g2) {
        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            // The renderers deliberately never set a color, so the recorded ink is monochrome and
            // the preview color is applied once, here.
            g2.setColor(ScoreView.getPreviewElementColor());
            replayInk(g2);
        }
    }
}
