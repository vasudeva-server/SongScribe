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
 * the reason. Every other edit keeps it, including an insertion at the anchor's index or just past
 * the end element, and a swap of one element that carries no time for another.
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

    /** A song of one line, and the tuplet on it. */
    private record Fixture(Song song, Line line, Tuplet tuplet) {}

    /**
     * One edit to a fixture, and whether it removes the tuplet.
     *
     * @param description the edit, as the test's display name
     * @param fixture     builds a fresh song holding one tuplet
     * @param edit        makes the edit, inside a modification bracket
     * @param removed     whether the edit removes the tuplet and reports it
     */
    private record SweepCase(String description, Supplier<Fixture> fixture, Consumer<Line> edit, boolean removed) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sweepCases")
    void testAnEditKeepsOrRemovesTheTupletAsItsContractStates(SweepCase testCase) {
        var fixture = testCase.fixture().get();
        var song = fixture.song();
        var line = fixture.line();
        var reports = new ReportRecorder();

        try {
            song.withModification(() -> testCase.edit().accept(line));

            if (!testCase.removed()) {
                assertThat(line.spanRegistry().findSpans(Tuplet.class)).containsExactly(fixture.tuplet());
                assertThat(reports.reports()).isEmpty();
                return;
            }

            assertThat(line.spanRegistry().findSpans(Tuplet.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_TUPLETS_REMOVED);
                    assertThat(entry.description())
                        .isEqualTo(Strings.get(Strings.DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED));
                }));
        } finally {
            reports.dispose();
        }
    }

    static Stream<SweepCase> sweepCases() {
        return Stream.of(
            new SweepCase("an insertion at the anchor's index keeps it",
                TupletTest::notesFixture, line -> line.addElement(ANCHOR_INDEX, quaver()), false),
            new SweepCase("an insertion strictly inside it removes it",
                TupletTest::notesFixture, line -> line.addElement(INTERIOR_INDEX, quaver()), true),
            new SweepCase("an insertion at the end element's index removes it",
                TupletTest::notesFixture, line -> line.addElement(END_INDEX, quaver()), true),
            new SweepCase("an insertion just past the end element keeps it",
                TupletTest::notesFixture, line -> line.addElement(PAST_END_INDEX, quaver()), false),
            new SweepCase("deleting an interior member removes it",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(INTERIOR_INDEX)), true),
            new SweepCase("deleting an element before the bracket keeps it",
                TupletTest::notesFixture, line -> line.removeRange(ElementRange.single(BEFORE_INDEX)), false),
            new SweepCase("replacing a member with a note of another value removes it",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, crotchet()), true),
            new SweepCase("replacing a member with a rest of the same value removes it",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, quaverRest()), true),
            new SweepCase("replacing a member with a note of the same value and another dot count removes it",
                TupletTest::notesFixture, line -> line.setElement(INTERIOR_INDEX, dottedQuaver()), true),
            new SweepCase("replacing a barline with another barline keeps it",
                TupletTest::barlineFixture, line -> line.setElement(BARLINE_INDEX, doubleBarline()), false),
            new SweepCase("replacing an element outside the bracket keeps it",
                TupletTest::notesFixture, line -> line.setElement(OUTSIDE_INDEX, crotchet()), false),
            new SweepCase("a dot added to a member removes it",
                TupletTest::notesFixture,
                line -> line.modifyElement(INTERIOR_INDEX, EnumSet.of(ElementField.DOT_COUNT),
                    () -> ((NoteElement) line.getElement(INTERIOR_INDEX)).setDots(Dots.ONE)),
                true),
            new SweepCase("a lyric added to a member keeps it",
                TupletTest::notesFixture,
                line -> line.modifyElement(INTERIOR_INDEX, ElementField.LYRIC,
                    () -> line.getElement(INTERIOR_INDEX).putLyric(new Lyric(
                        Lyric.FIRST_VERSE, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false))),
                false)
        );
    }

    private static Fixture notesFixture() {
        var quavers = Stream.generate(StaffElementFactory::quaver)
            .limit(NOTES_LENGTH)
            .toArray(StaffElement[]::new);

        return fixtureOf(NOTES_TRIPLET, quavers);
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
        var tuplet = song.withoutMutationTrackingResult(() -> {
            var built = tripletOver(line, range, NoteValue.EIGHTH);
            built.addTo(line);

            return built;
        });

        return new Fixture(song, line, tuplet);
    }
}
