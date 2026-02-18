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

import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;

/**
 * Represents a fermata (pause/hold) attachment on a note.
 * <p>
 * Fermatas indicate that a note should be held longer than its written value.
 * They are typically placed above the note, centered on the note head.
 */
public class FermataAttachment extends Attachment {

    /** Default size for fermata symbol. */
    private static final double DEFAULT_SIZE = 16.0;

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
    public FermataAttachment(@Nullable Note parent) {
        setParentNote(parent);
        setAlignment(Alignment.CENTER);

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    @Override
    public double getContentWidth() {
        return DEFAULT_SIZE;
    }

    @Override
    public double getContentHeight() {
        return DEFAULT_SIZE;
    }
}
