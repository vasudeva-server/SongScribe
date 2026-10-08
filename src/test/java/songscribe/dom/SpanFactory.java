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

import java.util.List;
import java.util.function.Supplier;

import songscribe.dom.StaffElementRun.ElementRange;

import static songscribe.dom.SongFactory.buildSong;

/** Shared factory methods for spans used across test classes. */
public final class SpanFactory {

    /** The normal-note count of a triplet: three notes in the time of two. */
    public static final int TRIPLET_NORMAL_NOTES = 2;

    /** The normal-note count of a duplet: two notes in the time of three. */
    public static final int DUPLET_NORMAL_NOTES = 3;

    /**
     * The beat a test tuplet is judged against. The span is judged under
     * {@link TupletValidator.Strictness#LENIENT}, which does not apply the beat barrier, so the
     * beat only names the site.
     */
    private static final BeatUnit TUPLET_BEAT = BeatUnit.CROTCHET;

    private SpanFactory() {}

    /**
     * The ratio of {@code normalNotes} notes of {@code value}, undotted.
     *
     * @param normalNotes the notes the tuplet's time normally holds
     * @param value       the written value of one normal note
     * @return the ratio
     */
    public static TupletNormalTime ratioOf(int normalNotes, NoteValue value) {
        return new TupletNormalTime(normalNotes, new NotatableValue(value, Dots.NONE));
    }

    /**
     * A triplet of {@code value} over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose elements anchor and end the triplet
     * @param range the range the triplet covers
     * @param value the written value of one normal note
     * @return the triplet, with the ratio {@link #TRIPLET_NORMAL_NOTES} of {@code value}
     * @throws AssertionError if {@link Tuplet#spanningStated} refuses the range
     */
    public static Tuplet tripletOver(Line line, ElementRange range, NoteValue value) {
        return tupletOver(line, range, TupletGrade.TRIPLET, ratioOf(TRIPLET_NORMAL_NOTES, value));
    }

    /**
     * A duplet of {@code value} over {@code range} of {@code line}, not yet added to it.
     *
     * @param line  the line whose elements anchor and end the duplet
     * @param range the range the duplet covers
     * @param value the written value of one normal note
     * @return the duplet, with the ratio {@link #DUPLET_NORMAL_NOTES} of {@code value}
     * @throws AssertionError if {@link Tuplet#spanningStated} refuses the range
     */
    public static Tuplet dupletOver(Line line, ElementRange range, NoteValue value) {
        return tupletOver(line, range, TupletGrade.DUPLET, ratioOf(DUPLET_NORMAL_NOTES, value));
    }

    /** The tuplet of {@code grade} with the stated {@code ratio} over {@code range}, not yet added. */
    private static Tuplet tupletOver(Line line, ElementRange range, TupletGrade grade, TupletNormalTime ratio) {
        var site = new Tuplet.Site(line, range, TUPLET_BEAT);

        return switch (Tuplet.spanningStated(site, grade, ratio, TupletValidator.Strictness.LENIENT)) {
            case Spanning.Spanned<Tuplet> spanned -> spanned.span();
            case Spanning.Refused<Tuplet> refused ->
                throw new AssertionError("no " + grade + " over " + range + ": " + refused.reasonKey());
        };
    }

    /**
     * Builds a span with {@code build} and adds it to its line with the song's mutation tracking
     * suspended, as a fixture does before a test edits the line.
     *
     * @param song  the song the span's line belongs to
     * @param build builds the span, not yet added
     * @param <S>   the kind of span built
     * @return the span, now added
     */
    public static <S extends Span<?>> S addWithoutTracking(Song song, Supplier<S> build) {
        return song.withoutMutationTrackingResult(() -> {
            var span = build.get();

            span.addTo();

            return span;
        });
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
     * @throws AssertionError if {@link Beam#spanning} refuses the range
     */
    public static Beam beamOver(Line line, ElementRange range) {
        return switch (Beam.spanning(line, range)) {
            case Spanning.Spanned<Beam> spanned -> spanned.span();
            case Spanning.Refused<Beam> refused ->
                throw new AssertionError("no beam over " + range + ": " + refused.reasonKey());
        };
    }

    /**
     * A new one-line song of {@code notes}, with a beam over all of them added with the song's
     * mutation tracking suspended.
     *
     * @param notes the line's elements, in order; the first and the last anchor and end the beam
     * @return the song
     * @throws AssertionError if {@link Beam#spanning} refuses the range
     */
    public static Song beamedSong(List<NoteElement> notes) {
        var song = buildSong(line -> notes.forEach(line::addElement));
        var line = song.getLine(0);

        addWithoutTracking(song, () -> beamOver(line, new ElementRange(0, notes.size() - 1)));

        return song;
    }

    /**
     * The beam of {@link #beamedSong} over {@code notes}.
     *
     * @param notes the line's elements, in order; the first and the last anchor and end the beam
     * @return the beam, added to its song's one line
     * @throws AssertionError if {@link Beam#spanning} refuses the range
     */
    public static Beam beamOverAll(List<NoteElement> notes) {
        var beam = beamedSong(notes).getLine(0).spanRegistry().findBeamAt(0);

        if (beam == null) {
            throw new AssertionError("no beam at the first note of " + notes);
        }

        return beam;
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
