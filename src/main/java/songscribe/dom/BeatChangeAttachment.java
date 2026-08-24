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
     * {@inheritDoc}
     *
     * <p>Deliberately <em>not</em> routed through
     * {@link Song#withBeatDefiningEdit}: the new owner is not yet in the document, so there
     * is no position to validate from. The clipboard is the only caller that copies a beat
     * change into a different context, and paste re-validates the tuplets it carries in
     * {@code PasteSpanReconciliation}.
     */
    @Override
    protected Attachment createCopy(StaffElement newOwner) {
        return new BeatChangeAttachment(newOwner, beatChange);
    }

    /**
     * Returns the beat change data.
     */
    public BeatChange getBeatChange() {
        return beatChange;
    }

    /**
     * Replaces the beat change data, dropping any tuplet the new beat invalidates.
     *
     * <p>Stays public because the beat-change dialog lives in the UI package. The write
     * itself is routed through {@link Song#withBeatDefiningEdit}, so there is nothing to
     * bypass; a caller that wants to know whether tuplets were removed should wrap its own
     * edit in that helper and read its result.
     */
    public void setBeatChange(BeatChange beatChange) {
        Song.withBeatDefiningEditOn(getOwnerElement(), () -> this.beatChange = beatChange);
    }
}
