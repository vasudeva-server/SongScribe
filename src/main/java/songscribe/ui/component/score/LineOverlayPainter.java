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

import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ScaleContext;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.PasteModeManager;
import songscribe.util.GraphicUtils;

/**
 * Paints the overlays that belong to a line but cannot be drawn by it.
 * <p>
 * Both the hover preview element and the paste-mode insertion marker span the full legal
 * staff-position range, which is wider than the content-hugging bounds a line is laid out to,
 * and Swing clips a component to its own bounds. They are therefore painted by the
 * {@link ScoreView} — the one level in the hierarchy whose bounds are never the binding
 * constraint — after all children, in page coordinates.
 */
public final class LineOverlayPainter {

    private LineOverlayPainter() {
    }

    /**
     * Paints every line overlay into {@code host}'s coordinate space, on top of whatever
     * {@code host} has already painted.
     *
     * @param g the graphics context to paint into, in {@code host} coordinates
     * @param host the ancestor doing the painting
     */
    public static void paintOverlays(Graphics2D g, ScoreView host) {
        PreviewElementManager.paintOverlay(g, host);
        paintPasteInsertionPoint(g, host);
    }

    /**
     * Paints the paste-mode insertion marker on the line currently tracked as the placement
     * target, or nothing when paste mode is inactive or the pointer is off any line.
     * <p>
     * The marker is driven by {@link PasteModeManager}, not by the preview element: paste mode
     * suppresses the preview, so the two overlays never have a line in common.
     */
    private static void paintPasteInsertionPoint(Graphics2D g, ScoreView host) {
        var pasteModeManager = PasteModeManager.getActiveInstance();

        if (pasteModeManager == null) {
            return;
        }

        paintOnLine(g, host, pasteModeManager.getTargetLineComponent(),
            LineComponent::renderInsertionPointOverlay);
    }

    /**
     * Runs {@code render} against a graphics context transformed into {@code line}'s origin
     * and scaled to staff spaces, matching the transform {@code LineComponent.render}
     * establishes for the line's own paint pass.
     *
     * @param g the graphics context to paint into, in {@code host} coordinates
     * @param host the ancestor doing the painting
     * @param line the line the overlay belongs to, or null when there is nothing to paint
     * @param render draws the overlay in the line's staff-space coordinates
     */
    static void paintOnLine(
        Graphics2D g,
        ScoreView host,
        @Nullable LineComponent line,
        BiConsumer<LineComponent, Graphics2D> render
    ) {
        // A line component from a previous rebuildLayout() is stale — it is no longer in
        // the host's hierarchy, so its origin there is meaningless.
        if (line == null || !SwingUtilities.isDescendingFrom(line, host)) {
            return;
        }

        var g2 = (Graphics2D) g.create();

        try {
            // The overlay bypasses ScoreComponent.paintComponent, which is what normally
            // applies these for a line's own paint pass.
            GraphicUtils.setRenderingHints(g2);

            var origin = SwingUtilities.convertPoint(line, 0, 0, host);
            g2.translate(origin.x, origin.y);

            var scale = ScaleContext.getPixelsPerStaffSpace() * line.getViewScale().factor();
            g2.scale(scale, scale);

            render.accept(line, g2);
        } finally {
            g2.dispose();
        }
    }
}
