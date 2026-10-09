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

import java.math.RoundingMode;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.quavers;
import static songscribe.dom.SpanFactory.DUPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.dupletOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.dottedQuaver;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.quaverRest;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The sweep every edit runs through {@link Tuplet#outcomeFor}: a structural change strictly inside
 * a tuplet's bracket, or a change to a written duration inside it, removes the tuplet and reports
 * the reason, and so does a fermata written anywhere from the anchor to the end. Every other edit
 * keeps it, including an insertion at the anchor's index or just past the end element, and a swap
 * of one element that carries no time for another. Also {@link Tuplet#performedTicks}, which
 * scales a written duration to the time it is performed in.
 */
class TupletTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The notes fixture: six quavers, with the triplet over the middle three. */
    private static final int NOTES_LENGTH = 6;

    private static final ElementRange NOTES_TRIPLET = new ElementRange(1, 3);

    private static final int BEFORE_INDEX = NOTES_TRIPLET.begin() - 1;
    private static final int ANCHOR_INDEX = NOTES_TRIPLET.begin();
    private static final int INTERIOR_INDEX = ANCHOR_INDEX + 1;
    private static final int END_INDEX = NOTES_TRIPLET.end();
    private static final int PAST_END_INDEX = END_INDEX + 1;
    private static final int OUTSIDE_INDEX = NOTES_LENGTH - 1;

    /**
     * The barline fixture: a quaver, then the triplet's anchor, a barline, and three more quavers,
     * the last of which ends the triplet, with one quaver after it.
     */
    private static final int BARLINE_INDEX = 2;

    private static final ElementRange BARLINE_TRIPLET = new ElementRange(1, 4);

    /** The duplet fixture: two quavers, under one duplet. */
    private static final ElementRange DUPLET_RANGE = new ElementRange(0, 1);

    private static final long ONE_TICK = 1;

    /** One tick scaled by a duplet's 3:2, which is one and a half ticks, rounded up. */
    private static final long ONE_AND_A_HALF_TICKS_ROUNDED_UP = 2;

    /** A song of one line, and the tuplet on it. */
    private record Fixture(Song song, Line line, Tuplet tuplet) {}

    /**
     * One edit to a fixture that removes the tuplet, and why.
     *
     * @param description the edit, as the test's display name
     * @param fixture     builds a fresh song holding one tuplet
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@code Strings} key of the reason reported
     */
    private record RemovalCase(String description, Supplier<Fixture> fixture, Consumer<Line> edit, String reasonKey) {}

    /**
     * One edit to a fixture that leaves the tuplet alone.
     *
     * @param description the edit, as the test's display name
     * @param fixture     builds a fresh song holding one tuplet
     * @param edit        makes the edit, inside a modification bracket
     */
    private record KeptCase(String description, Supplier<Fixture> fixture, Consumer<Line> edit) {}

    /**
     * Makes {@code edit} to the fixture's line inside a modification bracket, then hands the
     * reports the edit delivered to {@code verdict}.
     */
    private static void whileEditing(Fixture fixture, Consumer<Line> edit, Consumer<ReportRecorder> verdict) {
        ReportRecorder.whileRecording(reports -> {
            var line = fixture.line();

            fixture.song().withModification(() -> edit.accept(line));
            verdict.accept(reports);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removalCases")
    void testAnEditThatUnbalancesTheTupletRemovesItAndReportsWhy(RemovalCase testCase) {
        var fixture = testCase.fixture().get();

        whileEditing(fixture, testCase.edit(), reports -> {
            assertThat(fixture.line().spanRegistry().findSpans(Tuplet.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.REPORT_TUPLETS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(testCase.reasonKey()));
                }));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keptCases")
    void testAnEditThatLeavesTheTupletBalancedKeepsIt(KeptCase testCase) {
        var fixture = testCase.fixture().get();

        whileEditing(fixture, testCase.edit(), reports -> {
            assertThat(fixture.line().spanRegistry().findSpans(Tuplet.class)).containsExactly(fixture.tuplet());
            assertThat(reports.reports()).isEmpty();
        });
    }

    static Stream<RemovalCase> removalCases() {
        return Stream.concat(Stream.of(
            interiorRemoval("an insertion strictly inside it",
                TupletTest::notesFixture, line -> line.addElement(INTERIOR_INDEX, quaver())),
            interiorRemoval("an insertion at the end element's index",
                TupletTest::notesFixture, line -> line.addElement(END_INDEX, quaver())),
            interiorRemoval("deleting an interior member",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(INTERIOR_INDEX))),
            interiorRemoval("deleting only the anchor",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(ANCHOR_INDEX))),
            interiorRemoval("deleting only the end",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(END_INDEX))),
            interiorRemoval("replacing a member with a note of another value",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, crotchet())),
            interiorRemoval("replacing a member with a rest of the same value",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, quaverRest())),
            interiorRemoval("replacing a member with a note of the same value and another dot count",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, dottedQuaver())),
            interiorRemoval("replacing a member and a note outside the bracket",
                TupletTest::notesFixture,
                replacingPairs(OUTSIDE_INDEX, crotchet(), INTERIOR_INDEX, crotchet())),
            interiorRemoval("replacing a member with a note of its own kind, and the end with another value",
                TupletTest::notesFixture,
                replacingPairs(INTERIOR_INDEX, quaver(), END_INDEX, crotchet())),
            fermataRemoval("a fermata added on the anchor", ANCHOR_INDEX),
            fermataRemoval("a fermata added on an interior member", INTERIOR_INDEX),
            fermataRemoval("a fermata added on the end", END_INDEX)
        ), fieldRemovals());
    }

    static Stream<KeptCase> keptCases() {
        return Stream.concat(Stream.of(
            new KeptCase("an insertion at the anchor's index",
                TupletTest::notesFixture, line -> line.addElement(ANCHOR_INDEX, quaver())),
            new KeptCase("an insertion just past the end element",
                TupletTest::notesFixture, line -> line.addElement(PAST_END_INDEX, quaver())),
            new KeptCase("deleting an element before the bracket",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(BEFORE_INDEX))),
            new KeptCase("deleting the element just past the end",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(PAST_END_INDEX))),
            new KeptCase("replacing a barline with another barline",
                TupletTest::barlineFixture, line -> line.setElement(BARLINE_INDEX, doubleBarline())),
            new KeptCase("replacing an element outside the bracket",
                TupletTest::notesFixture, line -> line.setElement(OUTSIDE_INDEX, crotchet())),
            new KeptCase("replacing two elements outside the bracket",
                TupletTest::notesFixture,
                replacingPairs(BEFORE_INDEX, crotchet(), OUTSIDE_INDEX, crotchet())),
            new KeptCase("replacing a member with a note of its own kind, and an element outside the bracket",
                TupletTest::notesFixture,
                replacingPairs(INTERIOR_INDEX, quaver(), OUTSIDE_INDEX, crotchet()))
        ), fieldKeeps());
    }

    /** The removal of a tuplet that an edit to the notes fixture unbalances. */
    private static RemovalCase interiorRemoval(String description, Supplier<Fixture> fixture, Consumer<Line> edit) {
        return new RemovalCase(description, fixture, edit, Strings.REPORT_REASON_TUPLET_INTERIOR_CHANGED);
    }

    /** The removal of the notes fixture's tuplet by a fermata written on the element at {@code index}. */
    private static RemovalCase fermataRemoval(String description, int index) {
        return new RemovalCase(description, TupletTest::notesFixture,
            line -> line.modifyElement(index, ElementField.FERMATA,
                () -> FermataAttachment.addFermata(line.getElement(index))),
            Strings.REPORT_REASON_FERMATA);
    }

    /**
     * What a modification of {@code field} on a member does to the tuplet: a fermata removes it
     * for its own reason, a change to the dot count removes it as an interior change, and every
     * other field keeps it. The switch names every field, so a field added to
     * {@link ElementField} fails the build here until the contract says what it does.
     *
     * @return the {@code Strings} key of the reason the tuplet is removed, or {@code null} when it
     *     is kept
     */
    private static @Nullable String modificationReasonKey(ElementField field) {
        return switch (field) {
            case FERMATA -> Strings.REPORT_REASON_FERMATA;
            case DOT_COUNT -> Strings.REPORT_REASON_TUPLET_INTERIOR_CHANGED;
            case SLIDE, PITCH, TRILL, STEM, ACCIDENTAL, ARTICULATION, DYNAMIC_ATTACHMENT,
                 TEMPO_CHANGE, BEAT_CHANGE, ANNOTATION, LYRIC, KEY -> null;
        };
    }

    /** One removal for every field whose modification removes the tuplet. */
    private static Stream<RemovalCase> fieldRemovals() {
        return Stream.of(ElementField.values()).flatMap(field -> {
            var reasonKey = modificationReasonKey(field);

            if (reasonKey == null) {
                return Stream.empty();
            }

            return Stream.of(new RemovalCase("a modification of " + field + " on a member",
                TupletTest::notesFixture, modifying(field), reasonKey));
        });
    }

    /** One kept case for every field whose modification leaves the tuplet alone. */
    private static Stream<KeptCase> fieldKeeps() {
        return Stream.of(ElementField.values()).flatMap(field -> {
            if (modificationReasonKey(field) != null) {
                return Stream.empty();
            }

            return Stream.of(new KeptCase("a modification of " + field + " on a member",
                TupletTest::notesFixture, modifying(field)));
        });
    }

    /**
     * A modification of {@code field} on an interior member. The change itself is empty: the
     * tuplet judges the fields a modification names, not what it writes.
     */
    private static Consumer<Line> modifying(ElementField field) {
        return line -> line.modifyElement(INTERIOR_INDEX, field, () -> { });
    }

    /** A replacement of two elements, applied as one change. */
    private static Consumer<Line> replacingPairs(
        int firstIndex, StaffElement first, int secondIndex, StaffElement second
    ) {
        return line -> {
            var pairs = new TreeMap<Integer, StaffElement>();
            pairs.put(firstIndex, first);
            pairs.put(secondIndex, second);

            line.replace(new ElementChange.Replacement(line, pairs));
        };
    }

    private static Fixture notesFixture() {
        return fixtureOf(NOTES_TRIPLET, quavers(NOTES_LENGTH).toArray(StaffElement[]::new));
    }

    private static Fixture barlineFixture() {
        return fixtureOf(BARLINE_TRIPLET, quaver(), quaver(), singleBarline(), quaver(), quaver(), quaver());
    }

    /** A song of one line of {@code elements}, holding a triplet of quavers over {@code range}. */
    private static Fixture fixtureOf(ElementRange range, StaffElement... elements) {
        var song = buildSong(line -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
        var line = song.getLine(FIRST_LINE);
        var tuplet = addWithoutTracking(song, () -> tripletOver(line, range, NoteValue.EIGHTH));

        return new Fixture(song, line, tuplet);
    }

    /** A duplet of quavers on a line of two quavers. */
    private static Tuplet dupletOfQuavers() {
        var song = buildSong(line -> quavers(DUPLET_RANGE.size()).forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);

        return addWithoutTracking(song, () -> dupletOver(line, DUPLET_RANGE, NoteValue.EIGHTH));
    }

    // -------------------------------------------------------------------------
    // Performed ticks
    // -------------------------------------------------------------------------

    /**
     * A tuplet, a written duration, and the duration it is performed in.
     *
     * @param description  the case, as the test's display name
     * @param tuplet       builds the tuplet
     * @param writtenTicks the written duration to scale
     * @param rounding     how a result that is not a whole number of ticks is rounded
     * @param expected     the performed duration
     */
    private record PerformedCase(
        String description, Supplier<Tuplet> tuplet, long writtenTicks, RoundingMode rounding, long expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("performedCases")
    void testAWrittenDurationIsScaledByNormalNotesOverGrade(PerformedCase testCase) {
        var tuplet = testCase.tuplet().get();

        assertThat(tuplet.performedTicks(testCase.writtenTicks(), testCase.rounding()))
            .isEqualTo(testCase.expected());
    }

    @Test
    void testAnInexactScalingIsRefusedWhenNoRoundingIsAllowed() {
        var tuplet = notesFixture().tuplet();

        assertThatThrownBy(() -> tuplet.performedTicks(ONE_TICK, RoundingMode.UNNECESSARY))
            .isInstanceOf(ArithmeticException.class);
    }

    static Stream<PerformedCase> performedCases() {
        Supplier<Tuplet> triplet = () -> notesFixture().tuplet();

        return Stream.of(
            new PerformedCase("a triplet's whole group of ticks is performed in the normal notes' time",
                triplet, TupletGrade.TRIPLET.number(), RoundingMode.UNNECESSARY, TRIPLET_NORMAL_NOTES),
            new PerformedCase("a duplet's whole group of ticks is performed in the normal notes' time",
                TupletTest::dupletOfQuavers, TupletGrade.DUPLET.number(), RoundingMode.UNNECESSARY,
                DUPLET_NORMAL_NOTES),
            new PerformedCase("no written time is no performed time",
                triplet, 0, RoundingMode.UNNECESSARY, 0),
            new PerformedCase("a triplet's two thirds of a tick round up to a tick",
                triplet, ONE_TICK, RoundingMode.HALF_UP, ONE_TICK),
            new PerformedCase("a triplet's two thirds of a tick round down to none",
                triplet, ONE_TICK, RoundingMode.DOWN, 0),
            new PerformedCase("a duplet's one and a half ticks round up",
                TupletTest::dupletOfQuavers, ONE_TICK, RoundingMode.HALF_UP, ONE_AND_A_HALF_TICKS_ROUNDED_UP),
            new PerformedCase("a duplet's one and a half ticks round half down",
                TupletTest::dupletOfQuavers, ONE_TICK, RoundingMode.HALF_DOWN, ONE_TICK)
        );
    }
}
