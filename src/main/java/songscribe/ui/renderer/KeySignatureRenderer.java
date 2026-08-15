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

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

import module java.desktop;

import java.util.List;

import songscribe.dom.Key;
import songscribe.dom.KeySignature;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.layout.LayoutResult;
import songscribe.util.GraphicsState;

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
        drawRun(g2, element.getKey().signatureAccidentals(), element.getXSs(), invariants);
    }

    // ==========================================================================
    // Key Change Rendering (at end of line)
    // ==========================================================================

    /**
     * Renders the cautionary key change at the end of a staff line — the warning to the
     * performer that the next line starts in a different key.
     * <p>
     * Which accidentals are drawn is {@link Key#accidentalsFrom}'s answer, not this
     * method's; nothing is drawn when {@code previous} and {@code next} are the same key.
     * <p>
     * <b>Where</b> the run sits depends on whether the line's content fits the staff:
     * <ul>
     *   <li>On a line that fits, the run is right-aligned to {@code lineWidthSs} less
     *       {@link StaffHeaderMetrics#CAUTIONARY_RIGHT_MARGIN_SS}. Layout reserves that span, so
     *       the run clears the music it follows.</li>
     *   <li>On a line whose {@link LayoutResult#overflowsStaffWidth() content overflows}, the
     *       margin is already behind the last element and pinning to it would drop the run on
     *       top of the music. The run instead starts one line rest past the rightmost element
     *       edge — the same trailing gap layout reserves after a last element that is not the
     *       flush-right terminal — and so extends the overflow rather than colliding with it.
     *       {@code LayoutEngine.positionTerminalFlushRight} skips an overflowing line for the
     *       same reason.</li>
     * </ul>
     * The glyphs are the hit target for editing the change, so a caller that hit-tests the
     * cautionary derives its bounds from this placement rather than from the margin.
     *
     * @param g2           Graphics context
     * @param previous     The key in effect at the end of this line
     * @param next         The key the following line starts in
     * @param lineWidthSs  The width of the staff line (ss)
     * @param invariants   Line invariants, for the solved layout of this line
     */
    public void renderKeyChange(
        Graphics2D g2,
        Key previous,
        Key next,
        double lineWidthSs,
        LineInvariants invariants
    ) {
        var accidentals = next.accidentalsFrom(previous);

        if (accidentals.isEmpty()) {
            return;
        }

        var widthSs = next.widthSsFrom(previous);
        var layoutResult = invariants.getLayoutResult();
        double xPosSs;

        if (layoutResult.overflowsStaffWidth()) {
            xPosSs = layoutResult.contentRightEdgeSs()
                + invariants.getSong().getDefaultRestLengthSs();
        } else {
            xPosSs = lineWidthSs - StaffHeaderMetrics.CAUTIONARY_RIGHT_MARGIN_SS - widthSs;
        }

        drawRun(g2, accidentals, xPosSs, invariants);
    }

    /** Paints an already laid-out run of accidentals starting at {@code xPosSs}. */
    private static void drawRun(
        Graphics2D g2,
        List<Key.DrawnAccidental> accidentals,
        double xPosSs,
        LineInvariants invariants
    ) {
        if (accidentals.isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

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
