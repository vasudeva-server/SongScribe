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

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * {@link Span#copy}: a copy is a new span of the same concrete type over the elements it is
 * given, and it carries the state that defines its kind.
 */
class SpanCopyTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** Four quavers: the span every case copies covers the middle two. */
    private static final int LINE_LENGTH = 4;

    private static final ElementRange COPIED = new ElementRange(1, 2);

    /**
     * One kind of span over {@link #COPIED}.
     *
     * @param description the kind, as the test's display name
     * @param source      makes a span of the kind over {@link #COPIED} of a line
     */
    private record KindCase(String description, Function<Line, Span<?>> source) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindCases")
    void testACopyIsANewSpanOfTheSameTypeOverTheGivenElements(KindCase testCase) {
        var source = testCase.source().apply(lineOfQuavers());
        var newAnchor = quaver();
        var newEnd = quaver();

        var copy = copyOver(source, newAnchor, newEnd);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getClass()).isEqualTo(source.getClass());
        assertThat(copy.getAnchorElement()).isSameAs(newAnchor);
        assertThat(copy.getEndElement()).isSameAs(newEnd);
    }

    static Stream<KindCase> kindCases() {
        return Stream.of(
            new KindCase("a beam", line -> beamOver(line, COPIED)),
            new KindCase("a trill", line -> trillOver(line, COPIED)),
            new KindCase("a tuplet", line -> tripletOver(line, COPIED, NoteValue.EIGHTH)),
            new KindCase("a crescendo", line -> hairpinOver(line, Hairpin.Kind.CRESCENDO, COPIED)),
            new KindCase("a diminuendo", line -> hairpinOver(line, Hairpin.Kind.DIMINUENDO, COPIED))
        );
    }

    @Test
    void testATupletCopyKeepsTheGradeAndTheRatio() {
        var source = new Tuplet(quaver(), quaver(), TupletGrade.DUPLET,
            ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.SIXTEENTH));

        var copy = (Tuplet) source.copy(quaver(), quaver());

        assertThat(copy.getGrade()).isEqualTo(source.getGrade());
        assertThat(copy.getRatio()).isEqualTo(source.getRatio());
    }

    private static Line lineOfQuavers() {
        var song = buildSong(line -> {
            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(quaver());
            }
        });

        return song.getLine(FIRST_LINE);
    }

    /** Copies {@code source} over the given elements, which must suit its endpoint type. */
    private static <E extends StaffElement> Span<E> copyOver(Span<E> source, StaffElement anchor, StaffElement end) {
        return source.copy(source.narrowEndpoint(anchor), source.narrowEndpoint(end));
    }
}
