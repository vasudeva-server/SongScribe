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

package songscribe.lint;

import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Where a lint finding sits in the score, and what the score view draws there.
 *
 * <p>Two consumers switch over this type, drawing ({@code LineInvariants}) and geometry
 * ({@code LineSelectionHandler}), neither with a {@code default} branch, so a new kind fails to
 * compile in both until it is drawn and placed.
 *
 * <p>Only a {@link Flagged} mark competes for its element's color, where playing, selection and
 * hover each win over the lint color ({@code ColorPriority}).
 */
public sealed interface LintMark {

    /** Held as the object, not an index, so the mark stays on its element when an edit moves it. */
    StaffElement element();

    record Flagged(StaffElement element) implements LintMark {
    }

    /** The text and the element keep their own colors. */
    record FlaggedAnnotation(StaffElement element) implements LintMark {
    }

    /** Drawn in the lyric row of {@code script}, where the element's syllable would stand. */
    record MissingLyric(StaffElement element, Song.Script script) implements LintMark {
    }
}
