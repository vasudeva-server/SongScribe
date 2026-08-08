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

package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static songscribe.dom.InitialTempoTestSupport.ORIGINAL_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.TARGET_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.attachBeatChange;
import static songscribe.dom.InitialTempoTestSupport.attachTempo;
import static songscribe.dom.InitialTempoTestSupport.tempoOf;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.ui.InitialTempoConfirmsTestSupport.CANCEL_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.NO_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.YES_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.stubAnswer;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.undo.UndoTestSupport;

/**
 * Unit tests for {@link InitialTempoConfirms}: {@link InitialTempoConfirms#confirmTransfer},
 * {@link InitialTempoConfirms#applyDecision} and
 * {@link InitialTempoConfirms#warnIfTempoAndBeatChange}. See {@code docs/initial-tempo.md} for
 * the behavior these methods implement and why.
 */
class InitialTempoConfirmsTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A one-line song whose sole element is the anchor, with {@code tempo} attached to it. */
    private static Song songWithAnchorTempo(Tempo tempo) {
        var song = new Song();
        var line = song.getLine(0);
        var anchor = crotchet();
        song.withoutMutationTracking(() -> line.addElement(anchor));
        attachTempo(anchor, tempo);
        return song;
    }

    // -------------------------------------------------------------------------
    // confirmTransfer — no prompt cases
    // -------------------------------------------------------------------------

    @Nested
    class ConfirmTransferNoPrompt {

        @Test
        void testNoPromptWhenSongHasNoStartingTempo() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = crotchet();
            song.withoutMutationTracking(() -> line.addElement(anchor));
            // anchor carries no TempoChangeAttachment: the song has no starting tempo.

            var prospective = crotchet();
            attachTempo(prospective, new Tempo());

            try (var od = mockStatic(OptionDialogs.class)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO);
                od.verifyNoInteractions();
            }
        }

        @Test
        void testNoPromptWhenProspectiveNewFirstElementIsNull() {
            var tempo = new Tempo();
            var song = songWithAnchorTempo(tempo);

            try (var od = mockStatic(OptionDialogs.class)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, null);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO);
                od.verifyNoInteractions();
            }
        }

        @Test
        void testNoPromptWhenProspectiveNewFirstElementHasNoTempoChange() {
            var tempo = new Tempo();
            var song = songWithAnchorTempo(tempo);
            var prospective = crotchet();
            // prospective carries no TempoChangeAttachment.

            try (var od = mockStatic(OptionDialogs.class)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO);
                od.verifyNoInteractions();
            }
        }
    }

    // -------------------------------------------------------------------------
    // confirmTransfer — the ambiguous case: a prompt is raised, indices map to Decisions
    // -------------------------------------------------------------------------

    @Nested
    class ConfirmTransferPrompted {

        private Song song;
        private StaffElement prospective;

        @BeforeEach
        void buildFixture() {
            song = songWithAnchorTempo(new Tempo());
            prospective = crotchet();
            attachTempo(prospective, new Tempo());
        }

        @Test
        void testCancelIndexMapsToCancel() {
            try (var stub = stubAnswer(CANCEL_INDEX)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.CANCEL);
            }
        }

        @Test
        void testNoIndexMapsToKeepTargetTempo() {
            try (var stub = stubAnswer(NO_INDEX)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO);
            }
        }

        @Test
        void testYesIndexMapsToRestoreOriginalTempo() {
            try (var stub = stubAnswer(YES_INDEX)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.RESTORE_ORIGINAL_TEMPO);
            }
        }

        @Test
        void testClosedOptionMapsToCancel() {
            try (var stub = stubAnswer(JOptionPane.CLOSED_OPTION)) {
                var decision = InitialTempoConfirms.confirmTransfer(null, song, prospective);

                assertThat(decision).isEqualTo(InitialTempoConfirms.Decision.CANCEL);
            }
        }
    }

    // -------------------------------------------------------------------------
    // applyDecision
    // -------------------------------------------------------------------------

    @Nested
    class ApplyDecision {

        @Test
        void testRestoreOriginalTempoReplacesTheTargetsOwnTempoAndSyncsSongTempo() {
            var targetOwnTempo = tempoOf(TARGET_TEMPO_BPM);
            var song = songWithAnchorTempo(targetOwnTempo);
            var anchor = song.getLine(0).getElement(0);

            var originalTempo = tempoOf(ORIGINAL_TEMPO_BPM);

            song.withModification(() -> InitialTempoConfirms.applyDecision(
                song, originalTempo, InitialTempoConfirms.Decision.RESTORE_ORIGINAL_TEMPO));

            var attachment = anchor.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getTempo().getVisibleTempo()).isEqualTo(ORIGINAL_TEMPO_BPM);
            assertThat(song.getTempo()).isNotNull();
            assertThat(Tempo.haveSameValue(song.getTempo(), originalTempo)).isTrue();
        }

        @Test
        void testKeepTargetTempoAfterARealPromptLeavesTheTargetsOwnTempoAndSyncsToIt() {
            var targetOwnTempo = tempoOf(TARGET_TEMPO_BPM);
            var song = songWithAnchorTempo(targetOwnTempo);
            var anchor = song.getLine(0).getElement(0);

            // The discarded original — the user answered No to keeping it.
            var originalTempo = tempoOf(ORIGINAL_TEMPO_BPM);

            song.withModification(() -> InitialTempoConfirms.applyDecision(
                song, originalTempo, InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO));

            var attachment = anchor.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getTempo().getVisibleTempo())
                .as("the target keeps its own tempo, not the discarded original")
                .isEqualTo(TARGET_TEMPO_BPM);
            assertThat(song.getTempo()).isNotNull();
            assertThat(song.getTempo().getVisibleTempo()).isEqualTo(TARGET_TEMPO_BPM);
        }

        @Test
        void testKeepTargetTempoWithNoPromptRecordsNoExtraMutationAndLeavesSongTempoUnchanged() {
            // The silent-transfer case: the DOM edit already moved a value-equal copy of the
            // tempo onto the new anchor, so applying "keep" here must be a pure no-op.
            var tempo = new Tempo();
            var song = songWithAnchorTempo(tempo.copy());
            song.setTempo(tempo);

            assertThatIllegalStateException()
                .as("a value-equal sync must not post a mutation")
                .isThrownBy(() -> UndoTestSupport.captureBatch(song, () ->
                    InitialTempoConfirms.applyDecision(
                        song, null, InitialTempoConfirms.Decision.KEEP_TARGET_TEMPO)));

            assertThat(song.getTempo())
                .as("the song tempo instance is untouched by the no-op sync")
                .isSameAs(tempo);
        }
    }

    // -------------------------------------------------------------------------
    // warnIfTempoAndBeatChange
    // -------------------------------------------------------------------------

    @Nested
    class WarnIfTempoAndBeatChange {

        @Test
        void testWarnsWhenAnchorHasBothATempoAndABeatChange() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = crotchet();
            song.withoutMutationTracking(() -> line.addElement(anchor));
            attachTempo(anchor, new Tempo());
            attachBeatChange(anchor);

            try (var od = mockStatic(OptionDialogs.class)) {
                InitialTempoConfirms.warnIfTempoAndBeatChange(null, song);

                od.verify(() -> OptionDialogs.showWarningMessage(
                    any(), any(), any()
                ));
            }
        }

        @Test
        void testNoWarningWhenAnchorHasOnlyATempoChange() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = crotchet();
            song.withoutMutationTracking(() -> line.addElement(anchor));
            attachTempo(anchor, new Tempo());

            try (var od = mockStatic(OptionDialogs.class)) {
                InitialTempoConfirms.warnIfTempoAndBeatChange(null, song);

                od.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }
        }

        @Test
        void testNoWarningWhenAnchorHasOnlyABeatChange() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = crotchet();
            song.withoutMutationTracking(() -> line.addElement(anchor));
            attachBeatChange(anchor);

            try (var od = mockStatic(OptionDialogs.class)) {
                InitialTempoConfirms.warnIfTempoAndBeatChange(null, song);

                od.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }
        }

        @Test
        void testNoWarningWhenAnchorHasNeitherAttachment() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(crotchet()));

            try (var od = mockStatic(OptionDialogs.class)) {
                InitialTempoConfirms.warnIfTempoAndBeatChange(null, song);

                od.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }
        }

        @Test
        void testNoWarningWhenSongHasNoAnchor() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.removeLine(0));

            try (var od = mockStatic(OptionDialogs.class)) {
                InitialTempoConfirms.warnIfTempoAndBeatChange(null, song);

                od.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }
        }
    }
}
