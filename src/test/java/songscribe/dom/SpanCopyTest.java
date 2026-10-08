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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.editableLine;
import static songscribe.dom.SongFactory.lineOfQuavers;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.dupletOver;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * {@link Span#copy}: a copy is a new span of the same concrete type over the elements it is
 * given, and it carries the state that defines its kind.
 */
class SpanCopyTest extends UnitTest {

    /** Four quavers: the span every case copies covers the middle two. */
    private static final int LINE_LENGTH = 4;

    private static final ElementRange COPIED = new ElementRange(1, 2);

    /** A user offset a copy must carry, distinct from the zero a new span starts with. */
    private static final double USER_X_OFFSET_SS = 0.75;

    /** A user offset a copy must carry, distinct from {@link #USER_X_OFFSET_SS} and from zero. */
    private static final double USER_Y_OFFSET_SS = -1.25;

    /**
     * One kind of span.
     *
     * @param description the kind, as the test's display name
     * @param kind        the kind's concrete class
     * @param line        builds the line the span is over
     * @param source      makes a span of the kind on the line {@code line} builds
     */
    private record KindCase(
        String description,
        Class<? extends Span<?>> kind,
        Supplier<Line> line,
        Function<Line, Span<?>> source
    ) {

        /** A kind over {@link #COPIED} of a line of quavers. */
        KindCase(String description, Class<? extends Span<?>> kind, Function<Line, Span<?>> source) {
            this(description, kind, () -> lineOfQuavers(LINE_LENGTH), source);
        }

        Span<?> newSource() {
            return source.apply(line.get());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindCases")
    void testACopyIsANewSpanOfTheSameTypeOverTheGivenElements(KindCase testCase) {
        var source = testCase.newSource();
        var newAnchor = quaver();
        var newEnd = quaver();

        var copy = copyOver(source, newAnchor, newEnd);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getClass()).isEqualTo(source.getClass());
        assertThat(copy.getAnchorElement()).isSameAs(newAnchor);
        assertThat(copy.getEndElement()).isSameAs(newEnd);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindCases")
    void testACopyCarriesTheUserOffsetsOfItsSource(KindCase testCase) {
        var source = testCase.newSource();
        source.setUserXOffsetSs(USER_X_OFFSET_SS);
        source.setUserYOffsetSs(USER_Y_OFFSET_SS);

        var copy = copyOver(source, quaver(), quaver());

        assertThat(copy.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
        assertThat(copy.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
    }

    @ParameterizedTest
    @EnumSource(Hairpin.Kind.class)
    void testAHairpinCopyKeepsItsKind(Hairpin.Kind kind) {
        var source = hairpinOver(lineOfQuavers(LINE_LENGTH), kind, COPIED);

        var copy = (Hairpin) copyOver(source, quaver(), quaver());

        assertThat(copy.getKind()).isEqualTo(kind);
    }

    @Test
    void testCasesCoverEveryConcreteSpanKind() {
        var permittedKinds = Span.class.getPermittedSubclasses();

        assertThat(permittedKinds).as("every permitted kind is a leaf, so each is a kind a row must cover")
            .allSatisfy(kind -> assertThat(kind.getPermittedSubclasses()).isNull());
        assertThat(kindCases().<Class<?>>map(KindCase::kind).distinct())
            .containsExactlyInAnyOrder(permittedKinds);
    }

    static Stream<KindCase> kindCases() {
        return Stream.of(
            new KindCase("a beam", Beam.class, line -> beamOver(line, COPIED)),
            new KindCase("a trill", Trill.class, line -> trillOver(line, COPIED)),
            new KindCase("a tuplet", Tuplet.class, line -> dupletOver(line, COPIED, NoteValue.EIGHTH)),
            new KindCase("a crescendo", Hairpin.class, line -> hairpinOver(line, Hairpin.Kind.CRESCENDO, COPIED)),
            new KindCase("a diminuendo", Hairpin.class, line -> hairpinOver(line, Hairpin.Kind.DIMINUENDO, COPIED)),
            new KindCase("an ending", Ending.class, SpanCopyTest::newEndingLine, SpanCopyTest::endingOnLine)
        );
    }

    /** An empty line of a real song, which an ending needs for its section rule. */
    private static Line newEndingLine() {
        return editableLine();
    }

    /** An ending over the whole of {@code line}, to which it adds a note, a right repeat and two notes. */
    private static Ending endingOnLine(Line line) {
        line.addElement(crotchet());
        line.addElement(repeatRight());
        line.addElement(crotchet());
        line.addElement(crotchet());

        return endingOver(line, wholeRangeOf(line));
    }

    @Test
    void testATupletCopyKeepsTheGradeAndTheRatio() {
        var source = dupletOver(lineOfQuavers(LINE_LENGTH), COPIED, NoteValue.EIGHTH);

        var copy = (Tuplet) source.copy(quaver(), quaver());

        assertThat(copy.getGrade()).isEqualTo(source.getGrade());
        assertThat(copy.getRatio()).isEqualTo(source.getRatio());
    }

    /** Copies {@code source} over the given elements, which must suit its endpoint type. */
    private static <E extends StaffElement> Span<E> copyOver(Span<E> source, StaffElement anchor, StaffElement end) {
        return source.copy(source.narrowEndpoint(anchor), source.narrowEndpoint(end));
    }
}
