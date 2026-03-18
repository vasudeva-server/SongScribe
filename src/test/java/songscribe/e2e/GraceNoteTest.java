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
import org.junit.jupiter.api.Test;

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

    // Element indices for grace-note-pairs.mssw (ordinals match fixture order)
    private enum Element {
        TEMPO,
        PAIR_A_GRACE,
        PAIR_A_HOST,
        PAIR_B_GRACE,
        PAIR_B_HOST,
        PAIR_C_GRACE,
        PAIR_C_HOST,
        STANDALONE,
    }

    @Test
    void testGraceNoteOperations() throws Exception {
        loadFixture("grace-note-pairs");

        // --- Cancellation and rejection (insert at end of line) ---

        debugStep("1-6: Escape cancels + toolbar state", () -> {
            var countBefore = composition().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Verify toolbar state while in grace mode (before cancelling)
            assertAll(
                () -> assertThat(isActionEnabled(Actions.GLISSANDO_ACTION))
                    .as("1: glissando disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.REST_ACTION))
                    .as("2: rest disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.GRACE_EIGHTH_NOTE_ACTION))
                    .as("3: grace note disabled").isFalse(),
                () -> assertThat(isActionEnabled(Actions.QUARTER_NOTE_ACTION)
                    && isActionEnabled(Actions.HALF_NOTE_ACTION))
                    .as("4: durations enabled").isTrue()
            );

            robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
            pause();
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("5: escape cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("6: escape: mode inactive").isFalse()
            );
        });

        debugStep("7-8: Drag-left cancels", () -> {
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
                    .as("7: drag-left cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("8: drag-left: mode inactive").isFalse()
            );
        });

        debugStep("9-10: Same pitch error", () -> {
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
                    .as("9: same pitch: error shown").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("10: same pitch: grace removed").isFalse()
            );
        });

        // --- Insertion flows with toolbar verification (insert at end of line) ---

        debugStep("11-16: Click-click insertion", () -> {
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
                    .as("11: click-click: element count").isEqualTo(countBefore + 2),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("12: click-click: grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType().isPitchedNote())
                    .as("13: click-click: host type").isTrue(),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("14: click-click: glissando").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isGraceModeActive())
                    .as("15: click-click: grace mode inactive").isFalse(),
                () -> assertThat(isActionEnabled(Actions.GLISSANDO_ACTION)
                    && isActionEnabled(Actions.REST_ACTION))
                    .as("16: actions re-enabled").isTrue()
            );
        });

        debugStep("17-18: Click-click MIDI", () -> {
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
                    .as("17: click-click: only host NOTE_ON").hasSize(expectedNoteOns),
                () -> assertThat(bendEvents).as("18: click-click: slide-in pitch bend").isNotEmpty()
            );
        });

        debugStep("19-23: Duration change during flow", () -> {
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
                    .as("19: duration change: grace is quaver").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType())
                    .as("20: duration change: host is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("21: duration change: glissando connected").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isActionSelected(Actions.HALF_NOTE_ACTION))
                    .as("22: half note selected").isTrue(),
                () -> assertThat(isActionSelected(Actions.QUARTER_NOTE_ACTION))
                    .as("23: quarter deselected").isFalse()
            );
        });

        // --- Drag-to-connect and manipulation (fixture pairs) ---

        debugStep("24-27: Drag-connect to standalone note", () -> {
            var countBefore = composition().getLine(0).elementCount();

            enterEditMode();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click at midpoint between last pair host and standalone, at a different pitch
            var mid = midpoint(0, Element.PAIR_C_HOST.ordinal(), Element.STANDALONE.ordinal());
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
            var graceIdx = Element.STANDALONE.ordinal();

            assertAll(
                () -> assertThat(line.elementCount())
                    .as("24: drag connect: count").isEqualTo(countBefore + 1),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("25: drag connect: grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(graceIdx).getGlissando().type)
                    .as("26: drag connect: glissando").isEqualTo(StaffElement.Glissando.Type.CONNECTED),
                () -> assertThat(isGraceModeActive())
                    .as("27: drag connect: mode inactive").isFalse()
            );
        });

        debugStep("28-29: Delete host removes both (pair B)", () -> {
            var countBefore = composition().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_HOST.ordinal()));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            assertAll(
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("28: delete host removes both").isEqualTo(countBefore - 2),
                () -> assertThat(composition().getLine(0).elementCount())
                    .as("29: pair B removed from count").isEqualTo(countBefore - 2)
            );
        });

        debugStep("30-31: Delete grace note (pair C)", () -> {
            // After pair B deletion (2 elements removed), pair C indices shifted down by 2
            var pairCSrc = Element.PAIR_C_GRACE.ordinal() - 2;
            var countBefore = composition().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, pairCSrc));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // Grace note deleted, host preserved; host shifts to pairCSrc
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("30: delete grace removes grace").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(pairCSrc).getType().isPitchedNote())
                    .as("31: host preserved").isTrue()
            );
        });
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
