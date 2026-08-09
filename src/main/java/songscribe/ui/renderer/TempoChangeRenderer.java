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

import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.hit.HitTarget;

/** Renders tempo change indicators (note = number format, e.g. "♩ = 120"). */
public final class TempoChangeRenderer extends MetronomeRenderer {

    private static final TempoChangeRenderer INSTANCE = new TempoChangeRenderer();

    private TempoChangeRenderer() {
    }

    public static TempoChangeRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        if (attachment == null) {
            return;
        }

        renderTempoChange(g2, attachment, element, invariants, frame);
    }

    /**
     * Renders one tempo change.
     * <p>
     * Every mark this renderer draws is attached to a note and is therefore addressable as a hit
     * target. The song's own tempo is not one of them — it is drawn at the first line's staff
     * header by {@link SongTempoMarkRenderer}, from the same {@code drawTempo} core, and is
     * deliberately not hittable.
     */
    private void renderTempoChange(
        Graphics2D g2,
        TempoChangeAttachment attachment,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var tempo = attachment.getTempo();
        var setup = buildRenderSetup(note, TempoChangeAttachment.class, invariants, frame);

        // A tempo change is selectable on its own. Its selection is folded into the color the
        // setup already resolved rather than applied over it, so an unselected tempo mark still
        // follows its owner note through playback, hover and range selection.
        var color = RenderingUtils.decorationColor(
            new HitTarget.Attachment(attachment), note, invariants, frame);

        drawTempo(
            g2, tempo, setup.decorationLayout().xSs(), setup.ySs(), setup.attrFont(), color);
    }

}
