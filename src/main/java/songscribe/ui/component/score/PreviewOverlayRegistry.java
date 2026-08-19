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
 * Owns the hover-preview overlays and decides when their ink is rebuilt, re-anchored, or taken
 * down.
 * <p>
 * The overlays are static like the rest of the preview subsystem's state: only one line across all
 * hosts can carry the preview at a time, so one set serves whichever host installed it. Position
 * and hover tracking stay on {@link PreviewElementManager}; this class holds only the components
 * themselves and the deferred re-anchor that a mid-bracket type change leaves behind.
 */
final class PreviewOverlayRegistry {

    /**
     * The component that draws the hover preview, or null before a host has installed one
     * (headless converters never do).
     */
    @Nullable
    private static PreviewElementOverlay overlay = null;

    /**
     * The component that draws the connecting glissando between a grace note and the host-note
     * insertion preview, or null before a host has installed one. It is gated on grace mode, and
     * the note-head preview it connects to is showing at the same time.
     */
    @Nullable
    private static GraceGlissandoPreviewOverlay graceGlissandoOverlay = null;

    /**
     * True when {@link #previewTypeDidChange()} ran while a modification bracket was open and had
     * to defer re-anchoring the overlay until the bracket closes and layout is fresh. Consumed by
     * {@link #applyPendingRestore}.
     */
    private static boolean pendingRestore = false;

    private PreviewOverlayRegistry() {
    }

    /**
     * Creates {@code host}'s hover-preview overlays and registers them as children of the host.
     * Exactly one of each exists for the host's lifetime; none is ever recreated, only
     * retargeted.
     */
    static void install(OverlayHost host) {
        overlay = new PreviewElementOverlay(host);
        graceGlissandoOverlay = new GraceGlissandoPreviewOverlay(host);

        for (var installed : installed()) {
            host.addOverlay(installed);
        }
    }

    /**
     * Rebuilds every hover-preview overlay's ink and bounds from the current tracking state,
     * hiding each one that no longer applies. Call whenever anything the renderers read has
     * changed — the preview element itself, its decorations, or the staff position it sits at.
     * Each overlay's own gate decides whether it is the one that should be visible, so a single
     * call here keeps them all in sync without the caller having to know which one currently
     * applies.
     */
    static void previewDidChange() {
        var previewLine = PreviewElementManager.getCurrentInsertionLine();

        for (var installed : installed()) {
            installed.previewDidChange(previewLine);
        }
    }

    /**
     * The installed overlays, or an empty list before {@link #install} (headless converters never
     * install any). They are all created and cleared together, so the all-or-nothing check covers
     * each of them.
     */
    private static List<RecordedInkOverlay> installed() {
        if (overlay == null || graceGlissandoOverlay == null) {
            return List.of();
        }

        return List.of(overlay, graceGlissandoOverlay);
    }

    /**
     * Re-derives the insertion position from the real mouse location, then rebuilds the
     * overlays' ink for the newly-selected preview element type.
     * <p>
     * Call this instead of {@link #previewDidChange()} when the preview element's identity
     * changes (e.g. a toolbar tool selection, or the next-insertion element set up after a click)
     * rather than merely its decorations — otherwise the overlay re-anchors to whatever insertion
     * index and staff position were last computed, which can be stale relative to the line's
     * current layout.
     * <p>
     * When called from inside an open modification bracket — the post-insertion case, where
     * {@link EditModeManager#previewElementDidChange} sets up the next preview element while
     * {@code line.withModification(...)} is still open — the current {@link LayoutResult} is
     * one insertion out of date (invalidated only when the bracket closes), so re-anchoring
     * here would land on the just-inserted element and fall back to an empty-line offset. The
     * re-anchor is deferred to {@link #applyPendingRestore}, run from
     * {@code PreviewElementManager.songDidChange} once the bracket has closed and layout can be
     * made fresh again.
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
     * Re-anchors the preview overlay without rebuilding its ink. Only legal when the glyphs the
     * renderers would emit are unchanged and just the insertion X moved.
     */
    static void previewDidMove() {
        var previewLine = PreviewElementManager.getCurrentInsertionLine();

        if (overlay != null) {
            overlay.previewDidMove(previewLine);
        }

        // This is the path on which the grace-host glissando first appears. Entering the insert
        // phase hides the preview element (enterGraceNote) and then restores it
        // (enterGraceNoteInsert), which resets the insertion index to -1 and re-enters trackMouse
        // — so the next move reports a changed x index while the configuration is unchanged, and
        // lands here. The visibility flag that restore flipped is not one of the fields
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
     * Re-anchors the preview overlay for a pending click-to-insert, if any. Invoked from
     * {@code PreviewElementManager.songDidChange} once the outermost modification bracket has
     * committed, so the layout has been invalidated and can be recomputed to place the preview
     * against the just-inserted element rather than a stale {@link LayoutResult} that predates it.
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

    /** Returns the installed hover-preview overlay, or null before {@link #install}. */
    static @Nullable PreviewElementOverlay getOverlay() {
        return overlay;
    }
}
