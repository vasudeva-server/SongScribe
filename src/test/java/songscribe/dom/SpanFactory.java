/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import songscribe.dom.StaffElementRun.ElementRange;

/** Shared factory methods for spans used across test classes. */
public final class SpanFactory {

    private SpanFactory() {}

    /**
     * The ending {@link Ending#spanning} builds over {@code range} of {@code line}, not yet added
     * to it. For a fixture whose range must hold an ending: a refusal fails the test with the
     * reason it gives.
     *
     * @param line  a line of a real song
     * @param range the range the ending covers
     * @return the ending over {@code range}
     * @throws AssertionError if {@link Ending#spanning} refuses the range
     */
    public static Ending endingOver(Line line, ElementRange range) {
        return switch (Ending.spanning(line, range)) {
            case Spanning.Spanned<Ending> spanned -> spanned.span();
            case Spanning.Refused<Ending> refused ->
                throw new AssertionError("no ending over " + range + ": " + refused.reasonKey());
        };
    }

    /**
     * The hairpin {@link Hairpin#spanning} builds over {@code range} of {@code line}, not yet
     * added to it. For a fixture whose range must hold a hairpin: a refusal fails the test with
     * the reason it gives.
     *
     * @param line  the line whose elements anchor and end the hairpin
     * @param kind  the hairpin's kind
     * @param range the range the hairpin covers
     * @return the hairpin over {@code range}
     * @throws AssertionError if {@link Hairpin#spanning} refuses the range
     */
    public static Hairpin hairpinOver(Line line, Hairpin.Kind kind, ElementRange range) {
        return switch (Hairpin.spanning(line, kind, range)) {
            case Spanning.Spanned<Hairpin> spanned -> spanned.span();
            case Spanning.Refused<Hairpin> refused ->
                throw new AssertionError("no " + kind + " over " + range + ": " + refused.reasonKey());
        };
    }
}
