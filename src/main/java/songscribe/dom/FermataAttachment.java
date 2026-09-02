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

import org.jspecify.annotations.Nullable;

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

    static {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.FERMATA_ABOVE);
        FERMATA_WIDTH_SS = bbox.widthSs();
        FERMATA_HEIGHT_SS = bbox.heightSs();
    }

    /**
     * Creates a fermata attachment.
     */
    public FermataAttachment() {
        setAlignment(Alignment.CENTER);
    }

    /**
     * Creates a fermata attachment attached to a note.
     *
     * @param parent The parent note
     */
    public FermataAttachment(@Nullable StaffElement parent) {
        setOwnerElement(parent);
        setAlignment(Alignment.CENTER);
        // The line pointer is not set here. StaffElement.addAttachment — which every
        // caller reaches immediately — routes through LineElement.addChild, and that
        // owns it.
    }

    @Override
    protected Attachment createCopy(StaffElement newOwner) {
        return new FermataAttachment(newOwner);
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
}
