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
import static org.junit.jupiter.api.Assertions.assertAll;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.assertj.swing.edt.GuiActionRunner;
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

import songscribe.midi.GlissandoMidiHelper;
import songscribe.midi.PlaybackSettings;
import songscribe.music.Composition;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;

/**
 * Consolidated E2E test for beaming, stem direction, ties, and glissando.
 * Replaces BeamingTest, TieTest (minus pitch validation and drag),
 * and GlissandoTest.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class NoteConnectionTest extends E2ETest {

    @BeforeAll
    void loadConnectionsFixture() throws Exception {
        resetComposition();
        loadFixture("connections");
    }

    // Element indices for connections.mssw.
    // After PAIR_E_SRC is deleted, subsequent elements shift down by 1;
    // the post-deletion entries capture these shifted positions.
    private enum Element {
        TEMPO(0),
        EIGHTH_1(1),
        EIGHTH_2(2),
        TIED_1(3),
        TIED_2(4),
        PAIR_A_SRC(5),
        PAIR_A_TGT(6),
        PAIR_B_SRC(7),
        PAIR_B_TGT(8),
        PAIR_C_SRC(9),
        PAIR_C_TGT(10),
        PAIR_D_SRC(11),
        PAIR_D_TGT(12),
        PAIR_E_SRC(13),
        PAIR_E_TGT(14),
        PAIR_F_SRC(15),
        PAIR_F_TGT(16),
        SLIDE_OUT(17),
        // After PAIR_E_SRC deleted — remaining elements shift down by 1
        PAIR_E_TGT_SHIFTED(13),
        PAIR_F_SRC_SHIFTED(14),
        PAIR_F_TGT_SHIFTED(15),
        ;

        final int index;

        Element(int index) {
            this.index = index;
        }
    }


    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GlissandoSelection {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testClickSelectGlissando() {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_B_SRC.index, Element.PAIR_B_TGT.index));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertAll(
                () -> assertThat(Objects.requireNonNull(lss).hasGlissandoSelection())
                    .as("glissando selected by click").isTrue(),
                () -> assertThat(Objects.requireNonNull(lss).getSelectedGlissandoElementIndex())
                    .as("correct element index").isEqualTo(Element.PAIR_B_SRC.index)
            );
        }

        @Test
        void testSelectSourceNote() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_SRC.index));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_SRC.index))
                .as("source note selected").isTrue();

            var note = composition().getLine(0).getElement(Element.PAIR_B_SRC.index);
            assertThat(note.getGlissando()).as("source has glissando").isNotNull();
        }

        @Test
        void testSelectTargetNote() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_TGT.index));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_TGT.index))
                .as("target note selected").isTrue();

            var sourceNote = composition().getLine(0).getElement(Element.PAIR_B_SRC.index);
            assertThat(Objects.requireNonNull(sourceNote.getGlissando()).type)
                .as("source has connected glissando pointing to target")
                .isEqualTo(StaffElement.Glissando.Type.CONNECTED);
        }

        @Test
        void testNoPitchBendWithoutGlissando() {
            var note = composition().getLine(0).getElement(Element.PAIR_A_SRC.index);
            assertThat(note.getGlissando()).as("pair A source has no glissando").isNull();
        }
    }


    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Beaming {

        private boolean originalUpper;

        @Order(1)
        @Test
        void testToggleBeamOn() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.EIGHTH_1.index));
            shiftClickAt(noteScreenPosition(0, Element.EIGHTH_2.index));

            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, Element.EIGHTH_1.index)).as("note 1 beamed").isTrue(),
                () -> assertThat(isBeamed(0, Element.EIGHTH_2.index)).as("note 2 beamed").isTrue()
            );
        }

        @Order(2)
        @Test
        void testFlipStemWhileBeamed() {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.index);
            originalUpper = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertAll(
                () -> assertThat(note.isStemDirectionAuto())
                    .as("stem direction not auto").isFalse(),
                () -> assertThat(note.isUpper())
                    .as("isUpper changed").isNotEqualTo(originalUpper)
            );
        }

        @Order(3)
        @Test
        void testFlipStemBackWhileBeamed() {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.index);

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isUpper())
                .as("isUpper restored").isEqualTo(originalUpper);
        }

        @Order(4)
        @Test
        void testToggleBeamOff() {
            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, Element.EIGHTH_1.index)).as("note 1 unbeamed").isFalse(),
                () -> assertThat(isBeamed(0, Element.EIGHTH_2.index)).as("note 2 unbeamed").isFalse()
            );
        }

        @Order(5)
        @Test
        void testFlipStemUnbeamedWithPersistence() {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.index);
            var upperBefore = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isStemDirectionAuto()).as("stem direction not auto").isFalse();
            assertThat(note.isUpper()).as("isUpper changed").isNotEqualTo(upperBefore);

            var flippedUpper = note.isUpper();
            var reloaded = roundTripOnEdt();
            var reloadedNote = reloaded.getLine(0).getElement(Element.EIGHTH_1.index);

            assertAll(
                () -> assertThat(reloadedNote.isStemDirectionAuto())
                    .as("save/load: not auto").isFalse(),
                () -> assertThat(reloadedNote.isUpper())
                    .as("save/load: isUpper preserved").isEqualTo(flippedUpper)
            );
        }
    }


    @Nested
    @Order(3)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Ties {

        @Order(1)
        @Test
        void testTiePersistsThroughSaveLoad() {
            var line = composition().getLine(0);
            var tie = line.getTies().findInterval(Element.TIED_1.index);
            assertThat(tie).as("pre-tied pair exists").isNotNull();

            var reloaded = roundTripOnEdt();
            var reloadedLine = reloaded.getLine(0);
            var reloadedTie = reloadedLine.getTies().findInterval(Element.TIED_1.index);
            assertAll(
                () -> assertThat(reloadedTie).as("save/load: tie preserved").isNotNull(),
                () -> assertThat(Objects.requireNonNull(reloadedTie).getStart()).as("tie start").isEqualTo(Element.TIED_1.index),
                () -> assertThat(Objects.requireNonNull(reloadedTie).getEnd()).as("tie end").isEqualTo(Element.TIED_2.index)
            );
        }

        @Order(2)
        @Test
        void testTieCreation() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.EIGHTH_1.index));
            shiftClickAt(noteScreenPosition(0, Element.EIGHTH_2.index));

            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertAll(
                () -> assertThat(isTied(0, Element.EIGHTH_1.index)).as("note 1 tied").isTrue(),
                () -> assertThat(isTied(0, Element.EIGHTH_2.index)).as("note 2 tied").isTrue(),
                () -> assertThat(Objects.requireNonNull(lss).canToggleTie()).as("can toggle tie").isTrue()
            );
        }

        @Order(3)
        @Test
        void testFlipStemWhileTied() {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.index);
            var upperBefore = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isUpper()).as("stem flipped while tied").isNotEqualTo(upperBefore);
        }

        @Order(4)
        @Test
        void testTieRemoval() {
            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isTied(0, Element.EIGHTH_1.index)).as("note 1 untied").isFalse(),
                () -> assertThat(isTied(0, Element.EIGHTH_2.index)).as("note 2 untied").isFalse()
            );
        }
    }


    @Nested
    @Order(4)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GlissandoInsertion {

        @Order(1)
        @Test
        void testInsertConnectedGlissando() {
            enterEditMode();
            selectDuration(Actions.GLISSANDO_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_SRC.index, Element.PAIR_A_TGT.index));
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_A_SRC.index);
            var glissando = note.getGlissando();
            assertAll(
                () -> assertThat(glissando).as("has glissando").isNotNull(),
                () -> assertThat(Objects.requireNonNull(glissando).type)
                    .as("type is CONNECTED").isEqualTo(StaffElement.Glissando.Type.CONNECTED)
            );
        }

        @Order(2)
        @Test
        void testConnectedGlissandoMidi() throws Exception {
            var track = buildMidiTrack();
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            assertAll(
                () -> assertThat(bendEvents).as("pitch bend present").isNotEmpty(),
                () -> assertThat(ccEvents).as("CC events present").hasSizeGreaterThanOrEqualTo(4),
                () -> {
                    var controllers = ccEvents.stream()
                        .map(e -> ((ShortMessage) e.getMessage()).getData1())
                        .toList();
                    assertThat(controllers.subList(0, 4))
                        .as("RPN 0 sequence")
                        .containsExactly(101, 100, 6, 38);
                }
            );
        }

        @Order(3)
        @Test
        void testInsertSlideOut() {
            selectDuration(Actions.SLIDE_OUT_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_TGT.index, Element.PAIR_B_SRC.index));
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_A_TGT.index);
            var glissando = note.getGlissando();
            assertAll(
                () -> assertThat(glissando).as("has glissando").isNotNull(),
                () -> assertThat(Objects.requireNonNull(glissando).type)
                    .as("type is SLIDE_OUT").isEqualTo(StaffElement.Glissando.Type.SLIDE_OUT)
            );
        }

        @Order(4)
        @Test
        void testSlideOutMidi() throws Exception {
            var track = buildMidiTrack();
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            // The track contains CC 6 events from multiple glissandos (connected + slide-out).
            // Verify that at least one CC 6 event has the slide-out sensitivity value.
            var hasSlideOutSensitivity = ccEvents.stream()
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 6)
                .anyMatch(e -> ((ShortMessage) e.getMessage()).getData2()
                    == GlissandoMidiHelper.SLIDE_OUT_SEMITONES);

            assertAll(
                () -> assertThat(bendEvents).as("slide-out pitch bend present").isNotEmpty(),
                () -> assertThat(hasSlideOutSensitivity)
                    .as("RPN sensitivity includes slide-out semitones").isTrue()
            );
        }

        @Order(5)
        @Test
        void testGlissandoPersistence() {
            var originalNote = composition().getLine(0).getElement(Element.PAIR_A_SRC.index);
            var originalType = Objects.requireNonNull(originalNote.getGlissando()).type;

            var reloaded = roundTripOnEdt();
            var reloadedNote = reloaded.getLine(0).getElement(Element.PAIR_A_SRC.index);
            var reloadedGlissando = reloadedNote.getGlissando();
            assertAll(
                () -> assertThat(reloadedGlissando).as("save/load: glissando preserved").isNotNull(),
                () -> assertThat(Objects.requireNonNull(reloadedGlissando).type)
                    .as("save/load: glissando type preserved").isEqualTo(originalType)
            );
        }
    }


    @Nested
    @Order(5)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GlissandoDeletion {

        @Order(1)
        @Test
        void testDragToUnisonRemovesGlissando() {
            enterSelectMode();
            var targetSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Element.PAIR_C_TGT.index).getStaffPosition()
            ));

            dragNote(0, Element.PAIR_C_SRC.index, targetSp);
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_C_SRC.index);
            assertThat(note.getGlissando()).as("glissando removed on unison").isNull();
        }

        @Order(2)
        @Test
        void testDeleteSelectedGlissando() {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_D_SRC.index, Element.PAIR_D_TGT.index));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).hasGlissandoSelection()).as("glissando selected").isTrue();

            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_D_SRC.index);
            assertThat(note.getGlissando()).as("delete selected glissando").isNull();
        }

        @Order(3)
        @Test
        void testDeleteSourceNoteRemovesGlissando() {
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_E_SRC.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // After deleting PAIR_E_SRC: former pair E target shifts down by 1
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_E_TGT_SHIFTED.index).getGlissando())
                    .as("remaining note has no glissando").isNull()
            );
        }

        @Order(4)
        @Test
        void testDeleteTargetNoteRemovesGlissando() {
            // After previous deletion: pair F source and target each shifted down
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_F_TGT_SHIFTED.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // Pair F source should have glissando removed (target deleted)
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_F_SRC_SHIFTED.index).getGlissando())
                    .as("source glissando removed").isNull()
            );
        }
    }


    // -- Round-trip helper --

    /**
     * Wraps {@link #roundTrip} on the EDT. The connections fixture has a key
     * signature, and deserializing it triggers message bus events that update
     * UI actions, which must happen on the EDT.
     */
    private Composition roundTripOnEdt() {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> {
            try {
                return roundTrip(composition());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    // -- Coordinate helpers --

    /**
     * Returns the screen midpoint between two elements on a line,
     * suitable for clicking on a glissando line or inserting a glissando.
     */
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
