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

    /** The normal-note count of a triplet: three notes in the time of two. */
    public static final int TRIPLET_NORMAL_NOTES = 2;

    /** The normal-note count of a duplet: two notes in the time of three. */
    public static final int DUPLET_NORMAL_NOTES = 3;

    private SpanFactory() {}

    /**
     * The ratio of {@code normalNotes} notes of {@code value}, undotted.
     *
     * @param normalNotes the notes the tuplet's time normally holds
     * @param value       the written value of one normal note
     * @return the ratio
     */
    public static TupletRatio ratioOf(int normalNotes, NoteValue value) {
        return new TupletRatio(normalNotes, new NotatableValue(value, Dots.NONE));
    }

    /**
     * A triplet of {@code value} over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose elements anchor and end the triplet
     * @param range the range the triplet covers
     * @param value the written value of one normal note
     * @return the triplet, with the ratio {@link #TRIPLET_NORMAL_NOTES} of {@code value}
     */
    public static Tuplet tripletOver(Line line, ElementRange range, NoteValue value) {
        return new Tuplet(
            line.getElement(range.begin()), line.getElement(range.end()), TupletGrade.TRIPLET,
            ratioOf(TRIPLET_NORMAL_NOTES, value));
    }

    /**
     * A duplet of {@code value} over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose elements anchor and end the duplet
     * @param range the range the duplet covers
     * @param value the written value of one normal note
     * @return the duplet, with the ratio {@link #DUPLET_NORMAL_NOTES} of {@code value}
     */
    public static Tuplet dupletOver(Line line, ElementRange range, NoteValue value) {
        return new Tuplet(
            line.getElement(range.begin()), line.getElement(range.end()), TupletGrade.DUPLET,
            ratioOf(DUPLET_NORMAL_NOTES, value));
    }

    /**
     * A trill over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose elements anchor and end the trill
     * @param range the range the trill covers
     * @return the trill
     */
    public static Trill trillOver(Line line, ElementRange range) {
        return new Trill(line.getElement(range.begin()), line.getElement(range.end()));
    }

    /**
     * A beam over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose notes anchor and end the beam
     * @param range the range the beam covers; both ends are notes
     * @return the beam
     * @throws ClassCastException if either end of {@code range} is not a note
     */
    public static Beam beamOver(Line line, ElementRange range) {
        return new Beam((NoteElement) line.getElement(range.begin()), (NoteElement) line.getElement(range.end()));
    }

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
