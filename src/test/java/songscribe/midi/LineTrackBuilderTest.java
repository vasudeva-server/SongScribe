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
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

@SuppressWarnings({ "OverlyBroadThrowsClause" })
class LineTrackBuilderTest extends UnitTest {

    // Quarter-note tempo — reference note duration == PPQ
    private static final Tempo CROTCHET_TEMPO = new Tempo(120, Duration.CROTCHET, "", false);

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false
    );

    // Note-off tick for a CONNECTED glissando is duration − 1
    private static final int CONNECTED_NOTE_OFF_OFFSET = 1;

    // Tuplet ratios: N in the time of M notes of the written value V.
    private static final int DUPLET_GRADE = 2;
    private static final int DUPLET_NORMAL_NOTES = 3;
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int QUINTUPLET_GRADE = 5;
    private static final int QUINTUPLET_NORMAL_NOTES = 4;
    private static final int SEPTUPLET_GRADE = 7;
    private static final int SEPTUPLET_NORMAL_NOTES = 4;
    private static final int NO_DOTS = 0;

    // Staff position of a pitched note used in tuplet fixtures (any pitch will do)
    private static final int TEST_STAFF_POSITION = -2;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Track newTrack() throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ);
        return sequence.createTrack();
    }

    private static Track buildTrack(
        Line line,
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

    /**
     * Builds a line whose leading elements form one tuplet of the given ratio,
     * optionally followed by a plain crotchet whose NOTE_ON tick reveals exactly
     * where the tuplet closed.
     */
    private static Line tupletLine(
        int grade,
        int normalNotes,
        ElementType noteValue,
        boolean appendTrailingCrotchet,
        ElementType... memberTypes
    ) {
        var line = detachedLine();

        for (var type : memberTypes) {
            var element = type.newInstance();

            if (!type.isRest()) {
                element.setStaffPosition(TEST_STAFF_POSITION);
            }

            line.addElement(element);
        }

        line.addSpan(new Tuplet(
            line.getElement(0),
            line.getElement(memberTypes.length - 1),
            grade,
            normalNotes,
            noteValue,
            NO_DOTS
        ));

        if (appendTrailingCrotchet) {
            var trailing = ElementType.CROTCHET.newInstance();
            trailing.setStaffPosition(TEST_STAFF_POSITION);
            line.addElement(trailing);
        }

        return line;
    }

    private static ElementType[] repeated(ElementType type, int count) {
        var types = new ElementType[count];
        Arrays.fill(types, type);
        return types;
    }

    /** Sums the tuplet-adjusted durations of {@code count} elements starting at 0. */
    private static int sumDurations(Line line, int count) {
        var builder = new LineTrackBuilder(line);
        var total = 0;

        for (var i = 0; i < count; i++) {
            total += builder.getElementDurationWithTuplet(i);
        }

        return total;
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

        // A triplet quaver: a quaver (PPQ / 2) re-timed by 2/3
        private static final int TRIPLET_QUAVER_TICKS =
            (PPQ / 2) * TRIPLET_NORMAL_NOTES / TRIPLET_GRADE;

        // A triplet crotchet: a crotchet re-timed by 2/3
        private static final int TRIPLET_CROTCHET_TICKS =
            PPQ * TRIPLET_NORMAL_NOTES / TRIPLET_GRADE;

        // A compound duplet crotchet: a crotchet re-timed by 3/2
        private static final int DUPLET_CROTCHET_TICKS =
            PPQ * DUPLET_NORMAL_NOTES / DUPLET_GRADE;

        // 7 semiquavers in the time of 4 → exactly one crotchet
        private static final int SEPTUPLET_MEMBER_COUNT = SEPTUPLET_GRADE;
        private static final int SEPTUPLET_TOTAL_TICKS = PPQ;

        private static final int TRIPLET_MEMBER_COUNT = TRIPLET_GRADE;
        private static final int DUPLET_MEMBER_COUNT = DUPLET_GRADE;

        @Test
        void testNonTupletElementReturnsRawDuration() {
            // A crotchet (PPQ ticks) with no tuplet → raw duration returned unchanged.
            var line = detachedLine();
            line.addElement(crotchet());
            var builder = new LineTrackBuilder(line);

            var result = builder.getElementDurationWithTuplet(0);

            assertThat(result).isEqualTo(PPQ);
        }

        /**
         * A tuplet read from a file carries only its printed number until the load pass
         * derives the ratio, and playback must not try to scale by a ratio that is not there
         * yet. The load pass resolves or drops every tuplet before a song is playable, so
         * this should be unreachable — but the arithmetic divides by the printed number and
         * multiplies by a count that is zero while unresolved, so if it ever were reached
         * without the guard every note in the group would collapse to nothing and the
         * passage would go silent.
         */
        @Test
        void testUnresolvedTupletPlaysAtTheWrittenDuration() {
            var line = detachedLine();
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addSpan(Tuplet.withUnresolvedRatio(
                line.getElement(0), line.getElement(TRIPLET_MEMBER_COUNT - 1), TRIPLET_GRADE));

            var result = new LineTrackBuilder(line).getElementDurationWithTuplet(0);

            assertThat(result)
                .as("an unresolved ratio must leave the written duration alone, not zero it")
                .isEqualTo(PPQ);
        }

        @Test
        void testTripletElementDurationIsScaledDown() {
            // 3 quavers written as 3:2 → each sounds for 2/3 of a quaver.
            var line = tupletLine(
                TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, false,
                repeated(ElementType.QUAVER, TRIPLET_MEMBER_COUNT)
            );

            var result = new LineTrackBuilder(line).getElementDurationWithTuplet(0);

            assertThat(result).isEqualTo(TRIPLET_QUAVER_TICKS);
        }

        @Test
        void testTripletOfCrotchetsClosesExactly() {
            // 3 crotchets as 3:2 occupy exactly two crotchets.
            var line = tupletLine(
                TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, true,
                repeated(ElementType.CROTCHET, TRIPLET_MEMBER_COUNT)
            );

            var total = sumDurations(line, TRIPLET_MEMBER_COUNT);

            assertThat(total)
                .as("3:2 of crotchets occupies two crotchets exactly")
                .isEqualTo(TRIPLET_CROTCHET_TICKS * TRIPLET_MEMBER_COUNT);
            assertThat(total).isEqualTo(PPQ * TRIPLET_NORMAL_NOTES);
        }

        @Test
        void testCompoundDupletClosesExactly() {
            // 2 crotchets as 2:3 occupy exactly three crotchets — a compound-beat duplet.
            var line = tupletLine(
                DUPLET_GRADE, DUPLET_NORMAL_NOTES, ElementType.CROTCHET, true,
                repeated(ElementType.CROTCHET, DUPLET_MEMBER_COUNT)
            );

            var builder = new LineTrackBuilder(line);

            assertThat(builder.getElementDurationWithTuplet(0))
                .as("each duplet crotchet stretches by 3/2")
                .isEqualTo(DUPLET_CROTCHET_TICKS);
            assertThat(sumDurations(line, DUPLET_MEMBER_COUNT))
                .as("2:3 of crotchets occupies three crotchets exactly")
                .isEqualTo(PPQ * DUPLET_NORMAL_NOTES);
        }

        @Test
        void testSeptupletOfSemiquaversClosesOnExactlyOneCrotchet() {
            // 7 semiquavers as 7:4. Rounding each element on its own gives
            // 7 × round(96/7) = 98 ticks; rounding absolute positions gives 96.
            var line = tupletLine(
                SEPTUPLET_GRADE, SEPTUPLET_NORMAL_NOTES, ElementType.SEMIQUAVER, true,
                repeated(ElementType.SEMIQUAVER, SEPTUPLET_MEMBER_COUNT)
            );

            assertThat(sumDurations(line, SEPTUPLET_MEMBER_COUNT))
                .as("7:4 of semiquavers occupies exactly one crotchet")
                .isEqualTo(SEPTUPLET_TOTAL_TICKS);
        }

        @Test
        void testElementAfterSeptupletStartsWhereTupletCloses() throws Exception {
            var line = tupletLine(
                SEPTUPLET_GRADE, SEPTUPLET_NORMAL_NOTES, ElementType.SEMIQUAVER, true,
                repeated(ElementType.SEMIQUAVER, SEPTUPLET_MEMBER_COUNT)
            );

            var track = buildTrack(line, CROTCHET_TEMPO);
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);

            assertThat(noteOns)
                .as("seven tuplet members plus the trailing crotchet")
                .hasSize(SEPTUPLET_MEMBER_COUNT + 1);
            assertThat(noteOns.getLast().getTick())
                .as("the note after the septuplet starts exactly one crotchet in")
                .isEqualTo(SEPTUPLET_TOTAL_TICKS);
        }

        @Test
        void testRestInTupletConsumesItsScaledShare() throws Exception {
            // Triplet quaver / quaver rest / triplet quaver: the rest must eat its
            // third, so the last member sounds two triplet quavers in.
            var line = tupletLine(
                TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.QUAVER, true,
                ElementType.QUAVER, ElementType.QUAVER_REST, ElementType.QUAVER
            );

            var builder = new LineTrackBuilder(line);

            assertThat(builder.getElementDurationWithTuplet(1))
                .as("the rest is scaled like a note")
                .isEqualTo(TRIPLET_QUAVER_TICKS);

            var track = buildTrack(line, CROTCHET_TEMPO);
            var noteOns = eventsByCommand(track, ShortMessage.NOTE_ON);
            var soundingMembers = TRIPLET_MEMBER_COUNT - 1;

            assertThat(noteOns)
                .as("the sounding tuplet members plus the trailing crotchet")
                .hasSize(soundingMembers + 1)
                .extracting(MidiEvent::getTick)
                .containsExactly(
                    0L,
                    (long) (2 * TRIPLET_QUAVER_TICKS),
                    (long) (TRIPLET_MEMBER_COUNT * TRIPLET_QUAVER_TICKS)
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
                .isEqualTo(PPQ);

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
                .isEqualTo(2 * PPQ);
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
                .isEqualTo(2 * PPQ);
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
                .isEqualTo(PPQ);
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
                .isEqualTo(PPQ - CONNECTED_NOTE_OFF_OFFSET);
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
                .isEqualTo(PPQ);
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
                .isEqualTo(PPQ);
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
            var noteOffTick = (long) PPQ;
            var slideStartTick = SlideMidiHelper.calculateSustainTicks(PPQ);

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
            var slideDuration = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, PPQ);
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

            var slideDuration = Math.min(SlideMidiHelper.GRACE_SLIDE_TICKS, PPQ);
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
            var endTick = (long) PPQ;

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
            var endTick = (long) PPQ;

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

    private static Song songWithOneCrotchet() {
        var song = new Song();
        var line = detachedLine();
        line.addElement(crotchet());
        song.getLines().clear();
        song.getLines().add(line);
        return song;
    }
}
