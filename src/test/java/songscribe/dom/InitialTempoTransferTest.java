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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static songscribe.dom.InitialTempoTestSupport.ORIGINAL_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.TARGET_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.attachTempo;
import static songscribe.dom.InitialTempoTestSupport.countTempoChangeAttachments;
import static songscribe.dom.InitialTempoTestSupport.tempoOf;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song.LyricsSource;
import songscribe.message.SongData;
import songscribe.message.mutation.Mutation;
import songscribe.undo.UndoTestSupport;

/**
 * DOM-level tests for the starting-tempo transfer machinery: {@link Song#initialTempoAnchor()},
 * {@link InitialTempoTransfer}'s lookahead queries, and the automatic transfer performed by
 * {@link Line#removeElement}, {@link Line#removeRange}, {@link Line#addElement(int, StaffElement)}
 * and {@link Song#removeLine}. See {@code docs/initial-tempo.md} for the behavior these
 * mechanisms implement and why.
 */
class InitialTempoTransferTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A grace note connected to its host by a glissando, per {@link StaffElement#isPairedGraceNote}. */
    private static StaffElement pairedGraceNote() {
        var grace = StaffElementFactory.graceQuaver();
        grace.setGlissando();
        return grace;
    }

    /**
     * Builds a song with no default line, so each test controls exactly which lines exist and
     * whether any carries a terminal barline. Mirrors
     * {@code MusicXmlRoundTripSupport.buildSong}.
     */
    private static Song emptySong() {
        var song = new Song();
        song.withoutMutationTracking(() -> song.removeLine(0));
        return song;
    }

    /** Adds a new line built by {@code builder} as the song's new last line. */
    private static Line addLine(Song song, Consumer<Line> builder) {
        var line = new Line(song);
        song.withoutMutationTracking(() -> {
            builder.accept(line);
            song.addLine(line);
        });
        return line;
    }

    private static SongData songDataWith(@Nullable Tempo tempo, List<Line> lines) {
        return new SongData(
            tempo,
            "",
            "",
            "",
            0,
            0,
            "",
            "",
            "",
            "",
            Song.SRI_CHINMOY,
            Song.SRI_CHINMOY,
            LyricsSource.LYRICIST,
            false,
            "",
            false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT,
            Song.DEFAULT_KEY_TYPE,
            0.0,
            0.0,
            lines,
            false,
            1,
            "", "", 0, 0
        );
    }

    /**
     * Asserts undo restores the pre-edit serialization and redo the post-edit one, for an
     * {@code edit} that is a raw DOM mutator and so needs a bracket opened around it.
     */
    private static void assertOneStepUndoRedo(Song song, Runnable edit) {
        assertUndoRedo(song, edit, UndoTestSupport::captureBatch);
    }

    /**
     * As {@link #assertOneStepUndoRedo}, for an {@code edit} that opens its own bracket. The
     * capture additionally asserts it posted exactly one notification, so an edit that came
     * apart into two undo steps fails here instead of passing unnoticed.
     */
    private static void assertSelfBracketingOneStepUndoRedo(Song song, Runnable edit) {
        assertUndoRedo(song, edit, UndoTestSupport::captureSingleBatch);
    }

    private static void assertUndoRedo(
        Song song, Runnable edit, BiFunction<Song, Runnable, List<Mutation>> capture) {

        var before = UndoTestSupport.serialize(song);
        var batch = capture.apply(song, edit);
        var after = UndoTestSupport.serialize(song);

        assertThat(after)
            .as("the edit must produce an observable change")
            .isNotEqualTo(before);

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertThat(UndoTestSupport.serialize(song))
            .as("undo must restore the exact pre-edit state, in one step")
            .isEqualTo(before);

        UndoTestSupport.replayRedo(scoreView, batch);
        assertThat(UndoTestSupport.serialize(song))
            .as("redo must re-apply the exact post-edit state")
            .isEqualTo(after);
    }

    // -------------------------------------------------------------------------
    // Anchor resolution
    // -------------------------------------------------------------------------

    @Nested
    class AnchorResolution {

        @Test
        void testInitialTempoAnchorSkipsLeadingEmptyLine() {
            var song = emptySong();
            addLine(song, line -> { });
            var secondLineNote = crotchet();
            var secondLine = addLine(song, line -> line.addElement(secondLineNote));

            assertThat(song.initialTempoAnchor()).isSameAs(secondLineNote);
            assertThat(song.indexOfLine(secondLine)).isEqualTo(1);
        }

        @Test
        void testInitialTempoAnchorReturnsNullForSongWithNoElementsAnywhere() {
            var song = emptySong();
            addLine(song, line -> { });
            addLine(song, line -> { });

            assertThat(song.initialTempoAnchor()).isNull();
        }

        @Test
        void testIsInitialTempoAnchorTrueOnFirstNonEmptyLine() {
            var song = emptySong();
            addLine(song, line -> { });
            var anchorNote = crotchet();
            var line = addLine(song, l -> l.addElement(anchorNote));

            assertThat(line.isInitialTempoAnchor(0)).isTrue();
        }

        @Test
        void testIsInitialTempoAnchorFalseOnLeadingEmptyLine() {
            var song = emptySong();
            var emptyLine = addLine(song, l -> { });
            addLine(song, l -> l.addElement(crotchet()));

            assertThat(emptyLine.isInitialTempoAnchor(0)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // anchorAfterRemoval / anchorAfterLineRemoval
    // -------------------------------------------------------------------------

    @Nested
    class AnchorLookahead {

        @Test
        void testAnchorAfterRemovalReturnsElementAfterRemovedRangeOnSameLine() {
            var song = emptySong();
            var survivor = crotchet();
            var line = addLine(song, l -> {
                l.addElement(crotchet());
                l.addElement(crotchet());
                l.addElement(survivor);
            });

            var anchor = InitialTempoTransfer.anchorAfterRemoval(song, line, 0, 1);

            assertThat(anchor).isSameAs(survivor);
        }

        @Test
        void testAnchorAfterRemovalReturnsNextNonEmptyLinesFirstElementWhenRangeEmptiesLine() {
            var song = emptySong();
            var line0 = addLine(song, l -> l.addElement(crotchet()));
            var line1FirstElement = crotchet();
            addLine(song, l -> l.addElement(line1FirstElement));

            var anchor = InitialTempoTransfer.anchorAfterRemoval(song, line0, 0, 0);

            assertThat(anchor).isSameAs(line1FirstElement);
        }

        @Test
        void testAnchorAfterRemovalReturnsNullWhenNothingLeft() {
            var song = emptySong();
            var line = addLine(song, l -> l.addElement(crotchet()));

            var anchor = InitialTempoTransfer.anchorAfterRemoval(song, line, 0, 0);

            assertThat(anchor).isNull();
        }

        @Test
        void testAnchorAfterRemovalSkipsTrailingBreathMark() {
            // The range [0,0] is widened past the breath mark at index 1, so the survivor is
            // at index 2, not index 1.
            var song = emptySong();
            var survivor = crotchet();
            var line = addLine(song, l -> {
                l.addElement(crotchet());
                l.addElement(ElementType.BREATH_MARK.newInstance());
                l.addElement(survivor);
            });

            var anchor = InitialTempoTransfer.anchorAfterRemoval(song, line, 0, 0);

            assertThat(anchor).isSameAs(survivor);
        }

        @Test
        void testAnchorAfterLineRemovalReturnsFirstElementOfLineThatBecomesLineZero() {
            var song = emptySong();
            addLine(song, l -> l.addElement(crotchet()));
            var newFirstElement = crotchet();
            addLine(song, l -> l.addElement(newFirstElement));

            var anchor = InitialTempoTransfer.anchorAfterLineRemoval(song, 0);

            assertThat(anchor).isSameAs(newFirstElement);
        }
    }

    // -------------------------------------------------------------------------
    // DOM transfer behavior — real, tracked mutations
    // -------------------------------------------------------------------------

    @Nested
    class DomTransfer {

        @Test
        void testDeletingRangeThatLeavesElementsOnLine0MovesTempoToNewFirstElement() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            var newAnchor = crotchet();
            var line = addLine(song, l -> {
                l.addElement(oldAnchor);
                l.addElement(newAnchor);
                l.addElement(crotchet());
            });
            attachTempo(oldAnchor, tempo);

            song.withModification(() -> line.removeRange(0, 0));

            var attachment = newAnchor.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(Tempo.haveSameValue(attachment.getTempo(), tempo)).isTrue();
            assertThat(line.getElementIndex(oldAnchor))
                .as("the deleted element is no longer part of the line")
                .isEqualTo(-1);
        }

        @Test
        void testDeletingEveryElementOfLine0MovesTempoToLine1FirstElement() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            var line0 = addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);
            var line1FirstElement = crotchet();
            addLine(song, l -> l.addElement(line1FirstElement));

            song.withModification(() -> line0.removeRange(0, 0));

            assertThat(line0.isEmpty()).isTrue();
            var attachment = line1FirstElement.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(Tempo.haveSameValue(attachment.getTempo(), tempo)).isTrue();
        }

        @Test
        void testRemoveLineMovesStartingTempoToFirstElementOfNewLineZero() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);
            var newFirstElement = crotchet();
            addLine(song, l -> l.addElement(newFirstElement));

            song.withModification(() -> song.removeLine(0));

            assertThat(song.getLine(0).getElement(0)).isSameAs(newFirstElement);
            var attachment = newFirstElement.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(Tempo.haveSameValue(attachment.getTempo(), tempo)).isTrue();
        }

        @Test
        void testDisplacedTempoIsDroppedWhenTargetAlreadyHasATempoChange() {
            var song = emptySong();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            var newFirstElement = crotchet();
            addLine(song, l -> l.addElement(newFirstElement));
            attachTempo(newFirstElement, tempoOf(TARGET_TEMPO_BPM));

            song.withModification(() -> song.removeLine(0));

            var tempoAttachments = newFirstElement.getAttachments().stream()
                .filter(TempoChangeAttachment.class::isInstance)
                .toList();

            assertThat(tempoAttachments)
                .as("the target keeps exactly one tempo change")
                .hasSize(1);
            var attachment = (TempoChangeAttachment) tempoAttachments.getFirst();
            assertThat(attachment.getTempo().getVisibleTempo())
                .as("the target's own tempo change wins over the displaced one")
                .isEqualTo(TARGET_TEMPO_BPM);
        }

        @Test
        void testRemovingTheSoleLineLeavesTheStartingTempoNowhereRatherThanDuplicated() {
            // The one shape with nothing left to carry the tempo. removeLine replaces a song's
            // last remaining line with a fresh one, but that happens after the transfer has
            // already run against a song with no lines at all — so the terminal barline the
            // replacement line receives never inherits the tempo.
            var song = emptySong();
            var onlyNote = crotchet();
            addLine(song, l -> l.addElement(onlyNote));
            attachTempo(onlyNote, tempoOf(ORIGINAL_TEMPO_BPM));

            song.withModification(() -> song.removeLine(0));

            assertThat(song.lineCount())
                .as("the song is never left with no lines at all")
                .isEqualTo(1);
            assertThat(song.getLine(0).getElementIndex(onlyNote))
                .as("the removed note is gone")
                .isEqualTo(-1);
            assertThat(countTempoChangeAttachments(song))
                .as("the tempo has nowhere to go, so it must not survive anywhere")
                .isZero();

            var anchor = song.initialTempoAnchor();
            assertThat(anchor).isNotNull();
            assertThat(anchor.getType())
                .as("only the replacement line's terminal barline is left")
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testAddElementWithATempoChangeAlreadyAttachedLeavesExactlyOne() {
            var song = emptySong();
            var oldAnchor = crotchet();
            var line = addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            var incoming = crotchet();
            attachTempo(incoming, tempoOf(TARGET_TEMPO_BPM));

            song.withModification(() -> line.addElement(0, incoming));

            var tempoAttachments = incoming.getAttachments().stream()
                .filter(TempoChangeAttachment.class::isInstance)
                .toList();

            assertThat(tempoAttachments)
                .as("the incoming element keeps exactly one tempo change, not doubled")
                .hasSize(1);
            assertThat(((TempoChangeAttachment) tempoAttachments.getFirst()).getTempo().getVisibleTempo())
                .isEqualTo(TARGET_TEMPO_BPM);
            assertThat(oldAnchor.findAttachment(TempoChangeAttachment.class))
                .as("the old anchor no longer carries the displaced tempo")
                .isNull();
        }

        @Test
        void testGraceNoteWideningTransfersTheStartingTempoInsteadOfDroppingIt() {
            // [grace(tempo), host, note, note] — deleting [1,3] widens to [0,3] because the
            // grace note at 0 is paired with the host at 1, destroying the anchor entirely.
            var song = emptySong();
            var tempo = new Tempo();
            var grace = pairedGraceNote();
            var host = crotchet();
            var middle = crotchet();
            var last = crotchet();
            var line0 = addLine(song, l -> {
                l.addElement(grace);
                l.addElement(host);
                l.addElement(middle);
                l.addElement(last);
            });
            attachTempo(grace, tempo);

            var line1FirstElement = crotchet();
            addLine(song, l -> l.addElement(line1FirstElement));

            song.withModification(() -> {
                var widened = line0.effectiveDeleteRange(1, 3);
                assertThat(widened.begin())
                    .as("the paired grace note widens the begin of the range back to 0")
                    .isEqualTo(0);
                line0.removeRange(widened.begin(), widened.end());
            });

            assertThat(line0.isEmpty()).isTrue();
            var attachment = line1FirstElement.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment)
                .as("the starting tempo must transfer to the next line, not vanish")
                .isNotNull();
            assertThat(Tempo.haveSameValue(attachment.getTempo(), tempo)).isTrue();
        }

        @Test
        void testCrossLineInsertMovesTempoOntoTheNewElementAndLeavesTheOldAnchorWithNone() {
            var song = emptySong();
            var line0 = addLine(song, l -> { });
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);

            var newElement = crotchet();

            song.withModification(() -> line0.addElement(0, newElement));

            var attachment = newElement.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(Tempo.haveSameValue(attachment.getTempo(), tempo)).isTrue();
            assertThat(oldAnchor.findAttachment(TempoChangeAttachment.class))
                .as("the old anchor's tempo moved to line 0, leaving it with none")
                .isNull();
        }

        @Test
        void testDeletingEveryElementLeavesNoAnchorAndSyncClearsSongTempo() {
            var song = emptySong();
            var tempo = new Tempo();
            var onlyNote = crotchet();
            var line = addLine(song, l -> l.addElement(onlyNote));
            attachTempo(onlyNote, tempo);
            song.setTempo(tempo);

            song.withModification(() -> line.removeRange(0, 0));

            assertThat(song.initialTempoAnchor()).isNull();

            song.syncTempoFromAnchor();

            assertThat(song.getTempo()).isNull();
        }

        @Test
        void testRemoveLineTransferDoesNotDoubleUpAcrossUndoAndRedo() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);
            addLine(song, l -> l.addElement(crotchet()));

            var batch = UndoTestSupport.captureBatch(song, () -> song.removeLine(0));
            var scoreView = UndoTestSupport.scoreViewFor(song);

            UndoTestSupport.replayUndo(scoreView, batch);
            assertThat(countTempoChangeAttachments(song))
                .as("undo must not leave a second tempo change behind")
                .isEqualTo(1);

            UndoTestSupport.replayRedo(scoreView, batch);
            assertThat(countTempoChangeAttachments(song))
                .as("redo must not double the transferred tempo change")
                .isEqualTo(1);
        }

        @Test
        void testLegacyLoadMaterializesTempoOnFirstNonEmptyLinesFirstElement() {
            var song = new Song();
            var emptyLine = new Line(song);
            var lineWithNote = new Line(song);
            var note = crotchet();
            song.withoutMutationTracking(() -> {
                lineWithNote.addElement(note);
                lineWithNote.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });

            var tempo = new Tempo();
            var data = songDataWith(tempo, List.of(emptyLine, lineWithNote));

            song.withoutMutationTracking(() -> song.loadFrom(data));

            var attachment = note.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment)
                .as("the tempo materializes on the first non-empty line's first element")
                .isNotNull();
            assertThat(attachment.getTempo()).isSameAs(tempo);
        }
    }

    // -------------------------------------------------------------------------
    // Undo — one step restores both structure and tempo placement
    // -------------------------------------------------------------------------

    @Nested
    class Undo {

        @Test
        void testUndoOfRangeDeletionRestoresStructureAndTempoInOneStep() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            var newAnchor = crotchet();
            var line = addLine(song, l -> {
                l.addElement(oldAnchor);
                l.addElement(newAnchor);
            });
            attachTempo(oldAnchor, tempo);

            assertOneStepUndoRedo(song, () -> line.removeRange(0, 0));
        }

        @Test
        void testUndoOfLineDeletionRestoresStructureAndTempoInOneStep() {
            var song = emptySong();
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);
            addLine(song, l -> l.addElement(crotchet()));

            // removeLine brackets itself, so the notification count is observable here.
            assertSelfBracketingOneStepUndoRedo(song, () -> song.removeLine(0));
        }

        @Test
        void testUndoOfCrossLineInsertRestoresStructureAndTempoInOneStep() {
            var song = emptySong();
            var line0 = addLine(song, l -> { });
            var tempo = new Tempo();
            var oldAnchor = crotchet();
            addLine(song, l -> l.addElement(oldAnchor));
            attachTempo(oldAnchor, tempo);

            var newElement = crotchet();

            assertOneStepUndoRedo(song, () -> line0.addElement(0, newElement));
        }
    }

    // -------------------------------------------------------------------------
    // Tempo.haveSameValue and Song.syncTempoFromAnchor
    // -------------------------------------------------------------------------

    @Nested
    class HaveSameValueAndSync {

        @Test
        void testHaveSameValueTrueForIdenticalFieldsIncludingBothNullAndTheSameInstance() {
            assertThat(Tempo.haveSameValue(null, null)).isTrue();

            var a = new Tempo();
            assertThat(Tempo.haveSameValue(a, a))
                .as("the same instance is trivially the same value")
                .isTrue();

            var b = a.copy();
            assertThat(Tempo.haveSameValue(a, b)).isTrue();
        }

        @Test
        void testHaveSameValueFalseWhenAnyFieldDiffers() {
            var a = new Tempo();
            assertThat(Tempo.haveSameValue(a, null)).isFalse();
            assertThat(Tempo.haveSameValue(null, a)).isFalse();

            var differentBpm = a.copy();
            differentBpm.setVisibleTempo(a.getVisibleTempo() + 1);
            assertThat(Tempo.haveSameValue(a, differentBpm)).isFalse();

            var differentType = a.copy();
            differentType.setTempoType(Duration.MINIM);
            assertThat(Tempo.haveSameValue(a, differentType)).isFalse();

            var differentDescription = a.copy();
            differentDescription.setTempoDescription(a.getTempoDescription() + " different");
            assertThat(Tempo.haveSameValue(a, differentDescription)).isFalse();

            var differentShowTempo = a.copy();
            differentShowTempo.setShowTempo(!a.shouldShowTempo());
            assertThat(Tempo.haveSameValue(a, differentShowTempo)).isFalse();
        }

        @Test
        void testSyncTempoFromAnchorOnValueEqualCopyRecordsNoMutation() {
            var song = emptySong();
            var tempo = new Tempo();
            var anchorNote = crotchet();
            addLine(song, l -> l.addElement(anchorNote));
            attachTempo(anchorNote, tempo.copy());
            song.setTempo(tempo);

            // captureBatch throws when the edit posted nothing — the guard this test exists
            // to prove: a value-equal transferred copy must not trigger a beat-defining edit.
            assertThatIllegalStateException()
                .isThrownBy(() -> UndoTestSupport.captureBatch(song, song::syncTempoFromAnchor));

            assertThat(song.getTempo())
                .as("the song tempo instance is untouched by the no-op sync")
                .isSameAs(tempo);
        }
    }
}
