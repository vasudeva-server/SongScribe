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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.undo.MutationReplayer;

/**
 * Tests for the beat-edit chokepoint {@link Song#withBeatDefiningEdit}: a write that
 * defines a beat must drop, in its own modification bracket, every tuplet the new beat
 * context invalidates.
 *
 * <p>The alert this raises on the UI side is out of scope here — these tests cover the
 * model behavior only.
 */
class SongBeatEditTupletTest extends UnitTest {

    /** The printed tuplet number of a triplet. */
    private static final int TRIPLET_GRADE = 3;

    /** M for a triplet: three notes in the time of two of the written value. */
    private static final int TRIPLET_NORMAL_NOTES = 2;

    /** An undotted written value. */
    private static final int NO_DOTS = 0;

    private static final int FIRST_LINE_INDEX = 0;
    private static final int ANCHOR_INDEX = 0;
    private static final int MIDDLE_INDEX = 1;
    private static final int END_INDEX = 2;

    /** The element just past a span anchored at {@link #ANCHOR_INDEX}. */
    private static final int AFTER_SPAN_INDEX = 3;

    /** Note count of the fixture's tuplet span. */
    private static final int SPAN_NOTE_COUNT = 3;

    /** Mutation counts the batch-ordering assertions expect. */
    private static final int ONE_REMOVAL_PLUS_ONE_EDIT = 2;
    private static final int NO_TUPLETS = 0;
    private static final int ONE_TUPLET = 1;

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /**
     * A one-line song whose beat comes from the song tempo, holding {@code types} followed
     * by whatever terminal the song maintains.
     */
    private static Song songWithBeat(Duration beat, ElementType... types) {
        var song = new Song();
        var line = song.getLine(FIRST_LINE_INDEX);

        song.withoutMutationTracking(() -> {
            song.setTempo(
                new Tempo(Tempo.DEFAULT_BPM, beat, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)
            );

            for (var type : types) {
                line.addElement(type.newInstance());
            }
        });

        return song;
    }

    /** Adds an untracked tuplet of {@code noteValue} over {@code [begin, end]}. */
    private static void addTuplet(Song song, int begin, int end, ElementType noteValue) {
        var line = song.getLine(FIRST_LINE_INDEX);
        song.withoutMutationTracking(() -> line.addTuplet(new Tuplet(
            line.getElement(begin), line.getElement(end),
            TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, noteValue, NO_DOTS
        )));
    }

    /**
     * Three crotchets under a crotchet beat, bracketed as a 3:2 crotchet triplet — valid,
     * so any removal below is caused by the one beat-defining edit the test performs.
     */
    private static Song crotchetTripletAtSimpleBeat() {
        var song = songWithBeat(
            Duration.CROTCHET,
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
        );
        addTuplet(song, ANCHOR_INDEX, END_INDEX, ElementType.CROTCHET);

        return song;
    }

    /**
     * Three semiquavers under a crotchet beat, bracketed as a 3:2 semiquaver triplet. A
     * semiquaver triplet derives to the same 3:2 under a dotted-crotchet beat too, so this
     * fixture isolates the barrier rule from the conventional-span rule.
     */
    private static Song semiquaverTripletAtSimpleBeat() {
        var song = songWithBeat(
            Duration.CROTCHET,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER, ElementType.SEMIQUAVER
        );
        addTuplet(song, ANCHOR_INDEX, END_INDEX, ElementType.SEMIQUAVER);

        return song;
    }

    private static BeatChangeAttachment toCompoundBeat() {
        return new BeatChangeAttachment(new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED));
    }

    private static TempoChangeAttachment tempoMarking(Duration beat) {
        return new TempoChangeAttachment(
            new Tempo(Tempo.DEFAULT_BPM, beat, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)
        );
    }

    private static int tupletCount(Song song) {
        var line = song.getLine(FIRST_LINE_INDEX);
        return line.findTupletsOverlapping(ANCHOR_INDEX, line.elementCount() - 1).size();
    }

    /**
     * Attaches {@code attachment} to an element the way {@code BeatChangeDialog} does: the
     * raw write is routed through the chokepoint, and the whole thing is recorded as one
     * element modification.
     *
     * @return {@code true} if the edit removed at least one tuplet
     */
    private static boolean attachThroughChokepoint(
        Song song, int elementIndex, ElementField field, Attachment attachment
    ) {
        var line = song.getLine(FIRST_LINE_INDEX);
        var element = line.getElement(elementIndex);
        var removed = new boolean[1];

        line.withModification(() -> line.modifyElement(elementIndex, field, () ->
            removed[0] = song.withBeatDefiningEdit(
                FIRST_LINE_INDEX, elementIndex, () -> element.addAttachment(attachment))
        ));

        return removed[0];
    }

    // -----------------------------------------------------------------------
    // Batch capture and undo replay (this package cannot see songscribe.undo's helper)
    // -----------------------------------------------------------------------

    private static List<Mutation> captureBatch(Runnable edit) {
        var captured = new ArrayList<List<Mutation>>();
        var listener = new Object() {
            @Handler(priority = Integer.MAX_VALUE)
            void onSongDidChange(SongDidChangeNotification notification) {
                captured.add(notification.getMutations());
            }
        };

        MessageCenter.subscribe(listener);

        try {
            edit.run();
        } finally {
            MessageCenter.unsubscribe(listener);
        }

        assertThat(captured).as("the edit must post exactly one batch").hasSize(1);

        return captured.getFirst();
    }

    /** Replays {@code batch} in reverse order inside a bracket, exactly as undo does. */
    private static void replayUndo(Song song, List<Mutation> batch) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var i = batch.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, batch.get(i));
            }
        }));
    }

    // -----------------------------------------------------------------------
    // Attachment edits
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttachmentEdits {

        @Test
        void testBeatChangeInsideSpanRemovesTheTuplet() {
            var song = crotchetTripletAtSimpleBeat();

            var removed = attachThroughChokepoint(
                song, MIDDLE_INDEX, ElementField.BEAT_CHANGE, toCompoundBeat());

            assertThat(removed).as("the chokepoint must report the removal").isTrue();
            assertThat(tupletCount(song)).isEqualTo(NO_TUPLETS);
        }

        /**
         * A barrier on the anchor defines the tuplet's beat instead of splitting it. The
         * semiquaver triplet derives identically under both beats, so survival here is the
         * anchor exemption and nothing else.
         */
        @Test
        void testBeatChangeOnTheAnchorKeepsTheTuplet() {
            var song = semiquaverTripletAtSimpleBeat();

            var removed = attachThroughChokepoint(
                song, ANCHOR_INDEX, ElementField.BEAT_CHANGE, toCompoundBeat());

            assertThat(removed).isFalse();
            assertThat(tupletCount(song)).isEqualTo(ONE_TUPLET);
        }

        /** A beat change past the span's end cannot reach back into it. */
        @Test
        void testBeatChangeAfterTheSpanRemovesNothing() {
            var song = songWithBeat(
                Duration.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            addTuplet(song, ANCHOR_INDEX, END_INDEX, ElementType.CROTCHET);

            var removed = attachThroughChokepoint(
                song, AFTER_SPAN_INDEX, ElementField.BEAT_CHANGE, toCompoundBeat());

            assertThat(removed).isFalse();
            assertThat(tupletCount(song)).isEqualTo(ONE_TUPLET);
        }

        /** The dialog's change branch routes itself, so no wrapping is needed to be safe. */
        @Test
        void testChangingAnExistingBeatChangeRoutesItself() {
            var song = crotchetTripletAtSimpleBeat();
            var line = song.getLine(FIRST_LINE_INDEX);
            var middle = line.getElement(MIDDLE_INDEX);
            var attachment = new BeatChangeAttachment(middle, new BeatChange(
                Duration.CROTCHET_DOTTED, Duration.CROTCHET));
            song.withoutMutationTracking(() -> middle.addAttachment(attachment));

            song.withModification(() -> attachment.setBeatChange(
                new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED)));

            assertThat(tupletCount(song)).isEqualTo(NO_TUPLETS);
        }
    }

    // -----------------------------------------------------------------------
    // Undo
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Undo {

        /**
         * The removal is a companion of the beat edit, so it must precede it in the batch:
         * reverse-order undo then restores the attachment's element first and re-adds the
         * tuplet against a live anchor.
         */
        @Test
        void testRemovalIsRecordedBeforeThePrimaryEdit() {
            var song = crotchetTripletAtSimpleBeat();

            var batch = captureBatch(() -> attachThroughChokepoint(
                song, MIDDLE_INDEX, ElementField.BEAT_CHANGE, toCompoundBeat()));

            assertThat(batch).hasSize(ONE_REMOVAL_PLUS_ONE_EDIT);
            assertThat(batch.getFirst()).isInstanceOf(TupletRemoval.class);
            assertThat(batch.getLast()).isInstanceOf(ElementModification.class);
        }

        @Test
        void testOneUndoRestoresBothTheBeatChangeAndTheTuplet() {
            var song = crotchetTripletAtSimpleBeat();

            var batch = captureBatch(() -> attachThroughChokepoint(
                song, MIDDLE_INDEX, ElementField.BEAT_CHANGE, toCompoundBeat()));

            replayUndo(song, batch);

            var middle = song.getLine(FIRST_LINE_INDEX).getElement(MIDDLE_INDEX);
            assertThat(middle.findAttachment(BeatChangeAttachment.class))
                .as("undo must take the beat change back off")
                .isNull();
            assertThat(tupletCount(song))
                .as("the same undo must bring the tuplet back")
                .isEqualTo(ONE_TUPLET);
        }
    }

    // -----------------------------------------------------------------------
    // Song tempo edits
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SongTempoEdits {

        /**
         * Nothing goes near the tuplet: changing the song's beat is what turns the tempo
         * marking already sitting inside the span into a barrier.
         */
        @Test
        void testChangingTheSongBeatTurnsAnInSpanTempoMarkingIntoABarrier() {
            var song = semiquaverTripletAtSimpleBeat();
            song.withoutMutationTracking(() -> song.getLine(FIRST_LINE_INDEX)
                .getElement(MIDDLE_INDEX)
                .addAttachment(tempoMarking(Duration.CROTCHET)));

            assertThat(tupletCount(song))
                .as("the marking restates the running beat, so the fixture starts valid")
                .isEqualTo(ONE_TUPLET);

            song.setTempo(new Tempo(
                Tempo.DEFAULT_BPM, Duration.CROTCHET_DOTTED,
                Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO
            ));

            assertThat(tupletCount(song)).isEqualTo(NO_TUPLETS);
        }

        /** A tempo change that leaves the beat alone invalidates nothing. */
        @Test
        void testChangingOnlyTheTempoBpmRemovesNothing() {
            var song = crotchetTripletAtSimpleBeat();

            song.setTempo(new Tempo(
                Tempo.DEFAULT_BPM * 2, Duration.CROTCHET,
                Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO
            ));

            assertThat(tupletCount(song)).isEqualTo(ONE_TUPLET);
        }
    }

    // -----------------------------------------------------------------------
    // Structural edits that move a beat-defining attachment
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DisplacedTempoAttachment {

        /**
         * Prepending to the first line moves the initial tempo from the old first element to
         * the new one. The attachment lands back on element 0 of line 0 carrying the same
         * tempo, so the beat in effect is unchanged everywhere and the tuplet survives —
         * which is why {@code Line.addElement} does not route the displacement through the
         * chokepoint.
         */
        @Test
        void testDisplacingTheInitialTempoLeavesTheBeatAndTheTupletAlone() {
            var song = crotchetTripletAtSimpleBeat();
            var line = song.getLine(FIRST_LINE_INDEX);
            song.withoutMutationTracking(() ->
                line.getElement(ANCHOR_INDEX).addAttachment(tempoMarking(Duration.CROTCHET)));

            line.withModification(() ->
                line.addElement(ANCHOR_INDEX, ElementType.CROTCHET.newInstance()));

            assertThat(line.getElement(ANCHOR_INDEX).findAttachment(TempoChangeAttachment.class))
                .as("the initial tempo must have moved to the incoming element")
                .isNotNull();
            assertThat(tupletCount(song)).isEqualTo(ONE_TUPLET);
        }
    }

    // -----------------------------------------------------------------------
    // Replay suppression
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReplaySuppression {

        /**
         * Under replay the recorded batch already carries whatever removals the original
         * edit made, so re-deriving them would drop tuplets the batch is about to restore.
         */
        @Test
        void testNoTupletIsRemovedUnderReplay() {
            var song = crotchetTripletAtSimpleBeat();
            var line = song.getLine(FIRST_LINE_INDEX);
            var middle = line.getElement(MIDDLE_INDEX);

            var removed = new boolean[1];
            song.withModification(() -> song.withReplay(() ->
                removed[0] = song.withBeatDefiningEdit(FIRST_LINE_INDEX, MIDDLE_INDEX,
                    () -> middle.addAttachment(toCompoundBeat()))
            ));

            assertThat(removed[0]).isFalse();
            assertThat(tupletCount(song)).isEqualTo(ONE_TUPLET);
            assertThat(line.elementCount())
                .as("the fixture must be unchanged apart from the attachment")
                .isGreaterThanOrEqualTo(SPAN_NOTE_COUNT);
        }
    }
}
