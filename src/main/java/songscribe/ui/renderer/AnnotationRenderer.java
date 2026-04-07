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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;


import songscribe.music.StaffElement;
import songscribe.ui.layout.AnnotationAttachment;

/**
 * Renders text annotations attached to notes.
 * <p>
 * Annotations are text labels that appear above or below notes,
 * typically used for performance instructions or other markings.
 */
public class AnnotationRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Crotchet width for positioning
    private static final double CROTCHET_WIDTH_PX = BaseElementRenderer.FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final AnnotationRenderer INSTANCE = new AnnotationRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private AnnotationRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static AnnotationRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var annotation = element.getAnnotation();

        if (annotation == null) {
            return;
        }

        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
            // Set font
            g2.setFont(composition.getAnnotationFont());
            applyDecorationColor(g2, element, ctx);

            // Calculate position
            float x = (float) getAnnotationXPosPx(g2, element);
            float y = getAnnotationYPosPx(element, ctx);

            // Draw the annotation text
            var text = annotation.getAnnotation();
            g2.drawString(text, x, y);
        }
    }

    /**
     * Renders an annotation for a note if it has one.
     *
     * @param g2   Graphics context
     * @param note The note to check
     * @param ctx  Render context
     */
    public void renderAnnotation(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Calculates the X position for an annotation, centering it over the note.
     */
    private double getAnnotationXPosPx(Graphics2D g2, StaffElement note) {
        var annotation = note.getAnnotation();

        if (annotation == null) {
            return note.getXPosSs();
        }

        var text = annotation.getAnnotation();
        var textWidth = g2.getFontMetrics().stringWidth(text);

        // Center annotation over note
        return note.getXPosSs() + (CROTCHET_WIDTH_PX / 2) - (textWidth / 2.0);
    }

    /**
     * Gets the Y position for an annotation from layout result.
     * <p>
     * Reads the {@link songscribe.ui.layout.LayoutResult.DecorationLayout} written
     * by the vertical stacking calculator. Converts from layout coordinates
     * (relative to middleLineY=0) to component coordinates.
     */
    private float getAnnotationYPosPx(
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var decorationLayout = layoutResult.findAttachmentDecorationLayout(
            note, AnnotationAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException("No DecorationLayout found for AnnotationAttachment on note");
        }

        return (float) layoutYToComponentYSs(decorationLayout.ySs(), ctx);
    }
}
