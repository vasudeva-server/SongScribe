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
 * about the document. Read once per opening and treated as a snapshot; the dialog is modal, so
 * nothing changes the document underneath it.
 *
 * <p>{@link SongSettingsOutput} is the answering half. The line width and the lyrics context are
 * read-only: the dialog never writes them back.
 *
 * @param fonts       the dialog edits six of the eight roles and copies the rest through, which
 *                     is why the whole holder arrives
 * @param lineWidthSs  read and never written back — the width belongs to page setup
 */
public record SongSettingsInput(
    SongMetadata metadata,
    DocumentFonts fonts,
    Tempo tempo,
    Ss lineWidthSs,
    LyricsContext lyrics
) implements Copyable<SongSettingsInput> {

    /**
     * {@link DocumentFonts} is the one mutable component, so it is the one that is rebuilt.
     */
    @Override
    public SongSettingsInput copy() {
        return new SongSettingsInput(
            metadata, new DocumentFonts(fonts), tempo, lineWidthSs, lyrics);
    }
}
