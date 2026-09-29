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

/**
 * Represents a beat change (metric modulation) attachment on a note.
 * <p>
 * Beat changes indicate tempo relationships between different note values
 * (e.g., ♩ = ♩. meaning "quarter note equals dotted quarter").
 * They are typically placed above the staff.
 */
public final class BeatChangeAttachment extends MetronomeAttachment {

    /** The beat change data. */
    private BeatChange beatChange;

    /**
     * Creates a beat change attachment with the specified change.
     *
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(BeatChange beatChange) {
        this.beatChange = beatChange;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The clipboard is the only caller that copies a beat change into a different context,
     * and paste re-validates the tuplets it carries in {@code PasteSpanReconciliation}.
     */
    @Override
    protected Attachment createCopy() {
        return new BeatChangeAttachment(beatChange);
    }

    /**
     * Returns the beat change data.
     */
    public BeatChange getBeatChange() {
        return beatChange;
    }

    /**
     * Replaces the beat change data.
     *
     * <p>Stays public because the beat-change dialog lives in the UI package. A change of beat
     * can invalidate tuplets; the tuplet beat rule finds them when the modification bracket
     * closes.
     */
    public void setBeatChange(BeatChange beatChange) {
        this.beatChange = beatChange;
    }
}
