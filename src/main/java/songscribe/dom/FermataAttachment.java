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
 * Represents a fermata (pause/hold) attachment on a note.
 * <p>
 * Fermatas indicate that a note should be held longer than its written value.
 * They are typically placed above the note, centered on the note head.
 */
public final class FermataAttachment extends Attachment implements IntrinsicHeight {

    // SMuFL bbox-derived dimensions in staff-space units
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
     * Puts a fermata on {@code element}. An element already holding one is left as it is, so
     * the element never carries two.
     *
     * @param element the element to hold
     * @effects afterwards {@code element.findAttachment(FermataAttachment.class)} is non-null;
     *     nothing changes when it already was
     */
    public static void addFermata(StaffElement element) {
        if (element.findAttachment(FermataAttachment.class) == null) {
            element.addAttachment(new FermataAttachment());
        }
    }

    /**
     * Takes the fermata off {@code element}. An element holding none is left as it is.
     *
     * @param element the element to release
     * @effects afterwards {@code element.findAttachment(FermataAttachment.class)} is null
     */
    public static void removeFermata(StaffElement element) {
        var existing = element.findAttachment(FermataAttachment.class);

        if (existing != null) {
            element.removeAttachment(existing);
        }
    }

    /**
     * Returns the glyph's own width in staff-space units, derived from SMuFL bounding box data.
     */
    public double intrinsicWidthSs() {
        return FERMATA_WIDTH_SS;
    }

    @Override
    public double intrinsicHeightSs() {
        return FERMATA_HEIGHT_SS;
    }

    /**
     * Returns {@code ticks} lengthened by this fermata: half again as long, which is what a
     * fermata is worth in this application's playback.
     *
     * <p>Integer division truncates, so an odd tick count loses its half tick. A double-dotted
     * thirty-second note is written at 21 ticks and is held for 31 rather than 31.5. That is the
     * arithmetic MIDI has always been given here — unlike {@link Dots#augment}, which refuses a
     * duration it cannot lengthen exactly, this is a playback stretch rather than a notated
     * value, and half a tick of it is inaudible.
     *
     * @param ticks the written duration
     * @return the held duration, in the same ticks
     */
    public int heldDuration(int ticks) {
        return ticks * HELD_NUMERATOR / HELD_DENOMINATOR;
    }
}
