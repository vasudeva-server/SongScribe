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

package songscribe.ui.layout;

import java.awt.Font;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import songscribe.music.BeatChange;
import songscribe.music.StaffElement;
import songscribe.util.GraphicUtils;

/**
 * Represents a beat change (metric modulation) attachment on a note.
 * <p>
 * Beat changes indicate tempo relationships between different note values
 * (e.g., ♩ = ♩. meaning "quarter note equals dotted quarter").
 * They are typically placed above the staff.
 */
public class BeatChangeAttachment extends MetronomeAttachment {

    /** The beat change data. */
    private BeatChange beatChange;

    /**
     * Creates a beat change attachment with the specified change.
     *
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(BeatChange beatChange) {
        super(Alignment.CENTER);
        this.beatChange = beatChange;
    }

    /**
     * Creates a beat change attachment attached to a note.
     *
     * @param parent     The parent note
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(@Nullable StaffElement parent, BeatChange beatChange) {
        super(parent, Alignment.CENTER);
        this.beatChange = beatChange;
    }

    @Override
    public Attachment copy(StaffElement newOwner) {
        return new BeatChangeAttachment(newOwner, beatChange);
    }

    /**
     * Returns the beat change data.
     */
    public BeatChange getBeatChange() {
        return beatChange;
    }

    /**
     * Sets the beat change data.
     */
    public void setBeatChange(BeatChange beatChange) {
        this.beatChange = beatChange;
    }

    /**
     * Computes the width and per-sub-region collision geometry for the beat change.
     * <p>
     * The three sub-regions are the left note glyph (duration), the "=" sign, and the
     * right note glyph (beat). The "=" sign is vertically aligned to the note cap-height,
     * so its descender extends below {@code QUARTER_NOTE_HEIGHT_SS} and must be accounted
     * for separately to prevent collisions with elements below.
     *
     * @param attrFont the attribution font (used for the "=" sign)
     * @return width and collision sub-regions in staff-space units
     */
    @Override
    public ContentMetrics computeContentMetrics(Font attrFont) {
        var regions = new ArrayList<CollisionRegion>(3);

        var leftNoteWidthSs = noteWidthSs(beatChange.duration().getNote());
        var equalsWidthSs = ScaleContext.textWidthSs(attrFont, "=");
        var rightNoteWidthSs = noteWidthSs(beatChange.beat().getNote());

        regions.add(new CollisionRegion(0, 0, leftNoteWidthSs, QUARTER_NOTE_HEIGHT_SS));

        var equalsLm = attrFont.getLineMetrics("=", GraphicUtils.SCREEN_FRC);
        var equalsAscentSs = ScaleContext.pxToSs(equalsLm.getAscent());
        var equalsDescentSs = ScaleContext.pxToSs(equalsLm.getDescent());
        var equalsXOffsetSs = leftNoteWidthSs;
        var equalsYOffsetSs = QUARTER_NOTE_HEIGHT_SS - equalsAscentSs;
        regions.add(new CollisionRegion(
            equalsXOffsetSs, equalsYOffsetSs, equalsWidthSs, equalsAscentSs + equalsDescentSs));

        var rightNoteXOffsetSs = equalsXOffsetSs + equalsWidthSs;
        regions.add(new CollisionRegion(rightNoteXOffsetSs, 0, rightNoteWidthSs, QUARTER_NOTE_HEIGHT_SS));

        var totalWidthSs =
            leftNoteWidthSs + equalsWidthSs + rightNoteWidthSs;

        return new ContentMetrics(totalWidthSs, regions);
    }
}
