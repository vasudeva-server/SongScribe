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

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

/**
 * Drives {@link MetronomeResolver} directly to cover the import-robustness
 * warn-and-drop branches that never fire through {@link MusicXmlWriter} (which
 * only emits well-formed, mapped output). Each test accumulates a
 * {@code <direction>} through the package-private state-machine calls, closes it
 * with {@link MetronomeResolver#endDirection()}, then binds the result to a
 * standalone note and inspects whether an attachment was produced.
 */
class MetronomeResolverTest extends UnitTest {

    private static final int TEMPO_BPM = 120;
    private static final String UNKNOWN_TOKEN = "bogus";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StaffElement newNote() {
        return ElementType.CROTCHET.newInstance();
    }

    private static void addMetronomeNote(MetronomeResolver resolver, String token) {
        resolver.beginMetronomeNote();
        resolver.setMetronomeType(token);
        resolver.endMetronomeNote();
    }

    // -------------------------------------------------------------------------
    // Positive controls — prove the driver actually can produce an attachment,
    // so the "no attachment" negatives below aren't passing on a broken setup.
    // -------------------------------------------------------------------------

    @Test
    void testCompleteTempoBuildsAttachment() {
        var resolver = new MetronomeResolver();
        resolver.setBeatUnitToken(NoteTypeMapping.TYPE_QUARTER);
        resolver.setVisibleTempo(TEMPO_BPM);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveTempo(note);

        var attachment = note.findAttachment(TempoChangeAttachment.class);
        assertThat(attachment).as("complete tempo binds to the note").isNotNull();

        if (attachment != null) {
            var tempo = attachment.getTempo();
            assertThat(tempo.getVisibleTempo()).as("visible tempo").isEqualTo(TEMPO_BPM);
            assertThat(tempo.getTempoType()).as("beat-unit duration").isEqualTo(Duration.CROTCHET);
        }
    }

    @Test
    void testCompleteMetricModulationBuildsAttachment() {
        var resolver = new MetronomeResolver();
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_EIGHTH);
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_EIGHTH);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveBeatChange(note);

        var attachment = note.findAttachment(BeatChangeAttachment.class);
        assertThat(attachment).as("complete metric modulation binds to the note").isNotNull();

        if (attachment != null) {
            var beatChange = attachment.getBeatChange();
            assertThat(beatChange.duration()).as("left note value").isEqualTo(Duration.QUAVER);
            assertThat(beatChange.beat()).as("right note value").isEqualTo(Duration.QUAVER);
        }
    }

    // -------------------------------------------------------------------------
    // Warn-and-drop branches — a malformed/foreign <metronome> must be dropped,
    // never bound to a note.
    // -------------------------------------------------------------------------

    @Test
    void testUnrecognisedBeatUnitTokenIsDropped() {
        var resolver = new MetronomeResolver();
        resolver.setBeatUnitToken(UNKNOWN_TOKEN);
        resolver.setVisibleTempo(TEMPO_BPM);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveTempo(note);

        assertThat(note.findAttachment(TempoChangeAttachment.class))
            .as("an unrecognised beat-unit token builds no tempo")
            .isNull();
    }

    @Test
    void testBeatUnitWithoutPerMinuteIsDropped() {
        var resolver = new MetronomeResolver();
        resolver.setBeatUnitToken(NoteTypeMapping.TYPE_QUARTER);
        // No <per-minute> — an incomplete metronome builds nothing.
        resolver.endDirection();

        var note = newNote();
        resolver.resolveTempo(note);

        assertThat(note.findAttachment(TempoChangeAttachment.class))
            .as("a beat-unit with no per-minute builds no tempo")
            .isNull();
    }

    @Test
    void testMetricModulationWithWrongNoteCountIsDropped() {
        var resolver = new MetronomeResolver();
        // Only one <metronome-note>; the modulation form requires exactly two.
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_QUARTER);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveBeatChange(note);

        assertThat(note.findAttachment(BeatChangeAttachment.class))
            .as("a one-note metric modulation builds no beat change")
            .isNull();
    }

    @Test
    void testMetricModulationWithUnrecognisedNoteIsDropped() {
        var resolver = new MetronomeResolver();
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_QUARTER);
        addMetronomeNote(resolver, UNKNOWN_TOKEN);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveBeatChange(note);

        assertThat(note.findAttachment(BeatChangeAttachment.class))
            .as("an unrecognised metronome-note builds no beat change")
            .isNull();
    }

    @Test
    void testMetronomeNoteMissingTypeIsDropped() {
        var resolver = new MetronomeResolver();
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_QUARTER);
        // A <metronome-note> with no <metronome-type> is dropped by endMetronomeNote,
        // leaving a one-note (wrong-count) modulation that builds nothing.
        resolver.beginMetronomeNote();
        resolver.endMetronomeNote();
        resolver.endDirection();

        var note = newNote();
        resolver.resolveBeatChange(note);

        assertThat(note.findAttachment(BeatChangeAttachment.class))
            .as("a metronome-note missing its type is dropped")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Boundary drop — a finished direction with no following note.
    // -------------------------------------------------------------------------

    @Test
    void testFlushPendingTempoDropsUnboundTempo() {
        var resolver = new MetronomeResolver();
        resolver.setBeatUnitToken(NoteTypeMapping.TYPE_QUARTER);
        resolver.setVisibleTempo(TEMPO_BPM);
        resolver.endDirection();

        // Part boundary before any note binds the tempo.
        resolver.flushPendingTempo();

        var note = newNote();
        resolver.resolveTempo(note);

        assertThat(note.findAttachment(TempoChangeAttachment.class))
            .as("a flushed tempo does not bind to a later note")
            .isNull();
    }

    @Test
    void testFlushPendingBeatChangeDropsUnboundModulation() {
        var resolver = new MetronomeResolver();
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_EIGHTH);
        addMetronomeNote(resolver, NoteTypeMapping.TYPE_EIGHTH);
        resolver.endDirection();

        resolver.flushPendingBeatChange();

        var note = newNote();
        resolver.resolveBeatChange(note);

        assertThat(note.findAttachment(BeatChangeAttachment.class))
            .as("a flushed metric modulation does not bind to a later note")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Reset — an incomplete direction must not corrupt the next direction.
    // -------------------------------------------------------------------------

    @Test
    void testIncompleteDirectionDoesNotLeakIntoNext() {
        var resolver = new MetronomeResolver();

        // An incomplete tempo direction (beat-unit, no per-minute) is dropped.
        resolver.setBeatUnitToken(NoteTypeMapping.TYPE_QUARTER);
        resolver.endDirection();

        // A subsequent complete tempo direction must still build cleanly.
        resolver.setBeatUnitToken(NoteTypeMapping.TYPE_QUARTER);
        resolver.setVisibleTempo(TEMPO_BPM);
        resolver.endDirection();

        var note = newNote();
        resolver.resolveTempo(note);

        var attachment = note.findAttachment(TempoChangeAttachment.class);
        assertThat(attachment).as("the next direction builds after a dropped one").isNotNull();

        if (attachment != null) {
            assertThat(attachment.getTempo().getVisibleTempo())
                .as("no state leaked from the dropped direction")
                .isEqualTo(TEMPO_BPM);
        }
    }
}
