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

import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.DisplayList;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.RecordingGraphics2D;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Shared base for the two slide-tool hover previews — the trailing {@link SlideZone#FALL} glyph
 * and the connecting {@link SlideZone#GLISSANDO} line — shown while a slide tool is active and
 * the mouse sits in a valid zone to the right of a pitched note.
 * <p>
 * A fall is a fixed-size glyph anchored to a single source note; a glissando spans two resolved
 * note endpoints and varies in length and angle. They stay separate subclasses so that
 * distinction remains visible in the type hierarchy, but the visibility gate — which of the two,
 * if either, may be shown at all — is written once here rather than twice.
 * {@link PreviewElementManager#shouldShowSlidePreviewOn} guarantees the two are mutually
 * exclusive: only one {@link SlideZone} is ever current at a time.
 *
 * <h2>Record once, replay many</h2>
 * Like {@link PreviewElementOverlay}, the ink is not composed by hand: the real
 * {@code SlideRenderer} preview method runs against a {@link RecordingGraphics2D} and the result
 * is kept as a {@link DisplayList}. Unlike the note-head preview, the recorded ink is already
 * positioned in the target line's own coordinate space — both endpoints are resolved note
 * positions, not a floating mouse-relative origin — so no translate is applied at paint time.
 */
public abstract class SlidePreviewOverlay extends LineOverlayComponent {

    /**
     * Reused across rebuilds: recording allocates neither the scratch image nor its delegate, and
     * the glyph-vector cache inside survives {@link RecordingGraphics2D#reset()}.
     */
    private final RecordingGraphics2D recorder = new RecordingGraphics2D();

    /** The ink the renderer last produced, in the target line's own coordinate space. */
    private DisplayList displayList = DisplayList.EMPTY;

    /** True when {@link #displayList} no longer describes the current slide-preview state. */
    private boolean displayListIsStale = true;

    protected SlidePreviewOverlay(OverlayHost host) {
        super(host);
    }

    /** The {@link SlideZone} this overlay draws for — the zone its subclass answers to. */
    protected abstract SlideZone getZone();

    /**
     * Runs the real {@code SlideRenderer} preview method against {@code g2} for the source note
     * at {@code sourceIndex} on {@code domLine}. Implementations must not set a color: the
     * recorded ink is monochrome and {@link #renderOverlay} applies the preview color once.
     */
    protected abstract void recordSlide(
        Graphics2D g2, Line domLine, int sourceIndex, LineInvariants invariants);

    /**
     * Rebuilds this overlay's ink from the current tracking state and re-anchors it to
     * {@code line}, or hides it when {@code line} is null. Call whenever the slide-preview state
     * — the tracked line, index, or zone — may have changed; unlike the note-head preview, a
     * slide preview's endpoints are resolved note positions rather than a continuously-tracked
     * mouse position, so every state change is a full rebuild rather than a mere translate.
     */
    void previewDidChange(@Nullable LineComponent line) {
        displayListIsStale = true;
        setTargetLine(line);
        inkDidChange();
    }

    @Override
    public void retarget() {
        // The line components were recreated, so both the target and the recorded ink refer to a
        // layout that no longer exists.
        displayListIsStale = true;
        setTargetLine(PreviewElementManager.getCurrentInsertionLine());

        // Not merely setTargetLine: when the manager still points at the same component the
        // setter returns early, and the unchanged-position early-out in updateBounds would then
        // keep the ink recorded against the discarded layout.
        inkDidChange();
    }

    @Override
    protected @Nullable Rectangle2D getInkBoundsSs() {
        var line = getTargetLine();

        if (line == null || !PreviewElementManager.shouldShowSlidePreviewOn(line, getZone())) {
            return null;
        }

        var domLine = line.getLine();
        var invariants = line.previewInvariants();

        if (domLine == null || invariants == null) {
            return null;
        }

        if (displayListIsStale) {
            var sourceIndex = PreviewElementManager.getCurrentXIndex() - 1;

            recorder.reset();
            GraphicUtils.setRenderingHints(recorder);
            recordSlide(recorder, domLine, sourceIndex, invariants);
            displayList = recorder.displayList();
            displayListIsStale = false;
        }

        return displayList.inkBoundsSs();
    }

    @Override
    protected void renderOverlay(Graphics2D g2) {
        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            // The renderer deliberately never sets a color, so the recorded ink is monochrome and
            // the preview color is applied once, here.
            g2.setColor(ScoreView.getPreviewElementColor());
            displayList.replay(g2);
        }
    }
}
