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

import java.awt.FontMetrics;

import org.jspecify.annotations.Nullable;

import songscribe.music.BeatChange;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLMetadata;

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
     * Computes the content width from the actual beat change glyphs and "=" sign.
     *
     * @param attrFontMetrics font metrics for the attribution font (used for the "=" sign)
     * @return width in staff-space units
     */
    public double computeContentWidthSs(FontMetrics attrFontMetrics) {
        var metadata = SMuFLMetadata.getInstance();
        var scale = ScaleContext.getInstance();

        double widthSs = noteWidthSs(beatChange.duration().getNote(), metadata);
        widthSs += EQUALS_GAP_SS;
        widthSs += scale.fromPixels(attrFontMetrics.stringWidth("="));
        widthSs += EQUALS_GAP_SS;
        widthSs += noteWidthSs(beatChange.beat().getNote(), metadata);

        return widthSs;
    }

}
