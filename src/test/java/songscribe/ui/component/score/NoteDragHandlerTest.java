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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

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
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.TieInterval;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.LineSelectionState;

class NoteDragHandlerTest extends UnitTest {

    // Static mocks
    private MockedStatic<EditModeManager> editModeMgrMock;
    private MockedStatic<ElementHitTest> hitTestMock;
    private MockedStatic<PreviewElementManager> previewMgrMock;
    private MockedStatic<MessageCenter> messageCenterMock;
    private MockedStatic<MidiController> midiControllerMock;
    private MockedStatic<PlayThread> playThreadStaticMock;
    private MockedConstruction<PlayThread> playThreadConstruction;
    private MockedStatic<ScaleContext> scaleContextMock;

    // Instance mocks
    private LineComponent lc;
    private LineSelectionHandler mockSelectionHandler;
    private LineSelectionState mockSelectionState;
    private ScaleContext mockScaleContext;

    // Subject under test
    private NoteDragHandler handler;

    @BeforeEach
    void setUp() {
        editModeMgrMock = mockStatic(EditModeManager.class);
        hitTestMock = mockStatic(ElementHitTest.class);
        previewMgrMock = mockStatic(PreviewElementManager.class);
        messageCenterMock = mockStatic(MessageCenter.class);
        midiControllerMock = mockStatic(MidiController.class);
        playThreadStaticMock = mockStatic(PlayThread.class);
        playThreadConstruction = mockConstruction(PlayThread.class);
        scaleContextMock = mockStatic(ScaleContext.class);

        editModeMgrMock.when(EditModeManager::getInstance).thenReturn(null);
        midiControllerMock.when(MidiController::isPlaying).thenReturn(false);

        mockScaleContext = mock(ScaleContext.class);
        scaleContextMock.when(ScaleContext::getInstance).thenReturn(mockScaleContext);

        lc = mock(LineComponent.class);
        var mockScore = mock(Score.class);
        mockSelectionHandler = mock(LineSelectionHandler.class);
        mockSelectionState = mock(LineSelectionState.class);

        when(lc.getScore()).thenReturn(mockScore);
        when(lc.getSelectionHandler()).thenReturn(mockSelectionHandler);
        when(lc.getLineSelectionState()).thenReturn(mockSelectionState);
        when(lc.getComposition()).thenReturn(mock(Composition.class));
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
        hitTestMock.close();
        editModeMgrMock.close();
    }

    // -------------------------------------------------------------------------
    // Drag Group Building
    // -------------------------------------------------------------------------

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
            line.getTies().addInterval(new TieInterval(0, 1));
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
            line.getTies().addInterval(new TieInterval(0, 1));
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
    // Glissando Cleanup
    // -------------------------------------------------------------------------

    @Nested
    class GlissandoCleanup {

        @Test
        void testRemovesBackwardGlissandoWhenUnisonAfterDrag() {
            // [crotchet@0 (connected gliss), crotchet@4]
            // Drag index 1 by -4 → unison → remove glissando from index 0
            var line = createLine(0, 4);
            line.getElement(0).setGlissando(StaffElement.Glissando.Type.CONNECTED);
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(1);
            pressOnNote(1);
            dragToPosition(0); // delta = 0 - 4 = -4
            handler.handleRelease();

            assertThat(line.getElement(0).getGlissando())
                .isNull();
        }

        @Test
        void testRemovesForwardGlissandoWhenUnisonAfterDrag() {
            // [crotchet@4 (connected gliss), crotchet@0]
            // Drag index 0 by -4 → position becomes 0, matches index 1 → remove glissando
            var line = createLine(4, 0);
            line.getElement(0).setGlissando(StaffElement.Glissando.Type.CONNECTED);
            when(lc.getLine()).thenReturn(line);

            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(0); // delta = 0 - 4 = -4
            handler.handleRelease();

            assertThat(line.getElement(0).getGlissando())
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Grace Note Validity Checks
    // -------------------------------------------------------------------------

    @Nested
    class GraceNoteValidity {

        @Test
        void testRemovesGraceNoteDraggedToHostPitch() {
            // [grace@0, crotchet@4] — drag grace note to position 4 (matches host)
            var line = new Line();
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
            var line = new Line();
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

    @Nested
    class MutationEmission {

        private static final int ORIGINAL_POSITION_SP = 4;
        private static final int DRAGGED_POSITION_SP = 6;

        private Line realLine;

        @BeforeEach
        void setUpWithRealComposition() {
            // Create a real Composition so line.withModification() in handleRelease
            // delegates to composition.withModification(), which posts a
            // CompositionDidChangeNotification via the static MessageCenter mock.
            var realComposition = new Composition();
            realLine = realComposition.getLine(0);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(ORIGINAL_POSITION_SP);
            realComposition.withoutMutationTracking(() -> realLine.addElement(note));

            when(lc.getLine()).thenReturn(realLine);
        }

        @Test
        void testBeforeCloneHasOriginalPitchAfterRelease() {
            // Press on the note, drag it to a new position, release.
            // handleRelease coalesces the mutations into one CompositionDidChangeNotification
            // carrying an ElementModification whose beforeElement reflects the pre-drag pitch.
            setupSingleSelection(0);
            pressOnNote(0);
            dragToPosition(DRAGGED_POSITION_SP);
            handler.handleRelease();

            // Capture all MessageCenter.post calls (includes the composition-constructor
            // notification and the handleRelease notification).
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(
                () -> MessageCenter.post(captor.capture()),
                atLeastOnce()
            );

            // Find the ElementModification inside whichever notification carries it.
            var modification = captor.getAllValues().stream()
                .filter(m -> m instanceof CompositionDidChangeNotification)
                .map(m -> (CompositionDidChangeNotification) m)
                .flatMap(n -> n.getMutations().stream())
                .filter(m -> m instanceof ElementModification)
                .map(m -> (ElementModification) m)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ElementModification in captured notifications"));

            // beforeElement is the clone captured at press time — original pitch.
            assertThat(modification.beforeElement().getStaffPosition()).isEqualTo(ORIGINAL_POSITION_SP);
            assertThat(modification.fields()).containsExactly(ElementField.PITCH);

            // The live element now has the dragged pitch.
            assertThat(realLine.getElement(0).getStaffPosition()).isEqualTo(DRAGGED_POSITION_SP);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a Line with crotchets at the given staff positions.
     */
    private Line createLine(int... staffPositions) {
        var line = new Line();

        for (var sp : staffPositions) {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(sp);
            line.addElement(note);
        }

        return line;
    }

    /**
     * Simulates a drag to the given staff position by mocking the coordinate
     * conversion chain and calling handleDrag.
     */
    private void dragToPosition(int targetPositionSp) {
        when(mockScaleContext.fromPixels(anyDouble())).thenReturn(5.0);
        previewMgrMock.when(
            () -> PreviewElementManager.calculateStaffPositionFromMouse(anyDouble(), anyDouble())
        ).thenReturn(targetPositionSp);

        var event = mouseEvent(lc, MouseEvent.MOUSE_DRAGGED, 100, 50, MouseEvent.BUTTON1);
        handler.handleDrag(event);
    }

    private MouseEvent mouseEvent(java.awt.Component source, int id, int x, int y, int button) {
        return new MouseEvent(source, id, 0L, 0, x, y, x, y, 1, false, button);
    }

    /**
     * Mocks the hit test to return the given index and calls handlePress.
     */
    private void pressOnNote(int hitIndex) {
        hitTestMock.when(() -> ElementHitTest.hitTestElement(any(), any())).thenReturn(hitIndex);
        var event = mouseEvent(lc, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON1);
        handler.handlePress(event);
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
