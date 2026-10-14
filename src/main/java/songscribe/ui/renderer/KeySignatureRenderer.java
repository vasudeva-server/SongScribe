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
import java.util.List;

import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.engraving.Staff;
import songscribe.layout.CautionaryKeySignature;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

/**
 * Renders the key signature runs of a staff line: the header's, a mid-line key change, and the
 * cautionary key change at the end of a line. What is drawn belongs to {@link Key}, because
 * layout and MusicXML need the same answers.
 */
public final class KeySignatureRenderer {

    private static final KeySignatureRenderer INSTANCE = new KeySignatureRenderer();

    private KeySignatureRenderer() {
    }

    public static KeySignatureRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * What is drawn is {@link KeyChangeElement#drawnAccidentals()}'s answer, which
     * {@code ElementColumnBuilder} also sized the column from, so the glyphs fill the room layout
     * kept and the hit target sits on the ink. Painted in the color the caller set.
     */
    public void renderMidLine(
        LineInvariants invariants,
        ElementFrame frame,
        KeyChangeElement element,
        Graphics2D g2
    ) {
        drawRun(g2, element.drawnAccidentals(), frame.resolveElementXSs(element, invariants), invariants);
    }

    /**
     * What is drawn, and where, are {@link CautionaryKeySignature}'s answers, so that the space
     * layout reserved and the hit target cannot disagree with the glyphs.
     */
    public void renderCautionary(
        Graphics2D g2,
        CautionaryKeySignature cautionary,
        LineInvariants invariants
    ) {
        var layoutResult = invariants.getLayoutResult();
        var placement = cautionary.placeIn(
            layoutResult.overflowsStaffWidth(),
            layoutResult.contentRightEdgeSs(),
            invariants.getSong().getLineWidthSs().value());

        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

            if (placement instanceof CautionaryKeySignature.Placement.WithBarLine withBarLine) {
                BarRenderer.drawSingleBarLine(
                    g2, withBarLine.barLineXSs(), invariants.getMiddleLineYSs());
            }

            drawRun(g2, cautionary.accidentals(), placement.accidentalsXSs(), invariants);
        }
    }

    /**
     * Paints in the color the caller set.
     */
    static void drawRun(
        Graphics2D g2,
        List<Key.DrawnAccidental> accidentals,
        double xPosSs,
        LineInvariants invariants
    ) {
        if (accidentals.isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);

            var middleLineYSs = invariants.getMiddleLineYSs();
            var penXSs = xPosSs;

            for (var i = 0; i < accidentals.size(); i++) {
                var accidental = accidentals.get(i);
                var y = middleLineYSs + accidental.staffPositionSp() * Staff.HALF_SPACE_SS;
                var next = i + 1 < accidentals.size() ? accidentals.get(i + 1) : null;

                penXSs += accidental.leadingGapSs();
                g2.drawString(accidental.glyph().asString(), (float) penXSs, (float) y);
                penXSs += accidental.advanceSs(next);
            }
        }
    }
}
