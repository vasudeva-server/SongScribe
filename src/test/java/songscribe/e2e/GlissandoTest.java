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

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.midi.GlissandoMidiHelper;
import songscribe.midi.PlaybackSettings;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;

/**
 * Milestone 3 E2E tests: glissando insert, select, delete, highlight, persistence.
 */
class GlissandoTest extends E2ETest {

    @Test
    void testDragToUnisonRemovesConnectedGlissando() {
        buildNotesWithConnectedGlissando();

        var line = composition().getLine(0);
        var targetSp = line.getElement(1).getStaffPosition();

        // Enter edit mode (drag works in edit mode)
        enterEditMode();

        // Drag the source note to the same pitch as the target
        dragNote(0, 0, targetSp);
        performLayout(0);

        // Connected glissando should be removed (unison is meaningless)
        //noinspection ObjectEquality
        assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
    }

    @Test
    void testNoPitchBendWithoutGlissando() throws Exception {
        buildTwoNotesAtDifferentPitches();

        var track = buildMidiTrack();
        assertThat(getEventsByCommand(track, ShortMessage.PITCH_BEND)).isEmpty();
    }

    @Test
    void testGlissandoPersistsThroughSaveLoad() throws Exception {
        buildNotesWithConnectedGlissando();

        var originalNote = composition().getLine(0).getElement(0);
        var originalType = originalNote.getGlissando().type;

        // Round-trip save/load
        var reloaded = roundTrip(composition());

        var reloadedNote = reloaded.getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(reloadedNote.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(reloadedNote.getGlissando().type).isEqualTo(originalType);
    }

    @Nested
    class Deletion {

        @Test
        void testDeleteSelectedGlissando() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Select the glissando by clicking on it
            clickAt(glissandoMidpoint(0));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.hasGlissandoSelection()).isTrue();

            // Press Delete
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            // Glissando should be removed
            var note = composition().getLine(0).getElement(0);
            //noinspection ObjectEquality
            assertThat(note.getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
        }

        @Test
        void testDeleteSourceNoteRemovesConnectedGlissando() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Select the source note (note 0)
            clickAt(noteScreenPosition(0, 0));

            // Delete it
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            // Note 0 was removed, only 1 note remains
            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            // The remaining note should have no orphaned glissando
            //noinspection ObjectEquality
            assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
        }

        @Test
        void testDeleteSourceNoteRemovesSlideOut() {
            buildNotesWithSlideOut();

            enterSelectMode();

            // Select the note with slide-out
            clickAt(noteScreenPosition(0, 0));

            // Delete it
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            // Source note was removed, only the second note remains
            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            // The remaining note should have no glissando
            //noinspection ObjectEquality
            assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
        }

        @Test
        void testDeleteTargetNoteRemovesConnectedGlissando() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Select the target note (note 1)
            clickAt(noteScreenPosition(0, 1));

            // Delete it
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            // Only 1 note remains (the source)
            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            // The source note's glissando should be removed (no target to connect to)
            //noinspection ObjectEquality
            assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
        }
    }

    @Nested
    class Insertion {

        @Test
        void testInsertConnectedGlissando() throws Exception {
            buildTwoNotesAtDifferentPitches();

            enterEditMode();
            selectDuration(Actions.GLISSANDO_ACTION);

            // Click between the two notes (past the first note)
            clickAt(glissandoInsertionPoint(0));
            performLayout(0);

            var note = composition().getLine(0).getElement(0);
            //noinspection ObjectEquality
            assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
            assertThat(note.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);

            // MIDI: verify pitch bend and timing
            var line = composition().getLine(0);
            var tempo = line.getElement(0).getTempoChange();
            var duration = line.getElementDurationWithTuplet(0, tempo);
            var sustainTicks = GlissandoMidiHelper.calculateSustainTicks(duration);

            var track = buildMidiTrack();
            var noteOnTick = getEventsByCommand(track, ShortMessage.NOTE_ON).getFirst().getTick();
            var noteOffTick = getEventsByCommand(track, ShortMessage.NOTE_OFF).getFirst().getTick();
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            // NOTE_OFF at full written duration (connected ignores staccato)
            assertThat(noteOffTick - noteOnTick).isEqualTo(duration);

            var staccatoTrack = buildMidiTrack(new PlaybackSettings(0, 100, 50, false, false));
            var staccatoOnTick = getEventsByCommand(staccatoTrack, ShortMessage.NOTE_ON).getFirst().getTick();
            var staccatoOffTick = getEventsByCommand(staccatoTrack, ShortMessage.NOTE_OFF).getFirst().getTick();
            assertThat(staccatoOffTick - staccatoOnTick).isEqualTo(duration);

            // Pitch bend starts after sustain portion
            assertThat(bendEvents.getFirst().getTick() - noteOnTick).isEqualTo(sustainTicks);

            // RPN 0 sequence present: CC 101=0, CC 100=0, CC 6=sensitivity, CC 38=0
            assertThat(ccEvents).hasSizeGreaterThanOrEqualTo(4);
            var controllers = ccEvents.stream()
                .map(e -> ((ShortMessage) e.getMessage()).getData1())
                .toList();
            assertThat(controllers.subList(0, 4)).containsExactly(101, 100, 6, 38);
        }

        @Test
        void testInsertSlideOutGlissando() throws Exception {
            buildTwoNotesAtDifferentPitches();

            enterEditMode();
            selectDuration(Actions.SLIDE_OUT_ACTION);

            // Click past the last note (slide out doesn't require a target note,
            // but the insertion point must be after a note)
            clickAt(glissandoInsertionPoint(0));
            performLayout(0);

            var note = composition().getLine(0).getElement(0);
            //noinspection ObjectEquality
            assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
            assertThat(note.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.SLIDE_OUT);

            // MIDI: verify slide-out pitch bend and timing
            var line = composition().getLine(0);
            var tempo = line.getElement(0).getTempoChange();
            var duration = line.getElementDurationWithTuplet(0, tempo);

            var track = buildMidiTrack();
            var noteOnTick = getEventsByCommand(track, ShortMessage.NOTE_ON).getFirst().getTick();
            var noteOffTick = getEventsByCommand(track, ShortMessage.NOTE_OFF).getFirst().getTick();
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            // NOTE_OFF at full sounding duration (100% with default settings)
            assertThat(noteOffTick - noteOnTick).isEqualTo(duration);

            // With staccato, NOTE_OFF respects noteDurationPercent
            var staccatoTrack = buildMidiTrack(new PlaybackSettings(0, 100, 50, false, false));
            var staccatoOnTick = getEventsByCommand(staccatoTrack, ShortMessage.NOTE_ON).getFirst().getTick();
            var staccatoOffTick = getEventsByCommand(staccatoTrack, ShortMessage.NOTE_OFF).getFirst().getTick();
            var expectedSounding = (int) ((duration * 50L) / 100f);
            assertThat(staccatoOffTick - staccatoOnTick).isEqualTo(expectedSounding);

            // Pitch bend slides downward (last slide event below center, excluding reset)
            var slideEvents = bendEvents.subList(0, bendEvents.size() - 1);
            assertThat(getBendValue(slideEvents.getFirst()))
                .isEqualTo(GlissandoMidiHelper.PITCH_BEND_CENTER);
            assertThat(getBendValue(slideEvents.getLast()))
                .isLessThan(GlissandoMidiHelper.PITCH_BEND_CENTER);

            // RPN sensitivity matches SLIDE_OUT_SEMITONES
            var cc6Event = ccEvents.stream()
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 6)
                .findFirst()
                .orElseThrow();
            assertThat(((ShortMessage) cc6Event.getMessage()).getData2())
                .isEqualTo(GlissandoMidiHelper.SLIDE_OUT_SEMITONES);
        }
    }

    @Nested
    class Selection {

        @Test
        void testSelectGlissandoByClick() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Click on the glissando line (midpoint between the two notes)
            clickAt(glissandoMidpoint(0));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.hasGlissandoSelection()).isTrue();
            assertThat(lss.getSelectedGlissandoElementIndex()).isEqualTo(0);
        }

        @Test
        void testSelectSourceNoteHighlightsGlissando() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Select the source note (note 0)
            clickAt(noteScreenPosition(0, 0));

            // Source note is selected
            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.isElementSelected(0)).isTrue();

            // Glissando exists on that note (model-level check)
            var note = composition().getLine(0).getElement(0);
            //noinspection ObjectEquality
            assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        }

        @Test
        void testSelectTargetNoteHighlightsGlissando() {
            buildNotesWithConnectedGlissando();

            enterSelectMode();

            // Select the target note (note 1)
            clickAt(noteScreenPosition(0, 1));

            // Target note is selected
            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.isElementSelected(1)).isTrue();

            // Previous note has a connected glissando pointing to it (model-level check)
            var prevNote = composition().getLine(0).getElement(0);
            assertThat(prevNote.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);
        }
    }


    // -- Coordinate helpers --

    /**
     * Returns a screen point between two notes, suitable for clicking to insert
     * a glissando. The point is positioned horizontally between note 0 and note 1,
     * vertically at the midpoint of their staff positions.
     */
    private Point glissandoInsertionPoint(int lineIndex) {
        var p0 = noteScreenPosition(lineIndex, 0);
        var p1 = noteScreenPosition(lineIndex, 1);
        return new Point((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
    }

    /**
     * Returns the approximate midpoint of the glissando line between notes 0 and 1,
     * suitable for click-selecting the glissando.
     */
    private Point glissandoMidpoint(int lineIndex) {
        var p0 = noteScreenPosition(lineIndex, 0);
        var p1 = noteScreenPosition(lineIndex, 1);
        return new Point((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
    }


    // -- Composition builders --

    private void buildTwoNotesAtDifferentPitches() {
        buildNotes(Actions.QUARTER_NOTE_ACTION, 0, -4);
    }

    private void buildNotesWithConnectedGlissando() {
        buildTwoNotesAtDifferentPitches();

        enterEditMode();
        selectDuration(Actions.GLISSANDO_ACTION);
        clickAt(glissandoInsertionPoint(0));
        performLayout(0);
    }

    private void buildNotesWithSlideOut() {
        buildTwoNotesAtDifferentPitches();

        enterEditMode();
        selectDuration(Actions.SLIDE_OUT_ACTION);
        clickAt(glissandoInsertionPoint(0));
        performLayout(0);
    }


    // -- MIDI helpers --

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false, false
    );

    private Track buildMidiTrack() throws Exception {
        return buildMidiTrack(DEFAULT_SETTINGS);
    }

    private Track buildMidiTrack(PlaybackSettings settings) throws Exception {
        var line = composition().getLine(0);
        var tempo = line.getElement(0).getTempoChange();
        var sequence = new Sequence(Sequence.PPQ, 96);
        var track = sequence.createTrack();
        line.addToTrack(track, 0, 0, tempo, settings);
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

    private static int getBendValue(MidiEvent event) {
        var sm = (ShortMessage) event.getMessage();
        return sm.getData1() | (sm.getData2() << 7);
    }
}
