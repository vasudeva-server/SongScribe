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

import org.jspecify.annotations.Nullable;

import songscribe.music.BeatChange;
import songscribe.music.StaffElement;

/**
 * Represents a beat change (metric modulation) attachment on a note.
 * <p>
 * Beat changes indicate tempo relationships between different note values
 * (e.g., ♩ = ♩. meaning "quarter note equals dotted quarter").
 * They are typically placed above the staff.
 */
public class BeatChangeAttachment extends Attachment {

    /** Default width for beat change markings. */
    private static final double DEFAULT_WIDTH = 50.0;

    /** Default height for beat change markings. */
    private static final double DEFAULT_HEIGHT = 20.0;

    /** The beat change data. */
    private BeatChange beatChange;

    /**
     * Creates a beat change attachment with the specified change.
     *
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(BeatChange beatChange) {
        this.beatChange = beatChange;
        setAlignment(Alignment.CENTER);
    }

    /**
     * Creates a beat change attachment attached to a note.
     *
     * @param parent     The parent note
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(@Nullable StaffElement parent, BeatChange beatChange) {
        this.beatChange = beatChange;
        setOwnerElement(parent);
        setAlignment(Alignment.CENTER);

        if (parent != null) {
            setOwnerElement(parent);
            setParentLine(parent.getParentLine());
        }
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
     * Returns the tempo change multiplier.
     */
    public float getTempoChangeMultiplier() {
        return beatChange.getTempoChange();
    }

    @Override
    public double getContentWidth() {
        return DEFAULT_WIDTH;
    }

    @Override
    public double getContentHeight() {
        return DEFAULT_HEIGHT;
    }
}
