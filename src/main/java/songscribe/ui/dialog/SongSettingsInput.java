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
import songscribe.dom.Tempo;
import songscribe.font.DocumentFonts;

/**
 * Everything {@code SongSettingsDialog} shows, as values: the whole of what the dialog knows
 * about the document it is editing.
 *
 * <p>Read once per opening, by {@link DialogOps#read()}, and treated as a snapshot
 * from then on. The dialog is modal, so nothing can change the document underneath it, and
 * nothing here is re-read while it is up.
 *
 * <p>{@link SongSettingsOutput} is the answering half, and carries the same three things the
 * dialog can edit. The fourth component here is the lyrics context, which the dialog reads to
 * build its previews and never writes back.
 *
 * @param metadata     the song's descriptive metadata, split across the Title and Attribution tabs
 * @param fonts        the document's fonts. The dialog edits six of the eight roles and copies the
 *                     rest through, which is why the whole holder arrives rather than six fonts
 * @param tempo        the song's tempo — a copy, so editing the Music tab's controls cannot reach
 *                     the document's own mutable {@link Tempo}
 * @param lineWidthSs  the stored line width, in staff spaces, which the Title tab's previews wrap
 *                     at so they break where the score does. Read and never written back — the
 *                     width belongs to page setup, and there is no control for it here
 * @param lyrics       what the Title and Attribution tabs need to know about the lyrics, which
 *                     this dialog does not edit
 */
public record SongSettingsInput(
    SongMetadata metadata,
    DocumentFonts fonts,
    Tempo tempo,
    double lineWidthSs,
    LyricsContext lyrics
) {}
