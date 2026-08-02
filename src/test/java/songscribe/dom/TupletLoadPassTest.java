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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;

/**
 * Unit tests for {@link TupletLoadPass}, the post-parse pass that completes or drops every
 * tuplet a reader produced.
 *
 * <p>The base fixture is quarter notes under the default quarter beat, so a three-note span
 * derives cleanly as a 3:2 quarter-note triplet. Each test below adds exactly one fact to
 * that fixture, so the verdict it asserts has exactly one cause.
 */
class TupletLoadPassTest extends UnitTest {

    private static final int FIRST_LINE_INDEX = 0;

    // Printed tuplet numbers (N)
    private static final int GRADE_TRIPLET = 3;
    private static final int GRADE_QUINTUPLET = 5;

    // Normal-note counts (M)
    private static final int NORMAL_TWO = 2;
    private static final int NORMAL_THREE = 3;
    private static final int NORMAL_FOUR = 4;

    /** An undotted written value. */
    private static final int NO_DOTS = 0;

    // Spans within the fixtures below
    private static final int FIRST_ANCHOR_INDEX = 0;
    private static final int FIRST_END_INDEX = 2;
    private static final int SECOND_ANCHOR_INDEX = 3;
    private static final int SECOND_END_INDEX = 5;

    /** The interior element of a three-note span. */
    private static final int MIDDLE_INDEX = 1;

    /** Notes in the single-span fixture. */
    private static final int TRIPLET_NOTE_COUNT = 3;

    /** Notes in the two-span fixture. */
    private static final int TWO_SPAN_NOTE_COUNT = 6;

    /**
     * A report reduced to its two counts, so a whole outcome is still one assertion now
     * that the report also carries per-change detail.
     */
    private record Outcome(int dropped, int migrated) {

        static Outcome of(TupletLoadPass.Report report) {
            return new Outcome(report.dropped(), report.migrated());
        }
    }

    private static final Outcome NOTHING_HAPPENED = new Outcome(0, 0);
    private static final Outcome ONE_DROPPED = new Outcome(1, 0);
    private static final Outcome ONE_MIGRATED = new Outcome(0, 1);
    private static final Outcome ONE_OF_EACH = new Outcome(1, 1);

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /**
     * A one-line song holding {@code noteCount} quarter notes. The song states no tempo, so
     * the beat everywhere is the default quarter — see {@link Song#resolveBeatAt}.
     */
    private static Song quarterNotes(int noteCount) {
        var song = new Song();
        var line = song.getLine(FIRST_LINE_INDEX);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < noteCount; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        });

        return song;
    }

    private static Line firstLine(Song song) {
        return song.getLine(FIRST_LINE_INDEX);
    }

    private static void addTuplet(Song song, Tuplet tuplet) {
        song.withoutMutationTracking(() -> firstLine(song).addTuplet(tuplet));
    }

    /** Adds a tuplet whose ratio the file did not state, over {@code beginIndex..endIndex}. */
    private static Tuplet addUnresolved(Song song, int beginIndex, int endIndex, int grade) {
        var line = firstLine(song);
        var tuplet = Tuplet.withUnresolvedRatio(
            line.getElement(beginIndex), line.getElement(endIndex), grade);
        addTuplet(song, tuplet);

        return tuplet;
    }

    /** Adds a tuplet whose ratio the file stated in full. */
    private static Tuplet addStated(
        Song song, int beginIndex, int endIndex, int grade, int normalNotes, ElementType noteValue
    ) {
        var line = firstLine(song);
        var tuplet = new Tuplet(
            line.getElement(beginIndex), line.getElement(endIndex),
            grade, normalNotes, noteValue, NO_DOTS);
        addTuplet(song, tuplet);

        return tuplet;
    }

    private static int tupletCount(Song song) {
        return firstLine(song).findSpans(Tuplet.class).size();
    }

    /** Asserts that dropping a tuplet took nothing but the bracket: every note is still there. */
    private static void assertNotesSurvive(Song song, int noteCount) {
        var line = firstLine(song);

        for (var index = 0; index < noteCount; index++) {
            assertThat(line.getElement(index).getType())
                .as("note %d must survive the dropped tuplet", index)
                .isEqualTo(ElementType.CROTCHET);
        }
    }

    // -----------------------------------------------------------------------
    // A stated ratio is trusted
    // -----------------------------------------------------------------------

    /**
     * A file that states 3:4 is asking for a re-timing this editor would never have offered
     * — the conventional span for a quarter-note triplet at a quarter beat is 2 — and the
     * pass must keep it exactly as stated rather than re-deriving it. Redundancy is a
     * property of derivation, not of the tuplet.
     */
    @Test
    void testStatedRatioIsKeptEvenWhenItIsNotTheConventionalSpan() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        var tuplet = addStated(
            song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX,
            GRADE_TRIPLET, NORMAL_FOUR, ElementType.CROTCHET);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report))
                .as("a tuplet the file fully specified is neither dropped nor migrated")
                .isEqualTo(NOTHING_HAPPENED),
            () -> assertThat(tupletCount(song)).as("the tuplet survives").isEqualTo(1),
            () -> assertThat(tuplet.getNormalNotes())
                .as("the stated M must not be replaced by the conventional one")
                .isEqualTo(NORMAL_FOUR)
        );
    }

    // -----------------------------------------------------------------------
    // An absent ratio is derived
    // -----------------------------------------------------------------------

    /**
     * Every file written before the ratio was stored arrives here unresolved, so this is
     * the path every existing song containing a tuplet takes on its first load.
     */
    @Test
    void testAbsentRatioIsDerivedFromTheBeat() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        var tuplet = addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_TRIPLET);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report))
                .as("deriving the ratio counts as a migration, not a drop")
                .isEqualTo(ONE_MIGRATED),
            () -> assertThat(tuplet.isResolved()).as("the tuplet is resolved afterwards").isTrue(),
            () -> assertThat(tuplet.getNormalNotes())
                .as("three quarters at a quarter beat occupy the time of two")
                .isEqualTo(NORMAL_TWO),
            () -> assertThat(tuplet.getNoteValue())
                .as("V is S/N, a quarter note")
                .isEqualTo(ElementType.CROTCHET),
            () -> assertThat(tuplet.getNoteValueDots()).as("V carries no dot").isEqualTo(NO_DOTS)
        );
    }

    /**
     * The migration window names the tuplet it updated, so the report has to carry where it
     * was and what it now says — the counts alone would leave the user hunting.
     */
    @Test
    void testAnUpdatedTupletIsReportedWithItsSpanAndItsNewRatio() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_TRIPLET);

        var report = TupletLoadPass.run(song);

        assertThat(report.changes())
            .as("indices are reported 0-based, exactly as the document stores them")
            .containsExactly(new TupletLoadPass.Change.Updated(
                FIRST_LINE_INDEX, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_TRIPLET, NORMAL_TWO));
    }

    // -----------------------------------------------------------------------
    // Drops
    // -----------------------------------------------------------------------

    /**
     * A file stating an eighth-note value for a span of three quarters contradicts itself.
     * The policy is always to drop, never to repair, so neither the stated value nor the
     * derivable one is used to rescue it.
     */
    @Test
    void testStatedNoteValueDisagreeingWithTheSpanDropsTheTuplet() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addStated(
            song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX,
            GRADE_TRIPLET, NORMAL_TWO, ElementType.QUAVER);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report))
                .as("a self-contradicting file loses its tuplet")
                .isEqualTo(ONE_DROPPED),
            () -> assertThat(tupletCount(song)).as("the tuplet is gone").isZero(),
            () -> assertNotesSurvive(song, TRIPLET_NOTE_COUNT)
        );
    }

    /** Constraint 1: N and M must be a real ratio, and 3:3 is not one. */
    @Test
    void testStatedRatioWhoseNormalNotesEqualTheGradeDropsTheTuplet() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addStated(
            song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX,
            GRADE_TRIPLET, NORMAL_THREE, ElementType.CROTCHET);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report)).isEqualTo(ONE_DROPPED),
            () -> assertThat(tupletCount(song)).as("the tuplet is gone").isZero(),
            () -> assertNotesSurvive(song, TRIPLET_NOTE_COUNT)
        );
    }

    /**
     * Constraint 2: three quarters under a 5 give no exact written value — 288 ticks do not
     * divide by five — so there is nothing the number could stand for.
     */
    @Test
    void testSpanThatDoesNotDivideByTheGradeDropsTheTuplet() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_QUINTUPLET);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report))
                .as("an unresolved tuplet that is also invalid is dropped, not migrated")
                .isEqualTo(ONE_DROPPED),
            // The span is read before the removal detaches the tuplet, so it survives here.
            () -> assertThat(report.changes())
                .as("a removal is reported with its span and the validator's own reason")
                .containsExactly(new TupletLoadPass.Change.Removed(
                    FIRST_LINE_INDEX, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_QUINTUPLET,
                    TupletValidator.Reason.NOT_NOTATABLE)),
            () -> assertThat(tupletCount(song)).as("the tuplet is gone").isZero(),
            () -> assertNotesSurvive(song, TRIPLET_NOTE_COUNT)
        );
    }

    /**
     * Constraint 4: a fermata inflates performed duration by half again, which would break
     * the exact M:N relationship between the written and the performed span.
     */
    @Test
    void testFermataInTheSpanDropsTheTuplet() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_TRIPLET);
        song.withoutMutationTracking(
            () -> firstLine(song).getElement(MIDDLE_INDEX).addAttachment(new FermataAttachment())
        );

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report)).isEqualTo(ONE_DROPPED),
            () -> assertThat(tupletCount(song)).as("the tuplet is gone").isZero(),
            () -> assertNotesSurvive(song, TRIPLET_NOTE_COUNT)
        );
    }

    // -----------------------------------------------------------------------
    // The counts
    // -----------------------------------------------------------------------

    /**
     * One song, one valid unresolved tuplet and one invalid one: the two outcomes must be
     * reported separately, since Phase 10 tells the user about them in different words.
     */
    @Test
    void testCountsReportDroppedAndMigratedSeparately() {
        var song = quarterNotes(TWO_SPAN_NOTE_COUNT);
        var kept = addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_TRIPLET);
        addUnresolved(song, SECOND_ANCHOR_INDEX, SECOND_END_INDEX, GRADE_QUINTUPLET);

        var report = TupletLoadPass.run(song);

        assertAll(
            () -> assertThat(Outcome.of(report)).isEqualTo(ONE_OF_EACH),
            () -> assertThat(tupletCount(song)).as("only the valid tuplet survives").isEqualTo(1),
            () -> assertThat(kept.getNormalNotes())
                .as("the surviving tuplet is the derived triplet")
                .isEqualTo(NORMAL_TWO)
        );
    }

    // -----------------------------------------------------------------------
    // Suspension
    // -----------------------------------------------------------------------

    /**
     * The pass must emit nothing. A removal recorded outside suspension would land in the
     * undo history, so a freshly opened song could be undone back to its invalid state, and
     * a modified flag set here would be cleared again by {@code ScoreView.setSong} anyway.
     */
    @Test
    void testPassLeavesTheModifiedFlagAndTheUndoHistoryUntouched() {
        var song = quarterNotes(TRIPLET_NOTE_COUNT);
        addUnresolved(song, FIRST_ANCHOR_INDEX, FIRST_END_INDEX, GRADE_QUINTUPLET);
        song.setModified(false);

        // Mocked only now, so the fixture's own construction still reaches the real bus.
        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            var report = TupletLoadPass.run(song);

            assertThat(Outcome.of(report))
                .as("the fixture must actually have exercised a removal")
                .isEqualTo(ONE_DROPPED);

            messageCenterMock.verify(() -> MessageCenter.post(any()), never());
        }

        assertThat(song.isModified())
            .as("the pass must not mark the song modified; the caller does that after setSong")
            .isFalse();
    }
}
