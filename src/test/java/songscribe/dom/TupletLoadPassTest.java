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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link TupletLoadPass#run}: the one place a read tuplet becomes a {@link Tuplet}. A stated ratio
 * is kept when the validator accepts it, an absent one is derived and reported, and a tuplet the
 * validator rejects or whose span holds too few notes is dropped with its reason.
 */
class TupletLoadPassTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int LINE = 0;

    /** A triplet: three notes in the time of two. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;

    /** The three crotchets every case's tuplet spans, unless it spans rests. */
    private static final String THREE_NOTES = PITCH + " " + PITCH + " " + PITCH + " " + PITCH;
    private static final ElementRange SPAN = new ElementRange(0, 2);

    private static final TupletRatio TWO_CROTCHETS =
        new TupletRatio(TRIPLET_NORMAL_NOTES, new NotatableValue(NoteValue.QUARTER, Dots.NONE));

    /** A ratio whose normal-note count equals the grade, which states no tuplet at all. */
    private static final TupletRatio SAME_COUNT =
        new TupletRatio(TRIPLET_GRADE, new NotatableValue(NoteValue.QUARTER, Dots.NONE));

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

        var report = TupletLoadPass.run(song, List.of(pendingOver(line, testCase.statedRatio(), 0)));
        var tuplets = line.spanRegistry().findSpans(Tuplet.class);

        switch (testCase.outcome()) {
            case ADDED_AS_STATED -> {
                assertThat(report.isEmpty()).isTrue();
                assertThat(tuplets).singleElement()
                    .satisfies(tuplet -> assertThat(tuplet.getRatio()).isEqualTo(testCase.statedRatio()));
            }
            case ADDED_DERIVED -> {
                assertThat(report.changes()).singleElement()
                    .isInstanceOfSatisfying(TupletLoadPass.Change.Updated.class,
                        updated -> assertThat(updated.normalNotes()).isEqualTo(TRIPLET_NORMAL_NOTES));
                assertThat(tuplets).singleElement()
                    .satisfies(tuplet -> assertThat(tuplet.getRatio().normalNotes()).isEqualTo(TRIPLET_NORMAL_NOTES));
            }
            case DROPPED -> {
                assertThat(report.changes()).singleElement()
                    .isInstanceOfSatisfying(TupletLoadPass.Change.Removed.class,
                        removed -> assertThat(removed.reason()).isEqualTo(testCase.reason()));
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
                THREE_NOTES, SAME_COUNT, Outcome.DROPPED, TupletValidator.Reason.BAD_RATIO),
            new LoadCase("a span with fewer than two notes is dropped as holding too few",
                PITCH + " R R " + PITCH, TWO_CROTCHETS, Outcome.DROPPED, TupletValidator.Reason.TOO_FEW_NOTES)
        );
    }

    @Test
    void testTheAddedTupletTakesThePendingOffset() {
        var song = compactSong(Key.NO_ACCIDENTALS, THREE_NOTES);
        var line = song.getLine(LINE);

        TupletLoadPass.run(song, List.of(pendingOver(line, TWO_CROTCHETS, Y_OFFSET_SS)));

        assertThat(line.spanRegistry().findSpans(Tuplet.class)).singleElement()
            .satisfies(tuplet -> assertThat(tuplet.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS));
    }

    private static PendingTuplet pendingOver(Line line, @Nullable TupletRatio statedRatio, double yOffsetSs) {
        return new PendingTuplet(
            line.getElement(SPAN.begin()), line.getElement(SPAN.end()), TupletGrade.TRIPLET, statedRatio, yOffsetSs);
    }
}
