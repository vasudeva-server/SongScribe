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

import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

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
     * Every mark this renderer draws is attached to a note and is therefore a hit target; the
     * song's own tempo ({@link SongTempoMarkRenderer}) deliberately is not.
     */
    private void renderTempoChange(
        Graphics2D g2,
        TempoChangeAttachment attachment,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        renderAttachment(note, attachment, invariants, frame, g2);
    }

}
