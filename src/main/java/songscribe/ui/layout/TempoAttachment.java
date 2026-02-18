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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.Tempo;

/**
 * Represents a tempo marking attachment on a note.
 * <p>
 * Tempo attachments display tempo changes (e.g., "♩ = 120" or "Allegro").
 * They are typically placed above the staff.
 */
public class TempoAttachment extends Attachment {

    /** Default width for tempo markings. */
    private static final double DEFAULT_WIDTH = 60.0;

    /** Default height for tempo markings. */
    private static final double DEFAULT_HEIGHT = 20.0;

    /** The tempo data. */
    private @NotNull Tempo tempo;

    /**
     * Creates a tempo attachment with the specified tempo.
     *
     * @param tempo The tempo data
     */
    public TempoAttachment(@NotNull Tempo tempo) {
        this.tempo = tempo;
        setAlignment(Alignment.LEFT);
    }

    /**
     * Creates a tempo attachment attached to a note.
     *
     * @param parent The parent note
     * @param tempo  The tempo data
     */
    public TempoAttachment(@Nullable Note parent, @NotNull Tempo tempo) {
        this.tempo = tempo;
        setParentNote(parent);
        setAlignment(Alignment.LEFT);

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the tempo data.
     */
    public @NotNull Tempo getTempo() {
        return tempo;
    }

    /**
     * Sets the tempo data.
     */
    public void setTempo(@NotNull Tempo tempo) {
        this.tempo = tempo;
    }

    /**
     * Returns the visible tempo value (BPM).
     */
    public int getVisibleTempo() {
        return tempo.getVisibleTempo();
    }

    /**
     * Returns the tempo description text.
     */
    public @Nullable String getTempoDescription() {
        return tempo.getTempoDescription();
    }

    /**
     * Returns whether the tempo should be shown.
     */
    public boolean shouldShowTempo() {
        return tempo.shouldShowTempo();
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
