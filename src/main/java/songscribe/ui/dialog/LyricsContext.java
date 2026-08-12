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

/**
 * The two things Song Settings needs to know about the song's lyrics, neither of which it
 * edits.
 *
 * <p>They are here rather than read from the song because they drive controls: the Take button
 * derives a title from {@link #text()} and is disabled without it, and the unofficial-
 * translation checkbox is shown only when there is a translation for it to be about. Both are
 * facts about the document as it stands when the dialog opens.
 *
 * @param text          every syllable of the song's lyrics run together, as
 *                      {@code Song.getLyricsText} builds it; empty when the song has none
 * @param hasTranslation whether the song carries translated lyrics
 */
public record LyricsContext(String text, boolean hasTranslation) {}
