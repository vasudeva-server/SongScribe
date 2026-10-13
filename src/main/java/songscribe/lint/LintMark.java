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
 * <p>A mark says only which element and which kind of mark; where it stands in the score is
 * worked out from the line's layout. Exactly two consumers switch over this type: drawing, in
 * {@code LineInvariants}, which decides what the score draws for each mark on an element, and
 * geometry, in {@code LineSelectionHandler}, which finds the box of the item a mark is drawn
 * on. Neither switch has a {@code default} branch, so a new kind of mark fails to compile in
 * both until it is drawn and placed. Every other reader treats a mark as a value: it compares
 * marks, or it calls {@link #element()}.
 *
 * <p>Only a {@link Flagged} mark competes for its element's color, where playing, selection and
 * hover each win over the lint color ({@code ColorPriority}). A squiggle and a question mark are
 * always drawn in the lint color.
 */
public sealed interface LintMark {

    /**
     * The element the finding names. Held as the object, not as an index, so the mark stays
     * on its element when an edit moves it.
     *
     * @return the offending element
     */
    StaffElement element();

    /**
     * The element itself is drawn in the lint color.
     *
     * @param element the offending element
     */
    record Flagged(StaffElement element) implements LintMark {
    }

    /**
     * A sawtooth squiggle is drawn under the element's annotation text; the text and the
     * element keep their own colors.
     *
     * @param element the element whose annotation is at fault
     */
    record FlaggedAnnotation(StaffElement element) implements LintMark {
    }

    /**
     * A question mark is drawn in the lint color in the lyric row of {@code script}, where the
     * element's syllable would stand.
     *
     * @param element the slot that lacks a lyric
     * @param script  the script that lacks it
     */
    record MissingLyric(StaffElement element, Song.Script script) implements LintMark {
    }
}
