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

import net.engio.mbassy.listener.Handler;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * What {@link Span#addTo} and {@link Span#removeFrom} promise across more than one call: a
 * removal may be repeated, an addition's displacements are applied once however often the
 * addition is replayed, and a deletion and the span it removes are undone and redone together.
 */
class SpanTest extends UnitTest {

    private static final int LINE_LENGTH = 5;

    /** The crescendo on the line before the one under test is added. */
    private static final ElementRange EXISTING = new ElementRange(0, 2);

    /** A crescendo flush against {@link #EXISTING}, which a crescendo absorbs. */
    private static final ElementRange FLUSH =
        new ElementRange(EXISTING.end() + SPAN_ADJACENCY_REACH, LINE_LENGTH - 1);

    private static final ElementRange MERGED = new ElementRange(EXISTING.begin(), FLUSH.end());

    /** Takes the batch of each modification bracket that closes on the class's bus. */
    private static final class BatchRecorder {
        private final List<List<Mutation>> batches = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void songDidChange(SongDidChangeNotification notification) {
            batches.add(List.copyOf(notification.getMutations()));
        }
    }

    @Test
    void testRemovingASpanASecondTimeChangesNothingAndThrowsNothing() {
        var line = detachedLine();

        for (var i = 0; i < LINE_LENGTH; i++) {
            line.addElement(quaver());
        }

        var removed = crescendoOver(line, EXISTING);
        var bystander = new Diminuendo(line.getElement(FLUSH.begin()), line.getElement(FLUSH.end()));
        removed.addTo(line);
        bystander.addTo(line);

        removed.removeFrom(line);

        assertThatCode(() -> removed.removeFrom(line)).doesNotThrowAnyException();
        assertThat(line.spanRegistry().getSpans())
            .as("the second removal finds nothing of its own and leaves the other span standing")
            .containsExactly(bystander);
    }

    @Test
    void testAnAdditionsDisplacementsAreAppliedOnceAcrossUndoAndRedo() {
        var song = songOfQuavers();
        var line = song.getLine(0);
        song.withModification(() -> crescendoOver(line, EXISTING).addTo(line));

        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> crescendoOver(line, FLUSH).addTo(line));

            var batch = recorder.batches.getFirst();

            assertThat(crescendoRanges(line)).containsExactly(MERGED);

            undo(song, batch);

            assertThat(crescendoRanges(line))
                .as("undo takes the merged crescendo out and puts the absorbed one back")
                .containsExactly(EXISTING);

            redo(song, batch);

            assertThat(crescendoRanges(line))
                .as("redo reproduces the batch as recorded, so the absorption is not derived again")
                .containsExactly(MERGED);
        } finally {
            recorder.subscription.dispose();
        }
    }

    @Test
    void testADeletionAndTheSpanItRemovesAreUndoneAndRedoneTogether() {
        var song = songOfQuavers();
        var line = song.getLine(0);
        var elements = List.copyOf(line.effectiveElements());
        var crescendo = crescendoOver(line, EXISTING);
        song.withModification(() -> crescendo.addTo(line));

        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> line.deleteRange(line.effectiveRange(EXISTING)));

            var batch = recorder.batches.getFirst();
            var survivors = elements.subList(EXISTING.end() + 1, elements.size());

            assertThat(line.effectiveElements()).containsExactlyElementsOf(survivors);
            assertThat(line.spanRegistry().getSpans()).isEmpty();

            undo(song, batch);

            assertThat(line.effectiveElements())
                .as("undo puts every deleted element back where it stood")
                .containsExactlyElementsOf(elements);
            assertThat(line.spanRegistry().getSpans()).containsExactly(crescendo);

            redo(song, batch);

            assertThat(line.effectiveElements()).containsExactlyElementsOf(survivors);
            assertThat(line.spanRegistry().getSpans()).isEmpty();
        } finally {
            recorder.subscription.dispose();
        }
    }

    /** A song of one line of {@link #LINE_LENGTH} quavers. */
    private static Song songOfQuavers() {
        return SongFactory.buildSong(line -> {
            line.setKey(Key.NO_ACCIDENTALS);

            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(quaver());
            }
        });
    }

    /** Replays {@code batch} backwards, as the undo engine does. */
    private static void undo(Song song, List<Mutation> batch) {
        var scoreView = scoreViewOf(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var i = batch.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, batch.get(i));
            }
        }));
    }

    /** Replays {@code batch} forwards, as the undo engine does. */
    private static void redo(Song song, List<Mutation> batch) {
        var scoreView = scoreViewOf(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var mutation : batch) {
                MutationReplayer.applyRedo(scoreView, mutation);
            }
        }));
    }

    private static ScoreView scoreViewOf(Song song) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        return scoreView;
    }

    private static Crescendo crescendoOver(Line line, ElementRange range) {
        return new Crescendo(line.getElement(range.begin()), line.getElement(range.end()));
    }

    private static List<ElementRange> crescendoRanges(Line line) {
        return line.spanRegistry().getSpans().stream()
            .filter(Crescendo.class::isInstance)
            .map(span -> new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex()))
            .toList();
    }
}
