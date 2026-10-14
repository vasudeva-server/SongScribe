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

import java.awt.Color;
import java.awt.Graphics2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElement;
import songscribe.dom.Trill;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.layout.TrillGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

public final class TrillRenderer {

    private static final double TRILL_ADVANCE_WIDTH_SS = SMuFLMetadata.advanceWidthSs(SMuFLGlyph.ORNAMENT_TRILL);

    private static final TrillRenderer INSTANCE = new TrillRenderer();

    private TrillRenderer() {
    }

    public static TrillRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * The layout's bounds are the trill's ink extent ({@link TrillGeometry#inkExtentSs}).
     *
     * @param g2          Graphics context with scale transform
     * @param layout      the trill's layout, with offsets already applied
     */
    private void renderTrill(
        Graphics2D g2,
        Trill trill,
        LayoutResult.DecorationLayout layout,
        double trillTopYSs,
        Color color
    ) {
        // SMuFL ornamentTrill glyph origin is at the baseline (bottom of glyph).
        // trillTopYSs is the top, so offset down by the glyph height.
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.ORNAMENT_TRILL);
        var y = trillTopYSs + bbox.heightSs();
        var xSs = layout.xSs();

        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.ORNAMENT_TRILL, xSs, y, true);

            if (!TrillGeometry.isSingleNote(trill)) {
                GlyphRun.startingAt(
                    ScaledGlyph.atDesignSize(SMuFLGlyph.WIGGLE_TRILL_FASTER),
                    new GlyphRun.Span(xSs + TRILL_ADVANCE_WIDTH_SS, xSs + layout.widthSs()),
                    y
                ).draw(g2);
            }
        }
    }

    public void renderTrillsFromLine(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var layoutResult = invariants.getLayoutResult();

        for (var entry : layoutResult.decorationLayouts().getDecorationLayoutsByType(Trill.class)) {
            var trill = entry.getKey();
            var layout = entry.getValue();

            var color = RenderingUtils.decorationColor(
                new HitTarget.Trill(trill), trill.getAnchorElement(), invariants, frame);
            var trillTopYSs = RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants);

            renderTrill(g2, trill, layout, trillTopYSs, color);
        }
    }

}
