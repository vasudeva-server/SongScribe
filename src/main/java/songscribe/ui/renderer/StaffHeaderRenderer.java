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

import songscribe.layout.StaffHeader;
import songscribe.smufl.SMuFLGlyph;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

/**
 * Renders the staff header — the treble clef and the key signature — at the start of a staff
 * line, at the positions the {@link StaffHeader} carries.
 * <p>
 * The clef is drawn so that its inner curve wraps around the G line (second line from bottom):
 * the SMuFL G clef's origin sits on that line. The signature is the run
 * {@link KeySignatureRenderer} draws wherever a key signature appears, painted here in the
 * element color: the header is not an element of the line, so no paint loop colors it for
 * selection or playback.
 */
public final class StaffHeaderRenderer implements ElementRenderer<StaffHeader> {

    /** Staff spaces from the middle line down to the G line, where the SMuFL G clef's origin sits. */
    private static final double G_LINE_BELOW_MIDDLE_SS = 1.0;

    /** Singleton instance. */
    private static final StaffHeaderRenderer INSTANCE = new StaffHeaderRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private StaffHeaderRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static StaffHeaderRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffHeader header,
        Graphics2D g2
    ) {
        var clefBaselineYSs = invariants.getMiddleLineYSs() + G_LINE_BELOW_MIDDLE_SS;
        RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.G_CLEF, header.clefXSs(), clefBaselineYSs);

        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);
            KeySignatureRenderer.drawRun(
                g2, header.key().signatureAccidentals(), header.keySignatureXSs(), invariants);
        }
    }
}
