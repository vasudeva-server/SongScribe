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

package songscribe.ui.renderer;

import java.awt.Graphics2D;
import java.util.EnumMap;

import songscribe.dom.ElementType;
import songscribe.dom.GlyphAppearance;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

/**
 * Renders rest glyphs (whole, half, quarter, eighth, sixteenth, thirty-second rests).
 * <p>
 * Rest glyphs are rendered at the note's position using SMuFL/Bravura glyphs.
 * All coordinates are in staff-space units; the Graphics2D scale transform
 * applied by LineComponent handles pixel conversion.
 * Rests don't have stems, flags, or accidentals.
 */
public final class RestRenderer implements ElementRenderer<StaffElement> {

    // ==========================================================================
    // Rest Glyphs (SMuFL/Bravura)
    // ==========================================================================

    // Y position adjustment for whole and half rests (relative to middle line)
    static final int SEMIBREVE_REST_Y_OFFSET = -2;  // Above the middle line
    static final int MINIM_REST_Y_OFFSET = 0;       // On the middle line

    // Dot positioning derived from SMuFL metadata, in staff-space units.
    // The Graphics2D scale transform handles pixel conversion.
    private static final double DOT_GAP_SS = 0.5; // ss gap between rest glyph right edge and first dot
    private static final EnumMap<ElementType, Float> FIRST_DOT_X_SS = new EnumMap<>(ElementType.class);

    static {
        // Compute first dot X for each rest type from its advance width (in ss)
        for (var elementType : ElementType.values()) {
            if (!elementType.isRest()) {
                continue;
            }

            if (elementType.appearance() instanceof GlyphAppearance(var glyph)) {
                var advanceWidth = SMuFLMetadata.advanceWidthSs(glyph);
                FIRST_DOT_X_SS.put(elementType, (float) (advanceWidth + DOT_GAP_SS));
            }
        }
    }

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

    /**
     * Resolves the X coordinate for a rest from the layout result,
     * snapped to device pixels for crisp rendering.
     */
    private static double resolveRestXSs(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        double noteX;

        if (frame.hasOverrideElementX()) {
            noteX = frame.overrideElementXSs();
        } else {
            noteX = invariants.getLayoutResult().getElementXSs(note);
        }

        return noteX;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var noteType = element.getType();

        if (!noteType.isRest()) {
            return;
        }

        // Get rest position in staff-space units
        var noteX = resolveRestXSs(g2, element, invariants, frame);
        var noteY = calculateRestYSs(element, invariants.getMiddleLineYSs());

        try (var _ = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.translate(noteX, noteY);
            g2.setFont(RenderingUtils.MUSIC_FONT);
            // Note: Don't set color here - respect the color set by the caller
            // (e.g., blue for insertion notes, black for song notes)

            // Draw rest glyph
            if (noteType.appearance() instanceof GlyphAppearance(var glyph)) {
                g2.drawString(glyph.asString(), 0f, 0f);
            }

            // Draw dots
            renderDots(g2, element, noteType);
        }
    }

    /**
     * Calculates the Y position for a rest in staff-space units.
     * Whole rests hang from the second line, half rests sit on the middle line.
     * Other rests are centered vertically on the staff.
     */
    double calculateRestYSs(StaffElement note, double middleLineYSs) {
        var noteType = note.getType();

        if (noteType == ElementType.SEMIBREVE_REST) {
            // Whole rest hangs below the 4th line (second from top)
            return middleLineYSs + Staff.spToSs(SEMIBREVE_REST_Y_OFFSET);
        }

        if (noteType == ElementType.MINIM_REST) {
            // Half rest sits on the middle line
            return middleLineYSs + Staff.spToSs(MINIM_REST_Y_OFFSET);
        }

        // Other rests use standard note Y positioning
        return middleLineYSs + Staff.spToSs(note.getStaffPosition());
    }

    /**
     * Renders dots for dotted rests.
     */
    private void renderDots(Graphics2D g2, StaffElement note, ElementType noteType) {
        if (note.getDotCount() == 0) {
            return;
        }

        var firstDotX = FIRST_DOT_X_SS.get(noteType);

        if (firstDotX == null) {
            return;
        }

        // Draw augmentation dots using SMuFL glyph
        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            float dotX = firstDotX;

            for (var i = 0; i < note.getDotCount(); i++) {
                g2.drawString(SMuFLGlyph.AUGMENTATION_DOT.asString(), dotX, 0f);
                dotX += NoteGeometry.DOT_SPACING_SS;
            }
        }
    }
}
