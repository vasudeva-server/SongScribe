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

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.edit.GraceModeManager;
import songscribe.util.Utils;

/**
 * E2E tests for element insertion mechanics, including grace note insertion,
 * cancellation, drag-connect, and deletion.
 * <p>
 * The insertion.musicxml fixture is loaded once for the entire class. Nested classes
 * are ordered so that grace note tests run first, then general insertion tests
 * append at the end of the line. FullLine uses a separate fixture.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class ElementInsertionTest extends E2ETest {

    // Element indices for insertion.musicxml.
    // Multiple constants may share the same index to name positions at different
    // points in the test (e.g., STANDALONE and PAIR_D_GRACE both map to 7 because
    // GraceNoteDragConnect inserts a grace note at STANDALONE's original position).
    private enum GraceElement {
        TEMPO(0),
        PAIR_A_GRACE(1),
        PAIR_A_HOST(2),
        PAIR_B_GRACE(3),
        PAIR_B_HOST(4),
        PAIR_C_GRACE(5),
        PAIR_C_HOST(6),
        STANDALONE(7),
        // After GraceNoteDragConnect inserts a grace note before STANDALONE
        PAIR_D_GRACE(7),
        PAIR_D_HOST(8),
        ;

        final int index;

        GraceElement(int index) {
            this.index = index;
        }
    }

    /** Elements removed by deleting pair B (grace + host). */
    private static final int AFTER_PAIR_B_DELETED = 2;

    /** Elements removed by deleting pair C grace (on top of pair B deletion). */
    private static final int AFTER_PAIR_C_GRACE_DELETED = AFTER_PAIR_B_DELETED + 1;

    @BeforeAll
    void loadInsertionFixture() {
        resetSong();
        loadFixture("insertion");
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GraceNoteCancellation {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testEscapeCancels() {
            var countBefore = song().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Verify toolbar state while in grace mode (before cancelling)
            assertAll(
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
                () -> assertThat(song().getLine(0).elementCount())
                    .as("escape cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("escape: mode inactive").isFalse()
            );
        }

        @Test
        void testDragLeftCancels() {
            var countBefore = song().getLine(0).elementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            var insertPt = insertionPoint(0, 0);

            robot.pressMouse(insertPt, LEFT_BUTTON);
            Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
            var cancelScreenX = GuiActionRunner.execute(() -> {
                var lc = scoreView().getLineComponent(0);
                assertThat(lc).isNotNull();
                return lc.getLocationOnScreen().x + GraceModeManager.getCancelThresholdPx();
            });
            assertThat(cancelScreenX).isNotNull();
            robot.moveMouse(new Point(cancelScreenX, insertPt.y));
            pause();
            robot.releaseMouseButtons();
            pause();
            performLayout(0);

            assertAll(
                () -> assertThat(song().getLine(0).elementCount())
                    .as("drag-left cancels").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("drag-left: mode inactive").isFalse()
            );
        }

        @Test
        void testSamePitchError() {
            var countBefore = song().getLine(0).elementCount();

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
                () -> assertThat(song().getLine(0).elementCount())
                    .as("same pitch: error shown").isEqualTo(countBefore),
                () -> assertThat(isGraceModeActive())
                    .as("same pitch: grace removed").isFalse()
            );
        }
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GraceNoteDragConnect {

        @Test
        void testDragConnectToStandaloneNote() {
            var countBefore = song().getLine(0).elementCount();

            enterEditMode();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click at midpoint between last pair host and standalone, at a different pitch
            var mid = midpoint(0, GraceElement.PAIR_C_HOST.index, GraceElement.STANDALONE.index);
            var insertScreenY = GuiActionRunner.execute(() -> {
                var lc = scoreView().getLineComponent(0);
                assertThat(lc).isNotNull();
                return lc.getLocationOnScreen().y + lc.staffPositionToYPx(-2);
            });
            assertThat(insertScreenY).isNotNull();

            var insertPt = new Point(mid.x, insertScreenY);

            robot.pressMouse(insertPt, LEFT_BUTTON);
            Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
            var connectScreenX = GuiActionRunner.execute(() -> {
                var lc = scoreView().getLineComponent(0);
                assertThat(lc).isNotNull();
                return lc.getLocationOnScreen().x + GraceModeManager.getConnectThresholdPx();
            });
            assertThat(connectScreenX).isNotNull();
            robot.moveMouse(new Point(connectScreenX, insertPt.y));
            pause();
            robot.releaseMouseButtons();
            pause();
            performLayout(0);

            var line = song().getLine(0);
            // The grace note was inserted before standalone, shifting it right
            var graceIdx = GraceElement.PAIR_D_GRACE.index;

            assertAll(
                () -> assertThat(line.elementCount())
                    .as("drag connect: count").isEqualTo(countBefore + 1),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("drag connect: grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(graceIdx).hasGlissando())
                    .as("drag connect: glissando").isTrue(),
                () -> assertThat(isGraceModeActive())
                    .as("drag connect: mode inactive").isFalse()
            );
        }
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(3)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GraceNoteDeletion {

        @Order(2)
        @Test
        void testDeleteHostRemovesBoth() {
            var countBefore = song().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, GraceElement.PAIR_B_HOST.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            assertAll(
                () -> assertThat(song().getLine(0).elementCount())
                    .as("delete host removes both").isEqualTo(countBefore - 2),
                () -> assertThat(song().getLine(0).elementCount())
                    .as("pair B removed from count").isEqualTo(countBefore - 2)
            );
        }

        @Order(3)
        @Test
        void testDeleteGraceNote() {
            // After pair B deletion, pair C indices shifted down
            var pairCGraceIdx = GraceElement.PAIR_C_GRACE.index - AFTER_PAIR_B_DELETED;
            var countBefore = song().getLine(0).elementCount();

            enterSelectMode();
            clickAt(noteScreenPosition(0, pairCGraceIdx));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = song().getLine(0);
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
            var pairAHostIdx = GraceElement.PAIR_A_HOST.index;
            var countBefore = song().getLine(0).elementCount();

            enterEditMode();
            enableRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);

            clickAt(noteScreenPosition(0, pairAHostIdx));
            performLayout(0);

            var line = song().getLine(0);
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


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(4)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GraceNoteInsertion {

        @Order(1)
        @Test
        void testClickClickInsertion() {
            var countBefore = song().getLine(0).effectiveElementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = song().getLine(0);
            var graceIdx = countBefore;
            var hostIdx = countBefore + 1;

            assertAll(
                () -> assertThat(line.effectiveElementCount())
                    .as("element count").isEqualTo(countBefore + 2),
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType().isPitchedNote())
                    .as("host type").isTrue(),
                () -> assertThat(line.getElement(graceIdx).hasGlissando())
                    .as("glissando").isTrue(),
                () -> assertThat(isGraceModeActive())
                    .as("grace mode inactive").isFalse(),
                () -> assertThat(isActionEnabled(Actions.REST_ACTION))
                    .as("rest re-enabled").isTrue()
            );
        }

        /**
         * The decorations chosen for the grace note must land on the grace note and must not
         * follow it onto the host. Entering grace note mode clears them before the host preview
         * is rebuilt, and that preview is the object inserted as the host — so a regression in
         * either the clearing or its ordering shows up here as a decorated host.
         */
        @Order(2)
        @Test
        void testGraceDecorationsDoNotFollowOntoTheHost() {
            var countBefore = song().getLine(0).effectiveElementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Accidentals and articulations stay enabled for the grace duration precisely so
            // the grace note can be decorated before it is placed.
            triggerAction(Actions.FLAT_ACTION);
            triggerAction(Actions.STACCATO_ACTION);

            clickAt(insertionPoint(0, 0));
            performLayout(0);

            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = song().getLine(0);
            var grace = line.getElement(countBefore);
            var host = line.getElement(countBefore + 1);

            assertAll(
                () -> assertThat(grace.getAccidental())
                    .as("grace keeps its accidental").isEqualTo(StaffElement.Accidental.FLAT),
                () -> assertThat(grace.getArticulations())
                    .as("grace keeps its articulation")
                    .extracting(Articulation::getType)
                    .containsExactly(ArticulationType.STACCATO),
                () -> assertThat(host.getAccidental())
                    .as("host has no accidental").isNull(),
                () -> assertThat(host.getArticulations())
                    .as("host has no articulations").isEmpty(),
                () -> assertThat(host.getDotCount())
                    .as("host has no dots").isEqualTo(0),
                () -> assertThat(host.isAccidentalInParentheses())
                    .as("host has no accidental parentheses").isFalse()
            );
        }

        @Order(3)
        @Test
        void testDurationChangeDuringFlow() {
            var countBefore = song().getLine(0).effectiveElementCount();

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Change duration to half note during grace mode
            selectDuration(Actions.HALF_NOTE_ACTION);

            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = song().getLine(0);
            var graceIdx = countBefore;
            var hostIdx = countBefore + 1;

            assertAll(
                () -> assertThat(line.getElement(graceIdx).getType())
                    .as("grace is quaver").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIdx).getType())
                    .as("host is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(line.getElement(graceIdx).hasGlissando())
                    .as("glissando connected").isTrue(),
                () -> assertThat(isActionSelected(Actions.HALF_NOTE_ACTION))
                    .as("half note selected").isTrue(),
                () -> assertThat(isActionSelected(Actions.QUARTER_NOTE_ACTION))
                    .as("quarter deselected").isFalse()
            );
        }

        /**
         * A breath mark sitting immediately after the grace note occupies the slot the host
         * belongs in. The host's locked x sits a fixed gap past the grace note, which lands
         * past the breath mark — so an insertion driven by that x would strand the breath
         * mark between the pair, with the grace note's glissando pointing at it instead of
         * at the host. The host slot therefore comes from the pairing, not from the x.
         * <p>
         * This is the only test that runs the real spacing arithmetic for that arrangement;
         * the unit test in {@code PreviewElementManagerTrackMouseTest} mocks the layout and
         * so covers only which of the two sources the insertion index is read from.
         */
        @Order(4)
        @Test
        void testHostGoesBeforeABreathMarkTrailingTheGraceNote() {
            var countBefore = song().getLine(0).effectiveElementCount();

            // Append a note and attach a breath mark to it, so the breath mark ends the line.
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            clickAction(Actions.BREATH_MARK_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            var noteIndex = countBefore;
            var breathMarkIndexBeforeGrace = noteIndex + 1;

            // Drop the grace note between the note and its breath mark, leaving the breath
            // mark trailing the grace note — the arrangement that used to misplace the host.
            //
            // The grace note must land on the middle line, and the click's staff position is
            // what puts it there. The grace→breath gap does not charge the grace's flag when
            // the flag hangs clear of the breath mark's ink band (getRightExtentFacingSs),
            // while the locked x always charges the grace's full extent — so with the grace
            // low enough the breath mark packs closer than the locked x, which then resolves
            // past it. Place the grace up at the breath mark's own height instead and the flag
            // is charged in the gap too, putting the breath mark at exactly the locked x: both
            // index sources then agree and this test passes with or without the fix.
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPointBefore(0, breathMarkIndexBeforeGrace, 0));
            performLayout(0);

            // The second click places the host. Its x is ignored — grace mode locks it — so
            // only the staff position, and hence the host's pitch, comes from this point.
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var line = song().getLine(0);
            var graceIndex = noteIndex + 1;
            var hostIndex = graceIndex + 1;
            var breathMarkIndex = hostIndex + 1;

            assertAll(
                () -> assertThat(line.effectiveElementCount())
                    .as("the note, its breath mark and the grace/host pair are all present")
                    .isEqualTo(breathMarkIndex + 1),
                () -> assertThat(line.getElement(graceIndex).getType())
                    .as("grace type").isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(hostIndex).getType().isPitchedNote())
                    .as("the host sits immediately after the grace note, before the breath mark")
                    .isTrue(),
                () -> assertThat(line.getElement(breathMarkIndex).getType())
                    .as("the breath mark is pushed past the host rather than trapped inside the pair")
                    .isEqualTo(ElementType.BREATH_MARK),
                () -> assertThat(line.getElement(graceIndex).hasGlissando())
                    .as("the grace note's glissando connects it to the host").isTrue(),
                () -> assertThat(isGraceModeActive())
                    .as("grace mode inactive").isFalse()
            );
        }

        /**
         * Regression test for the ghost-preview and click suppression between a paired grace
         * note and its host (issue #750): no element type may be inserted in that gap, but
         * inserting immediately before the pair and replacing the host directly must both keep
         * working.
         */
        @Order(5)
        @Test
        void testInsertionBetweenGraceAndHostIsBlocked() {
            var graceIndex = song().getLine(0).effectiveElementCount();
            var hostIndex = graceIndex + 1;

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.HALF_NOTE_ACTION);
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            var countAfterPair = song().getLine(0).effectiveElementCount();

            // A click in the gap between the grace note and its host, with an ordinary
            // (non-grace) preview element selected, must be ignored entirely.
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPointBefore(0, hostIndex, 0));
            performLayout(0);

            assertAll(
                () -> assertThat(song().getLine(0).effectiveElementCount())
                    .as("a click between the grace note and its host is ignored")
                    .isEqualTo(countAfterPair),
                () -> assertThat(song().getLine(0).getElement(hostIndex).getType())
                    .as("the host is untouched by the ignored click")
                    .isEqualTo(ElementType.MINIM)
            );

            // Inserting immediately before the paired grace note is still allowed.
            clickAt(insertionPointBefore(0, graceIndex, 0));
            performLayout(0);

            var line = song().getLine(0);
            var shiftedGraceIndex = graceIndex + 1;
            var shiftedHostIndex = hostIndex + 1;

            assertAll(
                () -> assertThat(line.effectiveElementCount())
                    .as("insertion before the pair succeeds")
                    .isEqualTo(countAfterPair + 1),
                () -> assertThat(line.getElement(graceIndex).getType())
                    .as("the new note lands before the grace note")
                    .isEqualTo(ElementType.CROTCHET),
                () -> assertThat(line.getElement(shiftedGraceIndex).getType())
                    .as("the grace note is pushed one slot later")
                    .isEqualTo(ElementType.GRACE_QUAVER),
                () -> assertThat(line.getElement(shiftedGraceIndex).hasGlissando())
                    .as("the pairing survives the insertion before it")
                    .isTrue(),
                () -> assertThat(line.getElement(shiftedHostIndex).getType())
                    .as("the host is undisturbed")
                    .isEqualTo(ElementType.MINIM)
            );

            // Clicking directly on the host's head still replaces it normally.
            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            clickAt(noteScreenPosition(0, shiftedHostIndex));
            performLayout(0);

            assertAll(
                () -> assertThat(song().getLine(0).effectiveElementCount())
                    .as("replacing the host does not change the element count")
                    .isEqualTo(countAfterPair + 1),
                () -> assertThat(song().getLine(0).getElement(shiftedHostIndex).getType())
                    .as("the host was replaced with the selected duration")
                    .isEqualTo(ElementType.QUAVER)
            );
        }
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(5)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InsertElementTypes {

        /** Index of the first element inserted by this class (after all grace note tests). */
        private int baseIndex;

        @BeforeAll
        void setUp() {
            baseIndex = song().getLine(0).effectiveElementCount();

            buildNotes(Actions.QUARTER_NOTE_ACTION, -4);
            buildNotes(Actions.HALF_NOTE_ACTION, 2);
            buildNotes(Actions.EIGHTH_NOTE_ACTION, -2, -6);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
        }

        @Order(5)
        @Test
        void testClickWithRestSelectedReplacesWithRest() {
            var countBefore = song().getLine(0).elementCount();

            enableRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);

            clickAt(noteScreenPosition(0, baseIndex));
            performLayout(0);

            var line = song().getLine(0);
            assertAll(
                () -> assertThat(line.getElement(baseIndex).getType().isRest())
                    .as("replaced with rest").isTrue(),
                () -> assertThat(line.elementCount())
                    .as("count unchanged").isEqualTo(countBefore),
                () -> assertThat(scoreView().getMode())
                    .as("mode stays EDIT").isEqualTo(Mode.EDIT)
            );

            deselectRestMode();
        }

    }


    /**
     * The {@code b} key that beams the last placed note with its predecessor.
     * <p>
     * These are e2e tests because the risk is the integration itself: {@code b} is the
     * accelerator of {@link Actions#TOGGLE_BEAM_ACTION}, which is registered on the main
     * frame's root pane and stays <em>disabled</em> in edit mode. The key only reaches the
     * score because {@code ScoreInputHandler} binds it {@code WHEN_FOCUSED} on the score
     * view, which resolves ahead of the root-pane binding regardless of any action's enabled
     * state. Nothing below the real Swing dispatch can demonstrate that.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(6)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BeamKey {

        /** Staff position of the notes these tests place, on the middle staff line. */
        private static final int BEAM_NOTE_STAFF_POSITION = 0;

        private static final String LYRIC_SYLLABLE = "la";

        /**
         * Reloads the fixture so each test starts from a known, non-empty line. Non-empty
         * matters: beaming needs a predecessor note already in place to beam the newly
         * placed note with.
         */
        @BeforeEach
        void reloadFixture() {
            resetSong();
            loadFixture("insertion");
        }

        @Test
        void testBeamKeyTogglesTheBeamOnTheLastTwoPlacedNotes() {
            var firstIndex = song().getLine(0).effectiveElementCount();
            var secondIndex = firstIndex + 1;

            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, BEAM_NOTE_STAFF_POSITION));
            performLayout(0);
            clickAt(insertionPoint(0, BEAM_NOTE_STAFF_POSITION));
            performLayout(0);

            pressKey(KeyEvent.VK_B, 0);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, firstIndex))
                    .as("first quaver beamed").isTrue(),
                () -> assertThat(isBeamed(0, secondIndex))
                    .as("second quaver beamed").isTrue(),
                // The beam action stays disabled in edit mode — which is exactly why its
                // root-pane accelerator cannot carry the key and the WHEN_FOCUSED binding
                // on the score view has to.
                () -> assertThat(isActionEnabled(Actions.TOGGLE_BEAM_ACTION))
                    .as("beam action disabled in edit mode").isFalse()
            );

            pressKey(KeyEvent.VK_B, 0);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, firstIndex))
                    .as("first quaver unbeamed").isFalse(),
                () -> assertThat(isBeamed(0, secondIndex))
                    .as("second quaver unbeamed").isFalse()
            );
        }

        /**
         * A click on a line takes focus back from the lyric editor, so {@code b} beams
         * instead of being typed into the lyric.
         * <p>
         * This covers the {@code requestFocusInWindow()} call at the top of
         * {@code LineComponent.mousePressed}. Without it the editor keeps focus, the score's
         * key bindings stay disabled for the duration of the text editing, and the key press
         * lands in the lyric — a failure with no visible symptom other than the missing beam.
         */
        @Test
        void testBeamKeyWorksAfterTypingInTheLyricEditor() {
            var firstIndex = song().getLine(0).effectiveElementCount();
            var secondIndex = firstIndex + 1;

            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, BEAM_NOTE_STAFF_POSITION));
            performLayout(0);

            openLyricEditorOn(firstIndex);
            typeIntoLyricEditor();

            // Placing the second note is also the click that must hand focus back.
            clickAt(insertionPoint(0, BEAM_NOTE_STAFF_POSITION));
            performLayout(0);

            pressKey(KeyEvent.VK_B, 0);
            performLayout(0);

            var lyrics = song().getLine(0).getElement(firstIndex).getLyrics();

            assertAll(
                () -> assertThat(isBeamed(0, firstIndex))
                    .as("first quaver beamed").isTrue(),
                () -> assertThat(isBeamed(0, secondIndex))
                    .as("second quaver beamed").isTrue(),
                () -> assertThat(lyrics)
                    .as("one syllable committed").hasSize(1),
                () -> assertThat(lyrics.getFirst().text())
                    .as("the b did not land in the lyric").isEqualTo(LYRIC_SYLLABLE)
            );
        }

        private void openLyricEditorOn(int elementIndex) {
            GuiActionRunner.execute(() ->
                LyricEditor.deselectAndOpenOn(scoreView(), song().getLine(0), elementIndex));
            // The editor defers its focus request, so let that run before typing.
            pause();
        }

        /**
         * Sets the editor's text rather than driving the robot, for the same reason
         * {@code pressKey} dispatches synthetic events: the robot maps characters to
         * physical keys, which types the wrong letters on a non-QWERTY layout. What the
         * test needs from this step is only that the editor holds focus and has text in it.
         */
        private void typeIntoLyricEditor() {
            var editor = GuiActionRunner.execute(() -> scoreView().getActiveLyricEditor());

            assertThat(editor).as("the lyric editor did not open").isNotNull();

            GuiActionRunner.execute(() -> editor.setText(LYRIC_SYLLABLE));
            pause();
        }
    }


    // -- Grace note assertion helpers --

    private boolean isGraceModeActive() {
        var active = GuiActionRunner.execute(GraceModeManager::isActive);
        assertThat(active).isNotNull();

        return active;
    }

    private boolean isActionEnabled(UIAction action) {
        var enabled = GuiActionRunner.execute(action::isEnabled);
        assertThat(enabled).isNotNull();

        return enabled;
    }

    private boolean isActionSelected(UIAction action) {
        var selectable = (UIAction.Selectable) action;
        var selected = GuiActionRunner.execute(selectable::isSelected);
        assertThat(selected).isNotNull();

        return selected;
    }

}
