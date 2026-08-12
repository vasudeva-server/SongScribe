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
import songscribe.font.DocumentFonts;

/**
 * What {@code SongSettingsDialog}'s four tabs say when OK is pressed — the answering half of
 * {@link SongSettingsInput}.
 *
 * <p><strong>Producible from any state the controls can reach.</strong> Every component here
 * exists whatever the user has done, including leaving the line-width field empty or
 * unparseable: {@link MusicChoices#lineWidth()} carries that text rather than refusing to
 * produce a value. Whether the values may be committed is
 * {@link SongSettingsBackEnd#validate}'s question.
 *
 * <p>The two records that span tabs are assembled by the dialog, because no tab holds all of
 * either: the title, number and subtitle come from the Title tab and the rest of the metadata
 * from the Attribution tab; four of the six edited font roles come from three different tabs.
 * That is also why the commit is the dialog's and not a tab's — split across tabs it would be
 * several undo steps for one edit.
 *
 * @param metadata the metadata the Title and Attribution tabs together describe
 * @param fonts    the document's fonts with the six edited roles replaced, the other two
 *                 carried through from {@link SongSettingsInput#fonts()}
 * @param music    what the Music tab's controls say
 */
public record SongSettingsOutput(
    SongMetadata metadata,
    DocumentFonts fonts,
    MusicChoices music
) {}
