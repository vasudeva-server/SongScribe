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
    private static final String THREE_NOTES = PITCH + " " + PITCH + " " + PITCH;
    private static final ElementRange SPAN = new ElementRange(0, 2);

    private static final TupletNormalTime TWO_CROTCHETS = ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.QUARTER);

    /** A ratio whose normal-note count equals the grade, which states no tuplet at all. */
    private static final TupletNormalTime SAME_COUNT = ratioOf(TRIPLET_GRADE, NoteValue.QUARTER);

    private static final double Y_OFFSET_SS = 1.5;

    /**
     * One pending tuplet over {@link #SPAN} that the pass adds.
     *
     * @param description    the case, as the test's display name
     * @param statedRatio    the ratio the file states, or {@code null} when it states none
     * @param expectedReport what the pass records for it
     */
    private record AddedCase(
        String description,
        @Nullable TupletNormalTime statedRatio,
        List<ReportedChange> expectedReport
    ) {}

    /**
     * One pending tuplet over {@link #SPAN} that the pass drops.
     *
     * @param description the case, as the test's display name
     * @param statedRatio the ratio the file states
     * @param reasonKey   the {@code Strings} key of the validator's reason, which the pass reports
     */
    private record DroppedCase(String description, TupletNormalTime statedRatio, String reasonKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("addedCases")
    void testAPendingTupletIsAddedWithItsRatioAndOffset(AddedCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, THREE_NOTES);
        var line = song.getLine(LINE);

        var report = settle(song, pendingOver(line, testCase.statedRatio(), Y_OFFSET_SS));

        assertThat(report.changes()).isEqualTo(testCase.expectedReport());
        assertThat(line.spanRegistry().findSpans(Tuplet.class)).singleElement().satisfies(tuplet -> {
            assertThat(tuplet.getRatio().normalNotes()).isEqualTo(TRIPLET_NORMAL_NOTES);
            assertThat(tuplet.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS);

            if (testCase.statedRatio() != null) {
                assertThat(tuplet.getRatio()).isEqualTo(testCase.statedRatio());
            }
        });
    }

    static Stream<AddedCase> addedCases() {
        return Stream.of(
            new AddedCase("a stated ratio the validator accepts is kept and not reported",
                TWO_CROTCHETS, List.of()),
            new AddedCase("an absent ratio is derived from the beat and reported as updated", null,
                List.of(new ReportedChange(
                    Strings.DIALOG_REPORT_TUPLETS_UPDATED,
                    Strings.get(Strings.DIALOG_REPORT_UPDATED_DETAIL, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES),
                    spanLocation())))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("droppedCases")
    void testAPendingTupletTheValidatorRejectsIsDroppedWithItsReason(DroppedCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, THREE_NOTES);
        var line = song.getLine(LINE);

        var report = settle(song, pendingOver(line, testCase.statedRatio(), Y_OFFSET_SS));

        assertThat(report.changes()).containsExactly(new ReportedChange(
            Strings.DIALOG_REPORT_TUPLETS_REMOVED,
            Strings.get(testCase.reasonKey()),
            spanLocation()));
        assertThat(line.spanRegistry().findSpans(Tuplet.class)).isEmpty();
    }

    static Stream<DroppedCase> droppedCases() {
        return Stream.of(
            new DroppedCase("a stated ratio the validator rejects is dropped with the validator's reason",
                SAME_COUNT, Strings.DIALOG_REPORT_REASON_NUMBER_EQUALS_NORMAL_NOTES)
        );
    }

    /** The range every report entry of this class names: {@link #SPAN} on the one line. */
    private static ElementLocationRange spanLocation() {
        return new ElementLocationRange(
            new ElementLocation(LINE, SPAN.begin()), new ElementLocation(LINE, SPAN.end()));
    }

    /** Settles {@code song} with {@code tuplet} as the only pending span the reader found. */
    private static ChangeReport settle(Song song, PendingTuplet tuplet) {
        return new ParsedSong(song, List.of(tuplet), List.of(), List.of(), List.of()).settle();
    }

    private static PendingTuplet pendingOver(Line line, @Nullable TupletNormalTime statedRatio, double yOffsetSs) {
        return new PendingTuplet(
            line.getElement(SPAN.begin()), line.getElement(SPAN.end()), TupletGrade.TRIPLET, statedRatio, yOffsetSs);
    }
}
