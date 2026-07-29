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

import java.util.Objects;

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
            int cancelScreenX = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(scoreView().getLineComponent(0));
                return lc.getLocationOnScreen().x + GraceModeManager.getCancelThresholdPx();
            }));
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
            var insertPt = new Point(mid.x, Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(scoreView().getLineComponent(0));
                return lc.getLocationOnScreen().y + lc.staffPositionToYPx(-2);
            })));

            robot.pressMouse(insertPt, LEFT_BUTTON);
            Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
            int connectScreenX = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(scoreView().getLineComponent(0));
                return lc.getLocationOnScreen().x + GraceModeManager.getConnectThresholdPx();
            }));
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
                () -> assertThat(isActionEnabled(Actions.GLISSANDO_ACTION)
                    && isActionEnabled(Actions.REST_ACTION))
                    .as("actions re-enabled").isTrue()
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


    // -- Grace note assertion helpers --

    private boolean isGraceModeActive() {
        return Objects.requireNonNull(GuiActionRunner.execute(GraceModeManager::isActive));
    }

    private boolean isActionEnabled(UIAction action) {
        return Objects.requireNonNull(GuiActionRunner.execute(action::isEnabled));
    }

    private boolean isActionSelected(UIAction action) {
        var selectable = (UIAction.Selectable) action;
        return Objects.requireNonNull(GuiActionRunner.execute(selectable::isSelected));
    }

}
