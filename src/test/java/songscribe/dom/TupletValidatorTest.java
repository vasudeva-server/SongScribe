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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.TupletValidator.Reason;
import songscribe.dom.TupletValidator.Result;
import songscribe.dom.TupletValidator.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.DOTTED_BEAT_TEMPO;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.minimRest;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The verdict {@link TupletValidator} gives a span, under each {@link Strictness}: the ratio it
 * derives when the span can carry a tuplet of the grade, otherwise the first reason it cannot. A
 * ratio a file stated is echoed when the span's elements and its value agree with it, and needs
 * no conventional span.
 *
 * <p>The ratio, the fermata, the pitched-note count, the conventional span and the redundancy
 * test hold under both strictnesses. The beat barrier and the structural boundary hold under
 * {@link Strictness#STRICT} only.
 */
class TupletValidatorTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The middle of the three notes a triplet spans. */
    private static final int MIDDLE_INDEX = 1;

    private static final int TRIPLET_NOTES = 3;
    private static final int QUADRUPLET_NOTES = 4;

    /** Three quavers under a 3 in a crotchet beat: two quavers' time. */
    private static final TupletRatio TRIPLET_OF_QUAVERS = ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.EIGHTH);

    /**
     * One span, one grade, and the verdict under one strictness.
     *
     * @param description the case, as the test's display name
     * @param elements    builds the span's elements, all of the line the span is on
     * @param grade       the printed tuplet number
     * @param stated      the ratio a file stated, or {@code null} to derive one
     * @param strictness  how much of the rule set to enforce
     * @param expected    the verdict
     */
    private record ValidationCase(
        String description,
        Supplier<List<StaffElement>> elements,
        TupletGrade grade,
        @Nullable TupletRatio stated,
        Strictness strictness,
        Result expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationCases")
    void testASpanIsValidOrNamesTheFirstReasonItFails(ValidationCase testCase) {
        var elements = testCase.elements().get();
        var song = buildSong(line -> elements.forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);
        var span = new ElementRange(0, elements.size() - 1);
        var stated = testCase.stated();

        var result = stated == null
            ? TupletValidator.validateDerived(line, span, testCase.grade(), testCase.strictness())
            : TupletValidator.validateStated(
                TupletValidator.describeSpan(line, span), testCase.grade(), stated, testCase.strictness());

        assertThat(result).isEqualTo(testCase.expected());
    }

    @Test
    void testCasesCoverEveryReasonAndEveryStrictness() {
        var cases = validationCases().toList();

        assertThat(cases.stream()
            .map(ValidationCase::expected)
            .filter(Result.Invalid.class::isInstance)
            .map(result -> ((Result.Invalid) result).reason())
            .distinct())
            .containsExactlyInAnyOrder(Reason.values());
        assertThat(cases.stream().map(ValidationCase::strictness).distinct())
            .containsExactlyInAnyOrder(Strictness.values());
    }

    static Stream<ValidationCase> validationCases() {
        return Stream.of(
            derivedUnderBoth("three quavers under a 3 give 3:2",
                () -> quavers(TRIPLET_NOTES), TupletGrade.TRIPLET, valid(TRIPLET_OF_QUAVERS)),
            derivedUnderBoth("a span of grace notes holds no time",
                () -> List.of(graceQuaver(), graceQuaver()), TupletGrade.TRIPLET, invalid(Reason.EMPTY_SPAN)),
            derivedUnderBoth("two crotchets do not divide into three notatable values",
                () -> List.of(crotchet(), crotchet()), TupletGrade.TRIPLET, invalid(Reason.NOT_NOTATABLE)),
            derivedUnderBoth("three quavers under a dotted beat have no conventional span",
                TupletValidatorTest::dottedBeatQuavers, TupletGrade.TRIPLET, invalid(Reason.NO_CONVENTIONAL_SPAN)),
            derivedUnderBoth("four quavers under a 4 are a power of two",
                () -> quavers(QUADRUPLET_NOTES), TupletGrade.QUADRUPLET, invalid(Reason.POWER_OF_TWO_RATIO)),
            derivedUnderBoth("a fermata inside the span",
                TupletValidatorTest::fermataQuavers, TupletGrade.TRIPLET, invalid(Reason.FERMATA)),
            derivedUnderBoth("one note and two rests hold too few notes",
                () -> List.of(crotchet(), crotchetRest(), crotchetRest()),
                TupletGrade.TRIPLET, invalid(Reason.TOO_FEW_NOTES)),
            derivedUnderBoth("three rests hold too few notes",
                () -> List.of(crotchetRest(), crotchetRest(), crotchetRest()),
                TupletGrade.TRIPLET, invalid(Reason.TOO_FEW_NOTES)),
            derivedUnderBoth("a note, a grace note and a rest hold too few notes",
                () -> List.of(crotchet(), graceQuaver(), minimRest()),
                TupletGrade.TRIPLET, invalid(Reason.TOO_FEW_NOTES)),

            derived("a beat change inside the span is a barrier",
                TupletValidatorTest::beatChangeInside, Strictness.STRICT, invalid(Reason.BEAT_BARRIER)),
            derived("a beat change inside the span is no barrier",
                TupletValidatorTest::beatChangeInside, Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a barline inside the span is a structural boundary",
                () -> withInside(singleBarline()), Strictness.STRICT, invalid(Reason.STRUCTURAL_BOUNDARY)),
            derived("a barline inside the span is no boundary",
                () -> withInside(singleBarline()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a breath mark inside the span is a structural boundary",
                () -> withInside(breathMark()), Strictness.STRICT, invalid(Reason.STRUCTURAL_BOUNDARY)),
            derived("a breath mark inside the span is no boundary",
                () -> withInside(breathMark()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a barline that ends the span breaks nothing",
                TupletValidatorTest::endedByBarline, Strictness.STRICT, valid(TRIPLET_OF_QUAVERS)),

            statedUnderBoth("a stated ratio equal to the grade names no re-timing",
                () -> quavers(TRIPLET_NOTES), ratioOf(TupletGrade.TRIPLET.number(), NoteValue.QUARTER),
                invalid(Reason.BAD_RATIO)),
            statedUnderBoth("a stated value that is not the span's own note value",
                () -> quavers(TRIPLET_NOTES), ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.QUARTER),
                invalid(Reason.NOT_NOTATABLE)),
            statedUnderBoth("a stated ratio a dotted beat would not derive is echoed",
                TupletValidatorTest::dottedBeatQuavers, TRIPLET_OF_QUAVERS, valid(TRIPLET_OF_QUAVERS)),
            statedUnderBoth("a stated ratio over a fermata",
                TupletValidatorTest::fermataQuavers, TRIPLET_OF_QUAVERS, invalid(Reason.FERMATA)),
            stated("a stated ratio over a barline inside the span, strict",
                () -> withInside(singleBarline()), Strictness.STRICT, invalid(Reason.STRUCTURAL_BOUNDARY)),
            stated("a stated ratio over a barline inside the span, lenient",
                () -> withInside(singleBarline()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS))
        ).flatMap(cases -> cases);
    }

    private static Result valid(TupletRatio ratio) {
        return new Result.Valid(ratio);
    }

    private static Result invalid(Reason reason) {
        return new Result.Invalid(reason);
    }

    /** A derived case for each strictness, all with the same verdict. */
    private static Stream<ValidationCase> derivedUnderBoth(
        String description, Supplier<List<StaffElement>> elements, TupletGrade grade, Result expected
    ) {
        return Stream.of(Strictness.values())
            .map(strictness -> new ValidationCase(
                description + ", " + strictness, elements, grade, null, strictness, expected));
    }

    /** A derived triplet case under one strictness. */
    private static Stream<ValidationCase> derived(
        String description, Supplier<List<StaffElement>> elements, Strictness strictness, Result expected
    ) {
        return Stream.of(new ValidationCase(
            description + ", " + strictness, elements, TupletGrade.TRIPLET, null, strictness, expected));
    }

    /** A case for a triplet with a stated ratio, for each strictness, all with the same verdict. */
    private static Stream<ValidationCase> statedUnderBoth(
        String description, Supplier<List<StaffElement>> elements, TupletRatio stated, Result expected
    ) {
        return Stream.of(Strictness.values())
            .map(strictness -> new ValidationCase(
                description + ", " + strictness, elements, TupletGrade.TRIPLET, stated, strictness, expected));
    }

    /** A case for a triplet with the one stated ratio of quavers, under one strictness. */
    private static Stream<ValidationCase> stated(
        String description, Supplier<List<StaffElement>> elements, Strictness strictness, Result expected
    ) {
        return Stream.of(new ValidationCase(
            description, elements, TupletGrade.TRIPLET, TRIPLET_OF_QUAVERS, strictness, expected));
    }

    private static List<StaffElement> quavers(int count) {
        return Stream.generate(StaffElementFactory::quaver).limit(count).map(StaffElement.class::cast).toList();
    }

    /** Three quavers, the first of which sets a dotted beat. */
    private static List<StaffElement> dottedBeatQuavers() {
        var elements = quavers(TRIPLET_NOTES);
        elements.getFirst().addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));

        return elements;
    }

    /** Three quavers, the middle one of which holds a fermata. */
    private static List<StaffElement> fermataQuavers() {
        var elements = quavers(TRIPLET_NOTES);
        FermataAttachment.addFermata(elements.get(MIDDLE_INDEX));

        return elements;
    }

    /** Three quavers, the last of which sets a dotted beat. */
    private static List<StaffElement> beatChangeInside() {
        var elements = quavers(TRIPLET_NOTES);
        elements.getLast().addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));

        return elements;
    }

    /** Three quavers with {@code inside} between the first and the second. */
    private static List<StaffElement> withInside(StaffElement inside) {
        var elements = new ArrayList<>(quavers(TRIPLET_NOTES));
        elements.add(MIDDLE_INDEX, inside);

        return elements;
    }

    /** Three quavers followed by a barline, which ends the span. */
    private static List<StaffElement> endedByBarline() {
        var elements = new ArrayList<>(quavers(TRIPLET_NOTES));
        elements.add(singleBarline());

        return elements;
    }
}
