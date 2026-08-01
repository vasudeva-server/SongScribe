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

import module java.desktop;

import songscribe.dom.BeatChange;
import songscribe.dom.StaffElement;
import songscribe.dom.BeatChangeAttachment;
import songscribe.hit.HitTarget;

/**
 * Renders beat change indicators (note = note format).
 * <p>
 * Beat changes show equivalence between two note values, e.g., "♩ = ♪."
 * indicating that the quarter note of the old tempo equals the dotted
 * eighth note of the new tempo.
 */
public final class BeatChangeRenderer extends MetronomeRenderer {

    private static final BeatChangeRenderer INSTANCE = new BeatChangeRenderer();

    private BeatChangeRenderer() {
    }

    public static BeatChangeRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var attachment = element.findAttachment(BeatChangeAttachment.class);

        if (attachment == null) {
            return;
        }

        var setup = buildRenderSetup(element, BeatChangeAttachment.class, invariants, frame);
        var xSs = setup.decorationLayout().xSs();

        // A beat change is selectable on its own, so its selection is folded into the color the
        // setup resolved from the owner note rather than applied over it. Both draw helpers take
        // the color as an argument and scope it with their own GraphicsState block, so nothing
        // has to be set on g2 here.
        var color = RenderingUtils.decorationColor(
            new HitTarget.Attachment(attachment), element, invariants, frame);

        drawBeatChange(g2, attachment.getBeatChange(), xSs, setup.ySs(), setup.attrFont(), color);
    }

    private void drawBeatChange(
        Graphics2D g2,
        BeatChange beatChange,
        double xSs,
        double ySs,
        Font attrFont,
        Color color
    ) {
        var nextXSs = drawDurationEquals(g2, beatChange.duration(), xSs, ySs, attrFont, color);
        drawDurationGlyph(g2, beatChange.beat(), nextXSs, ySs, color);
    }

}
