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
import songscribe.dom.KeySignature;
import songscribe.dom.Line;
import songscribe.engraving.Staff;
import songscribe.layout.CautionaryKeySignature;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

/**
 * Renders key signatures (sharps or flats) at the start of a staff line, and the
 * cautionary key change at the end of one.
 * <p>
 * The key signature follows the clef and shows the sharps or flats
 * in the order they appear (FCGDAEB for sharps, BEADGCF for flats).
 * <p>
 * <b>What</b> is drawn — which accidentals, in which order, at which staff positions, and
 * how wide the run is — belongs to {@link Key}, in {@code songscribe.dom}, because layout and
 * MusicXML need the same answers. This class only paints what it is given.
 */
public final class KeySignatureRenderer implements ElementRenderer<KeySignature> {

    // Singleton instance
    private static final KeySignatureRenderer INSTANCE = new KeySignatureRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private KeySignatureRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static KeySignatureRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        KeySignature element,
        Graphics2D g2
    ) {
        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);
            drawRun(g2, element.getKey().signatureAccidentals(), element.getXSs(), invariants);
        }
    }

    // ==========================================================================
    // Mid-Line Key Change Rendering
    // ==========================================================================

    /**
     * Renders the key change {@code element} makes part-way through a line, at the position the
     * line's spacing gave its column.
     *
     * <p>What is drawn is {@link KeyChangeElement#drawnAccidentals()}'s answer, which
     * {@code ElementColumnBuilder} also sized the column from, so the glyphs fill exactly the
     * room layout kept for them and the double-click target {@code LayoutHitTester} finds sits on
     * the ink.
     *
     * <p>The run is painted in the color the caller set, as every element renderer does: a
     * mid-line key change is an ordinary element of the line, so selection, hover and playback
     * color it like its neighbors. The header and the cautionary have no such caller and set
     * their own.
     *
     * @param invariants Line invariants, for the solved layout of this line
     * @param frame      The element's frame, carrying any insertion-preview position
     * @param element    The key change to draw
     * @param g2         Graphics context
     */
    public void renderMidLine(
        LineInvariants invariants,
        ElementFrame frame,
        KeyChangeElement element,
        Graphics2D g2
    ) {
        drawRun(g2, element.drawnAccidentals(), frame.resolveElementXSs(element, invariants), invariants);
    }

    // ==========================================================================
    // Key Change Rendering (at end of line)
    // ==========================================================================

    /**
     * Renders {@code cautionary} at the end of a staff line — the warning to the performer that the
     * next line starts in a different key.
     * <p>
     * What is drawn, and where each part of it lands, are
     * {@link CautionaryKeySignature}'s answers rather than this method's, so that the space layout
     * reserved and the hit target for editing the change cannot disagree with the glyphs. This
     * method only paints them.
     *
     * @param g2         Graphics context
     * @param cautionary The cautionary to draw, from {@link CautionaryKeySignature#of(Line)}
     * @param invariants Line invariants, for the solved layout of this line
     */
    public void renderCautionary(
        Graphics2D g2,
        CautionaryKeySignature cautionary,
        LineInvariants invariants
    ) {
        var placement = cautionary.placeIn(
            invariants.getLayoutResult(),
            invariants.getSong().getLineWidthSs());

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
     * Paints an already laid-out run of accidentals starting at {@code xPosSs}, in the color the
     * caller set. Every caller either is an element of the line, which is colored by the paint
     * loop for selection and playback, or has set the element color itself.
     */
    private static void drawRun(
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
                var y = middleLineYSs + Staff.spToSs(accidental.staffPositionSp());

                penXSs += accidental.leadingGapSs();
                g2.drawString(accidental.glyph().asString(), (float) penXSs, (float) y);
                penXSs += accidental.advanceSs();
            }
        }
    }
}
