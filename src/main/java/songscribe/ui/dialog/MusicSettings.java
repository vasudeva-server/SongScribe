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

import songscribe.dom.Tempo;

/**
 * The three document settings the Song Settings Music tab shows, as the document holds them.
 *
 * <p>{@link MusicChoices} is the same three settings coming back out of the controls. They are
 * separate types because the line width is not: on the way in it is the stored width, on the
 * way out it is unvalidated field text.
 *
 * @param tempo        the song's tempo — a copy, so editing the controls cannot reach the
 *                     document's own mutable {@link Tempo}
 * @param key          the song's default key signature, canonicalized to an entry the combo
 *                     actually offers
 * @param lineWidthSs  the stored line width, in staff spaces
 */
public record MusicSettings(Tempo tempo, KeySelection key, double lineWidthSs) {}
