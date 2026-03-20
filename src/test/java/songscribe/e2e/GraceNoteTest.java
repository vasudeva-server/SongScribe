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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;
import static org.junit.jupiter.api.Assertions.assertAll;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import songscribe.midi.PlaybackSettings;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.edit.GraceModeManager;
import songscribe.util.Utils;

/**
 * Consolidated E2E test for the grace note insertion flow and manipulation.
 * Replaces the old GraceNoteTest (minus "no room" edge case, which moved
 * to ElementInsertionTest).
 */
class GraceNoteTest extends E2ETest {

    // Element indices for grace-note-pairs.mssw.
    // Multiple constants may share the same index to name positions at different
    // points in the test (e.g., STANDALONE and PAIR_D_GRACE both map to 7 because
    // step 7 inserts a grace note at STANDALONE's original position).
    private enum Element {
        TEMPO(0),
        PAIR_A_GRACE(1),
        PAIR_A_HOST(2),
        PAIR_B_GRACE(3),
        PAIR_B_HOST(4),
        PAIR_C_GRACE(5),
        PAIR_C_HOST(6),
        STANDALONE(7),
        // After step 7 inserts a grace note before STANDALONE via drag-connect
        PAIR_D_GRACE(7),
        PAIR_D_HOST(8),
        ;

        final int index;

        Element(int index) {
            this.index = index;
        }
    }

    /** Elements removed by deleting pair B (grace + host). */
    private static final int AFTER_PAIR_B_DELETED = 2;

    /** Elements removed by deleting pair C grace (on top of pair B deletion). */
    private static final int AFTER_PAIR_C_GRACE_DELETED = AFTER_PAIR_B_DELETED + 1;

    /** No elements removed — Order(1) replaces pair A host with a pitched note. */


    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Cancellation {

        @BeforeAll
        void setUp() throws Exception {
            resetComposition();
            loadFixture("grace-note-pairs");
        }

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testEscapeCancels() {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Verify toolbar state while in grace mode (before cancelling)
            assertAll(
                () -> assertThat(isActionEnabled(Actions.GLISSANDO_ACTION))
                    .as("glissando disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.REST_ACTION))
                    .as("rest disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.GRACE_EIGHTH_NOTE_ACTION))
                    .as("grace note disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.QUARTER_NOTE_ACTION)
                    && isActionEnabled(Actions.HALF_NOTE_ACTION))
                    .as("durations enabled").isTrue()
            );

            robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
            pause();
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("escape cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("escape: mode inactive").isFalse()
            );
        }

        @Test
        void testDragLeftCancels() {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            var insertPt = insertionPoint(0, 0);

            robot.pressMouse(insertPt, LEFT_BUTTON);
            Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
            int cancelScreenX = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                return lc.getLocationOnScreen().x + GraceModeManager.getCancelThresholdPx();
            }));
            robot.moveMouse(new Point(cancelScreenX, insertPt.y));
            pause();
            robot.releaseMouseButtons();
            pause();
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("drag-left cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("drag-left: mode inactive").isFalse()
            );
        }

        @Test
        void testSamePitchError() {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Click at the same pitch to trigger same-pitch error
            clickAt(insertionPoint(0, 0));

            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.requireErrorMessage();
            optionPane.okButton().click();
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("same pitch: error shown").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("same pitch: grace removed").isFalse()
            );
        }
    }


    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Insertion {

        @BeforeAll
        void setUp() throws Exception {
            resetComposition();
            loadFixture("grace-note-pairs");
        }

        @Order(1)
        @Test
        void testClickClickInsertion() {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = composition().getLine(0);
            var graceIdx = countBefore;
            var hostIdx = countBefore + 1;

            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count").isEqualTo(countBefore + 2),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType().isPitchedNote())
                    .as("host type").isTrue(),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("glissando").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isGraceModeActive())
                    .as("grace mode inactive").isFalse(),
                () -> assertThat(isActionEnabled(Actions.GLISSANDO_ACTION)
                    && isActionEnabled(Actions.REST_ACTION))
                    .as("actions re-enabled").isTrue()
            );
        }

        @Order(2)
        @Test
        void testClickClickMidi() throws Exception {
            var line = composition().getLine(0);

            // Count non-grace pitched notes — only these should produce NOTE_ONs
            var pitchedNonGraceCount = 0;

            for (var i = 0; i < line.elementCount(); i++) {
                var type = line.getElement(i).getType();

                if (type.isPitchedNote() && !type.isGraceNote()) {
                    pitchedNonGraceCount++;
                }
            }

            var track = buildMidiTrack();
            var noteOnEvents = getEventsByCommand(track, ShortMessage.NOTE_ON);
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

            var expectedNoteOns = pitchedNonGraceCount;
            assertAll(
                () -> assertThat(noteOnEvents)
                    .as("only host NOTE_ON").hasSize(expectedNoteOns),
                () -> assertThat(bendEvents).as("slide-in pitch bend").isNotEmpty()
            );
        }

        @Order(3)
        @Test
        void testDurationChangeDuringFlow() {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Change duration to half note during grace mode
            selectDuration(Actions.HALF_NOTE_ACTION);

            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = composition().getLine(0);
            var graceIdx = countBefore;
            var hostIdx = countBefore + 1;

            assertAll(
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("grace is quaver").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType())
                    .as("host is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("glissando connected").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isActionSelected(Actions.HALF_NOTE_ACTION))
                    .as("half note selected").isTrue(),
                () -> assertThat(isActionSelected(Actions.QUARTER_NOTE_ACTION))
                    .as("quarter deselected").isFalse()
            );
        }
    }


    @Nested
    class DragConnect {

        @BeforeEach
        void setUp() throws Exception {
            resetComposition();
            loadFixture("grace-note-pairs");
        }

        @Test
        void testDragConnectToStandaloneNote() {
            var countBefore = composition().getLine(0).elementCount();

            enterEditMode();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click at midpoint between last pair host and standalone, at a different pitch
            var mid = midpoint(0, Element.PAIR_C_HOST.index, Element.STANDALONE.index);
            var insertPt = new Point(mid.x, Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                return lc.getLocationOnScreen().y + lc.staffPositionToYPx(-2);
            })));

            robot.pressMouse(insertPt, LEFT_BUTTON);
            Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
            int connectScreenX = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                return lc.getLocationOnScreen().x + GraceModeManager.getConnectThresholdPx();
            }));
            robot.moveMouse(new Point(connectScreenX, insertPt.y));
            pause();
            robot.releaseMouseButtons();
            pause();
            performLayout(0);

            var line = composition().getLine(0);
            // The grace note was inserted before standalone, shifting it right
            var graceIdx = Element.PAIR_D_GRACE.index;

            assertAll(
                () -> assertThat(line.elementCount())
                    .as("drag connect: count").isEqualTo(countBefore + 1),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("drag connect: grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("drag connect: glissando").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isGraceModeActive())
                    .as("drag connect: mode inactive").isFalse()
            );
        }
    }


    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Deletion {

        @BeforeAll
        void setUp() throws Exception {
            resetComposition();
            loadFixture("grace-note-pairs");
        }

        @Order(1)
        @Test
        void testReplaceHostWithPitchedNotePreservesGlissando() {
            var pairAGraceIdx = Element.PAIR_A_GRACE.index;
            var pairAHostIdx = Element.PAIR_A_HOST.index;
            var countBefore = composition().getLine(0).elementCount();

            enterEditMode();
            selectDuration(Actions.HALF_NOTE_ACTION);

            // Click on pair A host at a different pitch
            var existingPos = noteScreenPosition(0, pairAHostIdx);
            var replacePoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(existingPos.x, loc.y + lc.staffPositionToYPx(-4));
            }));

            clickAt(replacePoint);
            performLayout(0);

            var line = composition().getLine(0);
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count unchanged").isEqualTo(countBefore),
                () -> assertThat(line.getElement(pairAHostIdx).getType())
                    .as("host replaced with half note").isEqualTo(ElementType.MINIM),
                () -> assertThat(line.getElement(pairAGraceIdx).getGlissando().type)
                    .as("glissando still connected").isEqualTo(StaffElement.Glissando.Type.CONNECTED)
            );
        }

        @Order(2)
        @Test
        void testDeleteHostRemovesBoth() {
            var countBefore = composition().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_HOST.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("delete host removes both").isEqualTo(countBefore - 2),
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("pair B removed from count").isEqualTo(countBefore - 2)
            );
        }

        @Order(3)
        @Test
        void testDeleteGraceNote() {
            // After pair B deletion, pair C indices shifted down
            var pairCGraceIdx = Element.PAIR_C_GRACE.index - AFTER_PAIR_B_DELETED;
            var countBefore = composition().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, pairCGraceIdx));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // Grace note deleted, host preserved; host shifts to pairCGraceIdx
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("delete grace removes grace").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(pairCGraceIdx).getType().isPitchedNote())
                    .as("host preserved").isTrue()
            );
        }

        @Order(4)
        @Test
        void testReplaceHostWithRestRemovesGrace() {
            // After steps above: pair B deleted (-2), pair C grace deleted (-1) = -3 total shift
            var pairAHostIdx = Element.PAIR_A_HOST.index;
            var countBefore = composition().getLine(0).elementCount();

            enterEditMode();
            enableRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);

            clickAt(noteScreenPosition(0, pairAHostIdx));
            performLayout(0);

            var line = composition().getLine(0);
            // Grace note removed, rest now occupies the position where grace was
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("grace note removed, count decreased by 1").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(pairAHostIdx - 1).getType().isRest())
                    .as("host replaced with rest").isTrue()
            );

            deselectRestMode();
        }

    }


    // -- Assertion helpers --

    private boolean isGraceModeActive() {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> GraceModeManager.isActive()));
    }

    private boolean isActionEnabled(UIAction action) {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> action.isEnabled()));
    }

    private boolean isActionSelected(UIAction action) {
        var selectable = (UIAction.Selectable) action;
        return Objects.requireNonNull(GuiActionRunner.execute(() -> selectable.isSelected()));
    }

    // -- Coordinate helpers --

    private Point midpoint(int lineIndex, int index1, int index2) {
        var p1 = noteScreenPosition(lineIndex, index1);
        var p2 = noteScreenPosition(lineIndex, index2);
        return new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
    }

    // -- MIDI helpers --

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false, false
    );

    private Track buildMidiTrack() throws Exception {
        var line = composition().getLine(0);
        var tempo = Objects.requireNonNull(line.getElement(0).getTempoChange());
        var sequence = new Sequence(Sequence.PPQ, 96);
        var track = sequence.createTrack();
        line.addToTrack(track, 0, 0, tempo, DEFAULT_SETTINGS);
        return track;
    }

    private static List<MidiEvent> getEventsByCommand(Track track, int command) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                events.add(event);
            }
        }

        return events;
    }
}
