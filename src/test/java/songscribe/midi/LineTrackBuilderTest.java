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

package songscribe.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized" })
class LineTrackBuilderTest extends UnitTest {

    // Quarter-note tempo — reference note duration == PPQ
    private static final Tempo CROTCHET_TEMPO = new Tempo(120, Duration.CROTCHET, "", false);

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false
    );

    // Note-off tick for a CONNECTED glissando is duration − 1
    private static final int CONNECTED_NOTE_OFF_OFFSET = 1;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Track newTrack() throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, MidiSequenceBuilder.PPQ);
        return sequence.createTrack();
    }

    private static Track buildTrack(
        songscribe.dom.Line line,
        Tempo tempo
    ) throws Exception {
        var track = newTrack();
        new LineTrackBuilder(line).addToTrack(track, 0, 0, tempo, DEFAULT_SETTINGS);
        return track;
    }

    private static List<MidiEvent> eventsByCommand(Track track, int command) {
        var list = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);

            if (ev.getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                list.add(ev);
            }
        }

        return list;
    }

    private static List<MidiEvent> metaEventsByType(Track track, int metaType) {
        var list = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);

            if (ev.getMessage() instanceof MetaMessage mm && mm.getType() == metaType) {
                list.add(ev);
            }
        }

        return list;
    }

    private static int bendValue(MidiEvent event) {
        var sm = (ShortMessage) event.getMessage();
        return sm.getData1() | (sm.getData2() << 7);
    }

    // -------------------------------------------------------------------------
    // Row 23 — getElementDurationWithTuplet
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetElementDurationWithTuplet {

        @Test
        void testNonTupletElementReturnsRawDuration() {
            // A crotchet (PPQ ticks) with no tuplet → raw duration returned unchanged.
            var line = detachedLine();
            line.addElement(crotchet());
            var builder = new LineTrackBuilder(line);

            var result = builder.getElementDurationWithTuplet(0, CROTCHET_TEMPO);

            assertThat(result).isEqualTo(PPQ);
        }

        @Test
        void testTripletElementDurationIsScaledDown() {
            // 3 quavers as a triplet over a crotchet reference (3-in-2 feel):
            //   tupletDuration = (3 × PPQ/2) / PPQ = 1.5 → ≥1 branch
            //   factor = floor(1.5) / 1.5 = 1/1.5 ≈ 0.6667
            //   rounded duration of one quaver (PPQ/2) × 0.6667 ≈ PPQ/3
            var line = detachedLine();
            var q1 = ElementType.QUAVER.newInstance();
            var q2 = ElementType.QUAVER.newInstance();
            var q3 = ElementType.QUAVER.newInstance();
            line.addElement(q1);
            line.addElement(q2);
            line.addElement(q3);
            line.addRangeElement(new Tuplet(q1, q3, 3));

            var builder = new LineTrackBuilder(line);
            var result = builder.getElementDurationWithTuplet(0, CROTCHET_TEMPO);

            // Expected: Math.round((PPQ/2) × (2/3)) = Math.round(PPQ/3)
            var expected = Math.round((PPQ / 2f) * (2f / 3f));
            assertThat(result).isEqualTo(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Row 24 — getTupletFactor  (all five branches)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetTupletFactor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("tupletFactorCases")
        void testTupletFactor(String description, float expected, float actual) {
            assertThat(actual).isCloseTo(expected, offset(0.0001f));
        }

        static Stream<Arguments> tupletFactorCases() {
            // Case 1: no tuplet → factor must be 1.0
            var line1 = detachedLine();
            line1.addElement(crotchet());
            var builder1 = new LineTrackBuilder(line1);
            var noTupletFactor = builder1.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 2: tupletDuration < 1 — three semiquavers over a crotchet reference
            //   tupletDuration = (3 × PPQ/4) / PPQ = 3/4 = 0.75 → <1 branch
            //   log2(0.75) → floor = -1 → newDuration = 2^(-1) = 0.5
            //   factor = 0.5 / 0.75 = 2/3
            var line2 = detachedLine();
            var sq1 = ElementType.SEMIQUAVER.newInstance();
            var sq2 = ElementType.SEMIQUAVER.newInstance();
            var sq3 = ElementType.SEMIQUAVER.newInstance();
            line2.addElement(sq1);
            line2.addElement(sq2);
            line2.addElement(sq3);
            line2.addRangeElement(new Tuplet(sq1, sq3, 3));
            var builder2 = new LineTrackBuilder(line2);
            var smallTripletFactor = builder2.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 3: tupletDuration = 1.0 (floor(1)=1, not >1 so no decrement) → factor = 1.0
            //   Two crotchets over a minim reference: (2 × PPQ) / (PPQ×2) = 1.0
            var mimTempo = new Tempo(120, Duration.MINIM, "", false);
            var line3 = detachedLine();
            var c3a = crotchet();
            var c3b = crotchet();
            line3.addElement(c3a);
            line3.addElement(c3b);
            line3.addRangeElement(new Tuplet(c3a, c3b, 2));
            var builder3 = new LineTrackBuilder(line3);
            var unitDurationFactor = builder3.getTupletFactor(0, mimTempo);

            // Case 4: tupletDuration > 1 — quintuplet: 5 quavers over crotchet reference
            //   tupletDuration = (5 × PPQ/2) / PPQ = 2.5 → ≥1 branch
            //   floor(2.5)=2, 2 ≠ 2.5 so no decrement → factor = 2/2.5 = 0.8
            var line4 = detachedLine();
            var qv1 = ElementType.QUAVER.newInstance();
            var qv2 = ElementType.QUAVER.newInstance();
            var qv3 = ElementType.QUAVER.newInstance();
            var qv4 = ElementType.QUAVER.newInstance();
            var qv5 = ElementType.QUAVER.newInstance();
            line4.addElement(qv1);
            line4.addElement(qv2);
            line4.addElement(qv3);
            line4.addElement(qv4);
            line4.addElement(qv5);
            line4.addRangeElement(new Tuplet(qv1, qv5, 5));
            var builder4 = new LineTrackBuilder(line4);
            var quintupletFactor = builder4.getTupletFactor(0, CROTCHET_TEMPO);

            // Case 5: newDuration == tupletDuration && newDuration > 1 → newDuration--
            //   4 quavers over crotchet: tupletDuration = (4 × PPQ/2)/PPQ = 2.0 (exact integer > 1)
            //   floor(2.0)=2.0; 2.0==2.0 && 2.0>1 → newDuration=1.0 → factor = 1.0/2.0 = 0.5
            var line5 = detachedLine();
            var ev1 = ElementType.QUAVER.newInstance();
            var ev2 = ElementType.QUAVER.newInstance();
            var ev3 = ElementType.QUAVER.newInstance();
            var ev4 = ElementType.QUAVER.newInstance();
            line5.addElement(ev1);
            line5.addElement(ev2);
            line5.addElement(ev3);
            line5.addElement(ev4);
            line5.addRangeElement(new Tuplet(ev1, ev4, 4));
            var builder5 = new LineTrackBuilder(line5);
            var decrementBranchFactor = builder5.getTupletFactor(0, CROTCHET_TEMPO);

            return Stream.of(
                Arguments.of("noTuplet returns 1.0", 1.0f, noTupletFactor),
                Arguments.of("tupletDuration<1 (3 semiquavers / crotchet ref) returns 2/3",
                    2f / 3f, smallTripletFactor),
                Arguments.of("tupletDuration=1.0 (2 crotchets / minim ref) returns 1.0",
                    1.0f, unitDurationFactor),
                Arguments.of("tupletDuration>1 (quintuplet) returns 0.8",
                    0.8f, quintupletFactor),
                Arguments.of("newDuration==tupletDuration>1 triggers newDuration-- returning 0.5",
                    0.5f, decrementBranchFactor)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Row 25 — calculateSoundingDuration / calculateSoundingPercent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateSoundingDuration {

        @Test
        void testNoOverrideUsesSettingsPercent() {
            // A plain crotchet has no articulation that overrides duration (override == -1),
            // so the settings noteDurationPercent value must be used.
            var line = detachedLine();
            var note = crotchet();
            line.addElement(note);
            var builder = new LineTrackBuilder(line);
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = builder.calculateSoundingDuration(PPQ, note, settings);

            assertThat(result).isEqualTo((int) ((PPQ * (long) 80) / 100));
        }

        @Test
        void testOverridePresentIgnoresSettings() {
            // A staccato note has midiDurationPercent == 33 (ArticulationType.STACCATO).
            // That override must win regardless of settings.noteDurationPercent.
            var line = detachedLine();
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            line.addElement(note);
            var builder = new LineTrackBuilder(line);
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = builder.calculateSoundingDuration(PPQ, note, settings);

            var staccatoPercent = ArticulationType.STACCATO.getMidiDurationPercent();
            assertThat(result).isEqualTo((int) ((PPQ * (long) staccatoPercent) / 100));
        }

        @Test
        void testCalculateSoundingPercentNoOverrideReturnsSettingsPercent() {
            // Static method: when override == -1, returns settings percent directly.
            var note = crotchet();
            var settings = new PlaybackSettings(0, 100, 75, false);

            var result = LineTrackBuilder.calculateSoundingPercent(note, settings);

            assertThat(result).isEqualTo(75);
        }

        @Test
        void testCalculateSoundingPercentWithStaccatoReturnsOverride() {
            // Static method: staccato articulation returns its midiDurationPercent (33).
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            var settings = new PlaybackSettings(0, 100, 80, false);

            var result = LineTrackBuilder.calculateSoundingPercent(note, settings);

            assertThat(result).isEqualTo(ArticulationType.STACCATO.getMidiDurationPercent());
        }
    }

    // -------------------------------------------------------------------------
    // Row 26 — noteVelocity
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoteVelocity {

        private static final int LINE_INDEX = 0;
        private static final int NOTE_INDEX = 0;

        @Test
        void testNullVelocityMapUsesNonAccentFallback() {
            // When velocityMap is null and the note has no accent, NOTE_VELOCITY is returned.
            var note = crotchet();

            var result = LineTrackBuilder.noteVelocity(note, null, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(PlaybackController.NOTE_VELOCITY);
        }

        @Test
        void testNullVelocityMapWithAccentUsesAccentedVelocity() {
            // When velocityMap is null and the note carries an ACCENT articulation,
            // ACCENTED_NOTE_VELOCITY must be returned.
            var note = crotchet();
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));

            var result = LineTrackBuilder.noteVelocity(note, null, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(PlaybackController.ACCENTED_NOTE_VELOCITY);
        }

        @Test
        void testVelocityMapPresentReturnsMapValue() {
            // When a VelocityMap is supplied, its pre-computed value must be used
            // regardless of any articulation on the note.
            var song = songWithOneCrotchet();
            var map = VelocityMap.build(song, VelocityMap.MAX_VELOCITY);
            var note = crotchet();

            var result = LineTrackBuilder.noteVelocity(note, map, LINE_INDEX, NOTE_INDEX);

            assertThat(result).isEqualTo(map.getVelocity(LINE_INDEX, NOTE_INDEX));
        }
    }

    // ── Row 27: addNoteMessages dispatch branches ─────────────────────────────

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddNoteMessages {

        @Test
        void testGraceNoteEmitsNoNoteOn() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(-2);
            var line = detachedLine();
            line.addElement(grace);
            line.addElement(host);

            var track = buildTrack(line, new Tempo());
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);

            // Only the host note should produce a NOTE_ON; the grace note does not
            assertThat(noteOns).as("only host NOTE_ON, no grace NOTE_ON").hasSize(1);
            assertThat(noteOns.getFirst().getTick()).as("host NOTE_ON at tick 0").isEqualTo(0);
        }

        @Test
        void testGraceNoteStoresPitchForSlideIn() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(1);
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(-2);
            var line = detachedLine();
            line.addElement(grace);
            line.addElement(host);

            var track = buildTrack(line, new Tempo());
            var bendEvents = eventsByCommand(track, ShortMessage.PITCH_BEND);

            // A slide-in bend proves that the grace pitch was stored and consumed
            assertThat(bendEvents).as("slide-in pitch bend generated from stored grace pitch").isNotEmpty();
            // First bend value must not be center (i.e. there is a real offset)
            assertThat(bendValue(bendEvents.getFirst()))
                .as("initial bend is not center — grace pitch offset applied")
                .isNotEqualTo(SlideMidiHelper.PITCH_BEND_CENTER);
        }

        @Test
        void testRestAdvancesTicksNoNoteMessages() throws Exception {
            var line = lineWith(ElementType.CROTCHET_REST, ElementType.CROTCHET);
            line.getElement(1).setStaffPosition(-2);

            var track = buildTrack(line, new Tempo());
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            // The rest should generate no NOTE_ON; the note after it must land at PPQ
            assertThat(noteOns).as("no NOTE_ON for rest").hasSize(1);
            assertThat(noteOns.getFirst().getTick())
                .as("note after rest starts at one quarter-note tick offset")
                .isEqualTo(MidiSequenceBuilder.PPQ);

            assertThat(noteOffs).as("only note-off for the pitched note, none for rest").hasSize(1);
        }

        @Test
        void testTieAnchorEmitsNoteOnNoteTieEndDoesNot() throws Exception {
            var line = detachedLine();
            var note0 = ElementType.CROTCHET.newInstance();
            note0.setStaffPosition(-2);
            var note1 = ElementType.CROTCHET.newInstance();
            note1.setStaffPosition(-2);
            var note2 = ElementType.CROTCHET.newInstance();
            note2.setStaffPosition(-2);
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);

            // Tie note0 → note1: note0 is anchor, note1 is tie-end; note2 is standalone
            line.addTie(new Tie(note0, note1));

            var track = buildTrack(line, new Tempo());
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);

            // note0 (anchor) + note2 (standalone) → 2 NOTE_ONs; note1 (tie-end) → none
            assertThat(noteOns).as("anchor and standalone each get NOTE_ON, tie-end does not").hasSize(2);
            assertThat(noteOns.getFirst().getTick()).as("anchor NOTE_ON at tick 0").isEqualTo(0);
            assertThat(noteOns.getLast().getTick())
                .as("standalone NOTE_ON after two quarter durations")
                .isEqualTo(2 * MidiSequenceBuilder.PPQ);
        }

        @Test
        void testTieEndEmitsNoteOffAnchorDoesNot() throws Exception {
            var line = detachedLine();
            var note0 = ElementType.CROTCHET.newInstance();
            note0.setStaffPosition(-2);
            var note1 = ElementType.CROTCHET.newInstance();
            note1.setStaffPosition(-2);
            line.addElement(note0);
            line.addElement(note1);
            line.addTie(new Tie(note0, note1));

            var track = buildTrack(line, new Tempo());
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            // The note-off for a tied pair lands at the end of note1's duration
            assertThat(noteOffs).as("single note-off for the tie span").hasSize(1);
            // Default sounding-percent=100 → note-off at tick 2*PPQ (end of note1)
            assertThat(noteOffs.getFirst().getTick())
                .as("note-off at end of tie-end duration")
                .isEqualTo(2 * MidiSequenceBuilder.PPQ);
        }

        @Test
        void testNonPitchedNextElementFallsBackToNormalNoteOff() throws Exception {
            // A note with CONNECTED glissando followed by a rest must use normal note-off
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setGlissando();
            var rest = ElementType.CROTCHET_REST.newInstance();
            line.addElement(note);
            line.addElement(rest);

            var track = buildTrack(line, new Tempo());
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);
            var bendEvents = eventsByCommand(track, ShortMessage.PITCH_BEND);

            // Normal note-off: no glissando bend emitted
            assertThat(bendEvents).as("no pitch bend when fallback to normal note-off").isEmpty();
            // Note-off lands at sounding duration (100% of PPQ by default)
            assertThat(noteOffs).as("one note-off").hasSize(1);
            assertThat(noteOffs.getFirst().getTick())
                .as("normal note-off tick at full duration")
                .isEqualTo(MidiSequenceBuilder.PPQ);
        }
    }

    // ── Row 28: addGlissandoMessages ──────────────────────────────────────────

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddGlissandoMessages {

        @Test
        void testConnectedNoteOffAtDurationMinusOne() throws Exception {
            // CONNECTED glissando: note-off must be at duration − 1 to avoid pitch-reset race
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setGlissando();
            var nextNote = ElementType.CROTCHET.newInstance();
            nextNote.setStaffPosition(-4);
            line.addElement(note);
            line.addElement(nextNote);

            var track = buildTrack(line, new Tempo());
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            assertThat(noteOffs).as("at least one note-off").isNotEmpty();
            // The first note-off belongs to the glissando source note
            var glissNoteOffTick = noteOffs.getFirst().getTick();
            assertThat(glissNoteOffTick)
                .as("CONNECTED note-off at duration − 1")
                .isEqualTo(MidiSequenceBuilder.PPQ - CONNECTED_NOTE_OFF_OFFSET);
        }

        @Test
        void testConnectedFallbackWhenLastElement() throws Exception {
            // CONNECTED with no next element → normal note-off, no pitch bend
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setGlissando();
            line.addElement(note);

            var track = buildTrack(line, new Tempo());
            var bendEvents = eventsByCommand(track, ShortMessage.PITCH_BEND);
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            assertThat(bendEvents).as("no pitch bend when next element absent").isEmpty();
            assertThat(noteOffs).as("normal note-off emitted").hasSize(1);
            assertThat(noteOffs.getFirst().getTick())
                .as("fallback note-off at full sounding duration")
                .isEqualTo(MidiSequenceBuilder.PPQ);
        }

        @Test
        void testConnectedFallbackWhenNextIsRest() throws Exception {
            // CONNECTED with rest as next element → normal note-off, no pitch bend
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setGlissando();
            line.addElement(note);
            line.addElement(ElementType.CROTCHET_REST.newInstance());

            var track = buildTrack(line, new Tempo());
            var bendEvents = eventsByCommand(track, ShortMessage.PITCH_BEND);
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            assertThat(bendEvents).as("no pitch bend when next element is rest").isEmpty();
            assertThat(noteOffs).as("normal note-off emitted").hasSize(1);
            assertThat(noteOffs.getFirst().getTick())
                .as("fallback note-off at full sounding duration")
                .isEqualTo(MidiSequenceBuilder.PPQ);
        }

        @Test
        void testSlideOutEmitsExpressionCcEvents() throws Exception {
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setFall();
            line.addElement(note);

            var track = buildTrack(line, new Tempo());
            var ccEvents = eventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            // Slide starts at sustainTicks into the note (halfway through full duration).
            // Events at the note-off tick (PPQ) are the deferred reset, not the fade.
            var noteOffTick = (long) MidiSequenceBuilder.PPQ;
            var slideStartTick = SlideMidiHelper.calculateSustainTicks(MidiSequenceBuilder.PPQ);

            // Expression fade events are CC 11, emitted strictly before note-off tick
            var slideExpressionEvents = ccEvents.stream()
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 11)
                .filter(e -> e.getTick() >= slideStartTick && e.getTick() < noteOffTick)
                .toList();

            assertThat(slideExpressionEvents).as("SLIDE_OUT emits Expression CC fade events").isNotEmpty();

            // The fade starts at 127 and must reach a value below 127 by the slide end
            var firstExprValue = ((ShortMessage) slideExpressionEvents.getFirst().getMessage()).getData2();
            var lastExprValue = ((ShortMessage) slideExpressionEvents.getLast().getMessage()).getData2();

            assertThat(firstExprValue).as("expression fade starts at maximum").isEqualTo(127);
            assertThat(lastExprValue).as("expression fades to below starting value").isLessThan(firstExprValue);
        }

        @Test
        void testSlideOutShortenedByStaccato() throws Exception {
            // With staccato (33% duration) the note-off must be earlier than full duration
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setFall();
            note.addArticulation(new Articulation(ArticulationType.STACCATO));
            line.addElement(note);

            var trackStaccato = buildTrack(line, new Tempo());
            var noteOffsStaccato = eventsByCommand(trackStaccato, ShortMessage.NOTE_OFF);

            // Without staccato for comparison
            var lineNormal = detachedLine();
            var noteNormal = ElementType.CROTCHET.newInstance();
            noteNormal.setStaffPosition(-2);
            noteNormal.setFall();
            lineNormal.addElement(noteNormal);

            var trackNormal = buildTrack(lineNormal, new Tempo());
            var noteOffsNormal = eventsByCommand(trackNormal, ShortMessage.NOTE_OFF);

            assertThat(noteOffsStaccato).as("staccato slide-out has a note-off").hasSize(1);
            assertThat(noteOffsNormal).as("normal slide-out has a note-off").hasSize(1);
            assertThat(noteOffsStaccato.getFirst().getTick())
                .as("staccato shortens note-off tick compared to full duration")
                .isLessThan(noteOffsNormal.getFirst().getTick());
        }
    }

    // ── Row 29: addGraceGlissandoSlideIn ─────────────────────────────────────

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddGraceGlissandoSlideIn {

        @Test
        void testNoteOnVelocityIsReducedByGraceRatio() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(1);
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(-2);
            var line = detachedLine();
            line.addElement(grace);
            line.addElement(host);

            var track = buildTrack(line, new Tempo());
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);

            assertThat(noteOns).as("one NOTE_ON for the host").hasSize(1);

            var velocity = ((ShortMessage) noteOns.getFirst().getMessage()).getData2();
            var expectedVelocity = (int) (PlaybackController.NOTE_VELOCITY * 0.85);

            assertThat(velocity)
                .as("host NOTE_ON velocity reduced to 85% of default")
                .isEqualTo(expectedVelocity);
        }

        @Test
        void testPitchBendResetAtEndOfSlide() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(1);
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(-2);
            var line = detachedLine();
            line.addElement(grace);
            line.addElement(host);

            var track = buildTrack(line, new Tempo());
            var bendEvents = eventsByCommand(track, ShortMessage.PITCH_BEND);

            // The last pitch bend event within the slide window must be center (reset)
            var slideDuration = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, MidiSequenceBuilder.PPQ);
            var resetTick = (long) slideDuration;

            var resetEvent = bendEvents.stream()
                .filter(e -> e.getTick() == resetTick)
                .filter(e -> bendValue(e) == SlideMidiHelper.PITCH_BEND_CENTER)
                .findFirst();

            assertThat(resetEvent)
                .as("pitch bend reset (center) emitted at tick " + resetTick)
                .isPresent();
        }

        @Test
        void testExpressionResetAtEndOfSlide() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(1);
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(-2);
            var line = detachedLine();
            line.addElement(grace);
            line.addElement(host);

            var track = buildTrack(line, new Tempo());
            var ccEvents = eventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            var slideDuration = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, MidiSequenceBuilder.PPQ);
            var resetTick = (long) slideDuration;

            // Expression reset = CC 11 = 127 at the slide-end tick
            var expressionReset = ccEvents.stream()
                .filter(e -> e.getTick() == resetTick)
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 11)
                .filter(e -> ((ShortMessage) e.getMessage()).getData2() == 127)
                .findFirst();

            assertThat(expressionReset)
                .as("expression reset (CC 11 = 127) emitted at tick " + resetTick)
                .isPresent();
        }
    }

    // ── Row 30: addToTrack overloads ──────────────────────────────────────────

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class AddToTrack {

        @Test
        void testTempoChangeElementEmitsSetTempoMetaEvent() throws Exception {
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            var fastTempo = new Tempo(180, Duration.CROTCHET, "Fast", true);
            note.addAttachment(new TempoChangeAttachment(fastTempo));
            line.addElement(note);

            var track = buildTrack(line, new Tempo());
            var tempoEvents = metaEventsByType(track, MidiMetaMessageTypes.SET_TEMPO);

            assertThat(tempoEvents).as("SET_TEMPO meta event emitted").hasSize(1);
            assertThat(tempoEvents.getFirst().getTick())
                .as("tempo event at tick 0 (start of element)")
                .isEqualTo(0);
        }

        @Test
        void testRangeBoundariesRespected() throws Exception {
            // Three notes; only the middle note should produce a NOTE_ON/NOTE_OFF
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            line.getElement(0).setStaffPosition(-2);
            line.getElement(1).setStaffPosition(-4);
            line.getElement(2).setStaffPosition(-6);

            var track = newTrack();
            new LineTrackBuilder(line).addToTrack(track, 0, 0, new Tempo(), DEFAULT_SETTINGS, 1, 1);

            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);
            var noteOffs = eventsByCommand(track, ShortMessage.NOTE_OFF);

            assertThat(noteOns).as("only middle element NOTE_ON").hasSize(1);
            assertThat(noteOffs).as("only middle element NOTE_OFF").hasSize(1);
            // Middle element starts at tick 0 within the range call (startTicks=0)
            assertThat(noteOns.getFirst().getTick()).as("NOTE_ON at start of range").isEqualTo(0);
        }

        @Test
        void testOverload3FlushesGlissandoPendingResets() throws Exception {
            // A SLIDE_OUT note leaves pending resets. Overload[3] must flush them.
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setFall();
            line.addElement(note);

            var track = newTrack();
            // Overload[3]: takes startElement, endElement, velocityMap — flushes internally
            new LineTrackBuilder(line).addToTrack(track, 0, 0, new Tempo(), DEFAULT_SETTINGS,
                0, 0, (VelocityMap) null);

            var ccEvents = eventsByCommand(track, ShortMessage.CONTROL_CHANGE);
            var endTick = (long) MidiSequenceBuilder.PPQ;

            // After a SLIDE_OUT the pending expression reset (CC 11=127) must land at the end tick
            var expressionReset = ccEvents.stream()
                .filter(e -> e.getTick() == endTick)
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 11)
                .filter(e -> ((ShortMessage) e.getMessage()).getData2() == 127)
                .findFirst();

            assertThat(expressionReset)
                .as("overload[3] flushes pending expression reset at end tick")
                .isPresent();
        }

        @Test
        void testOverload4DoesNotFlushGlissandoPendingResets() throws Exception {
            // Overload[4] takes an external GlissandoMidiHelper; flushing is the caller's job.
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-2);
            note.setFall();
            line.addElement(note);

            var track = newTrack();
            var helper = new SlideMidiHelper();
            // Overload[4]: takes startElement, endElement, glissandoHelper — no auto-flush
            new LineTrackBuilder(line).addToTrack(track, 0, 0, new Tempo(), DEFAULT_SETTINGS,
                0, 0, helper);

            var ccEvents = eventsByCommand(track, ShortMessage.CONTROL_CHANGE);
            var endTick = (long) MidiSequenceBuilder.PPQ;

            // Without explicit flush, no expression reset at end tick
            var expressionResetAtEnd = ccEvents.stream()
                .filter(e -> e.getTick() == endTick)
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 11)
                .filter(e -> ((ShortMessage) e.getMessage()).getData2() == 127)
                .findFirst();

            assertThat(expressionResetAtEnd)
                .as("overload[4] does not auto-flush: no expression reset at end tick")
                .isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static songscribe.dom.Song songWithOneCrotchet() {
        var song = new songscribe.dom.Song();
        var line = detachedLine();
        line.addElement(crotchet());
        song.getLines().clear();
        song.getLines().add(line);
        return song;
    }
}
