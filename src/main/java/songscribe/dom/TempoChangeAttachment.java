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
 * A tempo marking attachment on a note.
 */
public final class TempoChangeAttachment extends MetronomeAttachment {

    private Tempo tempo;

    public TempoChangeAttachment(Tempo tempo) {
        this.tempo = tempo;
    }

    /**
     * The clipboard copies a tempo into a different beat context, and paste re-validates the
     * tuplets it carries in {@code PasteSpanReconciliation}.
     */
    @Override
    protected Attachment createCopy() {
        return new TempoChangeAttachment(tempo);
    }

    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Stays public because the tempo dialog lives in the UI package.
     */
    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }
}
