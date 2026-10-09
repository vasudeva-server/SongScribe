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

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.songOfQuavers;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.beamOverAll;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.demiSemiquaver;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.semiquaver;

/**
 * The beam rules that hold across calls: the sweep every edit runs through {@link Beam#outcomeFor}
 * (an edit that deletes an endpoint of a beam, or puts an element that is not beamable inside it,
 * removes the beam and reports the reason, and every other edit keeps it), the tie rule
 * {@link Beam#spanning} judges a range by, and what a beam derives from the beams its members
 * carry: how many it stacks and how far French beaming shortens a stem.
 */
class BeamTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The beam every sweep case edits around: an anchor, two interior members and an end. */
    private static final ElementRange BEAMED = new ElementRange(1, 4);

    private static final int ANCHOR_INDEX = BEAMED.begin();
    private static final int INTERIOR_INDEX = ANCHOR_INDEX + 1;
    private static final int END_INDEX = BEAMED.end();

    /** A note past the beam's end. */
    private static final int OUTSIDE_INDEX = END_INDEX + 1;

    private static final int LINE_LENGTH = OUTSIDE_INDEX + 1;

    private static final int ONE_BEAM = 1;
    private static final int TWO_BEAMS = 2;
    private static final int THREE_BEAMS = 3;

    private static final int NOT_SHORTENED = 0;
    private static final int SHORTENED_ONE_LEVEL = 1;
    private static final int SHORTENED_TWO_LEVELS = 2;

    /** A line of quavers, and the beam over {@link #BEAMED}. */
    private record BeamedLine(Song song, Line line, Beam beam) {}

    /**
     * One edit to a beamed line that removes the beam, and why.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@code Strings} key of the reason reported
     */
    private record RemovalCase(String description, Consumer<Line> edit, String reasonKey) {}

    /**
     * One edit to a beamed line that leaves the beam alone.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     */
    private record KeptCase(String description, Consumer<Line> edit) {}

    private static BeamedLine beamedLine() {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(FIRST_LINE);
        var beam = addWithoutTracking(song, () -> beamOver(line, BEAMED));

        return new BeamedLine(song, line, beam);
    }

    /**
     * Makes {@code edit} to the beamed line inside a modification bracket, then hands the reports
     * the edit delivered to {@code verdict}.
     */
    private static void whileEditing(BeamedLine beamed, Consumer<Line> edit, Consumer<ReportRecorder> verdict) {
        ReportRecorder.whileRecording(reports -> {
            var line = beamed.line();

            beamed.song().withModification(() -> edit.accept(line));
            verdict.accept(reports);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removalCases")
    void testAnEditThatBreaksTheInvariantRemovesTheBeamAndReportsWhy(RemovalCase testCase) {
        var beamed = beamedLine();

        whileEditing(beamed, testCase.edit(), reports -> {
            assertThat(beamed.line().spanRegistry().findSpans(Beam.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.REPORT_BEAMS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(testCase.reasonKey()));
                }));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keptCases")
    void testAnEditThatLeavesTheInvariantIntactKeepsTheBeam(KeptCase testCase) {
        var beamed = beamedLine();

        whileEditing(beamed, testCase.edit(), reports -> {
            assertThat(beamed.line().spanRegistry().findSpans(Beam.class)).containsExactly(beamed.beam());
            assertThat(reports.reports()).isEmpty();
        });
    }

    static Stream<RemovalCase> removalCases() {
        return Stream.of(
            new RemovalCase("deleting the anchor",
                line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                Strings.REPORT_REASON_ENDPOINT_DELETED),
            new RemovalCase("deleting the end",
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                Strings.REPORT_REASON_ENDPOINT_DELETED),
            new RemovalCase("replacing an interior note with a crotchet",
                line -> line.setElement(INTERIOR_INDEX, crotchet()),
                Strings.REPORT_REASON_BEAM_NOT_BEAMABLE),
            insertionRemoval("inserting a rest inside it", StaffElementFactory::crotchetRest),
            insertionRemoval("inserting a barline inside it", StaffElementFactory::singleBarline),
            insertionRemoval("inserting a breath mark inside it", StaffElementFactory::breathMark),
            insertionRemoval("inserting a key change inside it", () -> keyChange(Key.ONE_SHARP))
        );
    }

    static Stream<KeptCase> keptCases() {
        return Stream.of(
            new KeptCase("deleting an interior member",
                line -> line.removeRange(ElementRange.single(INTERIOR_INDEX))),
            new KeptCase("replacing a note outside the beam with a crotchet",
                line -> line.setElement(OUTSIDE_INDEX, crotchet())),
            new KeptCase("inserting a quaver inside it",
                line -> line.addElement(INTERIOR_INDEX, quaver())),
            new KeptCase("inserting a grace note inside it, which the beam passes over",
                line -> line.addElement(INTERIOR_INDEX, graceQuaver())),
            new KeptCase("modifying a member",
                line -> line.modifyElement(INTERIOR_INDEX, EnumSet.of(ElementField.DOT_COUNT),
                    () -> ((NoteElement) line.getElement(INTERIOR_INDEX)).setDots(Dots.ONE)))
        );
    }

    /** A removal by inserting what {@code inserted} makes between two members. */
    private static RemovalCase insertionRemoval(String description, Supplier<? extends StaffElement> inserted) {
        return new RemovalCase(description,
            line -> line.addElement(INTERIOR_INDEX, inserted.get()),
            Strings.REPORT_REASON_BEAM_INSERTION_NOT_BEAMABLE);
    }

    // -------------------------------------------------------------------------
    // The tie rule of Beam.spanning
    // -------------------------------------------------------------------------

    /**
     * A line of quavers with one tied, and the range a beam is asked over.
     *
     * @param description the case, as the test's display name
     * @param tiedFrom    the index of the note tied to the next note
     * @param range       the range the beam is asked over
     */
    private record TiedRangeCase(String description, int tiedFrom, ElementRange range) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedTiedRanges")
    void testARangeHoldingBothNotesOfATieIsRefused(TiedRangeCase testCase) {
        var line = tiedLine(testCase.tiedFrom());

        assertThat(Beam.spanning(line, testCase.range())).isEqualTo(new Spanning.Refused<Beam>(
            Strings.REPORT_REASON_BEAM_TIED_NOTES, Spanning.Cause.RANGE, Strings.REPORT_BEAMS_REMOVED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedTiedRanges")
    void testARangeHoldingOnlyOneNoteOfATieIsBeamed(TiedRangeCase testCase) {
        var line = tiedLine(testCase.tiedFrom());

        assertThat(Beam.spanning(line, testCase.range())).isInstanceOf(Spanning.Spanned.class);
    }

    static Stream<TiedRangeCase> refusedTiedRanges() {
        return Stream.of(
            new TiedRangeCase("a tie between the first two notes of the range", ANCHOR_INDEX, BEAMED),
            new TiedRangeCase("a tie between the last two notes of the range", END_INDEX - 1, BEAMED)
        );
    }

    static Stream<TiedRangeCase> acceptedTiedRanges() {
        return Stream.of(
            new TiedRangeCase("a last note tied to a note outside the range", END_INDEX, BEAMED),
            new TiedRangeCase("a first note tied from a note outside the range", ANCHOR_INDEX - 1, BEAMED)
        );
    }

    private static Line tiedLine(int tiedFrom) {
        var notes = Stream.generate(StaffElementFactory::quaver).limit(LINE_LENGTH).toList();
        var song = buildSong(line -> {
            notes.forEach(line::addElement);
            line.addTie(notes.get(tiedFrom));
        });

        return song.getLine(FIRST_LINE);
    }

    // -------------------------------------------------------------------------
    // What a beam derives from the beams its members carry
    // -------------------------------------------------------------------------

    /**
     * A beamed group, how many beams it stacks, and how far each stem is shortened.
     *
     * @param description        the group, as the test's display name
     * @param notes              makes the notes of the group, all of them beamed; the first and
     *                           the last are not grace notes
     * @param stackCount         the number of beams stacked over the group
     * @param frenchShortenings  {@link Beam.Member#frenchShortening()} of each member, in order
     */
    private record GroupCase(
        String description, Supplier<List<NoteElement>> notes, int stackCount, List<Integer> frenchShortenings
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupCases")
    void testTheStackCountIsTheMostBeamsAnyMemberCarries(GroupCase testCase) {
        assertThat(beamOverAll(testCase.notes().get()).beamStackCount()).isEqualTo(testCase.stackCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupCases")
    void testAStemIsShortenedByTheBeamsItSharesWithBothNeighboursLessOne(GroupCase testCase) {
        var shortenings = beamOverAll(testCase.notes().get()).members().stream()
            .map(Beam.Member::frenchShortening)
            .toList();

        assertThat(shortenings).isEqualTo(testCase.frenchShortenings());
    }

    static Stream<GroupCase> groupCases() {
        return Stream.of(
            new GroupCase("every stem carries a single beam",
                () -> List.of(quaver(), quaver(), quaver()),
                ONE_BEAM, List.of(NOT_SHORTENED, NOT_SHORTENED, NOT_SHORTENED)),
            new GroupCase("sixteenths shorten the middle stem by one level",
                () -> List.of(semiquaver(), semiquaver(), semiquaver()),
                TWO_BEAMS, List.of(NOT_SHORTENED, SHORTENED_ONE_LEVEL, NOT_SHORTENED)),
            new GroupCase("a 16th, two 32nds and a 16th shorten the 32nd stems by one level",
                () -> List.of(semiquaver(), demiSemiquaver(), demiSemiquaver(), semiquaver()),
                THREE_BEAMS, List.of(NOT_SHORTENED, SHORTENED_ONE_LEVEL, SHORTENED_ONE_LEVEL, NOT_SHORTENED)),
            new GroupCase("thirty-seconds throughout shorten the inner stems by two levels",
                () -> List.of(demiSemiquaver(), demiSemiquaver(), demiSemiquaver(), demiSemiquaver()),
                THREE_BEAMS, List.of(NOT_SHORTENED, SHORTENED_TWO_LEVELS, SHORTENED_TWO_LEVELS, NOT_SHORTENED)),
            new GroupCase("a break in the secondary beam shortens no stem",
                () -> List.of(semiquaver(), semiquaver(), quaver(), semiquaver(), semiquaver()),
                TWO_BEAMS, List.of(NOT_SHORTENED, NOT_SHORTENED, NOT_SHORTENED, NOT_SHORTENED, NOT_SHORTENED)),
            new GroupCase("a quaver among sixteenths leaves the sixteenths to stack the beams",
                () -> List.of(quaver(), semiquaver(), quaver()),
                TWO_BEAMS, List.of(NOT_SHORTENED, NOT_SHORTENED, NOT_SHORTENED)),
            new GroupCase("a grace note between members is neither counted nor a neighbour",
                () -> List.of(semiquaver(), graceQuaver(), semiquaver(), semiquaver()),
                TWO_BEAMS, List.of(NOT_SHORTENED, SHORTENED_ONE_LEVEL, NOT_SHORTENED))
        );
    }
}
