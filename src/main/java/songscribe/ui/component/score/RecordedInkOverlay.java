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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.DisplayList;
import songscribe.ui.renderer.RecordingGraphics2D;
import songscribe.util.GraphicsState;

/**
 * Shared base for the preview overlays that obtain their ink by <b>recording the real renderers</b>
 * rather than composing it by hand: the hover preview element and the two slide previews.
 * <p>
 * Each runs its renderers against a {@link RecordingGraphics2D} and keeps the result as a
 * {@link DisplayList}, so bounds are the union of that list and painting is a replay of it — the
 * two can never disagree. What differs between subclasses is only <em>what</em> gets recorded and
 * whether a translate is applied on top; the record/replay bookkeeping is identical, so it lives
 * here.
 *
 * <h2>Staleness</h2>
 * Recording is expensive relative to how often bounds are queried — dragging the mouse along a
 * line re-queries on every insertion-slot crossing while the glyphs the renderers would emit stay
 * put. Subclasses therefore record only while {@link #inkIsStale()}, and mark the ink stale via
 * {@link #markInkStale()} when something the renderers read has changed.
 */
public abstract class RecordedInkOverlay extends LineOverlayComponent {

    /**
     * Reused across rebuilds: recording allocates neither the scratch image nor its delegate, and
     * the glyph-vector cache inside survives {@link RecordingGraphics2D#reset()}.
     */
    protected final RecordingGraphics2D recorder = new RecordingGraphics2D();

    /** The ink the renderers last produced. */
    private DisplayList displayList = DisplayList.EMPTY;

    /**
     * The union of {@link #displayList}'s ink, cached alongside it.
     * <p>
     * A pure function of a list that is queried far more often than it is rebuilt, and computing
     * it walks every drawable — so it is derived once when the list is installed rather than on
     * each bounds query.
     */
    @Nullable
    private Rectangle2D displayListInkSs;

    /** True when {@link #displayList} no longer describes the current preview state. */
    private boolean displayListIsStale = true;

    protected RecordedInkOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Rebuilds this overlay's ink from the current state and re-anchors it to {@code line}, or
     * hides it when {@code line} is null. Call whenever anything the renderers read has changed.
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

    /** Marks the recorded ink as no longer describing the current state. */
    protected final void markInkStale() {
        displayListIsStale = true;
    }

    /** Whether the recorded ink must be rebuilt before its bounds can be trusted. */
    protected final boolean inkIsStale() {
        return displayListIsStale;
    }

    /** Installs freshly recorded ink, caching its union bounds and clearing the stale flag. */
    protected final void setDisplayList(DisplayList newDisplayList) {
        displayList = newDisplayList;
        displayListInkSs = newDisplayList.inkBoundsSs();
        displayListIsStale = false;
    }

    /**
     * The union of the recorded ink, in the coordinate space it was recorded in, or null when
     * nothing was recorded — which for an overlay means there is nothing to show.
     */
    protected final @Nullable Rectangle2D getDisplayListInkSs() {
        return displayListInkSs;
    }

    /** Replays the recorded ink in {@code g2}'s current color. */
    protected final void replayInk(Graphics2D g2) {
        displayList.replay(g2);
    }

    /**
     * Replays the recorded ink in the preview color. The default for every subclass whose ink is
     * already in the target line's own coordinate space; {@link PreviewElementOverlay}, whose ink
     * is relative to the preview element's origin, overrides to add the translate.
     */
    @Override
    protected void renderOverlay(Graphics2D g2) {
        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            // The renderers deliberately never set a color, so the recorded ink is monochrome and
            // the preview color is applied once, here.
            g2.setColor(ScoreView.getPreviewElementColor());
            replayInk(g2);
        }
    }
}
