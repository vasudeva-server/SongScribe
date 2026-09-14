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
import java.awt.Shape;
import java.awt.geom.AffineTransform;

import songscribe.dom.ArticulationInk;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

/**
 * Renders articulation markings on notes (staccato, accent).
 * <p>
 * Articulations modify how a note is played:
 * <ul>
 *   <li>Staccato - short, detached (dot above/below note)</li>
 *   <li>Accent - emphasized attack (> symbol)</li>
 * </ul>
 */
public final class ArticulationRenderer implements ElementRenderer<StaffElement> {

    // Singleton instance
    private static final ArticulationRenderer INSTANCE = new ArticulationRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private ArticulationRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static ArticulationRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        if (!(element instanceof NoteElement note) || note.getArticulations().isEmpty()) {
            return;
        }

        var decorationLayouts = frame.decorationLayoutsFor(note, invariants);

        try (var _ = GraphicsState.save(g2, COLOR)) {
            var side = NoteAttachedStacker.articulationDirection(note);

            for (var articulation : note.getArticulations()) {
                var layout = decorationLayouts.getDecorationLayout(articulation);

                if (layout == null) {
                    continue;
                }

                // Each articulation is selectable on its own, so the color is decided per
                // articulation rather than once for the note. Neither glyph helper sets a
                // color, so this is the only place it can be set.
                RenderingUtils.applyDecorationColor(
                    g2, new HitTarget.Articulation(articulation), element, invariants, frame);

                switch (articulation.getType().ink()) {
                    case ArticulationInk.Glyphs glyphs ->
                        drawArticulationGlyph(g2, layout, invariants, glyphs.on(side));
                    case ArticulationInk.Outline outline ->
                        drawOutline(g2, layout, invariants, outline.shape());
                }
            }
        }
    }

    /**
     * The X at which to draw a glyph of the given ink bounds so that it sits centered within its
     * layout box, which the stacker already centered on the notehead.
     * <p>
     * The box is sized from the above-staff glyph's bbox, so centering rather than drawing flush at
     * the box's left edge is what keeps a glyph whose own bbox differs — a future below-staff
     * variant, or a wedge — on the note's axis.
     */
    private static double centeredInLayoutBoxXSs(
        LayoutResult.DecorationLayout layout, double glyphBBoxLeftSs, double glyphWidthSs) {

        return layout.xSs() + (layout.widthSs() - glyphWidthSs) / 2.0 - glyphBBoxLeftSs;
    }

    /**
     * Draws a single articulation glyph centered within its layout box, which the stacker already
     * centered on the notehead.
     */
    private static void drawArticulationGlyph(
        Graphics2D g2,
        LayoutResult.DecorationLayout layout,
        LineInvariants invariants,
        SMuFLGlyph glyph) {

        var bbox = SMuFLMetadata.bboxSs(glyph);
        var componentTopYSs = RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants);
        var x = centeredInLayoutBoxXSs(layout, bbox.leftSs(), bbox.widthSs());
        var y = RenderingUtils.glyphOriginYFromLayoutTop(componentTopYSs, glyph);
        RenderingUtils.drawBravuraGlyph(g2, glyph, x, y, true);
    }

    /**
     * Draws an articulation outline, filled, centered within its layout box, which the stacker
     * already centered on the notehead.
     */
    private static void drawOutline(
        Graphics2D g2,
        LayoutResult.DecorationLayout layout,
        LineInvariants invariants,
        Shape outline
    ) {
        var bounds = outline.getBounds2D();

        var componentTopYSs = RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants);
        var x = centeredInLayoutBoxXSs(layout, bounds.getMinX(), bounds.getWidth());
        var y = componentTopYSs - bounds.getMinY();

        g2.fill(AffineTransform.getTranslateInstance(x, y).createTransformedShape(outline));
    }
}
