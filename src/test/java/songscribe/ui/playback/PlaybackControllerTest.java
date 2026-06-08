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

package songscribe.ui.playback;

import java.util.ArrayList;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.midi.PlaybackSettings;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.playback.PlaybackController.PlaybackState;
import songscribe.ui.selection.ElementSelection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackControllerTest extends UnitTest {

    @AfterEach
    void tearDown() {
        PlaybackController.setState(PlaybackState.STOPPED);
        PlaybackController.setPreviousPlayingLine(-1);
        PlaybackController.setActiveSelection(null);
        PlaybackController.setRegisteredScore(null);
        PlaybackController.setPausedTickPosition(0);
        PlaybackController.setInstrument(0);
        PlaybackController.setTempoChangePercent(100);
        PlaybackController.setNoteDurationPercent(100);
        PlaybackController.setPlayWithRepeats(false);
        MidiController.sequencer = null;
        MidiController.midiReceiver = null;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionDidChange {

        @Test
        void testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection() {
            var mockScore = mock(ScoreView.class);
            var mockLineComponent = mock(LineComponent.class);
            when(mockScore.getLineComponent(0)).thenReturn(mockLineComponent);
            PlaybackController.register(mockScore);

            PlaybackController.setState(PlaybackState.PAUSED);
            PlaybackController.setPreviousPlayingLine(0);

            var selection = new ElementSelection(detachedLine(), 1, 3);
            PlaybackController.selectionDidChange(selection);

            verify(mockLineComponent).setPlayingIndices(-1, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(-1);
            assertThat(PlaybackController.getActiveSelection()).isEqualTo(selection);
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PAUSED);
        }

        @Test
        void testDoesNothingWhenPlaying() {
            PlaybackController.setState(PlaybackState.PLAYING);
            var selection = new ElementSelection(detachedLine(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.getActiveSelection()).isNull();
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testDoesNothingWhenStopped() {
            var selection = new ElementSelection(detachedLine(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.getActiveSelection()).isNull();
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
        }

        @Test
        void testStopsWhenSelectionClearedWhilePaused() {
            PlaybackController.setState(PlaybackState.PAUSED);

            // stop() posts a notification — suppress the real message bus
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.selectionDidChange(null);

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
                assertThat(PlaybackController.getActiveSelection()).isNull();
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TogglePlayPause {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMock() {
            messageCenterMock.close();
        }

        /**
         * Configures a mock sequencer and ScoreView so that play() can run without
         * real MIDI hardware. MidiSequenceBuilder construction is intercepted so
         * buildFullSequence() returns a freshly constructed (real) Sequence.
         */
        private MockedConstruction<MidiSequenceBuilder> setupForPlay(Sequencer mockSequencer) {
            var mockScore = mock(ScoreView.class);
            when(mockScore.getSong()).thenReturn(mock(Song.class));
            when(mockScore.getSelection()).thenReturn(null);
            PlaybackController.register(mockScore);
            MidiController.sequencer = mockSequencer;

            // Sequencer tick check: position(0) < length(1000) — the sequence
            // is always new so setTickPosition(0) + setSequence() are both called.
            when(mockSequencer.getTickLength()).thenReturn(1000L);
            when(mockSequencer.getTickPosition()).thenReturn(0L);

            // MidiSequenceBuilder is only constructible (not mockable via interface),
            // so intercept construction and stub buildFullSequence to avoid real MIDI.
            return mockConstruction(MidiSequenceBuilder.class, (builder, ctx) ->
                when(builder.buildFullSequence()).thenReturn(new Sequence(Sequence.PPQ, 480))
            );
        }

        @Test
        void testTransitionsStoppedToPlaying() throws Exception {
            var mockSequencer = mock(Sequencer.class);

            try (var ignored = setupForPlay(mockSequencer)) {
                PlaybackController.togglePlayPause();
            }

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testTransitionsPlayingToPaused() {
            PlaybackController.setState(PlaybackState.PLAYING);
            // sequencer is null → stopSequencer() is a no-op; tick position stays 0

            PlaybackController.togglePlayPause();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PAUSED);
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.PAUSED);
        }

        /**
         * Registers a mock sequencer and score reporting the given selection.
         * Returns a MockedConstruction that stubs buildFromNoteToEnd on any
         * MidiSequenceBuilder constructed while it is open.
         */
        private MockedConstruction<MidiSequenceBuilder> setupForResume(
            Sequencer mockSequencer,
            ElementSelection scoreSelection,
            long tickPosition
        ) {
            var mockScore = mock(ScoreView.class);
            when(mockScore.getSong()).thenReturn(mock(Song.class));
            when(mockScore.getSelection()).thenReturn(scoreSelection);
            PlaybackController.register(mockScore);
            MidiController.sequencer = mockSequencer;
            when(mockSequencer.getTickLength()).thenReturn(1000L);
            when(mockSequencer.getTickPosition()).thenReturn(tickPosition);

            return mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFromNoteToEnd(anyInt(), anyInt()))
                    .thenReturn(new Sequence(Sequence.PPQ, 480)));
        }

        @Test
        void testPausedSameSelectionResumesPlayback() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var selection = new ElementSelection(detachedLine(), 0, 2);
            PlaybackController.setActiveSelection(selection);
            PlaybackController.setState(PlaybackState.PAUSED);
            // pausedTickPosition = 5 < getTickLength(1000) — tick check passes
            PlaybackController.setPausedTickPosition(5L);

            // Same selection as activeSelection → resume() path is taken
            try (var ignored = setupForResume(mockSequencer, selection, 5L)) {
                PlaybackController.togglePlayPause();
            }

            // resume() calls playbackDidStart() → state = PLAYING
            // activeSelection is unchanged (resume() does not update it)
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
            assertThat(PlaybackController.getActiveSelection()).isSameAs(selection);
        }

        @Test
        void testPausedDifferentSelectionPlaysNewSelection() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var originalSelection = new ElementSelection(detachedLine(), 0, 2);
            PlaybackController.setActiveSelection(originalSelection);
            PlaybackController.setState(PlaybackState.PAUSED);

            // Score reports a different selection → play(newSelection) is called
            var newSelection = new ElementSelection(detachedLine(), 1, 3);

            try (var ignored = setupForResume(mockSequencer, newSelection, 0L)) {
                PlaybackController.togglePlayPause();
            }

            // play(newSelection) was called → activeSelection updated to newSelection
            assertThat(PlaybackController.getActiveSelection()).isEqualTo(newSelection);
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PlaybackLifecycle {

        @Test
        void testPlaybackDidStartSetsStateToPlayingAndPostsNotification() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.playbackDidStart();

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.PLAYING);
            }
        }

        @Test
        void testPlaybackDidPauseSetsStateSavesTickAndPostsNotification() {
            var mockSequencer = mock(Sequencer.class);
            final long savedTick = 42L;
            when(mockSequencer.getTickPosition()).thenReturn(savedTick);
            MidiController.sequencer = mockSequencer;

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.playbackDidPause();

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PAUSED);
                assertThat(PlaybackController.getPausedTickPosition()).isEqualTo(savedTick);
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.PAUSED);
            }
        }

        @Test
        void testStopClearsStateAndActiveSelectionAndPostsNotification() {
            PlaybackController.setState(PlaybackState.PLAYING);
            PlaybackController.setActiveSelection(new ElementSelection(detachedLine(), 0, 1));
            PlaybackController.setPausedTickPosition(99L);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.stop();

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
                assertThat(PlaybackController.getActiveSelection()).isNull();
                assertThat(PlaybackController.getPausedTickPosition()).isZero();
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.STOPPED);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RewindToBeginning {

        @Test
        void testWhilePlayingClearsHighlightAndSeeksSequencerToTickZero() {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            var mockLineComponent = mock(LineComponent.class);
            when(mockScore.getLineComponent(0)).thenReturn(mockLineComponent);
            PlaybackController.register(mockScore);
            MidiController.sequencer = mockSequencer;

            PlaybackController.setState(PlaybackState.PLAYING);
            PlaybackController.setPreviousPlayingLine(0);

            PlaybackController.rewindToBeginning();

            verify(mockLineComponent).setPlayingIndices(-1, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(-1);
            verify(mockSequencer).setTickPosition(0);
            // State stays PLAYING — rewind does not stop playback
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testWhilePausedCallsStopAndStateBecomeStopped() {
            PlaybackController.setState(PlaybackState.PAUSED);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.rewindToBeginning();

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.STOPPED);
            }
        }

        @Test
        void testWhileStoppedIsNoOp() {
            // State is already STOPPED from tearDown default; no sequencer set
            PlaybackController.rewindToBeginning();

            // State unchanged, no exceptions
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleMetaMessage {

        @Test
        void testSequenceNumberMessageDecodesIndicesAndCallsUpdatePlayingNote()
            throws Exception {
            var mockScore = mock(ScoreView.class);
            var mockLine = mock(Line.class);
            var mockLineComponent = mock(LineComponent.class);
            final int lineIndex = 2;
            final int noteIndex = 5;
            var mockSong = mock(Song.class);
            when(mockScore.getLineComponent(lineIndex)).thenReturn(mockLineComponent);
            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.getLine(lineIndex)).thenReturn(mockLine);
            when(mockLine.precedingGraceNoteIndex(noteIndex)).thenReturn(-1);
            PlaybackController.register(mockScore);

            // Pack lineIndex and noteIndex into 4 bytes matching the decode formula:
            // lineIndex = (data[0] << 8) | data[1], noteIndex = (data[2] << 8) | data[3]
            var data = new byte[]{
                (byte) (lineIndex >> 8), (byte) (lineIndex & 0xFF),
                (byte) (noteIndex >> 8), (byte) (noteIndex & 0xFF)
            };
            var meta = new MetaMessage(MidiMetaMessageTypes.SEQUENCE_NUMBER, data, data.length);

            PlaybackController.handleMetaMessage(meta);

            verify(mockLineComponent).setPlayingIndices(noteIndex, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(lineIndex);
        }

        @Test
        void testEndOfTrackMessageCallsStop() throws Exception {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var meta = new MetaMessage();
                // END_OF_TRACK carries no payload; set the type explicitly since the
                // no-arg MetaMessage constructor defaults to type 0 (SEQUENCE_NUMBER).
                meta.setMessage(MidiMetaMessageTypes.END_OF_TRACK, new byte[0], 0);

                PlaybackController.handleMetaMessage(meta);

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.STOPPED);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UpdatePlayingNote {

        @Test
        void testClearsPreviousLineHighlightWhenLineChanges() {
            var mockScore = mock(ScoreView.class);
            var mockPrevLineComponent = mock(LineComponent.class);
            var mockNewLineComponent = mock(LineComponent.class);
            var mockLine = mock(Line.class);
            final int prevLine = 0;
            final int newLine = 1;
            final int noteIndex = 3;
            var mockSong = mock(Song.class);
            when(mockScore.getLineComponent(prevLine)).thenReturn(mockPrevLineComponent);
            when(mockScore.getLineComponent(newLine)).thenReturn(mockNewLineComponent);
            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.getLine(newLine)).thenReturn(mockLine);
            when(mockLine.precedingGraceNoteIndex(noteIndex)).thenReturn(-1);
            PlaybackController.register(mockScore);
            PlaybackController.setPreviousPlayingLine(prevLine);

            PlaybackController.updatePlayingNote(newLine, noteIndex);

            // Previous line component must be cleared
            verify(mockPrevLineComponent).setPlayingIndices(-1, -1);
            // New line component must be set to the playing note
            verify(mockNewLineComponent).setPlayingIndices(noteIndex, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(newLine);
        }

        @Test
        void testDoesNotClearPreviousLineComponentWhenLineIsUnchanged() {
            var mockScore = mock(ScoreView.class);
            var mockLineComponent = mock(LineComponent.class);
            var mockLine = mock(Line.class);
            final int lineIndex = 1;
            final int noteIndex = 2;
            var mockSong = mock(Song.class);
            when(mockScore.getLineComponent(lineIndex)).thenReturn(mockLineComponent);
            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.getLine(lineIndex)).thenReturn(mockLine);
            when(mockLine.precedingGraceNoteIndex(noteIndex)).thenReturn(-1);
            PlaybackController.register(mockScore);
            PlaybackController.setPreviousPlayingLine(lineIndex);

            PlaybackController.updatePlayingNote(lineIndex, noteIndex);

            // Same line — setPlayingIndices(-1,-1) must NOT be called to clear the previous
            verify(mockLineComponent, never()).setPlayingIndices(-1, -1);
            // New note IS highlighted
            verify(mockLineComponent).setPlayingIndices(noteIndex, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(lineIndex);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyPrefsDuringPlayback {

        @Test
        void testDoesNothingWhenNotPlaying() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;
            // State is STOPPED (default) — method should return immediately

            PlaybackController.applyPrefsDuringPlayback();

            // Sequencer must not be touched
            verify(mockSequencer, never()).stop();
            verify(mockSequencer, never()).start();
        }

        @Test
        void testDoesNothingWhenPaused() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;
            PlaybackController.setState(PlaybackState.PAUSED);

            PlaybackController.applyPrefsDuringPlayback();

            verify(mockSequencer, never()).stop();
            verify(mockSequencer, never()).start();
        }

        @Test
        void testWhilePlayingStopsRebuildsSequenceRestoresTickAndRestarts() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            final long savedTick = 200L;
            final long tickLength = 1000L;

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.getLines()).thenReturn(new ArrayList<>());
            when(mockSequencer.getTickPosition()).thenReturn(savedTick);
            when(mockSequencer.getTickLength()).thenReturn(tickLength);
            // isRunning() returns false immediately so the spin-wait exits at once
            when(mockSequencer.isRunning()).thenReturn(false);

            MidiController.sequencer = mockSequencer;
            PlaybackController.register(mockScore);
            PlaybackController.setState(PlaybackState.PLAYING);

            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFullSequence())
                    .thenReturn(new Sequence(Sequence.PPQ, 480)))) {
                PlaybackController.applyPrefsDuringPlayback();
            }

            // stop → setSequence → setTickPosition (clamped) → start
            verify(mockSequencer).stop();
            verify(mockSequencer).setTickPosition(Math.min(savedTick, tickLength));
            verify(mockSequencer).start();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetLoopSequence {

        @Test
        void testSetsLoopContinuouslyWhenPrefTrueAndNotSingleNote() {
            var mockSequencer = mock(Sequencer.class);
            var selection = new ElementSelection(detachedLine(), 0, 2);

            try (var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).thenReturn(true);

                PlaybackController.setLoopSequence(selection, mockSequencer);
            }

            verify(mockSequencer).setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        }

        @Test
        void testDoesNotLoopWhenSingleNoteEvenIfPrefTrue() {
            var mockSequencer = mock(Sequencer.class);
            // begin == end → single-note selection
            var singleNoteSelection = new ElementSelection(detachedLine(), 3, 3);

            try (var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).thenReturn(true);

                PlaybackController.setLoopSequence(singleNoteSelection, mockSequencer);
            }

            verify(mockSequencer).setLoopCount(0);
        }

        @Test
        void testDoesNotLoopWhenPrefFalse() {
            var mockSequencer = mock(Sequencer.class);
            var selection = new ElementSelection(detachedLine(), 0, 5);

            try (var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).thenReturn(false);

                PlaybackController.setLoopSequence(selection, mockSequencer);
            }

            verify(mockSequencer).setLoopCount(0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BuildSequenceForSelection {

        @Test
        void testNullSelectionBuildsFullSequence() throws Exception {
            var mockSong = mock(Song.class);

            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFullSequence())
                    .thenReturn(new Sequence(Sequence.PPQ, 480)))) {

                var result = PlaybackController.buildSequenceForSelection(mockSong, null);

                // buildFullSequence() was invoked and its return value was passed through
                verify(ignored.constructed().get(0)).buildFullSequence();
                assertThat(result).isNotNull();
            }
        }

        @Test
        void testNonNullSelectionBuildsFromNoteToEnd() throws Exception {
            var mockSong = mock(Song.class);
            var line = detachedLine();
            final int beginIndex = 2;
            var selection = new ElementSelection(line, beginIndex, 5);

            // getLines().indexOf(line) must return the line index used in the call
            var lines = new ArrayList<Line>();
            lines.add(line);
            when(mockSong.getLines()).thenReturn(lines);
            final int expectedLineIndex = 0;

            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFromNoteToEnd(anyInt(), anyInt()))
                    .thenReturn(new Sequence(Sequence.PPQ, 480)))) {

                var result = PlaybackController.buildSequenceForSelection(mockSong, selection);

                assertThat(result).isNotNull();
                verify(ignored.constructed().get(0))
                    .buildFromNoteToEnd(expectedLineIndex, beginIndex);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyVolumeFromPrefs {

        @Test
        void testDelegatesToMidiControllerWithPrefValue() {
            final int prefVolume = 80;

            try (var prefsMock = mockStatic(Prefs.class);
                 var midiControllerMock = mockStatic(MidiController.class)) {
                prefsMock.when(() -> Prefs.getInt(PrefsKey.PLAYBACK_VOLUME)).thenReturn(prefVolume);

                PlaybackController.applyVolumeFromPrefs();

                midiControllerMock.verify(() -> MidiController.setPlaybackVolume(prefVolume));
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetAndApplySettings {

        @Test
        void testGetPlaybackSettingsApplySettingsRoundTrip() {
            final int expectedInstrument = 42;
            final int expectedTempo = 110;
            final int expectedDuration = 75;
            final boolean expectedPlayWithRepeats = true;

            PlaybackController.setInstrument(expectedInstrument);
            PlaybackController.setTempoChangePercent(expectedTempo);
            PlaybackController.setNoteDurationPercent(expectedDuration);
            PlaybackController.setPlayWithRepeats(expectedPlayWithRepeats);

            var settings = PlaybackController.getPlaybackSettings();

            // Reset all fields to different values to confirm applySettings restores them
            PlaybackController.setInstrument(0);
            PlaybackController.setTempoChangePercent(100);
            PlaybackController.setNoteDurationPercent(100);
            PlaybackController.setPlayWithRepeats(false);

            PlaybackController.applySettings(settings);

            var restored = PlaybackController.getPlaybackSettings();
            assertThat(restored.instrument()).isEqualTo(expectedInstrument);
            assertThat(restored.tempoChangePercent()).isEqualTo(expectedTempo);
            assertThat(restored.noteDurationPercent()).isEqualTo(expectedDuration);
            assertThat(restored.playWithRepeats()).isEqualTo(expectedPlayWithRepeats);
        }
    }
}
