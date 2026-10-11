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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.SpanRemoval;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.SongFactory.songOfQuavers;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.quaverRest;

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

    /** The diminuendo that separates two crescendos the deletion leaves touching. */
    private static final ElementRange BRIDGING_DIMINUENDO = new ElementRange(EXISTING.end(), EXISTING.end() + 3);

    /** The crescendo after {@link #BRIDGING_DIMINUENDO}, before the deletion. */
    private static final ElementRange LAST_CRESCENDO =
        new ElementRange(BRIDGING_DIMINUENDO.end(), BRIDGING_DIMINUENDO.end() + 2);

    /** How many elements the deletion between the two crescendos takes. */
    private static final int DELETED_COUNT = 2;

    /** The elements inside {@link #BRIDGING_DIMINUENDO}, which the deletion takes. */
    private static final ElementRange DELETED_BETWEEN =
        new ElementRange(EXISTING.end() + 1, EXISTING.end() + DELETED_COUNT);

    private static final int CHAIN_LINE_LENGTH = LAST_CRESCENDO.end() + 1;

    /** {@link #LAST_CRESCENDO} once {@link #DELETED_BETWEEN} is gone: it touches {@link #EXISTING}. */
    private static final ElementRange LAST_CRESCENDO_LEFT = LAST_CRESCENDO.shiftedBy(-DELETED_COUNT);

    @Test
    void testRemovingASpanASecondTimeChangesNothingAndThrowsNothing() {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(0);
        var removed = hairpinOver(line, Hairpin.Kind.CRESCENDO, EXISTING);
        var bystander = hairpinOver(line, Hairpin.Kind.DIMINUENDO, FLUSH);
        song.withModification(() -> {
            removed.addTo();
            bystander.addTo();
        });

        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> removed.removeFrom());

            var firstRemovalBatches = recorder.batches().size();

            assertThatCode(() -> song.withModification(() -> removed.removeFrom())).doesNotThrowAnyException();

            assertThat(recorder.batches().stream().skip(firstRemovalBatches).flatMap(List::stream))
                .as("the second removal records nothing")
                .isEmpty();
            assertThat(line.spanRegistry().getSpans())
                .as("the second removal finds nothing of its own and leaves the other span standing")
                .containsExactly(bystander);
        } finally {
            recorder.dispose();
        }
    }

    @Test
    void testAnAdditionsDisplacementsAreAppliedOnceAcrossUndoAndRedo() {
        var song = songOfQuavers(CHAIN_LINE_LENGTH);
        var line = song.getLine(0);
        song.withModification(() -> {
            hairpinOver(line, Hairpin.Kind.CRESCENDO, EXISTING).addTo();
            hairpinOver(line, Hairpin.Kind.DIMINUENDO, BRIDGING_DIMINUENDO).addTo();
            hairpinOver(line, Hairpin.Kind.CRESCENDO, LAST_CRESCENDO).addTo();
            line.deleteRange(line.effectiveRange(DELETED_BETWEEN));
        });

        assertThat(crescendoRanges(line))
            .as("the arrangement leaves both crescendos touching and apart")
            .containsExactlyInAnyOrder(EXISTING, LAST_CRESCENDO_LEFT);

        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> line.setElement(LAST_CRESCENDO_LEFT.end(), quaverRest()));

            var editBatch = recorder.batches().getFirst();

            assertThat(crescendoRanges(line)).as("the edit removes the crescendo it ends").containsExactly(EXISTING);

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, editBatch);

            var replayBatch = recorder.batches().getLast();

            assertThat(crescendoRanges(line))
                .as("undo puts the removed crescendo back without absorbing the one it touches")
                .containsExactlyInAnyOrder(EXISTING, LAST_CRESCENDO_LEFT);
            assertThat(replayBatch).as("the replay records no removal").noneMatch(SpanRemoval.class::isInstance);

            MutationReplayer.redoBatch(song, NO_FONT_CHANGES, editBatch);

            assertThat(crescendoRanges(line)).containsExactly(EXISTING);
        } finally {
            recorder.dispose();
        }
    }

    @Test
    void testADeletionAndTheSpanItRemovesAreUndoneAndRedoneTogether() {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(0);
        var elements = List.copyOf(line.effectiveElements());
        var crescendo = hairpinOver(line, Hairpin.Kind.CRESCENDO, EXISTING);
        song.withModification(() -> crescendo.addTo());

        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> line.deleteRange(line.effectiveRange(EXISTING)));

            var batch = recorder.batches().getFirst();
            var survivors = elements.subList(EXISTING.end() + 1, elements.size());

            assertThat(line.effectiveElements()).containsExactlyElementsOf(survivors);
            assertThat(line.spanRegistry().getSpans()).isEmpty();

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(line.effectiveElements())
                .as("undo puts every deleted element back where it stood")
                .containsExactlyElementsOf(elements);
            assertThat(line.spanRegistry().getSpans()).containsExactly(crescendo);

            MutationReplayer.redoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(line.effectiveElements()).containsExactlyElementsOf(survivors);
            assertThat(line.spanRegistry().getSpans()).isEmpty();
        } finally {
            recorder.dispose();
        }
    }

    private static List<ElementRange> crescendoRanges(Line line) {
        return line.spanRegistry().getSpans().stream()
            .filter(span -> span instanceof Hairpin hairpin && hairpin.getKind() == Hairpin.Kind.CRESCENDO)
            .map(Span::range)
            .toList();
    }
}
