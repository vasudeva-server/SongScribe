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

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.TupletValidator.Result;
import songscribe.dom.TupletValidator.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.DOTTED_BEAT_TEMPO;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.DUPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.minimRest;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The verdict {@link TupletValidator} gives a span, under each {@link Strictness}: the ratio it
 * derives when the span can carry a tuplet of the grade, otherwise the reason of the first check
 * it fails. A ratio a file stated is echoed when the span's elements and its value agree with it,
 * and needs no conventional span.
 *
 * <p>The ratio, the fermata, the pitched-note count, the conventional span and the redundancy
 * test hold under both strictnesses. The beat barrier and the bar or breath mark inside the span
 * hold under {@link Strictness#STRICT} only.
 */
class TupletValidatorTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The middle of the three notes a triplet spans. */
    private static final int MIDDLE_INDEX = 1;

    /** Three quavers under a 3 in a crotchet beat: two quavers' time. */
    private static final TupletNormalTime TRIPLET_OF_QUAVERS = ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.EIGHTH);

    /**
     * The normal notes a quintuplet, a sextuplet and a septuplet of equal notes take the time of
     * in a crotchet beat: the largest binary division of the beat below their number.
     */
    private static final int FOUR_NORMAL_NOTES = 4;

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
        @Nullable TupletNormalTime stated,
        Strictness strictness,
        Result expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationCases")
    void testASpanIsValidOrNamesTheFirstReasonItFails(ValidationCase testCase) {
        var elements = testCase.elements().get();
        var song = buildSong(line -> elements.forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);
        var context = TupletValidator.measure(Tuplet.Site.at(line, new ElementRange(0, elements.size() - 1)));
        var stated = testCase.stated();

        var result = stated == null
            ? TupletValidator.validate(context, testCase.grade(), testCase.strictness())
            : TupletValidator.validateStated(context, testCase.grade(), stated, testCase.strictness());

        assertThat(result).isEqualTo(testCase.expected());
    }

    @Test
    void testCasesCoverEveryStrictness() {
        assertThat(validationCases().map(ValidationCase::strictness).distinct())
            .containsExactlyInAnyOrder(Strictness.values());
    }

    static Stream<ValidationCase> validationCases() {
        return Stream.of(
            derivedUnderBoth("three quavers under a 3 give 3:2",
                () -> quaversFor(TupletGrade.TRIPLET), TupletGrade.TRIPLET, valid(TRIPLET_OF_QUAVERS)),
            derivedUnderBoth("a span of grace notes holds no time",
                () -> List.of(graceQuaver(), graceQuaver()), TupletGrade.TRIPLET,
                invalid(Strings.DIALOG_REPORT_REASON_EMPTY_SPAN)),
            derivedUnderBoth("three quavers do not divide into seven equal parts",
                () -> quaversFor(TupletGrade.TRIPLET), TupletGrade.SEPTUPLET,
                invalid(Strings.DIALOG_REPORT_REASON_NOT_DIVISIBLE)),
            derivedUnderBoth("four quavers do not divide into five equal parts",
                () -> quaversFor(TupletGrade.QUADRUPLET), TupletGrade.QUINTUPLET,
                invalid(Strings.DIALOG_REPORT_REASON_NOT_DIVISIBLE)),
            derivedUnderBoth("two crotchets divide into three parts that no note value writes",
                () -> List.of(crotchet(), crotchet()), TupletGrade.TRIPLET,
                invalid(Strings.DIALOG_REPORT_REASON_NOT_NOTATABLE)),
            derivedUnderBoth("four quavers divide into six parts that no note value writes",
                () -> quaversFor(TupletGrade.QUADRUPLET), TupletGrade.SEXTUPLET,
                invalid(Strings.DIALOG_REPORT_REASON_NOT_NOTATABLE)),
            derivedUnderBoth("three quavers under a dotted beat have no conventional span",
                () -> dottedBeatQuavers(TupletGrade.TRIPLET), TupletGrade.TRIPLET,
                invalid(Strings.DIALOG_REPORT_REASON_NO_CONVENTIONAL_SPAN)),
            derivedUnderBoth("two quavers under a dotted beat are a duplet of 2:3",
                () -> dottedBeatQuavers(TupletGrade.DUPLET), TupletGrade.DUPLET,
                valid(ratioOf(DUPLET_NORMAL_NOTES, NoteValue.EIGHTH))),
            derivedUnderBoth("four quavers under a 4 are a power of two",
                () -> quaversFor(TupletGrade.QUADRUPLET), TupletGrade.QUADRUPLET,
                invalid(Strings.DIALOG_REPORT_REASON_POWER_OF_TWO)),
            derivedUnderBoth("five sixteenths under a 5 give 5:4",
                () -> notesFor(TupletGrade.QUINTUPLET, StaffElementFactory::semiquaver), TupletGrade.QUINTUPLET,
                valid(ratioOf(FOUR_NORMAL_NOTES, NoteValue.SIXTEENTH))),
            derivedUnderBoth("six sixteenths under a 6 give 6:4",
                () -> notesFor(TupletGrade.SEXTUPLET, StaffElementFactory::semiquaver), TupletGrade.SEXTUPLET,
                valid(ratioOf(FOUR_NORMAL_NOTES, NoteValue.SIXTEENTH))),
            derivedUnderBoth("seven thirty-seconds under a 7 give 7:4",
                () -> notesFor(TupletGrade.SEPTUPLET, StaffElementFactory::demiSemiquaver), TupletGrade.SEPTUPLET,
                valid(ratioOf(FOUR_NORMAL_NOTES, NoteValue.THIRTY_SECOND))),
            derivedUnderBoth("a fermata inside the span",
                TupletValidatorTest::fermataQuavers, TupletGrade.TRIPLET,
                invalid(Strings.DIALOG_REPORT_REASON_FERMATA)),
            derivedUnderBoth("one note and two rests hold too few notes",
                () -> List.of(crotchet(), crotchetRest(), crotchetRest()),
                TupletGrade.TRIPLET, invalid(Strings.DIALOG_REPORT_REASON_TOO_FEW_NOTES)),
            derivedUnderBoth("three rests hold too few notes",
                () -> List.of(crotchetRest(), crotchetRest(), crotchetRest()),
                TupletGrade.TRIPLET, invalid(Strings.DIALOG_REPORT_REASON_TOO_FEW_NOTES)),
            derivedUnderBoth("a note, a grace note and a rest hold too few notes",
                () -> List.of(crotchet(), graceQuaver(), minimRest()),
                TupletGrade.TRIPLET, invalid(Strings.DIALOG_REPORT_REASON_TOO_FEW_NOTES)),

            derived("a beat change inside the span is a barrier",
                TupletValidatorTest::beatChangeInside, Strictness.STRICT,
                invalid(Strings.DIALOG_REPORT_REASON_BEAT_BARRIER)),
            derived("a beat change inside the span is no barrier",
                TupletValidatorTest::beatChangeInside, Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a barline inside the span divides it",
                () -> withInside(singleBarline()), Strictness.STRICT,
                invalid(Strings.DIALOG_REPORT_REASON_TUPLET_BAR_OR_BREATH_MARK)),
            derived("a barline inside the span divides nothing",
                () -> withInside(singleBarline()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a breath mark inside the span divides it",
                () -> withInside(breathMark()), Strictness.STRICT,
                invalid(Strings.DIALOG_REPORT_REASON_TUPLET_BAR_OR_BREATH_MARK)),
            derived("a breath mark inside the span divides nothing",
                () -> withInside(breathMark()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS)),
            derived("a barline that ends the span breaks nothing",
                TupletValidatorTest::endedByBarline, Strictness.STRICT, valid(TRIPLET_OF_QUAVERS)),

            statedUnderBoth("a stated number equal to the grade re-times nothing",
                () -> quaversFor(TupletGrade.TRIPLET), TupletGrade.TRIPLET,
                ratioOf(TupletGrade.TRIPLET.number(), NoteValue.QUARTER),
                invalid(Strings.DIALOG_REPORT_REASON_NUMBER_EQUALS_NORMAL_NOTES)),
            statedUnderBoth("a stated value that is not the span's own share",
                () -> quaversFor(TupletGrade.TRIPLET), TupletGrade.TRIPLET,
                ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.QUARTER),
                invalid(Strings.DIALOG_REPORT_REASON_STATED_VALUE_MISMATCH)),
            statedUnderBoth("a stated ratio a dotted beat would not derive is echoed",
                () -> dottedBeatQuavers(TupletGrade.TRIPLET), TupletGrade.TRIPLET, TRIPLET_OF_QUAVERS,
                valid(TRIPLET_OF_QUAVERS)),
            statedUnderBoth("a stated ratio over a fermata",
                TupletValidatorTest::fermataQuavers, TupletGrade.TRIPLET, TRIPLET_OF_QUAVERS,
                invalid(Strings.DIALOG_REPORT_REASON_FERMATA)),
            statedUnderBoth("a stated ratio over a span of grace notes, which holds no time",
                () -> List.of(graceQuaver(), graceQuaver()), TupletGrade.TRIPLET, TRIPLET_OF_QUAVERS,
                invalid(Strings.DIALOG_REPORT_REASON_EMPTY_SPAN)),
            statedUnderBoth("a stated ratio over a span that does not divide by the grade",
                () -> quaversFor(TupletGrade.TRIPLET), TupletGrade.SEPTUPLET, TRIPLET_OF_QUAVERS,
                invalid(Strings.DIALOG_REPORT_REASON_NOT_DIVISIBLE)),
            stated("a stated ratio over a barline inside the span, strict",
                () -> withInside(singleBarline()), Strictness.STRICT,
                invalid(Strings.DIALOG_REPORT_REASON_TUPLET_BAR_OR_BREATH_MARK)),
            stated("a stated ratio over a barline inside the span, lenient",
                () -> withInside(singleBarline()), Strictness.LENIENT, valid(TRIPLET_OF_QUAVERS))
        ).flatMap(cases -> cases);
    }

    private static Result valid(TupletNormalTime ratio) {
        return new Result.Valid(ratio);
    }

    private static Result invalid(String reasonKey) {
        return new Result.Invalid(reasonKey);
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

    /** A case for a stated ratio, for each strictness, all with the same verdict. */
    private static Stream<ValidationCase> statedUnderBoth(
        String description, Supplier<List<StaffElement>> elements, TupletGrade grade, TupletNormalTime stated,
        Result expected
    ) {
        return Stream.of(Strictness.values())
            .map(strictness -> new ValidationCase(
                description + ", " + strictness, elements, grade, stated, strictness, expected));
    }

    /** A case for a triplet with the one stated ratio of quavers, under one strictness. */
    private static Stream<ValidationCase> stated(
        String description, Supplier<List<StaffElement>> elements, Strictness strictness, Result expected
    ) {
        return Stream.of(new ValidationCase(
            description, elements, TupletGrade.TRIPLET, TRIPLET_OF_QUAVERS, strictness, expected));
    }

    /** As many notes as {@code grade} prints, each made by {@code note}. */
    private static List<StaffElement> notesFor(TupletGrade grade, Supplier<? extends StaffElement> note) {
        return Stream.<StaffElement>generate(note).limit(grade.number()).toList();
    }

    /** As many quavers as {@code grade} prints. */
    private static List<StaffElement> quaversFor(TupletGrade grade) {
        return notesFor(grade, StaffElementFactory::quaver);
    }

    /** As many quavers as {@code grade} prints, the first of which sets a dotted beat. */
    private static List<StaffElement> dottedBeatQuavers(TupletGrade grade) {
        var elements = quaversFor(grade);
        elements.getFirst().addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));

        return elements;
    }

    /** Three quavers, the middle one of which holds a fermata. */
    private static List<StaffElement> fermataQuavers() {
        var elements = quaversFor(TupletGrade.TRIPLET);
        FermataAttachment.addFermata(elements.get(MIDDLE_INDEX));

        return elements;
    }

    /** Three quavers, the last of which sets a dotted beat. */
    private static List<StaffElement> beatChangeInside() {
        var elements = quaversFor(TupletGrade.TRIPLET);
        elements.getLast().addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));

        return elements;
    }

    /** Three quavers with {@code inside} between the first and the second. */
    private static List<StaffElement> withInside(StaffElement inside) {
        var elements = new ArrayList<>(quaversFor(TupletGrade.TRIPLET));
        elements.add(MIDDLE_INDEX, inside);

        return elements;
    }

    /** Three quavers followed by a barline, which ends the span. */
    private static List<StaffElement> endedByBarline() {
        var elements = new ArrayList<>(quaversFor(TupletGrade.TRIPLET));
        elements.add(singleBarline());

        return elements;
    }
}
