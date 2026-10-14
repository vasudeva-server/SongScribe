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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.layout.LayoutResult;
import songscribe.ui.edit.EditModeManager;

/**
 * Owns the hover-preview overlays. Static like the rest of the preview subsystem's state: only
 * one line across all hosts can carry the preview at a time.
 */
final class PreviewOverlayRegistry {

    /** Null before a host has installed one (headless converters never do). */
    @Nullable
    private static PreviewElementOverlay overlay = null;

    /** Null before a host has installed one. */
    @Nullable
    private static GraceGlissandoPreviewOverlay graceGlissandoOverlay = null;

    private static boolean pendingRestore = false;

    private PreviewOverlayRegistry() {
    }

    /**
     * Exactly one of each exists for the host's lifetime; none is recreated, only retargeted.
     */
    static void install(OverlayHost host) {
        overlay = new PreviewElementOverlay(host);
        graceGlissandoOverlay = new GraceGlissandoPreviewOverlay(host);

        for (var installed : installed()) {
            host.addOverlay(installed);
        }
    }

    /**
     * Call whenever anything the renderers read has changed. Each overlay's own gate decides
     * whether it is visible, so the caller need not know which one applies.
     */
    static void previewDidChange() {
        var previewLine = PreviewElementManager.getCurrentInsertionLine();

        for (var installed : installed()) {
            installed.previewDidChange(previewLine);
        }
    }

    /**
     * Empty before {@link #install}. They are created and cleared together.
     */
    private static List<RecordedInkOverlay> installed() {
        if (overlay == null || graceGlissandoOverlay == null) {
            return List.of();
        }

        return List.of(overlay, graceGlissandoOverlay);
    }

    /**
     * Call instead of {@link #previewDidChange()} when the preview element's identity changes,
     * not merely its decorations; otherwise the overlay re-anchors to a possibly stale insertion
     * index and staff position.
     * <p>
     * Inside an open modification bracket the current {@link LayoutResult} is one insertion out
     * of date, so re-anchoring is deferred to {@link #applyPendingRestore}.
     */
    static void previewTypeDidChange() {
        var mouseLine = PreviewElementManager.getCurrentMouseLine();

        if (mouseLine != null && mouseLine.getScoreView().getSong().isModifying()) {
            pendingRestore = true;
            return;
        }

        PreviewElementManager.restorePreviewElement(mouseLine);
        previewDidChange();
    }

    /**
     * Only legal when the glyphs the renderers would emit are unchanged and just the insertion X
     * moved.
     */
    static void previewDidMove() {
        var previewLine = PreviewElementManager.getCurrentInsertionLine();

        if (overlay != null) {
            overlay.previewDidMove(previewLine);
        }

        // This is the path on which the grace-host glissando first appears. Entering the insert
        // phase hides the preview element (enterGraceNote) and then restores it
        // (enterGraceNoteInsert), which drops the hover and re-enters trackMouse — so the next
        // move reports a changed x index while the configuration is unchanged, and lands here.
        // The visibility flag that restore flipped is not one of the fields
        // configurationChanged tracks, so nothing else would rebuild this overlay's ink.
        //
        // Its own X never moves while grace mode holds it (trackMouse pins the mouse X to the
        // locked insertion slot), so off this transition the rebuild is a no-op beyond a rejected
        // gate check.
        if (graceGlissandoOverlay != null) {
            graceGlissandoOverlay.previewDidChange(previewLine);
        }
    }

    /**
     * Runs once the outermost modification bracket has committed, so layout can be recomputed
     * against the just-inserted element.
     */
    static void applyPendingRestore() {
        if (!pendingRestore) {
            return;
        }

        pendingRestore = false;

        var mouseLine = PreviewElementManager.getCurrentMouseLine();

        if (mouseLine == null) {
            return;
        }

        // Recompute now: ScoreViewController's higher-priority handler has just invalidated
        // the layout, so ensureLayout gives the just-inserted element its final position
        // before the preview is re-anchored against it.
        mouseLine.ensureLayout();
        PreviewElementManager.restorePreviewElement(mouseLine);
        previewDidChange();
    }

    /** Null before {@link #install}. */
    static @Nullable PreviewElementOverlay getOverlay() {
        return overlay;
    }
}
