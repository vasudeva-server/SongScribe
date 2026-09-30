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
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.ratioOf;

/**
 * {@link TupletLoadPass#run}: the one place a read tuplet becomes a {@link Tuplet}. A stated ratio
 * is kept when the validator accepts it, an absent one is derived and reported, and a tuplet the
 * validator rejects is dropped with the validator's reason. An added tuplet carries the offset the
 * file stated.
 */
class TupletLoadPassTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int LINE = 0;

    private static final int TRIPLET_GRADE = TupletGrade.TRIPLET.number();

    /** The three crotchets every case's tuplet spans. */
    private static final String THREE_NOTES = PITCH + " " + PITCH + " " + PITCH + " " + PITCH;
    private static final ElementRange SPAN = new ElementRange(0, 2);

    private static final TupletRatio TWO_CROTCHETS = ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.QUARTER);

    /** A ratio whose normal-note count equals the grade, which states no tuplet at all. */
    private static final TupletRatio SAME_COUNT = ratioOf(TRIPLET_GRADE, NoteValue.QUARTER);

    private static final double Y_OFFSET_SS = 1.5;

    /**
     * One pending tuplet over {@link #SPAN}, and what the pass makes of it.
     *
     * @param description the case, as the test's display name
     * @param notation    the song's one line, in compact notation
     * @param statedRatio the ratio the file states, or {@code null} when it states none
     * @param outcome     what the pass does with it
     * @param reason      the reason reported for a dropped tuplet, {@code null} otherwise
     */
    private record LoadCase(
        String description,
        String notation,
        @Nullable TupletRatio statedRatio,
        Outcome outcome,
        TupletValidator.@Nullable Reason reason
    ) {}

    private enum Outcome {
        /** Added with the ratio the file stated, and not reported. */
        ADDED_AS_STATED,
        /** Added with a derived ratio, and reported as updated. */
        ADDED_DERIVED,
        /** Not added, and reported as removed. */
        DROPPED
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testAPendingTupletIsAddedWithItsRatioOrDroppedWithItsReason(LoadCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.notation());
        var line = song.getLine(LINE);

        var report = new ChangeReport();

        TupletLoadPass.run(song, List.of(pendingOver(line, testCase.statedRatio(), Y_OFFSET_SS)), report);

        var tuplets = line.spanRegistry().findSpans(Tuplet.class);

        switch (testCase.outcome()) {
            case ADDED_AS_STATED -> {
                assertThat(report.isEmpty()).isTrue();
                assertThat(tuplets).singleElement().satisfies(tuplet -> {
                    assertThat(tuplet.getRatio()).isEqualTo(testCase.statedRatio());
                    assertThat(tuplet.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS);
                });
            }
            case ADDED_DERIVED -> {
                assertThat(report.changes()).containsExactly(new ReportedChange(
                    Strings.DIALOG_REPORT_TUPLETS_UPDATED,
                    Strings.get(Strings.DIALOG_REPORT_UPDATED_DETAIL, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES),
                    spanLocation()));
                assertThat(tuplets).singleElement().satisfies(tuplet -> {
                    assertThat(tuplet.getRatio().normalNotes()).isEqualTo(TRIPLET_NORMAL_NOTES);
                    assertThat(tuplet.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS);
                });
            }
            case DROPPED -> {
                var reason = testCase.reason();

                assertThat(reason).isNotNull();
                assertThat(report.changes()).containsExactly(new ReportedChange(
                    Strings.DIALOG_REPORT_TUPLETS_REMOVED,
                    Strings.get(reason.messageKey()),
                    spanLocation()));
                assertThat(tuplets).isEmpty();
            }
        }
    }

    static Stream<LoadCase> loadCases() {
        return Stream.of(
            new LoadCase("a stated ratio the validator accepts is kept",
                THREE_NOTES, TWO_CROTCHETS, Outcome.ADDED_AS_STATED, null),
            new LoadCase("an absent ratio is derived from the beat",
                THREE_NOTES, null, Outcome.ADDED_DERIVED, null),
            new LoadCase("a stated ratio the validator rejects is dropped with the validator's reason",
                THREE_NOTES, SAME_COUNT, Outcome.DROPPED, TupletValidator.Reason.BAD_RATIO)
        );
    }

    /** The range every report entry of this class names: {@link #SPAN} on the one line. */
    private static ElementLocationRange spanLocation() {
        return new ElementLocationRange(
            new ElementLocation(LINE, SPAN.begin()), new ElementLocation(LINE, SPAN.end()));
    }

    private static PendingTuplet pendingOver(Line line, @Nullable TupletRatio statedRatio, double yOffsetSs) {
        return new PendingTuplet(
            line.getElement(SPAN.begin()), line.getElement(SPAN.end()), TupletGrade.TRIPLET, statedRatio, yOffsetSs);
    }
}
