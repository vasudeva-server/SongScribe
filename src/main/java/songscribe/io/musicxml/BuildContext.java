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
package songscribe.io.musicxml;

import org.audiveris.proxymusic.ObjectFactory;

import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LineLayoutProvider;

/**
 * Everything every builder in the write path needs, and nothing that varies per call.
 *
 * <p>Every builder method takes a {@code BuildContext} as its first parameter, plus only the
 * arguments that genuinely vary between calls ({@code Line}, {@code StaffElement}). Without it
 * each builder would grow its own five- or six-argument signature carrying these same values —
 * the shape that produced {@code NoteWriteContext} in the streaming writer.
 *
 * <p>{@code ObjectFactory} is stateless, so one instance is shared for the whole build.
 *
 * @param song the document being written
 * @param fonts the fonts in use, for font-dependent metrics
 * @param factory the shared {@code ObjectFactory} used to create every ProxyMusic node
 * @param layoutProvider supplies line layout for builders that need it
 * @param index handles into the {@code ScorePartwise} graph, recorded as the builders emit
 */
record BuildContext(
    Song song,
    DocumentFontsHolder fonts,
    ObjectFactory factory,
    LineLayoutProvider layoutProvider,
    BuildIndex index) {}
