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

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.engraving.EngravingConstants;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.shape.HairpinShape;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.STROKE;

public final class HairpinRenderer {

    /** The hairpin stroke width as a multiple of the LilyPond base thickness. */
    private static final double HAIRPIN_MULTIPLIER = 1.0;

    private static final double HAIRPIN_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * HAIRPIN_MULTIPLIER;

    private static final HairpinRenderer INSTANCE = new HairpinRenderer();

    private HairpinRenderer() {
    }

    public static HairpinRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * @param layout The pre-computed layout (with offsets already applied)
     */
    static Line2D.Double[] computeHairpinLines(
        LayoutResult.DecorationLayout layout,
        Hairpin.Kind kind,
        LineInvariants invariants
    ) {
        var boundsSs = new Rectangle2D.Double(
            layout.xSs(),
            RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants),
            layout.widthSs(),
            layout.heightSs());

        // songscribe.shape is pure geometry and does not depend on songscribe.dom, so it takes
        // the side the tip is on rather than the hairpin's kind.
        var tip = switch (kind) {
            case CRESCENDO -> HairpinShape.Tip.LEFT;
            case DIMINUENDO -> HairpinShape.Tip.RIGHT;
        };

        return HairpinShape.lines(boundsSs, tip);
    }

    /**
     * @param layout The pre-computed layout (with offsets already applied)
     * @param g2     Graphics context with scale transform
     */
    private void renderSingleHairpin(
        Hairpin hairpin,
        LayoutResult.DecorationLayout layout,
        Graphics2D g2,
        LineInvariants invariants
    ) {
        try (var _ = GraphicsState.save(g2, COLOR, STROKE)) {
            // A hairpin belongs to no single note, so it has no owner whose color it could take.
            g2.setColor(RenderingUtils.decorationColor(
                new HitTarget.Hairpin(hairpin), null, invariants, ElementFrame.LINE_LEVEL));
            // CAP_ROUND is intentional: its cap extends past the endpoint, so both lines
            // overlap at the narrow tip and fill it solidly. GraphicUtils.drawRoundedLine
            // keeps ends within endpoints, which leaves the tip visually unclosed.
            g2.setStroke(new BasicStroke(
                (float) HAIRPIN_SS,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            ));

            for (var line : computeHairpinLines(layout, hairpin.getKind(), invariants)) {
                g2.draw(line);
            }
        }
    }

    public void renderHairpinsFromLine(
        Graphics2D g2,
        LineInvariants invariants
    ) {
        var decorationLayouts = invariants.getLayoutResult().decorationLayouts();

        for (var entry : decorationLayouts.getDecorationLayoutsByType(Hairpin.class)) {
            renderSingleHairpin(entry.getKey(), entry.getValue(), g2, invariants);
        }
    }

}
