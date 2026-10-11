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

import songscribe.dom.StaffElement;

/**
 * Where a lint finding sits in the score, and what the score view draws there.
 *
 * <p>Every consumer that draws a mark switches over this type without a {@code default}
 * branch, so a new mark fails to compile until each consumer draws it. A consumer that needs
 * only the element calls {@link #element()}.
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
     * A question mark is drawn in the lint color in the lyric row of {@code verse}, where the
     * element's syllable would stand.
     *
     * @param element the slot that lacks a lyric
     * @param verse   the verse that lacks it, 1-based as {@code Lyric.verse()} is
     */
    record MissingLyric(StaffElement element, int verse) implements LintMark {
    }
}
