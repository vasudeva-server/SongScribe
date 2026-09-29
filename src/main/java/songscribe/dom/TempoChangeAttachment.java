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
 * Represents a tempo marking attachment on a note.
 * <p>
 * Tempo attachments display tempo changes (e.g., "♩ = 120" or "Allegro").
 * They are typically placed above the staff.
 */
public final class TempoChangeAttachment extends MetronomeAttachment {

    private Tempo tempo;

    public TempoChangeAttachment(Tempo tempo) {
        this.tempo = tempo;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The clipboard is the only caller that copies a tempo into a different beat context,
     * and paste re-validates the tuplets it carries in {@code PasteSpanReconciliation}.
     */
    @Override
    protected Attachment createCopy() {
        return new TempoChangeAttachment(tempo);
    }

    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Replaces the tempo this attachment marks.
     *
     * <p>Stays public because the tempo dialog lives in the UI package. A change of beat can
     * invalidate tuplets; the tuplet beat rule finds them when the modification bracket closes.
     */
    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }
}
