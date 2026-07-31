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

package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.awt.Font;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.FontChange;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.ReflectionTestHelper;

/**
 * Round-trip tests for {@link MutationReplayer}: for each mutation type, a real edit
 * is driven on a known {@link Song}, the posted batch is captured, and replaying that
 * batch's undo restores the pre-edit document while its redo re-applies the edit.
 *
 * <p>The batch is captured from a real {@code SongDidChangeNotification} (not a
 * hand-built lone {@link songscribe.message.mutation.Mutation}) so companion mutations
 * — terminal maintenance, span invalidation, tuplet auto-removal, tempo displacement —
 * ride along exactly as in production. Deep equality is compared via native
 * serialization (see {@link UndoTestSupport}).
 *
 * <p><b>FontChange exclusion:</b> font state lives on {@link ScoreView}, not on
 * {@link Song}, so it cannot be exercised through a {@code Song}-only edit/serialize
 * harness. It is covered here by {@link Fonts} with a {@code ScoreView} test double
 * asserting the replayer dispatches {@code setFonts}, and end-to-end in Phase 7 manual.
 */
class MutationReplayerRoundTripTest extends UnitTest {


    /** A triplet: three notes in the time of two of its written value. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;

    /**
     * Serializes the pre-edit state, captures the batch a real edit posts, then asserts
     * undo restores the pre-edit serialization and redo restores the post-edit one.
     */
    private static void assertRoundTrip(Song song, Runnable edit) {
        var before = UndoTestSupport.serialize(song);
        var batch = UndoTestSupport.captureBatch(song, edit);
        var after = UndoTestSupport.serialize(song);

        assertThat(after)
            .as("the edit must produce an observable change, else the round-trip is vacuous")
            .isNotEqualTo(before);

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertThat(UndoTestSupport.serialize(song))
            .as("undo must restore the exact pre-edit state")
            .isEqualTo(before);

        UndoTestSupport.replayRedo(scoreView, batch);
        assertThat(UndoTestSupport.serialize(song))
            .as("redo must re-apply the exact post-edit state")
            .isEqualTo(after);
    }

    private static Song songWithNotes(int count) {
        var song = new Song();
        UndoTestSupport.addCrotchets(song, song.getLine(0), count);
        return song;
    }

    // -----------------------------------------------------------------------
    // Element-scoped mutations
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementMutations {

        @Test
        void testElementInsertionRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addElement(1, UndoTestSupport.crotchet()));
        }

        @Test
        void testElementDeletionRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.removeElement(1));
        }

        @Test
        void testElementRangeDeletionRoundTrips() {
            var song = songWithNotes(4);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.removeRange(1, 2));
        }

        @Test
        void testElementReplacementRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.setElement(1, ElementType.QUAVER.newInstance()));
        }

        @Test
        void testElementModificationRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            assertRoundTrip(song, () ->
                line.modifyElement(1, ElementField.FERMATA, () -> line.getElement(1).setFermata(true)));
        }

        @Test
        void testDeletingBeamedNoteRestoresNoteAndBeamOnUndo() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(0), line.getElement(1))));

            // Deleting a beam endpoint invalidates and removes the beam as a companion;
            // undo must restore both the note and the beam.
            assertRoundTrip(song, () -> line.removeElement(1));
        }

        @Test
        void testDeletingHairpinEndpointRestoresTheOriginalSpanOnUndo() {
            var song = songWithNotes(4);
            var line = song.getLine(0);
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(2))));

            // Deleting an endpoint shortens the crescendo — a companion removal plus the
            // addition of a copy; undo must restore both the note and the original span.
            assertRoundTrip(song, () -> line.removeElement(0));
        }

        @Test
        void testDeletingElementBetweenHairpinsRestoresBothOnUndo() {
            var song = songWithNotes(5);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1)));
                line.addCrescendo(new Crescendo(line.getElement(3), line.getElement(4)));
            });

            // Deleting the only element between them merges the two crescendos into one;
            // undo must restore the note and both original crescendos.
            assertRoundTrip(song, () -> line.removeElement(2));
        }

        @Test
        void testDeletingTiedNoteRestoresNoteAndTieOnUndo() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(0), line.getElement(1))));

            assertRoundTrip(song, () -> line.removeElement(1));
        }

        @Test
        void testInsertingAtIndexZeroDisplacesInitialTempoAttachment() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                var first = line.getElement(0);
                // The attachment's tempo value is irrelevant to the displacement
                // round-trip; a fresh non-null Tempo keeps the null-marked package happy.
                first.addAttachment(new TempoChangeAttachment(first, new Tempo()));
            });

            // Prepending to the first line moves the initial-tempo attachment onto the
            // new first element via a tracked companion modification; undo must move it back.
            assertRoundTrip(song, () -> line.addElement(0, UndoTestSupport.crotchet()));
        }

        @Test
        void testDeletingIndexZeroReanchorsInitialTempoAttachment() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                var first = line.getElement(0);
                first.addAttachment(new TempoChangeAttachment(first, new Tempo()));
            });

            // The mirror image of the insertion case above: deleting the first element of
            // the first line moves the initial-tempo attachment onto the element that takes
            // its place, via a tracked companion modification recorded after the deletion.
            // Undo must strip that tempo and bring back the element that owned it.
            assertRoundTrip(song, () -> line.removeElement(0));
        }

        @Test
        void testDeletingARangeFromIndexZeroReanchorsInitialTempoAttachment() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                var first = line.getElement(0);
                first.addAttachment(new TempoChangeAttachment(first, new Tempo()));
            });

            assertRoundTrip(song, () -> line.removeRange(0, 1));
        }
    }

    // -----------------------------------------------------------------------
    // Line-scoped mutations
    // -----------------------------------------------------------------------

    /**
     * The grace-note case, which cannot use {@link #assertRoundTrip}: the element is already
     * on the line when the bracket opens, so the pre-edit state to restore predates it.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeferredInsertionRepair {

        private static final String FIRST_SYLLABLE = "A";
        private static final String SECOND_SYLLABLE = "mi";

        /** The two notes the grace note is inserted between. */
        private static final int NOTE_COUNT = 2;

        /**
         * Issue #659 at the model level. Grace mode inserts its grace note with mutation
         * tracking suspended and defers the lyric-chain repairs, so the pairing bracket
         * carries both the retroactive insertion record and the repairs — and undo, replaying
         * that batch in reverse, has to put the hyphen back.
         */
        @Test
        void testUndoRestoresTheHyphenTheGraceNoteInsertionBroke() {
            var song = songWithNotes(NOTE_COUNT);
            var line = song.getLine(0);
            var grace = ElementType.GRACE_QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                setSyllable(line, 0, Lyric.Syllabic.BEGIN, FIRST_SYLLABLE);
                setSyllable(line, 1, Lyric.Syllabic.END, SECOND_SYLLABLE);
                line.addElement(1, grace);
            });

            var batch = UndoTestSupport.captureBatch(song, () -> {
                line.applyChange(new ElementInsertion(line, 1, grace), () -> {});
                line.repairNeighborsAfterUntrackedInsertion(1);
            });

            assertThat(syllabicAt(line, 0))
                .as("the inserted grace note breaks the hyphen chain")
                .isEqualTo(Lyric.Syllabic.SINGLE);

            var scoreView = UndoTestSupport.scoreViewFor(song);
            UndoTestSupport.replayUndo(scoreView, batch);

            assertThat(line.effectiveElementCount())
                .as("undo removes the grace note")
                .isEqualTo(NOTE_COUNT);
            assertThat(syllabicAt(line, 0))
                .as("undo re-establishes the hyphen on the first syllable")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(syllabicAt(line, 1))
                .as("undo re-establishes the word ending on the second syllable")
                .isEqualTo(Lyric.Syllabic.END);

            UndoTestSupport.replayRedo(scoreView, batch);

            assertThat(line.effectiveElementCount())
                .as("redo puts the grace note back")
                .isEqualTo(NOTE_COUNT + 1);
            assertThat(syllabicAt(line, 0))
                .as("redo breaks the hyphen chain again")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        /**
         * The melisma half of the same story. A melisma is one syllable held across several
         * notes, marked START on the note carrying the word and STOP on a text-less carrier
         * where it ends. The repair has to start its cascade one slot past the inserted note,
         * since its indices are post-insertion — get that wrong and the syllables above still
         * round-trip perfectly while a held note never comes back.
         */
        @Test
        void testUndoRestoresTheMelismaTheGraceNoteInsertionBroke() {
            var song = songWithNotes(NOTE_COUNT);
            var line = song.getLine(0);
            var grace = ElementType.GRACE_QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                line.getElement(0).setLyricForVerse(
                    Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, FIRST_SYLLABLE, Lyric.Extend.START);
                line.getElement(1).setLyricForVerse(
                    Lyric.FIRST_VERSE, null, false, "", Lyric.Extend.STOP);
                line.addElement(1, grace);
            });

            var batch = UndoTestSupport.captureBatch(song, () -> {
                line.applyChange(new ElementInsertion(line, 1, grace), () -> {});
                line.repairNeighborsAfterUntrackedInsertion(1);
            });

            assertThat(extendAt(line, 0))
                .as("the inserted grace note ends the melisma on the note that started it")
                .isEqualTo(Lyric.Extend.NONE);
            assertThat(line.getElement(2).getLyricForVerse(Lyric.FIRST_VERSE))
                .as("the severed carrier is dropped rather than left as an empty lyric")
                .isNull();

            var scoreView = UndoTestSupport.scoreViewFor(song);
            UndoTestSupport.replayUndo(scoreView, batch);

            assertThat(line.effectiveElementCount())
                .as("undo removes the grace note")
                .isEqualTo(NOTE_COUNT);
            assertThat(extendAt(line, 0))
                .as("undo re-establishes the melisma on the note that held the syllable")
                .isEqualTo(Lyric.Extend.START);
            assertThat(extendAt(line, 1))
                .as("undo brings back the carrier the melisma ended on")
                .isEqualTo(Lyric.Extend.STOP);

            UndoTestSupport.replayRedo(scoreView, batch);

            assertThat(extendAt(line, 0))
                .as("redo breaks the melisma again")
                .isEqualTo(Lyric.Extend.NONE);
            assertThat(line.getElement(2).getLyricForVerse(Lyric.FIRST_VERSE))
                .as("redo drops the carrier again")
                .isNull();
        }

        /**
         * The glissando half of the same story, and the one arrangement that reaches it: a
         * grace note dropped between an existing pair and its host leaves that pair's
         * glissando connecting to the newcomer rather than to a note it may legally reach, so
         * the deferred repair takes it off. Recorded nowhere, undo would put the pair back
         * silently unpaired.
         */
        @Test
        void testUndoRestoresTheGlissandoTheGraceNoteInsertionBroke() {
            var song = songWithNotes(NOTE_COUNT);
            var line = song.getLine(0);
            var pairedGrace = ElementType.GRACE_QUAVER.newInstance();
            var grace = ElementType.GRACE_QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                pairedGrace.setGlissando();
                line.addElement(0, pairedGrace);
                line.addElement(1, grace);
            });

            var batch = UndoTestSupport.captureBatch(song, () -> {
                line.applyChange(new ElementInsertion(line, 1, grace), () -> {});
                line.repairNeighborsAfterUntrackedInsertion(1);
            });

            assertThat(pairedGrace.hasGlissando())
                .as("the inserted grace note leaves the existing pair's glissando no valid target")
                .isFalse();

            var scoreView = UndoTestSupport.scoreViewFor(song);
            UndoTestSupport.replayUndo(scoreView, batch);

            assertThat(line.effectiveElementCount())
                .as("undo removes the grace note")
                .isEqualTo(NOTE_COUNT + 1);
            assertThat(line.getElement(0).hasGlissando())
                .as("undo reconnects the pair the insertion broke")
                .isTrue();

            UndoTestSupport.replayRedo(scoreView, batch);

            assertThat(line.getElement(0).hasGlissando())
                .as("redo disconnects it again")
                .isFalse();
        }

        private static void setSyllable(Line line, int index, Lyric.Syllabic syllabic, String text) {
            line.getElement(index)
                .setLyricForVerse(Lyric.FIRST_VERSE, syllabic, false, text, Lyric.Extend.NONE);
        }

        private static Lyric.@Nullable Syllabic syllabicAt(Line line, int index) {
            var lyric = line.getElement(index).getLyricForVerse(Lyric.FIRST_VERSE);
            return lyric == null ? null : lyric.syllabic();
        }

        private static Lyric.@Nullable Extend extendAt(Line line, int index) {
            var lyric = line.getElement(index).getLyricForVerse(Lyric.FIRST_VERSE);
            return lyric == null ? null : lyric.extend();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineMutations {

        @Test
        void testLineInsertionThatBecomesNewLastLineRoundTrips() {
            var song = songWithNotes(2);
            // Appending an empty line makes it the new last line, transferring the
            // terminal barline off the previous last line — a multi-mutation batch.
            assertRoundTrip(song, () -> song.addLine(song.lineCount(), new Line(song)));
        }

        @Test
        void testMidScoreLineInsertionRoundTrips() {
            var song = songWithNotes(2);
            song.withoutMutationTracking(() -> song.addLine(song.lineCount(), new Line(song)));

            // Insert between the two lines — not the last line, so no terminal transfer.
            assertRoundTrip(song, () -> song.addLine(1, new Line(song)));
        }

        @Test
        void testDeletingLastLineRoundTrips() {
            var song = songWithNotes(2);
            // A real append so the terminal barline lives on the appended last line;
            // deleting it then transfers the terminal back — the delete-last-line canary.
            song.addLine(song.lineCount(), new Line(song));

            var lastIndex = song.lineCount() - 1;
            assertRoundTrip(song, () -> song.removeLine(lastIndex));
        }

        @Test
        void testDeletingSoleLineRoundTrips() {
            var song = songWithNotes(2);

            // Removing the only line replaces it with a fresh empty line — a
            // LineDeletion followed by a LineInsertion (plus terminal maintenance)
            // in one batch, exercising the replay guard that must not re-trigger
            // repopulation while undo/redo is replaying that same batch.
            assertRoundTrip(song, () -> song.removeLine(0));
        }

        @Test
        void testDeletingMidScoreLineRoundTrips() {
            var song = songWithNotes(2);
            song.addLine(song.lineCount(), new Line(song));
            song.addLine(song.lineCount(), new Line(song));

            // Remove a middle line (not last) — no terminal maintenance.
            assertRoundTrip(song, () -> song.removeLine(1));
        }

        @Test
        void testLineKeyAccidentalCountChangeRoundTrips() {
            var song = songWithNotes(1);
            var line = song.getLine(0);
            var changed = song.getDefaultKeyAccidentalCount() + 2;
            assertRoundTrip(song, () -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(changed);
            });
        }
    }

    // -----------------------------------------------------------------------
    // Line-layout mutations
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineLayoutMutations {

        @Test
        void testLyricsYPosChangeRoundTrips() {
            var song = songWithNotes(1);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.setLyricsYPosSs(line.getLyricsYPosSs() + 3.0));
        }

        @Test
        void testElementSpacingRatioChangeRoundTrips() {
            var song = songWithNotes(1);
            var line = song.getLine(0);
            // The forward edit uses the multiplying setter (the real UI path); the
            // replayer inverts it via the absolute setter.
            assertRoundTrip(song, () -> line.changeElementSpacingRatio(1.5f));
        }
    }

    // -----------------------------------------------------------------------
    // Span (range-element) mutations
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SpanMutations {

        @Test
        void testBeamingAdditionRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addBeaming(new Beam(line.getElement(0), line.getElement(1))));
        }

        @Test
        void testBeamingRemovalRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            var beam = new Beam(line.getElement(0), line.getElement(1));
            song.withoutMutationTracking(() -> line.addBeaming(beam));
            assertRoundTrip(song, () -> line.removeBeaming(beam));
        }

        @Test
        void testMergingBeamsRestoresBothOriginalsOnUndo() {
            var song = songWithNotes(4);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addBeaming(new Beam(line.getElement(0), line.getElement(1)));
                line.addBeaming(new Beam(line.getElement(2), line.getElement(3)));
            });

            // A wide beam absorbs both existing beams; the subsumed removals ride the
            // batch and undo restores both originals. Full-serialize equality is not
            // used here: undo re-adds the two beams in reverse order, so rangeElements'
            // storage order flips (semantically identical). Assert the beam *set*.
            var batch = UndoTestSupport.captureBatch(
                song, () -> line.addBeaming(new Beam(line.getElement(0), line.getElement(3))));

            var merged = UndoTestSupport.serialize(song);
            assertThat(merged).as("merge collapses to one wide beam").contains("<beamings>0,3;</beamings>");

            var scoreView = UndoTestSupport.scoreViewFor(song);

            UndoTestSupport.replayUndo(scoreView, batch);
            var restored = UndoTestSupport.serialize(song);
            assertThat(restored).as("undo restores the first original beam").contains("0,1;");
            assertThat(restored).as("undo restores the second original beam").contains("2,3;");
            assertThat(restored).as("undo removes the merged wide beam").doesNotContain("0,3;");

            UndoTestSupport.replayRedo(scoreView, batch);
            assertThat(UndoTestSupport.serialize(song))
                .as("redo re-collapses to the wide beam")
                .contains("<beamings>0,3;</beamings>");
        }

        @Test
        void testTieAdditionRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addTie(new Tie(line.getElement(0), line.getElement(1))));
        }

        @Test
        void testTieRemovalRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            var tie = new Tie(line.getElement(0), line.getElement(1));
            song.withoutMutationTracking(() -> line.addTie(tie));
            assertRoundTrip(song, () -> line.removeTie(tie));
        }

        @Test
        void testTupletAdditionRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS)));
        }

        @Test
        void testTupletRemovalRoundTrips() {
            var song = songWithNotes(3);
            var line = song.getLine(0);
            var tuplet = new Tuplet(line.getElement(0), line.getElement(2), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            song.withoutMutationTracking(() -> line.addTuplet(tuplet));
            assertRoundTrip(song, () -> line.removeTuplet(tuplet));
        }

        @Test
        void testCrescendoAdditionRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1))));
        }

        @Test
        void testCrescendoRemovalRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            var crescendo = new Crescendo(line.getElement(0), line.getElement(1));
            song.withoutMutationTracking(() -> line.addCrescendo(crescendo));
            assertRoundTrip(song, () -> line.removeCrescendo(crescendo));
        }

        @Test
        void testDiminuendoAdditionRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addDiminuendo(new Diminuendo(line.getElement(0), line.getElement(1))));
        }

        @Test
        void testDiminuendoRemovalRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            var diminuendo = new Diminuendo(line.getElement(0), line.getElement(1));
            song.withoutMutationTracking(() -> line.addDiminuendo(diminuendo));
            assertRoundTrip(song, () -> line.removeDiminuendo(diminuendo));
        }

        @Test
        void testRangeElementAdditionRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            assertRoundTrip(song, () -> line.addRangeElement(new Trill(line.getElement(0), line.getElement(1))));
        }

        @Test
        void testRangeElementRemovalRoundTrips() {
            var song = songWithNotes(2);
            var line = song.getLine(0);
            var trill = new Trill(line.getElement(0), line.getElement(1));
            song.withoutMutationTracking(() -> line.addRangeElement(trill));
            assertRoundTrip(song, () -> line.removeRangeElement(trill));
        }
    }

    // -----------------------------------------------------------------------
    // Hairpin add/extend as the user drives it
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HairpinExecution {

        private static final int NOTE_COUNT = 4;

        /** Selection reaching past the pre-existing crescendo on [0, 1]. */
        private static final int SELECTION_BEGIN = 2;
        private static final int SELECTION_END = 3;

        private static final DynamicAttachment.DynamicType POINT_DYNAMIC =
            DynamicAttachment.DynamicType.FORTE;

        private record Fixture(Song song, Line line, MusicEditOperations operations) {}

        /**
         * A four-note song whose first two notes carry a crescendo and whose first note
         * carries a point dynamic, with [2, 3] selected. {@code addHairpinToSelection}
         * then extends the crescendo to [0, 3] and strips the dynamic from element 0 —
         * a note outside the selection.
         */
        private Fixture extendFixture() {
            var song = songWithNotes(NOTE_COUNT);
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1)));
                var first = line.getElement(0);
                first.addAttachment(new DynamicAttachment(first, POINT_DYNAMIC));
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, SELECTION_BEGIN, SELECTION_END);
            return new Fixture(song, line, new MusicEditOperations(song, coordinator));
        }

        private static Crescendo soleCrescendoOf(Line line) {
            return line.getRangeElements().stream()
                .filter(Crescendo.class::isInstance)
                .map(Crescendo.class::cast)
                .reduce((first, second) -> {
                    throw new AssertionError("expected exactly one crescendo on the line");
                })
                .orElseThrow(() -> new AssertionError("no crescendo on the line"));
        }

        @Test
        void testDeletingAMergedHairpinRoundTrips() {
            var fixture = extendFixture();
            var song = fixture.song();
            var line = fixture.line();
            song.withoutMutationTracking(() -> fixture.operations().addHairpinToSelection(true));

            // The hairpin being deleted is the one mergeOverlappingSpans reshaped, not the
            // one the constructor produced; undo must restore that reshaped span.
            assertRoundTrip(song, () -> line.removeCrescendo(soleCrescendoOf(line)));
        }

        @Test
        void testAddWithPointDynamicStripUndoesAndRedoes() {
            var fixture = extendFixture();
            var song = fixture.song();
            var line = fixture.line();
            var batch = UndoTestSupport.captureBatch(
                song, () -> fixture.operations().addHairpinToSelection(true));

            // Point dynamics are not part of the native serialization, so the strip is
            // asserted directly on the model rather than through assertRoundTrip.
            assertThat(line.getElement(0).findAttachment(DynamicAttachment.class))
                .as("the add must strip the point dynamic, else the round trip is vacuous")
                .isNull();

            var scoreView = UndoTestSupport.scoreViewFor(song);

            UndoTestSupport.replayUndo(scoreView, batch);
            assertThat(line.getElement(0).findAttachment(DynamicAttachment.class))
                .as("undo must restore the stripped point dynamic")
                .isNotNull();

            UndoTestSupport.replayRedo(scoreView, batch);
            assertThat(line.getElement(0).findAttachment(DynamicAttachment.class))
                .as("redo must strip the point dynamic again")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // Song-scoped mutations
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SongScopedMutations {

        @Test
        void testMetadataAttributionChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setMetadata(song.getMetadata().withTitle("Round Trip Title")));
        }

        @Test
        void testMetadataTempoChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setTempo(new Tempo(90, Duration.CROTCHET, "Slow", true)));
        }

        @Test
        void testMetadataFootnotesChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setFootnotes("A footnote"));
        }

        @Test
        void testMetadataDefaultKeyAccidentalCountChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setDefaultKeyAccidentalCount(song.getDefaultKeyAccidentalCount() + 3));
        }

        @Test
        void testMetadataDefaultKeyTypeChangeRoundTrips() {
            var song = songWithNotes(1);
            // The document default is FLATS, so change to a genuinely different type.
            assertRoundTrip(song, () -> song.setDefaultKeyType(KeyType.SHARPS));
        }

        @Test
        void testLayoutLineWidthChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setLineWidthSs(song.getLineWidthSs() + 10.0));
        }

        @Test
        void testLayoutRowHeightAdjustmentChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setRowHeightAdjustmentSs(song.getRowHeightAdjustmentSs() + 5.0));
        }

        @Test
        void testLyricsUnderChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setUnderLyrics("under lyrics"));
        }

        @Test
        void testLyricsBanglaChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setBanglaLyrics("bangla lyrics"));
        }

        @Test
        void testLyricsTranslatedChangeRoundTrips() {
            var song = songWithNotes(1);
            assertRoundTrip(song, () -> song.setTranslatedLyrics("translated lyrics"));
        }
    }

    // -----------------------------------------------------------------------
    // FontChange — dispatches to ScoreView (not part of the Song serialize harness)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Fonts {

        private static final int OLD_TITLE_SIZE = 12;
        private static final int NEW_TITLE_SIZE = 24;

        // Mockito verify matches by equals, so the two snapshots must differ in a
        // field value — with two bare (equals-equal) DocumentFonts a swapped
        // undo/redo direction would still pass.
        private static DocumentFonts fontsWithTitleSize(int size) {
            var fonts = new DocumentFonts();
            fonts.setFont(FontKey.TITLE, Font.SERIF, size);
            return fonts;
        }

        @Test
        void testFontChangeUndoDispatchesOldFontsToScoreView() {
            var oldFonts = fontsWithTitleSize(OLD_TITLE_SIZE);
            var newFonts = fontsWithTitleSize(NEW_TITLE_SIZE);
            var scoreView = mock(ScoreView.class);

            MutationReplayer.applyUndo(scoreView, new FontChange(oldFonts, newFonts));

            verify(scoreView).setFonts(oldFonts);
        }

        @Test
        void testFontChangeRedoDispatchesNewFontsToScoreView() {
            var oldFonts = fontsWithTitleSize(OLD_TITLE_SIZE);
            var newFonts = fontsWithTitleSize(NEW_TITLE_SIZE);
            var scoreView = mock(ScoreView.class);

            MutationReplayer.applyRedo(scoreView, new FontChange(oldFonts, newFonts));

            verify(scoreView).setFonts(newFonts);
        }
    }
}
