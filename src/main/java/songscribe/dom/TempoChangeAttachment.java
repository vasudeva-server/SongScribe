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

import java.awt.Font;

import org.jspecify.annotations.Nullable;

/**
 * Represents a tempo marking attachment on a note.
 * <p>
 * Tempo attachments display tempo changes (e.g., "♩ = 120" or "Allegro").
 * They are typically placed above the staff.
 */
public final class TempoChangeAttachment extends MetronomeAttachment {

    private Tempo tempo;

    public TempoChangeAttachment(Tempo tempo) {
        super(Alignment.LEFT);
        this.tempo = tempo;
    }

    public TempoChangeAttachment(@Nullable StaffElement parent, Tempo tempo) {
        super(parent, Alignment.LEFT);
        this.tempo = tempo;
    }

    /**
     * Copies this tempo change onto a new owner. Deliberately <em>not</em> routed through
     * {@link Song#withBeatDefiningEdit}: the new owner is not yet in the document, so there
     * is no position to validate from. The clipboard is the only caller that copies a tempo
     * into a different beat context, and paste re-validates the tuplets it carries in
     * {@code PasteSpanReconciliation}.
     */
    @Override
    public Attachment copy(StaffElement newOwner) {
        return new TempoChangeAttachment(newOwner, tempo.copy());
    }

    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Replaces the tempo this attachment marks, dropping any tuplet the new beat
     * invalidates.
     *
     * <p>Stays public because the tempo dialog lives in the UI package. The write itself is
     * routed through {@link Song#withBeatDefiningEdit}, so there is nothing to bypass; a
     * caller that wants to know whether tuplets were removed should wrap its own edit in
     * that helper and read its result.
     */
    public void setTempo(Tempo tempo) {
        Song.withBeatDefiningEditOn(getOwnerElement(), () -> this.tempo = tempo);
    }

    @Override
    public ContentMetrics computeContentMetrics(Font attrFont) {
        return TempoContent.metrics(tempo, attrFont);
    }

}
