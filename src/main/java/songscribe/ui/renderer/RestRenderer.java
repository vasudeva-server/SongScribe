/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.renderer;

import java.awt.Graphics2D;

import songscribe.dom.Dots;
import songscribe.dom.RestElement;
import songscribe.engraving.StaffPosition;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

/**
 * Renders rest elements.
 * <p>
 * A rest is drawn as its type's glyph at its type's seat, followed by its augmentation dots.
 * All coordinates are in staff-space units; the Graphics2D scale transform
 * applied by LineComponent handles pixel conversion.
 * Rests don't have stems, flags, or accidentals.
 */
public final class RestRenderer implements ElementRenderer<RestElement> {

    // Gap in staff spaces between the rest glyph's advance and its first dot.
    private static final double DOT_GAP_SS = 0.5;

    // Singleton instance
    private static final RestRenderer INSTANCE = new RestRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private RestRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static RestRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        RestElement element,
        Graphics2D g2
    ) {
        var restX = frame.resolveElementXSs(element, invariants);
        var restY = calculateRestYSs(element, invariants.getMiddleLineYSs());

        try (var _ = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.translate(restX, restY);
            g2.setFont(RenderingUtils.MUSIC_FONT);
            // Note: Don't set color here - respect the color set by the caller
            // (e.g., blue for insertion notes, black for song notes)
            g2.drawString(element.glyph().asString(), 0f, 0f);
            renderDots(g2, element);
        }
    }

    /**
     * Calculates the Y position for a rest in staff-space units, from its type's glyph seat.
     */
    double calculateRestYSs(RestElement rest, double middleLineYSs) {
        return middleLineYSs + rest.seatSp().toSs();
    }

    /**
     * Renders the augmentation dots of a dotted rest, the first one {@link #DOT_GAP_SS} past the
     * glyph's advance width.
     */
    private void renderDots(Graphics2D g2, RestElement rest) {
        if (rest.dots() == Dots.NONE) {
            return;
        }

        var dotX = (float) (SMuFLMetadata.advanceWidthSs(rest.glyph()) + DOT_GAP_SS);

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);

            for (var i = 0; i < rest.dots().count(); i++) {
                g2.drawString(SMuFLGlyph.AUGMENTATION_DOT.asString(), dotX, 0f);
                dotX += NoteGeometry.DOT_SPACING_SS;
            }
        }
    }
}
