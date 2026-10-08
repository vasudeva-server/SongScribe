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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link StaffElementRun} promises about a note's real neighbors and about where content
 * may land on a run: a grace note is transparent to {@link StaffElementRun#previousNonGraceIndex}
 * and {@link StaffElementRun#nonGraceRange}, the element asked from is never its own answer, and
 * a direction — or a range — holding nothing but grace notes has no neighbor, or no narrowed
 * range, to offer. {@link StaffElementRun#canInsertRunAt}
 * composes the breath-mark placement rule for a run's two ends with the single-element insertion
 * rule it otherwise defers to, and {@link StaffElementRun#canInsertKeyChangeAt} adds a mid-line
 * key change's own slot rules to both, and {@link StaffElementRun#canReplaceElementAt} refuses
 * either half of a pair. {@link StaffElementRun#effectiveRange} widens a named range over
 * the element paired with either end, behind every kind of bar a key change can stand behind,
 * and {@link StaffElementRun#beginIncludingKeyChangeBarline} takes the barline half of that
 * widening without the grace-note half.
 */
class StaffElementRunTest extends UnitTest {

    /**
     * One {@link StaffElementRun#previousNonGraceIndex} lookup and the index it must answer.
     *
     * @param description the case, as the test's display name
     * @param from        the index the lookup asks from
     * @param expected    the neighbor's index, or {@code null} when the run offers none
     */
    private record NeighborCase(String description, int from, @Nullable Integer expected) {}

    /** The fixture line, by position: a grace note, a note, two grace notes, a note, a grace note. */
    private static final int LEADING_GRACE = 0;
    private static final int FIRST_NOTE = 1;
    private static final int FIRST_INNER_GRACE = 2;
    private static final int SECOND_INNER_GRACE = 3;
    private static final int LAST_NOTE = 4;
    private static final int TRAILING_GRACE = 5;

    /**
     * The fixture line for {@link StaffElementRun#canInsertRunAt}, by position: a note, a rest, a
     * barline, a note, a note, a breath mark, a note, a grace note paired with the note after it,
     * and that note — the host of the pair.
     */
    private static final int AFTER_REST = 2;
    private static final int AFTER_BARLINE = 3;
    private static final int AFTER_NOTE = 4;
    private static final int AT_EXISTING_BREATH_MARK = 5;
    private static final int GRACE_PAIR_HOST = 8;

    /** In a run of two notes and one more element, the slot in front of that element. */
    private static final int SLOT_AFTER_TWO_NOTES = 2;

    /**
     * The fixture line for the pairing rule, by position: a note, a barline, the key change behind
     * it, a note, the breath mark after it, a grace note paired with the note after it, that note,
     * a note paired with nothing, and a barline no key change stands behind.
     */
    private static final int PAIRING_BARLINE = 1;
    private static final int PAIRING_KEY_CHANGE = 2;
    private static final int PAIRING_NOTE_BEFORE_BREATH_MARK = 3;
    private static final int PAIRING_BREATH_MARK = 4;
    private static final int PAIRING_GRACE = 5;
    private static final int PAIRING_HOST = 6;
    private static final int PAIRING_LONE_NOTE = 7;
    private static final int PAIRING_LONE_BARLINE = 8;
    private static final int PAST_THE_PAIRING_LINE = 9;

    private Line line;
    private Line insertRunLine;
    private Line pairingLine;

    @BeforeAll
    void buildLine() {
        line = detachedLine();
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
    }

    @BeforeAll
    void buildInsertRunLine() {
        insertRunLine = detachedLine();
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(crotchetRest());
        insertRunLine.addElement(singleBarline());
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(breathMark());
        insertRunLine.addElement(crotchet());

        var pairedGrace = graceQuaver();
        pairedGrace.setSlide(Slide.GLISSANDO);
        insertRunLine.addElement(pairedGrace);
        insertRunLine.addElement(crotchet());
    }

    @BeforeAll
    void buildPairingLine() {
        pairingLine = detachedLine();
        pairingLine.addElement(crotchet());
        pairingLine.addElement(singleBarline());
        pairingLine.addElement(keyChange(Key.TWO_SHARPS));
        pairingLine.addElement(crotchet());
        pairingLine.addElement(breathMark());

        var pairedGrace = graceQuaver();
        pairedGrace.setSlide(Slide.GLISSANDO);
        pairingLine.addElement(pairedGrace);
        pairingLine.addElement(crotchet());
        pairingLine.addElement(crotchet());
        pairingLine.addElement(singleBarline());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("neighborCases")
    void testTheNearestNonGraceNeighborStepsOverGraceNotesAndIsAbsentPastTheEnds(NeighborCase testCase) {
        assertThat(line.previousNonGraceIndex(testCase.from())).isEqualTo(testCase.expected());
    }

    static Stream<NeighborCase> neighborCases() {
        return Stream.of(
            new NeighborCase("a run of grace notes before is stepped over", LAST_NOTE, FIRST_NOTE),
            new NeighborCase("a single grace note before is stepped over", SECOND_INNER_GRACE, FIRST_NOTE),
            new NeighborCase("only a grace note before leaves no predecessor", FIRST_NOTE, null),
            new NeighborCase("nothing before leaves no predecessor", LEADING_GRACE, null)
        );
    }

    /**
     * One {@link StaffElementRun#nonGraceRange} call and the range it must narrow to.
     *
     * @param description the case, as the test's display name
     * @param range        the range to narrow
     * @param expected     the narrowed range, or {@code null} when {@code range} holds only
     *                     grace notes
     */
    private record NonGraceRangeCase(
        String description, StaffElementRun.ElementRange range, StaffElementRun.@Nullable ElementRange expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonGraceRangeCases")
    void testNonGraceRangeNarrowsToTheNonGraceEndpoints(NonGraceRangeCase testCase) {
        assertThat(line.nonGraceRange(testCase.range())).isEqualTo(testCase.expected());
    }

    static Stream<NonGraceRangeCase> nonGraceRangeCases() {
        return Stream.of(
            new NonGraceRangeCase("grace notes at both ends are excluded",
                new StaffElementRun.ElementRange(LEADING_GRACE, TRAILING_GRACE),
                new StaffElementRun.ElementRange(FIRST_NOTE, LAST_NOTE)),
            new NonGraceRangeCase("a range of only grace notes yields null",
                new StaffElementRun.ElementRange(FIRST_INNER_GRACE, SECOND_INNER_GRACE), null),
            new NonGraceRangeCase("a range with one non-grace element yields that single",
                new StaffElementRun.ElementRange(SECOND_INNER_GRACE, LAST_NOTE),
                StaffElementRun.ElementRange.single(LAST_NOTE))
        );
    }

    /**
     * One {@link StaffElementRun#canInsertRunAt} call and the answer it must give.
     *
     * @param description the case, as the test's display name
     * @param index        the slot the run would be inserted at
     * @param run          the elements that would land there
     * @param expected     whether the run may be inserted
     */
    private record InsertRunCase(String description, int index, List<? extends StaffElement> run, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("insertRunCases")
    void testCanInsertRunAtComposesInsertionAndBreathMarkRules(InsertRunCase testCase) {
        assertThat(insertRunLine.canInsertRunAt(testCase.index(), testCase.run())).isEqualTo(testCase.expected());
    }

    static Stream<InsertRunCase> insertRunCases() {
        return Stream.of(
            new InsertRunCase("a breath mark leading a run after a barline is refused",
                AFTER_BARLINE, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run at index 0 is refused",
                0, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run in front of a breath mark is refused",
                AT_EXISTING_BREATH_MARK, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run after a note is accepted",
                AFTER_NOTE, List.of(breathMark()), true),
            new InsertRunCase("a breath mark leading a run after a rest is accepted",
                AFTER_REST, List.of(breathMark()), true),
            new InsertRunCase("a run ending in a barline in front of a breath mark is refused",
                AT_EXISTING_BREATH_MARK, List.of(singleBarline()), false),
            new InsertRunCase("a run ending in a note in front of a breath mark is accepted",
                AT_EXISTING_BREATH_MARK, List.of(crotchet()), true),
            new InsertRunCase("a run holding a note is refused inside a grace pair",
                GRACE_PAIR_HOST, List.of(crotchet()), false),
            new InsertRunCase("a run holding a breath mark is refused inside a grace pair",
                GRACE_PAIR_HOST, List.of(breathMark()), false)
        );
    }

    /**
     * One {@link StaffElementRun#canInsertKeyChangeAt} call on {@link #pairingLine}.
     *
     * @param description the case, as the test's display name
     * @param index       the slot the key change would be inserted at
     * @param expected    whether a key change may be inserted there
     */
    private record KeyChangeSlotCase(String description, int index, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyChangeSlotCases")
    void testAKeyChangeLandsOnlyWhereItReachesANoteAndTouchesNoPairOrBreathMark(KeyChangeSlotCase testCase) {
        assertThat(pairingLine.canInsertKeyChangeAt(testCase.index())).isEqualTo(testCase.expected());
    }

    static Stream<KeyChangeSlotCase> keyChangeSlotCases() {
        return Stream.of(
            new KeyChangeSlotCase(
                "index 0 is refused, since a key change is never a line's first element", 0, false),
            new KeyChangeSlotCase(
                "the slot before the barline a key change sits behind is refused",
                PAIRING_BARLINE, false),
            new KeyChangeSlotCase(
                "the slot between a key change and its barline is refused",
                PAIRING_KEY_CHANGE, false),
            new KeyChangeSlotCase(
                "the slot immediately after a key change is refused",
                PAIRING_NOTE_BEFORE_BREATH_MARK, false),
            new KeyChangeSlotCase(
                "the slot in front of a breath mark is refused, since a breath mark stands only "
                    + "directly after a duration element",
                PAIRING_BREATH_MARK, false),
            new KeyChangeSlotCase(
                "a slot clear of every pair and breath mark is accepted", PAIRING_GRACE, true),
            new KeyChangeSlotCase(
                "the slot between a paired grace note and its host is refused", PAIRING_HOST, false),
            new KeyChangeSlotCase(
                "the last note's slot is accepted, since the change governs that note",
                PAIRING_LONE_NOTE, true),
            new KeyChangeSlotCase(
                "the slot past the last note is refused, since the change governs nothing",
                PAIRING_LONE_NOTE + 1, false)
        );
    }

    /**
     * A run whose last note is not its last element, and a slot past that note.
     *
     * @param description the case, as the test's display name
     * @param elements    the run's elements, in order
     * @param index       the slot the key change would be inserted at
     */
    private record NoNoteFollowsCase(String description, List<StaffElement> elements, int index) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("noNoteFollowsCases")
    void testAKeyChangeIsRefusedWhereNoNoteFollowsIt(NoNoteFollowsCase testCase) {
        assertThat(new DetachedLyricRun(testCase.elements()).canInsertKeyChangeAt(testCase.index()))
            .isFalse();
    }

    static Stream<NoNoteFollowsCase> noNoteFollowsCases() {
        return Stream.of(
            new NoNoteFollowsCase(
                "the slot before a trailing barline is refused",
                List.of(crotchet(), crotchet(), singleBarline()), SLOT_AFTER_TWO_NOTES),
            new NoNoteFollowsCase(
                "the slot before a trailing rest is refused, since a key affects no rest",
                List.of(crotchet(), crotchet(), crotchetRest()), SLOT_AFTER_TWO_NOTES),
            new NoNoteFollowsCase(
                "a run holding no note offers no slot at all",
                List.of(singleBarline(), singleBarline()), 1)
        );
    }

    /**
     * One {@link StaffElementRun#effectiveRange} call on {@link #pairingLine} and the range it
     * must widen to.
     *
     * @param description the case, as the test's display name
     * @param named        the range the caller names
     * @param expected     the range the pairing rule widens it to
     */
    private record EffectiveRangeCase(
        String description, StaffElementRun.ElementRange named, StaffElementRun.ElementRange expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("effectiveRangeCases")
    void testEffectiveRangeWidensOverTheElementPairedWithEitherEnd(EffectiveRangeCase testCase) {
        assertThat(pairingLine.effectiveRange(testCase.named()).range()).isEqualTo(testCase.expected());
    }

    static Stream<EffectiveRangeCase> effectiveRangeCases() {
        return Stream.of(
            new EffectiveRangeCase("a host reaches back over its paired grace note",
                StaffElementRun.ElementRange.single(PAIRING_HOST),
                new StaffElementRun.ElementRange(PAIRING_GRACE, PAIRING_HOST)),
            new EffectiveRangeCase("a key change reaches back over its barline",
                StaffElementRun.ElementRange.single(PAIRING_KEY_CHANGE),
                new StaffElementRun.ElementRange(PAIRING_BARLINE, PAIRING_KEY_CHANGE)),
            new EffectiveRangeCase("a barline reaches forward over the key change behind it",
                StaffElementRun.ElementRange.single(PAIRING_BARLINE),
                new StaffElementRun.ElementRange(PAIRING_BARLINE, PAIRING_KEY_CHANGE)),
            new EffectiveRangeCase("an end reaches forward over a breath mark",
                StaffElementRun.ElementRange.single(PAIRING_NOTE_BEFORE_BREATH_MARK),
                new StaffElementRun.ElementRange(PAIRING_NOTE_BEFORE_BREATH_MARK, PAIRING_BREATH_MARK)),
            new EffectiveRangeCase("a note alone widens nothing",
                StaffElementRun.ElementRange.single(PAIRING_LONE_NOTE),
                StaffElementRun.ElementRange.single(PAIRING_LONE_NOTE))
        );
    }

    /**
     * One {@link StaffElementRun#beginIncludingKeyChangeBarline} call on {@link #pairingLine}.
     *
     * @param description the case, as the test's display name
     * @param begin        the first element a copy names
     * @param expected     the first element the copy takes
     */
    private record CopyBeginCase(String description, int begin, int expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("copyBeginCases")
    void testACopyReachesBackOverAKeyChangesBarlineAndNotOverAHostsGraceNote(CopyBeginCase testCase) {
        assertThat(pairingLine.beginIncludingKeyChangeBarline(testCase.begin())).isEqualTo(testCase.expected());
    }

    static Stream<CopyBeginCase> copyBeginCases() {
        return Stream.of(
            new CopyBeginCase("a copy beginning at a key change takes its barline",
                PAIRING_KEY_CHANGE, PAIRING_BARLINE),
            new CopyBeginCase("a copy beginning at a host leaves its grace note behind",
                PAIRING_HOST, PAIRING_HOST)
        );
    }

    /**
     * One kind of bar a key change can stand behind.
     *
     * @param type the bar's type, as the test's display name and for the coverage assertion
     * @param bar  builds a bar of that type
     */
    private record BarCase(ElementType type, Supplier<StaffElement> bar) {}

    static Stream<BarCase> barCases() {
        return Stream.<Supplier<StaffElement>>of(
                StaffElementFactory::singleBarline,
                StaffElementFactory::doubleBarline,
                StaffElementFactory::finalDoubleBarline,
                StaffElementFactory::repeatLeft,
                StaffElementFactory::repeatRight,
                StaffElementFactory::repeatLeftRight)
            .map(bar -> new BarCase(bar.get().getType(), bar));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("barCases")
    void testAKeyChangePairsWithEveryKindOfBar(BarCase testCase) {
        var barLine = detachedLine();
        barLine.addElement(crotchet());
        barLine.addElement(testCase.bar().get());
        barLine.addElement(keyChange(Key.TWO_SHARPS));
        barLine.addElement(crotchet());

        var pair = new StaffElementRun.ElementRange(PAIRING_BARLINE, PAIRING_KEY_CHANGE);

        assertThat(barLine.effectiveRange(StaffElementRun.ElementRange.single(PAIRING_KEY_CHANGE)).range())
            .isEqualTo(pair);
        assertThat(barLine.effectiveRange(StaffElementRun.ElementRange.single(PAIRING_BARLINE)).range())
            .isEqualTo(pair);
    }

    /**
     * One {@link StaffElementRun#canReplaceElementAt} call on {@link #pairingLine}.
     *
     * @param description the case, as the test's display name
     * @param index       the element note entry would write over
     * @param expected    whether it may
     */
    private record ReplaceCase(String description, int index, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("replaceCases")
    void testAnElementMayBeReplacedUnlessItIsHalfOfAPair(ReplaceCase testCase) {
        assertThat(pairingLine.canReplaceElementAt(testCase.index())).isEqualTo(testCase.expected());
    }

    static Stream<ReplaceCase> replaceCases() {
        return Stream.of(
            new ReplaceCase("a note paired with nothing may be replaced", PAIRING_LONE_NOTE, true),
            new ReplaceCase("a grace note may not", PAIRING_GRACE, false),
            new ReplaceCase("a key change may not", PAIRING_KEY_CHANGE, false),
            new ReplaceCase("the barline a key change sits behind may not", PAIRING_BARLINE, false),
            new ReplaceCase("a barline no key change sits behind may", PAIRING_LONE_BARLINE, true),
            new ReplaceCase("an index past the run protects nothing", PAST_THE_PAIRING_LINE, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("barCases")
    void testAKeyChangeWrittenBehindAnyKindOfBarOwesNoBarline(BarCase testCase) {
        var barLine = detachedLine();
        barLine.addElement(crotchet());
        barLine.addElement(testCase.bar().get());

        assertThat(barLine.needsBarlineBeforeKeyChangeAt(barLine.elementCount())).isFalse();
    }

    @Test
    void testAKeyChangeWrittenAfterANoteOwesABarline() {
        var noteLine = detachedLine();
        noteLine.addElement(crotchet());

        assertThat(noteLine.needsBarlineBeforeKeyChangeAt(noteLine.elementCount())).isTrue();
    }

    @Test
    void testBarCasesCoverEveryBarType() {
        assertThat(barCases().map(BarCase::type))
            .containsExactlyInAnyOrderElementsOf(
                Stream.of(ElementType.values()).filter(ElementType::isBar).toList());
    }
}
