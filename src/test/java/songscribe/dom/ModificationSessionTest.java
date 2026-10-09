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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.TieRemoval;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.LineEdits.move;
import static songscribe.dom.SongFactory.compactSong;

/**
 * The gate {@link ModificationSession} puts on every modification rule: no rule runs while a
 * batch is replayed, and none judges an edit that throws, whose batch is still delivered.
 *
 * <p>The tie rule is the probe: moving one note of a tied pair to another pitch is an edit it
 * removes the tie for.
 */
class ModificationSessionTest extends UnitTest {

    /** The staff position every tied note starts at. */
    private static final int TIED = 3;

    /** Another staff position, at a different pitch. */
    private static final int OTHER = 4;

    private static final int ANCHOR = 0;
    private static final int END = 1;
    private static final int FIRST_LINE = 0;

    /** A line with a tie from its first note to its second, and a note after them. */
    private static final String TIED_PAIR = TIED + " ~ " + TIED + " " + OTHER;

    @Test
    void testNoRuleRunsWhileABatchIsReplayed() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> move(line, END, OTHER));
            var batch = recorder.batches().getFirst();

            assertThat(batch)
                .as("the fixture reaches the case only if the edit removes the tie")
                .anyMatch(TieRemoval.class::isInstance);

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch, ChangeReport.EMPTY);
            MutationReplayer.redoBatch(song, NO_FONT_CHANGES,
                batch.stream().filter(ElementModification.class::isInstance).toList(), ChangeReport.EMPTY);

            assertThat(anchor.isTiedToNext())
                .as("the replay moves the note without the removal, and nothing removes the tie for it")
                .isTrue();
        } finally {
            recorder.dispose();
        }
    }

    @Test
    void testUndoAndRedoDeliverNoReportEvenWhenARuleWouldRemoveSomething() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> move(line, END, OTHER));

            var batch = recorder.batches().getFirst();
            var moveOnly = batch.stream().filter(ElementModification.class::isInstance).toList();

            ReportRecorder.whileRecording(reports -> {
                MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch, ChangeReport.EMPTY);
                MutationReplayer.redoBatch(song, NO_FONT_CHANGES, moveOnly, ChangeReport.EMPTY);

                assertThat(reports.reports())
                    .as("the redo leaves a tie a rule would remove, and no rule runs to report it")
                    .isEmpty();
            });
        } finally {
            recorder.dispose();
        }
    }

    @Test
    void testAnEditThatThrowsRunsNoRuleAndItsBatchIsStillDelivered() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var recorder = new BatchRecorder();

        try {
            assertThatThrownBy(() -> song.withModification(() -> {
                move(line, END, OTHER);
                throw new IllegalStateException("the edit stops part way");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(anchor.isTiedToNext()).isTrue();
            assertThat(recorder.batches()).singleElement()
                .satisfies(batch -> assertThat(batch).noneMatch(TieRemoval.class::isInstance));
        } finally {
            recorder.dispose();
        }
    }
}
