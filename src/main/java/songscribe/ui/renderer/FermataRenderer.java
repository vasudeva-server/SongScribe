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

import songscribe.dom.FermataAttachment;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

public final class FermataRenderer implements ElementRenderer<StaffElement> {

    private static final double FERMATA_BBOX_LEFT_SS =
        SMuFLMetadata.bboxSs(SMuFLGlyph.FERMATA_ABOVE).leftSs();

    private static final double FERMATA_WIDTH_SS =
        SMuFLMetadata.bboxSs(SMuFLGlyph.FERMATA_ABOVE).widthSs();

    private static final FermataRenderer INSTANCE = new FermataRenderer();

    private FermataRenderer() {
    }

    public static FermataRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var fermata = element.findAttachment(FermataAttachment.class);

        if (fermata == null) {
            return;
        }

        var decorationLayout = frame.decorationLayoutsFor(element, invariants)
            .findAttachmentDecorationLayout(element, FermataAttachment.class);

        if (decorationLayout == null) {
            return;
        }

        var fermataTopYSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        // The decoration layout's x is the glyph's visual left edge (already centered on the
        // notehead by the stacker), so draw from it directly. Shifting by the bbox left offset
        // turns that visual-left into the glyph origin drawString expects.
        var x = decorationLayout.xSs() - FERMATA_BBOX_LEFT_SS;

        var y = RenderingUtils.glyphOriginYFromLayoutTop(fermataTopYSs, SMuFLGlyph.FERMATA_ABOVE);

        try (var _ = GraphicsState.save(g2, COLOR)) {
            RenderingUtils.applyDecorationColor(
                g2, new HitTarget.Attachment(fermata), element, invariants, frame);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.FERMATA_ABOVE, x, y, true);
        }
    }

    public void renderFermata(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        render(invariants, frame, note, g2);
    }
}
