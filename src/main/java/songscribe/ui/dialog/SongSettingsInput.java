/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import songscribe.dom.SongMetadata;
import songscribe.dom.Ss;
import songscribe.dom.Tempo;
import songscribe.font.DocumentFonts;
import songscribe.util.Copyable;

/**
 * Everything {@code SongSettingsDialog} shows, as values: the whole of what the dialog knows
 * about the document it is editing.
 *
 * <p>Read once per opening, by {@link DialogOps#read()}, and treated as a snapshot
 * from then on. The dialog is modal, so nothing can change the document underneath it, and
 * nothing here is re-read while it is up.
 *
 * <p>{@link SongSettingsOutput} is the answering half, and carries the same three things the
 * dialog can edit. The last two components here — the line width and the lyrics context — are
 * read-only: the dialog measures its previews against them and never writes them back.
 *
 * @param metadata     the song's descriptive metadata, split across the Title and Attribution tabs
 * @param fonts        the document's fonts. The dialog edits six of the eight roles and copies the
 *                     rest through, which is why the whole holder arrives rather than six fonts
 * @param tempo        the song's tempo
 * @param lineWidthSs  the stored line width, as an {@link Ss}, which the Title tab's previews
 *                     wrap at so they break where the score does. Read and never written back —
 *                     the width belongs to page setup, and there is no control for it here
 * @param lyrics       what the Title and Attribution tabs need to know about the lyrics, which
 *                     this dialog does not edit
 */
public record SongSettingsInput(
    SongMetadata metadata,
    DocumentFonts fonts,
    Tempo tempo,
    Ss lineWidthSs,
    LyricsContext lyrics
) implements Copyable<SongSettingsInput> {

    /**
     * {@inheritDoc}
     *
     * <p>{@link DocumentFonts} is the one mutable component, so it is the one that is rebuilt;
     * every other component is a value the document can safely share.
     *
     * @return an input whose fonts the document does not share, so the Fonts tab edits this
     *         dialog's own values until OK
     */
    @Override
    public SongSettingsInput copy() {
        return new SongSettingsInput(
            metadata, new DocumentFonts(fonts), tempo, lineWidthSs, lyrics);
    }
}
