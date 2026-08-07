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

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

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

import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.playback.PlaybackController.PlaybackState;
import songscribe.ui.selection.ElementSelection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackControllerTest extends UnitTest {

    @AfterEach
    void tearDown() {
        PlaybackController.setState(PlaybackState.STOPPED);
        PlaybackController.setPreviousPlayingLine(-1);
        PlaybackController.setActiveSelection(null);
        PlaybackController.setRegisteredScore(null);
        PlaybackController.setInstrument(0);
        PlaybackController.setTempoChangePercent(100);
        PlaybackController.setNoteDurationPercent(100);
        PlaybackController.setPlayWithRepeats(false);
        MidiController.sequencer = null;
        MidiController.midiReceiver = null;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TogglePlayStop {

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

            // play() loads a freshly built sequence and rewinds it unconditionally, so
            // these only need to be non-zero enough to look like a real loaded sequence.
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
                PlaybackController.togglePlayStop();
            }

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testTransitionsPlayingToStopped() {
            PlaybackController.setState(PlaybackState.PLAYING);
            // sequencer is null → stopSequencer() is a no-op

            PlaybackController.togglePlayStop();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(PlaybackController.getActiveSelection()).isNull();
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.STOPPED);
        }

        @Test
        void testPlayAfterStopRestartsAtBeginningOfSelection() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            var selection = new ElementSelection(detachedLine(), 2, 4);
            var mockSong = mock(Song.class);
            var lines = new ArrayList<Line>();
            lines.add(selection.line());
            when(mockSong.getLines()).thenReturn(lines);
            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockScore.getSelection()).thenReturn(selection);
            PlaybackController.register(mockScore);
            MidiController.sequencer = mockSequencer;

            final var tickLength = 1000L;
            // Non-zero tick, as if playback had advanced before Stop was pressed
            final var advancedTick = 500L;
            when(mockSequencer.getTickLength()).thenReturn(tickLength);
            when(mockSequencer.getTickPosition()).thenReturn(advancedTick);

            PlaybackController.setState(PlaybackState.PLAYING);

            // Stop leg: rewinds the sequencer to tick 0
            PlaybackController.togglePlayStop();

            verify(mockSequencer).setTickPosition(0);
            reset(mockSequencer);
            when(mockSequencer.getTickLength()).thenReturn(tickLength);
            when(mockSequencer.getTickPosition()).thenReturn(0L);

            final var stubVolumePercent = 80;

            // Play leg: rebuilds the sequence and seeks to the beginning of the selection
            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFromNoteToEnd(anyInt(), anyInt()))
                    .thenReturn(new Sequence(Sequence.PPQ, 480)));
                 var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).thenReturn(false);
                prefsMock.when(() -> Prefs.getInt(PrefsKey.PLAYBACK_VOLUME))
                    .thenReturn(stubVolumePercent);

                PlaybackController.togglePlayStop();
            }

            verify(mockSequencer).setSequence(any(Sequence.class));
            verify(mockSequencer).setTickPosition(0);
            assertThat(PlaybackController.getActiveSelection()).isEqualTo(selection);
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
        void testStopClearsStateAndActiveSelectionAndPostsNotification() {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            MidiController.sequencer = mockSequencer;
            PlaybackController.register(mockScore);
            PlaybackController.setState(PlaybackState.PLAYING);
            PlaybackController.setActiveSelection(new ElementSelection(detachedLine(), 0, 1));

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                PlaybackController.stop();

                assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
                assertThat(PlaybackController.getActiveSelection()).isNull();
                verify(mockSequencer).setTickPosition(0);
                // stop() must leave the music selection in place so the next
                // Play restarts at the selection
                verify(mockScore, never()).clearSelection();
                var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.STOPPED);
            }
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
            final var lineIndex = 2;
            final var noteIndex = 5;
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
        void testEndOfTrackMessageStopsPlayback() throws Exception {
            PlaybackController.setState(PlaybackState.PLAYING);

            PlaybackController.handleMetaMessage(endOfTrackMessage());
            drainEventQueue();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
        }

        /**
         * The MIDI system delivers end-of-track on the sequencer's own thread. The stop it
         * triggers posts synchronously, and the Play/Stop button derives its entire label
         * from that notification — so a subscriber running on the MIDI thread would be
         * mutating Swing state off the event thread. This pins the hand-off.
         */
        @Test
        void testEndOfTrackDeliversTheStateChangeOnTheEventThread() throws Exception {
            var listener = new NotifiedThreadRecorder();
            MessageCenter.subscribe(listener);

            PlaybackController.setState(PlaybackState.PLAYING);

            assertThat(EventQueue.isDispatchThread())
                .as("this test only means anything when it starts off the event thread")
                .isFalse();

            PlaybackController.handleMetaMessage(endOfTrackMessage());
            drainEventQueue();

            assertThat(listener.notifiedThread.get())
                .as("the playback state change must be delivered on the event thread")
                .isSameAs(eventThread());
        }

        private MetaMessage endOfTrackMessage() throws Exception {
            var meta = new MetaMessage();
            // END_OF_TRACK carries no payload; set the type explicitly since the
            // no-arg MetaMessage constructor defaults to type 0 (SEQUENCE_NUMBER).
            meta.setMessage(MidiMetaMessageTypes.END_OF_TRACK, new byte[0], 0);

            return meta;
        }
    }

    /**
     * Records the thread a playback state change is delivered on. A named class rather than
     * an anonymous one so the message bus can see the {@code @Handler} method; held by a
     * local in the test so the bus's weak reference does not collect it mid-test.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    static class NotifiedThreadRecorder {

        final AtomicReference<Thread> notifiedThread = new AtomicReference<>();

        @Handler
        public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
            notifiedThread.set(Thread.currentThread());
        }
    }

    /** Runs everything already queued on the event thread, then returns. */
    private static void drainEventQueue() throws Exception {
        EventQueue.invokeAndWait(() -> {
        });
    }

    private static Thread eventThread() throws Exception {
        var thread = new AtomicReference<Thread>();
        EventQueue.invokeAndWait(() -> thread.set(Thread.currentThread()));

        return thread.get();
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
            final var prevLine = 0;
            final var newLine = 1;
            final var noteIndex = 3;
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
            final var lineIndex = 1;
            final var noteIndex = 2;
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
    class RewindToBeginning {

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
         * Rewinding while a song plays ends playback and parks the sequencer at the start.
         * It must not restart the sequencer: the whole point of REWOUND is that playback
         * has stopped at the top of the song.
         */
        @Test
        void testRewindWhilePlayingEndsPlaybackAndParksTheSequencerAtTheStart() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;

            var mockScore = mock(ScoreView.class);
            final var playingLine = 1;
            var mockLineComponent = mock(LineComponent.class);
            when(mockScore.getLineComponent(playingLine)).thenReturn(mockLineComponent);
            PlaybackController.register(mockScore);

            PlaybackController.setPreviousPlayingLine(playingLine);
            PlaybackController.setActiveSelection(new ElementSelection(detachedLine(), 2, 4));
            PlaybackController.setState(PlaybackState.PLAYING);

            PlaybackController.rewindToBeginning();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.REWOUND);
            assertThat(PlaybackController.getActiveSelection()).isNull();
            verify(mockSequencer).stop();
            verify(mockSequencer).setTickPosition(0);
            verify(mockSequencer, never()).start();
            verify(mockLineComponent).setPlayingIndices(-1, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(-1);
        }

        /**
         * The score clears its own selection in response to the notification, so rewind must
         * announce REWOUND rather than reach into the score itself.
         */
        @Test
        void testRewindPostsRewoundAndNeverTouchesTheScoreSelection() {
            var mockScore = mock(ScoreView.class);
            PlaybackController.register(mockScore);
            PlaybackController.setState(PlaybackState.PLAYING);

            PlaybackController.rewindToBeginning();

            verify(mockScore, never()).clearSelection();
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.REWOUND);
        }

        /**
         * Rewinding from a stop still announces REWOUND, because the score's selection has
         * to be dropped for the next Play to start at the top of the song rather than at
         * that selection.
         */
        @Test
        void testRewindWhileStoppedStillAnnouncesRewound() {
            PlaybackController.setState(PlaybackState.STOPPED);

            PlaybackController.rewindToBeginning();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.REWOUND);
            var captor = ArgumentCaptor.forClass(PlaybackStateDidChangeNotification.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getState()).isEqualTo(PlaybackState.REWOUND);
        }

        /**
         * Holding the keyboard shortcut repeats it at the key-repeat rate. A second rewind
         * from an already-rewound state has nothing left to do, and must not post again —
         * every post repaints the score.
         */
        @Test
        void testRewindWhileAlreadyRewoundDoesNothing() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;
            PlaybackController.setState(PlaybackState.REWOUND);

            PlaybackController.rewindToBeginning();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.REWOUND);
            verify(mockSequencer, never()).stop();
            messageCenterMock.verifyNoInteractions();
        }

        /**
         * Rewind is reachable before a score is registered and with MIDI unavailable, so
         * neither may throw.
         */
        @Test
        void testRewindWithoutScoreOrSequencerStillRewinds() {
            // registeredScore and MidiController.sequencer are both null from tearDown
            PlaybackController.setState(PlaybackState.PLAYING);

            PlaybackController.rewindToBeginning();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.REWOUND);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionDidChange {

        /**
         * Selecting music after a rewind means the next Play should start at that selection,
         * so the rewound state has to give way to an ordinary stop. Without this, Play would
         * keep starting at the top of the song however many notes the user selected.
         */
        @Test
        void testReclassifiesRewoundAsStopped() {
            PlaybackController.setState(PlaybackState.REWOUND);

            PlaybackController.selectionDidChange();

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
        }

        @Test
        void testLeavesPlayingUntouched() {
            PlaybackController.setState(PlaybackState.PLAYING);

            PlaybackController.selectionDidChange();

            assertThat(PlaybackController.getState())
                .as("changing the selection mid-song must not stop playback")
                .isEqualTo(PlaybackState.PLAYING);
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
        void testWhilePlayingStopsRebuildsSequenceRestoresTickAndRestarts() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            final var savedTick = 200L;
            final var tickLength = 1000L;

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

            // stop → setSequence → setTickPosition (clamped) → loop count → start
            verify(mockSequencer).stop();
            verify(mockSequencer).setTickPosition(Math.min(savedTick, tickLength));
            verify(mockSequencer).start();

            // Reloading re-derives the loop count. Without it, a user with Loop Playback on
            // who changes the tempo mid-song would silently stop looping.
            verify(mockSequencer).setLoopCount(anyInt());
        }

        /**
         * Stopping a sequencer is asynchronous, so the reload spins until it reports itself
         * stopped. A sequencer that never does must not hang the event thread forever: the
         * spin gives up after a bounded wait and reloads anyway. Without the deadline this
         * test would never return.
         */
        @Test
        void testWedgedSequencerGivesUpWaitingAndReloadsAnyway() throws Exception {
            var mockSequencer = mock(Sequencer.class);
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.getLines()).thenReturn(new ArrayList<>());
            when(mockSequencer.getTickPosition()).thenReturn(0L);
            when(mockSequencer.getTickLength()).thenReturn(1000L);
            // Never goes quiet, so only the deadline can end the spin
            when(mockSequencer.isRunning()).thenReturn(true);

            MidiController.sequencer = mockSequencer;
            PlaybackController.register(mockScore);
            PlaybackController.setState(PlaybackState.PLAYING);

            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFullSequence())
                    .thenReturn(new Sequence(Sequence.PPQ, 480)))) {
                PlaybackController.applyPrefsDuringPlayback();
            }

            verify(mockSequencer).setSequence(any(Sequence.class));
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
                verify(ignored.constructed().getFirst()).buildFullSequence();
                assertThat(result).isNotNull();
            }
        }

        @Test
        void testNonNullSelectionBuildsFromNoteToEnd() throws Exception {
            var mockSong = mock(Song.class);
            var line = detachedLine();
            final var beginIndex = 2;
            var selection = new ElementSelection(line, beginIndex, 5);

            // getLines().indexOf(line) must return the line index used in the call
            var lines = new ArrayList<Line>();
            lines.add(line);
            when(mockSong.getLines()).thenReturn(lines);
            final var expectedLineIndex = 0;

            try (var ignored = mockConstruction(MidiSequenceBuilder.class,
                (builder, ctx) -> when(builder.buildFromNoteToEnd(anyInt(), anyInt()))
                    .thenReturn(new Sequence(Sequence.PPQ, 480)))) {

                var result = PlaybackController.buildSequenceForSelection(mockSong, selection);

                assertThat(result).isNotNull();
                verify(ignored.constructed().getFirst())
                    .buildFromNoteToEnd(expectedLineIndex, beginIndex);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyVolumeFromPrefs {

        @Test
        void testDelegatesToMidiControllerWithPrefValue() {
            final var prefVolume = 80;

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
            final var expectedInstrument = 42;
            final var expectedTempo = 110;
            final var expectedDuration = 75;
            final var expectedPlayWithRepeats = true;

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
