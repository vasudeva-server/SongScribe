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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.dom.ScaleContext;
import songscribe.engraving.Staff;
import songscribe.ui.hit.HitResult;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.LineSelectionState;

class NoteDragHandlerTest extends UnitTest {

    // Static mocks
    private MockedStatic<EditModeManager> editModeMgrMock;
    private MockedStatic<PreviewElementManager> previewMgrMock;
    private MockedStatic<MessageCenter> messageCenterMock;
    private MockedStatic<MidiController> midiControllerMock;
    private MockedStatic<PlayThread> playThreadStaticMock;
    private MockedConstruction<PlayThread> playThreadConstruction;
    private MockedStatic<ScaleContext> scaleContextMock;

    // Instance mocks
    private LineComponent lc;
    private LineSelectionState mockSelectionState;

    // Subject under test
    private NoteDragHandler handler;

    // Staff position of the pressed note at press time — used by dragToPosition to compute
    // the screen-Y delta that yields a specific target position.
    private int pressOriginalSp;

    // Mouse coordinates used by press/drag events. X is arbitrary; screen-Y values differ
    // so the handler sees a non-zero delta on drag.
    private static final int MOUSE_X = 100;
    private static final int PRESS_SCREEN_Y = 100;
    private static final int DRAG_SCREEN_Y = 50;

    @BeforeEach
    void setUp() {
        editModeMgrMock = mockStatic(EditModeManager.class);
        previewMgrMock = mockStatic(PreviewElementManager.class);
        messageCenterMock = mockStatic(MessageCenter.class);
        midiControllerMock = mockStatic(MidiController.class);
        playThreadStaticMock = mockStatic(PlayThread.class);
        playThreadConstruction = mockConstruction(PlayThread.class);
        scaleContextMock = mockStatic(ScaleContext.class);

        midiControllerMock.when(MidiController::isPlaying).thenReturn(false);

        lc = mock(LineComponent.class);
        var mockScore = mock(ScoreView.class);
        var mockSelectionHandler = mock(LineSelectionHandler.class);
        mockSelectionState = mock(LineSelectionState.class);

        when(lc.getScoreView()).thenReturn(mockScore);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getSelectionHandler()).thenReturn(mockSelectionHandler);
        when(lc.getLineSelectionState()).thenReturn(mockSelectionState);
        when(lc.getSong()).thenReturn(mock(Song.class));
        when(mockScore.getMode()).thenReturn(Mode.SELECT);

        handler = new NoteDragHandler(lc);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
        playThreadConstruction.close();
        playThreadStaticMock.close();
        midiControllerMock.close();
        messageCenterMock.close();
        previewMgrMock.close();
        editModeMgrMock.close();
    }

    // -------------------------------------------------------------------------
    // Drag Group Building
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DragGroupBuilding {

        @Test
        void testMultiSelectionCreatesGroupFromAllSelectedNotes() {
            var line = createLine(0, 2, 4);
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(0, 2, 1);
            pressOnNote(1);
            dragToPosition(5); // delta = 5 - 2 = +3

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(3);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(5);
            assertThat(line.getElement(2).getStaffPosition()).isEqualTo(7);
        }

        @Test
        void testMultiSelectionWithTiesExpandsTieChains() {
            // [crotchet@0, crotchet@2, crotchet@4] with tie 0→1
            // Select indices 1-2, drag on index 1
            // Tie chain expands: index 0 is tied to index 1, so index 0 joins the group
            var line = createLine(0, 2, 4);
            line.addRangeElement(new Tie(line.getElement(0), line.getElement(1)));
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(1, 2, 1);
            pressOnNote(1);
            dragToPosition(4); // delta = 4 - 2 = +2

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(2);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(4);
            assertThat(line.getElement(2).getStaffPosition()).isEqualTo(6);
        }

        @Test
        void testSingleNoteCreatesGroupOfOne() {
            var line = createLine(0, 4, 8);
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(1);
            pressOnNote(1);
            dragToPosition(6); // delta = 6 - 4 = +2

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(0);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(6);
            assertThat(line.getElement(2).getStaffPosition()).isEqualTo(8);
        }

        @Test
        void testTieChainExpandsDragGroup() {
            // [crotchet@0, crotchet@0, crotchet@4] with tie 0→1
            // Select only index 0 — tie should expand to include index 1
            var line = createLine(0, 0, 4);
            line.addRangeElement(new Tie(line.getElement(0), line.getElement(1)));
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(2); // delta = 2 - 0 = +2

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(2);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(2);
            assertThat(line.getElement(2).getStaffPosition()).isEqualTo(4);
        }
    }

    // -------------------------------------------------------------------------
    // Delta Computation and Clamping
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeltaComputationAndClamping {

        @Test
        void testClampingAtLowerBoundary() {
            // MAX_STAFF_POSITION_SP = 12
            // Notes at 8 and 10, drag down by +5 → clamped to +2 (10+2=12)
            var line = createLine(8, 10);
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(0, 1, 0);
            pressOnNote(0);
            dragToPosition(13); // delta = 13 - 8 = +5, clamped to +2

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(10);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(12);
        }

        @Test
        void testClampingAtUpperBoundary() {
            // MIN_STAFF_POSITION_SP = -10
            // Notes at -6 and -8, drag up by -5 → clamped to -2 (-8-2=-10)
            var line = createLine(-6, -8);
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(0, 1, 0);
            pressOnNote(0);
            dragToPosition(-11); // delta = -11 - (-6) = -5, clamped to -2

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(-8);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(-10);
        }

        @Test
        void testDragMovesAllGroupNotesBySameDelta() {
            var line = createLine(0, 2, 4);
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(0, 2, 1);
            pressOnNote(1);
            dragToPosition(5); // delta = 5 - 2 = +3

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(3);
            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(5);
            assertThat(line.getElement(2).getStaffPosition()).isEqualTo(7);
        }
    }

    // -------------------------------------------------------------------------
    // Glissando Unison Retention
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GlissandoUnisonRetention {

        @Test
        void testRetainsBackwardGlissandoWhenUnisonAfterDrag() {
            // [crotchet@0 (connected gliss), crotchet@4]
            // Drag index 1 by -4 → unison. The glissando is left intact; the renderer
            // hides it while the notes share a pitch and shows it again when they diverge.
            var line = createLine(0, 4);
            line.getElement(0).setGlissando();
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(1);
            pressOnNote(1);
            dragToPosition(0); // delta = 0 - 4 = -4
            handler.handleRelease();

            assertThat(line.getElement(0).hasGlissando())
                .isTrue();
        }

        @Test
        void testRetainsForwardGlissandoWhenUnisonAfterDrag() {
            // [crotchet@4 (connected gliss), crotchet@0]
            // Drag index 0 by -4 → position becomes 0, matches index 1 → unison. The
            // glissando is left intact; the renderer hides it only while the pitches match.
            var line = createLine(4, 0);
            line.getElement(0).setGlissando();
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(0); // delta = 0 - 4 = -4
            handler.handleRelease();

            assertThat(line.getElement(0).hasGlissando())
                .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Grace Note Validity Checks
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GraceNoteValidity {

        @Test
        void testRemovesGraceNoteDraggedToHostPitch() {
            // [grace@0, crotchet@4] — drag grace note to position 4 (matches host)
            var line = detachedLine();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(0);
            line.addElement(grace);

            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(4);
            line.addElement(host);
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(4); // delta = 4 - 0 = +4
            handler.handleRelease();

            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        }

        @Test
        void testRemovesGraceNoteWhenHostDraggedToGracePitch() {
            // [grace@4, crotchet@0] — drag host note to position 4 (matches grace)
            var line = detachedLine();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(4);
            line.addElement(grace);

            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(0);
            line.addElement(host);
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(1);
            pressOnNote(1);
            dragToPosition(4); // delta = 4 - 0 = +4
            handler.handleRelease();

            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        }
    }

    // -------------------------------------------------------------------------
    // Mutation emission
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MutationEmission {

        private static final int ORIGINAL_POSITION_SP = 4;
        private static final int DRAGGED_POSITION_SP = 6;

        private Line realLine;

        @BeforeEach
        void setUpWithRealSong() {
            // Create a real Song so line.withModification() in handleRelease
            // delegates to song.withModification(), which posts a
            // SongDidChangeNotification via the static MessageCenter mock.
            var realSong = new Song();
            realLine = realSong.getLine(0);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(ORIGINAL_POSITION_SP);
            // An accidental is written for the staff position it sits on, so a dragged note gives
            // it up. Without one here nothing observes that clearing: the ACCIDENTAL tag on the
            // recorded mutation comes from a fixed set and is reported either way.
            note.setAccidental(StaffElement.Accidental.SHARP);
            realSong.withoutMutationTracking(() -> realLine.addElement(note));

            when(lc.getLine()).thenReturn(realLine);
        }

        @Test
        void testBeforeCloneHasOriginalPitchAfterRelease() {
            // Press on the note, drag it to a new position, release.
            // handleRelease coalesces the mutations into one SongDidChangeNotification
            // carrying an ElementModification whose beforeElement reflects the pre-drag pitch.
            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(DRAGGED_POSITION_SP);
            handler.handleRelease();

            // Capture all MessageCenter.post calls (includes the song-constructor
            // notification and the handleRelease notification).
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(
                () -> MessageCenter.post(captor.capture()),
                atLeastOnce()
            );

            // Find the ElementModification inside whichever notification carries it.
            var modification = captor.getAllValues().stream()
                .filter(m -> m instanceof SongDidChangeNotification)
                .map(m -> (SongDidChangeNotification) m)
                .flatMap(n -> n.getMutations().stream())
                .filter(m -> m instanceof ElementModification)
                .map(m -> (ElementModification) m)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ElementModification in captured notifications"));

            // beforeElement is the clone captured at press time — original pitch and accidental,
            // which is what undo restores.
            assertThat(modification.beforeElement().getStaffPosition()).isEqualTo(ORIGINAL_POSITION_SP);
            assertThat(modification.beforeElement().getAccidental())
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(modification.fields()).containsExactly(ElementField.PITCH, ElementField.ACCIDENTAL);

            // The live element now has the dragged pitch, and gave up the accidental it carried
            // at the position it left.
            assertThat(realLine.getElement(0).getStaffPosition()).isEqualTo(DRAGGED_POSITION_SP);
            assertThat(realLine.getElement(0).getAccidental()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Restatement prompt on release (#681)
    // -------------------------------------------------------------------------

    /**
     * A drag is the one edit that has already changed the score before it asks about restatements:
     * the notes moved live as the mouse moved. Cancel therefore has to put them back rather than
     * merely decline to go forward, and it must leave nothing behind to undo, because the
     * modification bracket that would have recorded an undo step never opened.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RestatementPromptOnRelease {

        private static final int ORIGINAL_POSITION_SP = 4;
        private static final int DRAGGED_POSITION_SP = 6;

        private Line realLine = detachedLine();

        @BeforeEach
        void setUpWithARestatement() {
            var realSong = new Song();
            realLine = realSong.getLine(0);

            realSong.withoutMutationTracking(() -> {
                realLine.addElement(sharpNote(ORIGINAL_POSITION_SP));

                // The restatement: a second sharp at the same staff position, later in the song.
                // Dragging the first note away takes its sharp with it, so this one is offered.
                realLine.addElement(sharpNote(ORIGINAL_POSITION_SP));
            });

            when(lc.getLine()).thenReturn(realLine);
        }

        private static StaffElement sharpNote(int staffPosition) {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(staffPosition);
            note.setAccidental(StaffElement.Accidental.SHARP);
            return note;
        }

        private void dragAndReleaseAnswering(int answer) {
            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(DRAGGED_POSITION_SP);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

                handler.handleRelease();
            }
        }

        /**
         * Every element modification the release posted, across all captured notifications.
         * {@code atLeast(0)} because a cancelled release posts nothing at all — which is the
         * point of the Cancel test and would otherwise fail as "wanted but not invoked".
         */
        private List<ElementModification> postedElementModifications() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()), atLeast(0));

            return captor.getAllValues().stream()
                .filter(m -> m instanceof SongDidChangeNotification)
                .map(m -> (SongDidChangeNotification) m)
                .flatMap(n -> n.getMutations().stream())
                .filter(m -> m instanceof ElementModification)
                .map(m -> (ElementModification) m)
                .toList();
        }

        @Test
        void testCancelPutsTheDraggedNoteBackAndRecordsNothing() {
            dragAndReleaseAnswering(JOptionPane.CANCEL_OPTION);

            assertThat(realLine.getElement(0).getStaffPosition())
                .as("the drag was undone, not committed")
                .isEqualTo(ORIGINAL_POSITION_SP);
            assertThat(realLine.getElement(0).getAccidental())
                .as("the accidental the drag cleared is back")
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(realLine.getElement(1).getAccidental())
                .as("the restatement was left alone")
                .isEqualTo(StaffElement.Accidental.SHARP);

            // Nothing was recorded, so there is no undo step — which is exactly why the revert has
            // to happen here: Ctrl-Z could not get the user back.
            assertThat(postedElementModifications()).isEmpty();
        }

        @Test
        void testYesCommitsTheDragAndClearsTheRestatement() {
            dragAndReleaseAnswering(JOptionPane.YES_OPTION);

            assertThat(realLine.getElement(0).getStaffPosition()).isEqualTo(DRAGGED_POSITION_SP);
            assertThat(realLine.getElement(1).getAccidental())
                .as("the accepted restatement is gone")
                .isNull();
            assertThat(postedElementModifications()).isNotEmpty();
        }

        @Test
        void testNoCommitsTheDragAndLeavesTheRestatementAlone() {
            dragAndReleaseAnswering(JOptionPane.NO_OPTION);

            assertThat(realLine.getElement(0).getStaffPosition()).isEqualTo(DRAGGED_POSITION_SP);
            assertThat(realLine.getElement(1).getAccidental())
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(postedElementModifications()).isNotEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // No-drag on Preserved Multi-selection
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoDragOnPreservedMultiSelection {

        @Test
        void testReleaseWithoutDragCollapsesSelectionToClickedNote() {
            // [crotchet@0, crotchet@2, crotchet@4] with all three selected.
            // Press on note 1 (already in selection) → pressPreservedMultiSelection = true.
            // No drag. Release → selectElementAtIndex(1) called, not selectAndPlayElement.
            var line = createLine(0, 2, 4);
            when(lc.getLine()).thenReturn(line);

            setupMultiSelection(0, 2, 1);
            pressOnNote(1);
            handler.handleRelease();

            var mockSelectionHandler = lc.getSelectionHandler();
            verify(mockSelectionHandler).selectElementAtIndex(1);
            verify(mockSelectionHandler, never()).selectAndPlayElement(anyInt());
        }
    }

    // -------------------------------------------------------------------------
    // HandlePress Guards
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandlePressGuards {

        private MouseEvent pressEvent() {
            return mouseEvent(lc, MouseEvent.MOUSE_PRESSED, MOUSE_X, PRESS_SCREEN_Y, MouseEvent.BUTTON1);
        }

        private MouseEvent shiftPressEvent() {
            return new MouseEvent(
                lc, MouseEvent.MOUSE_PRESSED, 0L,
                MouseEvent.SHIFT_DOWN_MASK,
                MOUSE_X, PRESS_SCREEN_Y, MOUSE_X, PRESS_SCREEN_Y, 1, false, MouseEvent.BUTTON1
            );
        }

        /**
         * The cascade result a press on the line's first note produces, so each guard test below
         * proves its own guard refused rather than the press having missed every element.
         */
        private HitResult noteHeadHit() {
            return new HitResult.ElementHead(0);
        }

        @Test
        void testNotSelectModeReturnsFalse() {
            var mockScore = lc.getScoreView();
            when(mockScore.getMode()).thenReturn(Mode.EDIT);

            var result = handler.handlePress(pressEvent(), noteHeadHit());

            assertThat(result).isFalse();
        }

        @Test
        void testMidiPlayingReturnsFalse() {
            midiControllerMock.when(MidiController::isPlaying).thenReturn(true);

            var result = handler.handlePress(pressEvent(), noteHeadHit());

            assertThat(result).isFalse();
        }

        @Test
        void testShiftDownReturnsFalse() {
            var result = handler.handlePress(shiftPressEvent(), noteHeadHit());

            assertThat(result).isFalse();
        }

        @Test
        void testHitMissReturnsFalse() {
            var result = handler.handlePress(pressEvent(), new HitResult.Nothing());

            assertThat(result).isFalse();
        }

        /**
         * A lyric outranks a note head in the hit-test cascade, so lyric text drawn over an
         * element's rectangle arrives here as a lyric. Dragging it would change the pitch of a
         * note the user was aiming past.
         */
        @Test
        void testLyricHitReturnsFalse() {
            var lyricHit = new HitResult.Lyric(ElementType.CROTCHET.newInstance(), Lyric.FIRST_VERSE);

            var result = handler.handlePress(pressEvent(), lyricHit);

            assertThat(result).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a Line with crotchets at the given staff positions.
     */
    private Line createLine(int... staffPositions) {
        var line = detachedLine();

        for (var sp : staffPositions) {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(sp);
            line.addElement(note);
        }

        return line;
    }

    /**
     * Simulates a drag to the given staff position. The handler computes the new position
     * from the screen-Y delta between press and drag; this helper mocks
     * {@code ScaleContext.pxToSs} to return the staff-space delta that maps to the
     * requested target position.
     */
    private void dragToPosition(int targetPositionSp) {
        var deltaSp = targetPositionSp - pressOriginalSp;
        var deltaYSs = deltaSp * Staff.STAFF_POSITION_OFFSET_SS;
        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble())).thenReturn(deltaYSs);

        var event = mouseEvent(lc, MouseEvent.MOUSE_DRAGGED, MOUSE_X, DRAG_SCREEN_Y, MouseEvent.BUTTON1);
        handler.handleDrag(event);
    }

    private MouseEvent mouseEvent(Component source, int id, int x, int y, int button) {
        return new MouseEvent(source, id, 0L, 0, x, y, x, y, 1, false, button);
    }

    /**
     * Presses on the note at {@code hitIndex}, supplying the cascade result the production
     * caller would pass. Also records the pressed note's staff position so
     * {@link #dragToPosition} can compute the right delta.
     */
    private void pressOnNote(int hitIndex) {
        var line = lc.getLine();

        if (line != null) {
            pressOriginalSp = line.getElement(hitIndex).getStaffPosition();
        }

        var event = mouseEvent(lc, MouseEvent.MOUSE_PRESSED, MOUSE_X, PRESS_SCREEN_Y, MouseEvent.BUTTON1);
        handler.handlePress(event, new HitResult.ElementHead(hitIndex));
    }

    /**
     * Configures mock selection state for a multi-note selection where the
     * hit note is already part of the selection.
     */
    private void setupMultiSelection(int begin, int end, int hitIndex) {
        when(mockSelectionState.isElementSelected(hitIndex)).thenReturn(true);
        when(mockSelectionState.hasElementSelection()).thenReturn(true);
        when(mockSelectionState.getSelectionBegin()).thenReturn(begin);
        when(mockSelectionState.getSelectionEnd()).thenReturn(end);
    }

    /**
     * Configures mock selection state for a single-note selection at the hit index.
     * The note is not yet selected, so selectAndPlayElement will be called.
     */
    private void setupSingleSelection(int hitIndex) {
        when(mockSelectionState.isElementSelected(hitIndex)).thenReturn(false);
        when(mockSelectionState.hasElementSelection()).thenReturn(true);
        when(mockSelectionState.getSelectionBegin()).thenReturn(hitIndex);
        when(mockSelectionState.getSelectionEnd()).thenReturn(hitIndex);
    }
}
