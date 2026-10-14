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

package songscribe.dom;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * A fermata (pause/hold) attached to a note.
 */
public final class FermataAttachment extends Attachment implements IntrinsicHeight {

    private static final double FERMATA_WIDTH_SS;
    private static final double FERMATA_HEIGHT_SS;

    private static final int HELD_NUMERATOR = 3;
    private static final int HELD_DENOMINATOR = 2;

    static {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.FERMATA_ABOVE);
        FERMATA_WIDTH_SS = bbox.widthSs();
        FERMATA_HEIGHT_SS = bbox.heightSs();
    }

    @Override
    protected Attachment createCopy() {
        return new FermataAttachment();
    }

    /**
     * An element already holding one is left as it is, so it never carries two.
     */
    public static void addFermata(StaffElement element) {
        if (element.findAttachment(FermataAttachment.class) == null) {
            element.addAttachment(new FermataAttachment());
        }
    }

    public static void removeFermata(StaffElement element) {
        var existing = element.findAttachment(FermataAttachment.class);

        if (existing != null) {
            element.removeAttachment(existing);
        }
    }

    public double intrinsicWidthSs() {
        return FERMATA_WIDTH_SS;
    }

    @Override
    public double intrinsicHeightSs() {
        return FERMATA_HEIGHT_SS;
    }

    /**
     * Half again as long: what a fermata is worth in this application's playback. Integer
     * division truncates rather than refusing an inexact duration as {@link Dots#augment} does,
     * since this is a playback stretch, not a notated value.
     */
    public int heldDuration(int ticks) {
        return ticks * HELD_NUMERATOR / HELD_DENOMINATOR;
    }
}
