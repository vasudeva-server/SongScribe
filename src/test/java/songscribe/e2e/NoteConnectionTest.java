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
import org.junit.jupiter.api.Test;

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
class NoteConnectionTest extends E2ETest {

    // Element indices for connections.mssw (ordinals match fixture order)
    private enum Element {
        TEMPO,
        EIGHTH_1,
        EIGHTH_2,
        TIED_1,
        TIED_2,
        PAIR_A_SRC,
        PAIR_A_TGT,
        PAIR_B_SRC,
        PAIR_B_TGT,
        PAIR_C_SRC,
        PAIR_C_TGT,
        PAIR_D_SRC,
        PAIR_D_TGT,
        PAIR_E_SRC,
        PAIR_E_TGT,
        PAIR_F_SRC,
        PAIR_F_TGT,
        SLIDE_OUT,
    }

    @Test
    void testNoteConnectionOperations() throws Exception {
        loadFixture("connections");

        // --- Non-mutating: Glissando selection and MIDI ---

        debugStep("1-2: Click-select glissando", () -> {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_B_SRC.ordinal(), Element.PAIR_B_TGT.ordinal()));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertAll(
                () -> assertThat(Objects.requireNonNull(lss).hasGlissandoSelection())
                    .as("1: glissando selected by click").isTrue(),
                () -> assertThat(Objects.requireNonNull(lss).getSelectedGlissandoElementIndex())
                    .as("2: correct element index").isEqualTo(Element.PAIR_B_SRC.ordinal())
            );
        });

        debugStep("3: Source note selection", () -> {
            clickAt(noteScreenPosition(0, Element.PAIR_B_SRC.ordinal()));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_SRC.ordinal()))
                .as("3: source note selected").isTrue();

            var note = composition().getLine(0).getElement(Element.PAIR_B_SRC.ordinal());
            //noinspection ObjectEquality
            assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO)
                .as("3: source has glissando").isTrue();
        });

        debugStep("4: Target note selection", () -> {
            clickAt(noteScreenPosition(0, Element.PAIR_B_TGT.ordinal()));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_TGT.ordinal()))
                .as("4: target note selected").isTrue();

            var sourceNote = composition().getLine(0).getElement(Element.PAIR_B_SRC.ordinal());
            assertThat(sourceNote.getGlissando().type)
                .as("4: source has connected glissando pointing to target")
                .isEqualTo(StaffElement.Glissando.Type.CONNECTED);
        });

        debugStep("5: No pitch bend without glissando", () -> {
            var note = composition().getLine(0).getElement(Element.PAIR_A_SRC.ordinal());
            //noinspection ObjectEquality
            assertThat(note.getGlissando() == StaffElement.NO_GLISSANDO)
                .as("5: pair A source has no glissando").isTrue();
        });

        debugStep("6-7: Tie persistence through save/load", () -> {
            var line = composition().getLine(0);
            var tie = line.getTies().findInterval(Element.TIED_1.ordinal());
            assertThat(tie).as("6: pre-tied pair exists").isNotNull();

            var reloaded = roundTripOnEdt();
            var reloadedLine = reloaded.getLine(0);
            var reloadedTie = reloadedLine.getTies().findInterval(Element.TIED_1.ordinal());
            assertAll(
                () -> assertThat(reloadedTie).as("6: save/load: tie preserved").isNotNull(),
                () -> assertThat(Objects.requireNonNull(reloadedTie).getStart()).as("7: tie start").isEqualTo(Element.TIED_1.ordinal()),
                () -> assertThat(Objects.requireNonNull(reloadedTie).getEnd()).as("7: tie end").isEqualTo(Element.TIED_2.ordinal())
            );
        });

        // --- Mutating: Beam and stem operations (eighth notes) ---

        var originalUpper = new boolean[1];

        debugStep("8: Toggle beam on", () -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.EIGHTH_1.ordinal()));
            shiftClickAt(noteScreenPosition(0, Element.EIGHTH_2.ordinal()));

            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, Element.EIGHTH_1.ordinal())).as("8a: note 1 beamed").isTrue(),
                () -> assertThat(isBeamed(0, Element.EIGHTH_2.ordinal())).as("8b: note 2 beamed").isTrue()
            );
        });

        debugStep("9-10: Flip stem (beamed)", () -> {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.ordinal());
            originalUpper[0] = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertAll(
                () -> assertThat(note.isStemDirectionAuto())
                    .as("9: stem direction not auto").isFalse(),
                () -> assertThat(note.isUpper())
                    .as("10: isUpper changed").isNotEqualTo(originalUpper[0])
            );
        });

        debugStep("11-12: Flip stem back (beamed)", () -> {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.ordinal());

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertAll(
                () -> assertThat(note.isUpper())
                    .as("11-12: isUpper restored").isEqualTo(originalUpper[0])
            );
        });

        debugStep("13: Toggle beam off", () -> {
            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isBeamed(0, Element.EIGHTH_1.ordinal())).as("13a: note 1 unbeamed").isFalse(),
                () -> assertThat(isBeamed(0, Element.EIGHTH_2.ordinal())).as("13b: note 2 unbeamed").isFalse()
            );
        });

        debugStep("14-15: Flip stem (unbeamed) + persistence", () -> {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.ordinal());
            var upperBefore = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isStemDirectionAuto()).as("14a: stem direction not auto").isFalse();
            assertThat(note.isUpper()).as("14b: isUpper changed").isNotEqualTo(upperBefore);

            var flippedUpper = note.isUpper();
            var reloaded = roundTripOnEdt();
            var reloadedNote = reloaded.getLine(0).getElement(Element.EIGHTH_1.ordinal());

            assertAll(
                () -> assertThat(reloadedNote.isStemDirectionAuto())
                    .as("15a: save/load: not auto").isFalse(),
                () -> assertThat(reloadedNote.isUpper())
                    .as("15b: save/load: isUpper preserved").isEqualTo(flippedUpper)
            );
        });

        // --- Mutating: Tie operations (eighth notes) ---

        debugStep("16-17: Tie creation", () -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.EIGHTH_1.ordinal()));
            shiftClickAt(noteScreenPosition(0, Element.EIGHTH_2.ordinal()));

            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertAll(
                () -> assertThat(isTied(0, Element.EIGHTH_1.ordinal())).as("16a: note 1 tied").isTrue(),
                () -> assertThat(isTied(0, Element.EIGHTH_2.ordinal())).as("16b: note 2 tied").isTrue(),
                () -> assertThat(Objects.requireNonNull(lss).canToggleTie()).as("17: can toggle tie").isTrue()
            );
        });

        debugStep("18: Flip stem (tied)", () -> {
            var note = composition().getLine(0).getElement(Element.EIGHTH_1.ordinal());
            var upperBefore = note.isUpper();

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isUpper()).as("18: stem flipped while tied").isNotEqualTo(upperBefore);
        });

        debugStep("19: Tie removal", () -> {
            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            assertAll(
                () -> assertThat(isTied(0, Element.EIGHTH_1.ordinal())).as("19a: note 1 untied").isFalse(),
                () -> assertThat(isTied(0, Element.EIGHTH_2.ordinal())).as("19b: note 2 untied").isFalse()
            );
        });

        // --- Mutating: Glissando insertion and MIDI (pair A) ---

        debugStep("20: Insert connected glissando on pair A", () -> {
            enterEditMode();
            selectDuration(Actions.GLISSANDO_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_SRC.ordinal(), Element.PAIR_A_TGT.ordinal()));
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_A_SRC.ordinal());
            assertAll(
                () -> assertThat(note.getGlissando())
                    .as("20a: has glissando").isNotSameAs(StaffElement.NO_GLISSANDO),
                () -> assertThat(note.getGlissando().type)
                    .as("20b: type is CONNECTED").isEqualTo(StaffElement.Glissando.Type.CONNECTED)
            );
        });

        debugStep("21-23: Connected glissando MIDI", () -> {
            var track = buildMidiTrack();
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            assertAll(
                () -> assertThat(bendEvents).as("21: pitch bend present").isNotEmpty(),
                () -> assertThat(ccEvents).as("23a: CC events present").hasSizeGreaterThanOrEqualTo(4),
                () -> {
                    var controllers = ccEvents.stream()
                        .map(e -> ((ShortMessage) e.getMessage()).getData1())
                        .toList();
                    assertThat(controllers.subList(0, 4))
                        .as("23b: RPN 0 sequence")
                        .containsExactly(101, 100, 6, 38);
                }
            );
        });

        debugStep("24: Insert slide-out on pair A target", () -> {
            selectDuration(Actions.SLIDE_OUT_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_TGT.ordinal(), Element.PAIR_B_SRC.ordinal()));
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_A_TGT.ordinal());
            assertAll(
                () -> assertThat(note.getGlissando())
                    .as("24a: has glissando").isNotSameAs(StaffElement.NO_GLISSANDO),
                () -> assertThat(note.getGlissando().type)
                    .as("24b: type is SLIDE_OUT").isEqualTo(StaffElement.Glissando.Type.SLIDE_OUT)
            );
        });

        debugStep("25-26: Slide-out MIDI", () -> {
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
                () -> assertThat(bendEvents).as("25: slide-out pitch bend present").isNotEmpty(),
                () -> assertThat(hasSlideOutSensitivity)
                    .as("26: RPN sensitivity includes slide-out semitones").isTrue()
            );
        });

        // --- Mutating: Glissando persistence ---

        debugStep("27-28: Glissando persistence", () -> {
            var originalNote = composition().getLine(0).getElement(Element.PAIR_A_SRC.ordinal());
            var originalType = originalNote.getGlissando().type;

            var reloaded = roundTripOnEdt();
            var reloadedNote = reloaded.getLine(0).getElement(Element.PAIR_A_SRC.ordinal());
            assertAll(
                () -> assertThat(reloadedNote.getGlissando())
                    .as("27: save/load: glissando preserved").isNotSameAs(StaffElement.NO_GLISSANDO),
                () -> assertThat(reloadedNote.getGlissando().type)
                    .as("28: save/load: glissando type preserved").isEqualTo(originalType)
            );
        });

        // --- Mutating: Drag to unison ---

        debugStep("29: Drag to unison removes glissando", () -> {
            enterEditMode();
            var targetSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Element.PAIR_C_TGT.ordinal()).getStaffPosition()
            ));

            dragNote(0, Element.PAIR_C_SRC.ordinal(), targetSp);
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_C_SRC.ordinal());
            //noinspection ObjectEquality
            assertThat(note.getGlissando())
                .as("29: glissando removed on unison").isSameAs(StaffElement.NO_GLISSANDO);
        });

        // --- Mutating: Glissando deletion ---

        debugStep("30: Delete selected glissando (pair D)", () -> {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_D_SRC.ordinal(), Element.PAIR_D_TGT.ordinal()));

            var lss = Objects.requireNonNull(score().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).hasGlissandoSelection()).as("30: glissando selected").isTrue();

            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var note = composition().getLine(0).getElement(Element.PAIR_D_SRC.ordinal());
            //noinspection ObjectEquality
            assertThat(note.getGlissando())
                .as("30: delete selected glissando").isSameAs(StaffElement.NO_GLISSANDO);
        });

        debugStep("31: Delete source note (pair E)", () -> {
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_E_SRC.ordinal()));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // After deleting PAIR_E_SRC: former pair E target shifts down by 1
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("31a: element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_E_SRC.ordinal()).getGlissando())
                    .as("31b: remaining note has no glissando").isSameAs(StaffElement.NO_GLISSANDO)
            );
        });

        debugStep("32: Delete target note (pair F)", () -> {
            // After previous deletion: pair F source and target each shifted down by 1
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_F_TGT.ordinal() - 1));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = composition().getLine(0);
            // Pair F source should have glissando removed (target deleted)
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("32a: element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_F_SRC.ordinal() - 1).getGlissando())
                    .as("32b: source glissando removed").isSameAs(StaffElement.NO_GLISSANDO)
            );
        });

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
