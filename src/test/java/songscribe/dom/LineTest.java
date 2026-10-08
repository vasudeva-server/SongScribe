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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link Line} promises about a position and about a run landing at one.
 *
 * <p>{@link Line#insertRun} repairs both of its seams — the successor's against the last element
 * of the run, the predecessor's against the first — and a glissando loses its target to an
 * insertion and to {@link Line#deleteRange} alike. {@link Line#canInsertElementAt} and
 * {@link Line#canBearSyllableAt} are the two questions every caller asks about a position, and
 * both turn on the closed pairs a line can hold. {@link Line#keyBefore} and
 * {@link Line#keyAtEndOfLineAfterRemoving} read the key a position is in, and an append keeps the
 * song's terminal last. An edit that breaks the terminal policy throws, and the one final double
 * barline the policy allows, appended to the last line, does not.
 *
 * <p>An element is on a line exactly while the line holds it, and so is everything hanging on it.
 * {@link Line#setKey} holds no key where the line would inherit the one it is given.
 */
class LineTest extends UnitTest {

    /** The index the run lands at in every fixture here: after the line's first element. */
    private static final int RUN_INDEX = 1;

    private static final String SYLLABLE = "la";

    /**
     * One edit that changes which element follows the line's first note.
     *
     * @param description      the edit, as part of the test's display name
     * @param edit             performs it on a line holding the note and its follower
     * @param leavesNoteBehind whether a pitched note stands directly after the first note
     *     afterwards, by way of this edit's insertion
     */
    private record FollowerEdit(String description, Consumer<Line> edit, boolean leavesNoteBehind) {}

    /**
     * One slide on the first note, under one {@link FollowerEdit}.
     *
     * @param description the case, as the test's display name
     * @param slide       the slide the first note carries before the edit
     * @param edit        the edit that changes its follower
     */
    private record SlideCase(String description, Slide slide, FollowerEdit edit) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static Stream<FollowerEdit> followerEdits() {
        return Stream.of(
            new FollowerEdit("a non-pitched run inserted after it",
                line -> insertRun(line, List.of(singleBarline(), keyChange(Key.NO_ACCIDENTALS))), false),
            new FollowerEdit("a pitched run inserted after it",
                line -> insertRun(line, List.of(crotchet(), crotchet())), true),
            new FollowerEdit("its follower deleted",
                line -> line.deleteRange(
                    line.effectiveRange(new StaffElementRun.ElementRange(RUN_INDEX, RUN_INDEX))),
                false));
    }

    /** Every {@link Slide} crossed with every {@link FollowerEdit}. */
    static Stream<SlideCase> slideCases() {
        return Stream.of(Slide.values())
            .flatMap(slide -> followerEdits()
                .map(edit -> new SlideCase(slide + ", " + edit.description(), slide, edit)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("slideCases")
    void testASlideSurvivesAChangeOfFollowerUnlessItsTargetIsGone(SlideCase testCase) {
        var line = detachedLine();
        var source = crotchet();
        source.setSlide(testCase.slide());
        line.addElement(source);
        line.addElement(crotchet());

        testCase.edit().edit().accept(line);

        // Slide's own contract: a glissando connects to the note that follows, a fall has no
        // target. Only a slide with a target can lose it.
        var hasTarget = switch (testCase.slide()) {
            case GLISSANDO -> true;
            case FALL -> false;
        };
        var expectedSlide = hasTarget && !testCase.edit().leavesNoteBehind() ? null : testCase.slide();

        assertThat(source.getSlide()).isEqualTo(expectedSlide);
    }

    /**
     * One run inserted between a syllable and the syllable that continues its word, and what that
     * leaves the successor reading as.
     *
     * @param description      the case, as the test's display name
     * @param run              the elements inserted, in the order they land
     * @param expectedSyllabic what the successor's syllable then is
     */
    private record InsertedRunCase(
        String description,
        List<StaffElement> run,
        Lyric.Syllabic expectedSyllabic
    ) {}

    static Stream<InsertedRunCase> insertedRunCases() {
        return Stream.of(
            // The discriminating case: judged element by element, one interrupting element in the
            // run is enough. A run judged by its every element would leave this one transparent.
            new InsertedRunCase("a run mixing a transparent element with a note interrupts the word",
                List.of(singleBarline(), crotchet()), Lyric.Syllabic.BEGIN),
            new InsertedRunCase("a run carrying no syllable slot leaves the word whole",
                List.of(singleBarline(), keyChange(Key.NO_ACCIDENTALS)), Lyric.Syllabic.MIDDLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("insertedRunCases")
    void testTheSuccessorsSyllabicChainBreaksOnlyForARunHoldingASyllableSlot(InsertedRunCase testCase) {
        var line = detachedLine();
        line.addElement(crotchet());

        var successor = crotchet();
        successor.putLyric(new Lyric(Lyric.FIRST_VERSE, SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
        line.addElement(successor);

        insertRun(line, testCase.run());

        var successorLyric = successor.getLyricForVerse(Lyric.FIRST_VERSE);

        assertThat(successorLyric).isNotNull();
        assertThat(successorLyric.syllabic()).isEqualTo(testCase.expectedSyllabic());
    }

    /**
     * One index on {@link #pairedLine()}, and what the line's two position questions answer there.
     *
     * @param description       the case, as the test's display name
     * @param index             the index being asked about
     * @param canInsert         whether an element may be inserted in front of the element there
     * @param canBearSyllable   whether a syllable may be written on the element there
     */
    private record PositionCase(
        String description,
        int index,
        boolean canInsert,
        boolean canBearSyllable
    ) {}

    /**
     * A line holding both closed pairs: a grace note with the note it decorates at 0–1, first on
     * the line, and a key change behind its barline at 2–3, then a note.
     */
    private static Line pairedLine() {
        var line = detachedLine();

        var grace = graceQuaver();
        grace.setSlide(Slide.GLISSANDO);
        line.addElement(grace);
        line.addElement(crotchet());

        line.addElement(singleBarline());
        line.addElement(keyChange(Key.TWO_SHARPS));
        line.addElement(crotchet());

        return line;
    }

    static Stream<PositionCase> positionCases() {
        return Stream.of(
            new PositionCase("the slot in front of a grace note is outside the pair, and the grace carries the syllable",
                0, true, true),
            new PositionCase("the slot in front of a paired grace note's host is inside the pair, and the host takes no syllable",
                1, false, false),
            new PositionCase("the barline a key change stands behind takes an insertion, not a syllable",
                2, true, false),
            new PositionCase("the slot in front of a key change is inside the pair",
                3, false, false),
            new PositionCase("a note clear of both pairs takes an insertion in front of it and a syllable on it",
                4, true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionCases")
    void testCanInsertElementAtRefusesOnlyTheSlotsInsideAPair(PositionCase testCase) {
        assertThat(pairedLine().canInsertElementAt(testCase.index()))
            .isEqualTo(testCase.canInsert());
    }

    @Test
    void testCanInsertElementAtAcceptsTheSlotPastTheLastElement() {
        var line = pairedLine();

        assertThat(line.canInsertElementAt(line.elementCount()))
            .as("nothing stands past the last element, so there is no pair there to split")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionCases")
    void testCanBearSyllableAtRefusesEveryElementNoSyllableCanBeSungOn(PositionCase testCase) {
        assertThat(pairedLine().canBearSyllableAt(testCase.index()))
            .isEqualTo(testCase.canBearSyllable());
    }

    /** The keys of {@link #keyLine()}: the one it runs in, and the two its key changes establish. */
    private static final Key RUNNING_KEY = Key.NO_ACCIDENTALS;
    private static final Key FIRST_CHANGE_KEY = Key.TWO_SHARPS;
    private static final Key SECOND_CHANGE_KEY = Key.THREE_FLATS;

    /** {@link #keyLine()}, by position. */
    private static final int FIRST_NOTE = 0;
    private static final int FIRST_BARLINE = 1;
    private static final int FIRST_KEY_CHANGE = 2;
    private static final int SECOND_KEY_CHANGE = 5;
    private static final int LAST_NOTE = 6;

    /**
     * A line in {@link #RUNNING_KEY} holding {@code note, barline, key change, note, barline,
     * key change, note}, the key changes establishing {@link #FIRST_CHANGE_KEY} and then
     * {@link #SECOND_CHANGE_KEY}.
     */
    private static Line keyLine() {
        var line = detachedLine();
        line.setKey(RUNNING_KEY);
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(FIRST_CHANGE_KEY));
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(SECOND_CHANGE_KEY));
        line.addElement(crotchet());

        return line;
    }

    /**
     * One index on {@link #keyLine()} and the key in force just before it.
     *
     * @param description the case, as the test's display name
     * @param index       the insertion index asked about
     * @param expected    the key in force just before it
     */
    private record KeyBeforeCase(String description, int index, Key expected) {}

    static Stream<KeyBeforeCase> keyBeforeCases() {
        return Stream.of(
            new KeyBeforeCase("index 0 is the running key", FIRST_NOTE, RUNNING_KEY),
            new KeyBeforeCase("an index with no key change below it is the running key",
                FIRST_BARLINE, RUNNING_KEY),
            new KeyBeforeCase("a key change is not counted at its own index",
                FIRST_KEY_CHANGE, RUNNING_KEY),
            new KeyBeforeCase("a key change is counted at the index just after it",
                FIRST_KEY_CHANGE + 1, FIRST_CHANGE_KEY),
            new KeyBeforeCase("a key change stays in force until the next one",
                SECOND_KEY_CHANGE, FIRST_CHANGE_KEY),
            new KeyBeforeCase("the insertion index past the last element is in the last key change's key",
                LAST_NOTE + 1, SECOND_CHANGE_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyBeforeCases")
    void testKeyBeforeIsTheLastKeyChangeBelowTheIndexElseTheRunningKey(KeyBeforeCase testCase) {
        assertThat(keyLine().keyBefore(testCase.index())).isEqualTo(testCase.expected());
    }

    /**
     * One range removed from {@link #keyLine()} and the key the line then leaves off in.
     *
     * @param description the case, as the test's display name
     * @param named       the range a caller names, before widening
     * @param expected    the key the line leaves off in without it
     */
    private record KeyAfterRemovingCase(String description, StaffElementRun.ElementRange named, Key expected) {}

    static Stream<KeyAfterRemovingCase> keyAfterRemovingCases() {
        return Stream.of(
            new KeyAfterRemovingCase("a key change surviving after the range decides",
                StaffElementRun.ElementRange.single(FIRST_KEY_CHANGE), SECOND_CHANGE_KEY),
            new KeyAfterRemovingCase("with none surviving, the key in effect before the range",
                StaffElementRun.ElementRange.single(SECOND_KEY_CHANGE), FIRST_CHANGE_KEY),
            new KeyAfterRemovingCase("a range from index 0 leaves the running key",
                new StaffElementRun.ElementRange(FIRST_NOTE, LAST_NOTE), RUNNING_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyAfterRemovingCases")
    void testKeyAtEndOfLineAfterRemovingReadsTheSurvivingKeyChangeElseTheKeyBeforeTheRange(
        KeyAfterRemovingCase testCase) {

        var line = keyLine();

        assertThat(line.keyAtEndOfLineAfterRemoving(line.effectiveRange(testCase.named())))
            .isEqualTo(testCase.expected());
    }

    @Test
    void testAppendingToTheLastLineOfASongKeepsTheTerminalLast() {
        var song = new Song();
        var line = lastLine(song);
        var terminal = line.getElement(terminalIndex(song));

        assertThat(song.isTerminalOfItsSong(terminal))
            .as("the fixture reaches the case only if the line carries its song's terminal")
            .isTrue();

        var appended = List.of(crotchet(), crotchet(), crotchet());

        song.withModification(() -> appended.forEach(line::addElement));

        var expected = new ArrayList<StaffElement>(appended);
        expected.add(terminal);

        assertThat(line.getElements(new StaffElementRun.ElementRange(0, line.elementCount() - 1)))
            .containsExactlyElementsOf(expected);
    }

    @Test
    void testTheSpansOfAReplacedElementFollowTheReplacement() {
        var line = detachedLine();
        var before = crotchet();
        var replaced = crotchet();
        var after = crotchet();
        line.addElement(before);
        line.addElement(replaced);
        line.addElement(after);

        // Hairpins, because a hairpin survives the replacement of an endpoint by another note.
        var beforeIndex = line.getElementIndex(before);
        var replacedIndex = line.getElementIndex(replaced);
        var afterIndex = line.getElementIndex(after);
        var endingOnIt = hairpinOver(line, Hairpin.Kind.CRESCENDO, new ElementRange(beforeIndex, replacedIndex));
        var anchoredOnIt = hairpinOver(line, Hairpin.Kind.DIMINUENDO, new ElementRange(replacedIndex, afterIndex));
        endingOnIt.addTo();
        anchoredOnIt.addTo();

        var replacement = crotchet();
        line.setElement(line.getElementIndex(replaced), replacement);

        assertThat(line.spanRegistry().getSpans()).containsExactlyInAnyOrder(endingOnIt, anchoredOnIt);
        assertThat(endingOnIt.getEndElement()).isSameAs(replacement);
        assertThat(anchoredOnIt.getAnchorElement()).isSameAs(replacement);
    }

    /** The index every membership case edits at: between two other notes. */
    private static final int MEMBER_INDEX = 1;

    private static final int MEMBERSHIP_LINE_LENGTH = 3;

    /**
     * One edit that moves an element into or out of a line.
     *
     * @param description the case, as the test's display name
     * @param edit        performs it on the line at {@link #MEMBER_INDEX}, given the element that
     *                    enters, which for an exit is the element that takes the leaving one's place
     */
    private record MembershipEdit(String description, BiConsumer<Line, StaffElement> edit) {}

    static Stream<MembershipEdit> exits() {
        return Stream.of(
            new MembershipEdit("removed",
                (line, unused) -> line.removeRange(StaffElementRun.ElementRange.single(MEMBER_INDEX))),
            new MembershipEdit("replaced",
                (line, replacement) -> line.setElement(MEMBER_INDEX, replacement)));
    }

    static Stream<MembershipEdit> entries() {
        return Stream.of(
            new MembershipEdit("inserted",
                (line, entering) -> line.addElement(MEMBER_INDEX, entering)),
            new MembershipEdit("put in another's place",
                (line, entering) -> line.setElement(MEMBER_INDEX, entering)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exits")
    void testAnElementLeavingTheLineIsOnNoLineAndNeitherIsWhatHangsOnIt(MembershipEdit exit) {
        var line = membershipLine();
        var leaving = line.getElement(MEMBER_INDEX);
        var attachment = new FermataAttachment();
        leaving.addAttachment(attachment);

        exit.edit().accept(line, crotchet());

        assertThat(line.contains(leaving)).isFalse();
        assertThat(leaving.getParentLine()).isNull();
        assertThat(attachment.getParentLine()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entries")
    void testAnElementEnteringTheLineIsOnItAndSoIsWhatHangsOnIt(MembershipEdit entry) {
        var line = membershipLine();
        var entering = crotchet();
        var attachment = new FermataAttachment();
        entering.addAttachment(attachment);

        entry.edit().accept(line, entering);

        assertThat(line.contains(entering)).isTrue();
        assertThat(entering.getParentLine()).isSameAs(line);
        assertThat(attachment.getParentLine()).isSameAs(line);
    }

    private static Line membershipLine() {
        var line = detachedLine();

        for (var i = 0; i < MEMBERSHIP_LINE_LENGTH; i++) {
            line.addElement(crotchet());
        }

        return line;
    }

    /**
     * One key given to a line that inherits {@link #RUNNING_KEY}, and what the line then holds as
     * its own.
     *
     * @param description the case, as the test's display name
     * @param given       the key passed to {@link Line#setKey}
     * @param held        the line's own key afterwards
     */
    private record SetKeyCase(String description, Key given, @Nullable Key held) {}

    static Stream<SetKeyCase> setKeyCases() {
        return Stream.of(
            new SetKeyCase("the key the line would inherit is held as none, so the line goes on inheriting",
                RUNNING_KEY, null),
            new SetKeyCase("any other key is held as given", FIRST_CHANGE_KEY, FIRST_CHANGE_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("setKeyCases")
    void testSetKeyHoldsNoKeyWhereTheLineWouldInheritTheOneGiven(SetKeyCase testCase) {
        var song = SongFactory.buildSong(
            line -> {
                line.setKey(RUNNING_KEY);
                line.addElement(crotchet());
            },
            line -> line.addElement(crotchet()));
        var inheriting = song.getLine(1);

        song.withModification(() -> inheriting.setKey(testCase.given()));

        assertThat(inheriting.getKey()).isEqualTo(testCase.held());
    }

    /**
     * One edit through {@link Line} that breaks the song's terminal policy.
     *
     * @param description the case, as the test's display name
     * @param edit        performs it on a song of two lines, the last carrying the terminal
     */
    private record TerminalEdit(String description, Consumer<Song> edit) {}

    static Stream<TerminalEdit> terminalEdits() {
        return Stream.of(
            new TerminalEdit("removing the terminal",
                song -> lastLine(song).removeRange(StaffElementRun.ElementRange.single(terminalIndex(song)))),
            new TerminalEdit("replacing the terminal with a note",
                song -> lastLine(song).setElement(terminalIndex(song), crotchet())),
            new TerminalEdit("inserting a final double barline in front of the terminal",
                song -> lastLine(song).addElement(terminalIndex(song), finalDoubleBarline())),
            new TerminalEdit("appending a final double barline to a line before the last",
                song -> {
                    var first = song.getLine(0);
                    first.addElement(first.elementCount(), finalDoubleBarline());
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("terminalEdits")
    void testAnEditThatBreaksTheTerminalPolicyThrows(TerminalEdit testCase) {
        var song = twoLineSong();

        assertThatThrownBy(() -> song.withModification(() -> testCase.edit().accept(song)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAFinalDoubleBarlineMayBeAppendedToTheLastLine() {
        var song = twoLineSong();
        var line = lastLine(song);

        assertThatCode(() -> song.withModification(() -> line.addElement(line.elementCount(), finalDoubleBarline())))
            .doesNotThrowAnyException();
    }

    /** A song of two lines, the last carrying the song's terminal. */
    private static Song twoLineSong() {
        var song = new Song();
        song.withModification(() -> song.addLine(0, new Line(song)));

        assertThat(song.isTerminalOfItsSong(lastLine(song).getElement(terminalIndex(song))))
            .as("the fixture reaches the cases only if the last line carries its song's terminal")
            .isTrue();

        return song;
    }

    private static Line lastLine(Song song) {
        return song.getLine(song.lineCount() - 1);
    }

    /** The index of the last element of {@code song}'s last line, where its terminal stands. */
    private static int terminalIndex(Song song) {
        return lastLine(song).elementCount() - 1;
    }

    private static void insertRun(Line line, List<StaffElement> elements) {
        line.insertRun(RUN_INDEX, elements);
    }
}
