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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Covers {@link Song#resolveBeatAt}, the backward walk that answers "what is the beat
 * here?" in a meterless score, plus a regression guard on {@code getTempoAt}, whose
 * behavior the MIDI builder depends on.
 */
class SongBeatResolutionTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;
    private static final int THIRD_ELEMENT = 2;

    private static final int NEAR_TEMPO_BPM = 100;
    private static final int FAR_TEMPO_BPM = 80;

    private static final int NO_EVENT = Song.BeatAt.NO_DEFINING_EVENT;

    /**
     * Appends a plain crotchet to the line, which must already belong to song and have
     * mutation tracking suspended.
     */
    private static StaffElement addPlainNote(Line line) {
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        return note;
    }

    /** Appends a crotchet carrying a beat change whose new beat is {@code beat}. */
    private static StaffElement addNoteWithBeatChange(Line line, Duration beat) {
        var note = addPlainNote(line);
        note.addAttachment(new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, beat)));
        return note;
    }

    /** Appends a crotchet carrying a tempo change whose tempo type is {@code tempoType}. */
    private static StaffElement addNoteWithTempoChange(Line line, Duration tempoType, int bpm) {
        var note = addPlainNote(line);
        note.addAttachment(new TempoChangeAttachment(note, tempoOf(tempoType, bpm)));
        return note;
    }

    private static Tempo tempoOf(Duration tempoType, int bpm) {
        return new Tempo(bpm, tempoType, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO);
    }

    // -----------------------------------------------------------------------
    // Hits
    // -----------------------------------------------------------------------

    @Test
    void testResolvesBeatFromAPrecedingBeatChange() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addNoteWithBeatChange(line, Duration.CROTCHET_DOTTED);
            addPlainNote(line);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, SECOND_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.CROTCHET_DOTTED, FIRST_LINE, FIRST_ELEMENT));
    }

    @Test
    void testResolvesBeatFromAPrecedingTempoChange() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addNoteWithTempoChange(line, Duration.MINIM, NEAR_TEMPO_BPM);
            addPlainNote(line);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, SECOND_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.MINIM, FIRST_LINE, FIRST_ELEMENT));
    }

    // -----------------------------------------------------------------------
    // Precedence is positional, not by attachment type
    // -----------------------------------------------------------------------

    @Test
    void testNearerBeatChangeWinsOverAnEarlierTempoChange() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addNoteWithTempoChange(line, Duration.MINIM, FAR_TEMPO_BPM);
            addNoteWithBeatChange(line, Duration.QUAVER);
            addPlainNote(line);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, THIRD_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.QUAVER, FIRST_LINE, SECOND_ELEMENT));
    }

    @Test
    void testNearerTempoChangeWinsOverAnEarlierBeatChange() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addNoteWithBeatChange(line, Duration.QUAVER);
            addNoteWithTempoChange(line, Duration.MINIM, NEAR_TEMPO_BPM);
            addPlainNote(line);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, THIRD_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.MINIM, FIRST_LINE, SECOND_ELEMENT));
    }

    // A metric-modulation marking is the more specific statement about the beat, so it
    // outranks a tempo marking's note value when one element carries both.
    @Test
    void testBeatChangeWinsWhenOneElementCarriesBothKinds() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            var note = addNoteWithBeatChange(line, Duration.QUAVER);
            note.addAttachment(new TempoChangeAttachment(note, tempoOf(Duration.MINIM, NEAR_TEMPO_BPM)));
            addPlainNote(line);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, SECOND_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.QUAVER, FIRST_LINE, FIRST_ELEMENT));
    }

    // -----------------------------------------------------------------------
    // Walk shape
    // -----------------------------------------------------------------------

    @Test
    void testWalksBackIntoEarlierLinesWhenTheAnchorLineDefinesNoBeat() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            addNoteWithBeatChange(song.getLine(FIRST_LINE), Duration.MINIM_DOTTED);

            var secondLine = new Line(song);
            song.addLine(secondLine);
            addPlainNote(secondLine);
        });

        assertThat(song.resolveBeatAt(SECOND_LINE, FIRST_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.MINIM_DOTTED, FIRST_LINE, FIRST_ELEMENT));
    }

    // The beat in effect AT the anchor cannot be defined by an event that comes after it,
    // so the anchor's own line is scanned only from the anchor backward.
    @Test
    void testIgnoresABeatDefiningEventLaterOnTheAnchorLine() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addPlainNote(line);
            addNoteWithBeatChange(line, Duration.QUAVER);
        });

        assertThat(song.resolveBeatAt(FIRST_LINE, FIRST_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.CROTCHET, NO_EVENT, NO_EVENT));
    }

    // -----------------------------------------------------------------------
    // Fallbacks — no element defined the beat, so both indexes are NO_DEFINING_EVENT
    // -----------------------------------------------------------------------

    @Test
    void testFallsBackToTheSongTempoWhenNoEventPrecedesTheAnchor() {
        var song = new Song();
        song.setTempo(tempoOf(Duration.MINIM, NEAR_TEMPO_BPM));

        song.withoutMutationTracking(() -> addPlainNote(song.getLine(FIRST_LINE)));

        assertThat(song.resolveBeatAt(FIRST_LINE, FIRST_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.MINIM, NO_EVENT, NO_EVENT));
    }

    @Test
    void testFallsBackToTheQuarterNoteWhenTheSongHasNoTempo() {
        var song = new Song();
        assertThat(song.getTempo()).isNull();

        assertThat(song.resolveBeatAt(FIRST_LINE, FIRST_ELEMENT))
            .isEqualTo(new Song.BeatAt(Duration.CROTCHET, NO_EVENT, NO_EVENT));
    }

    // -----------------------------------------------------------------------
    // getTempoAt regression — MidiSequenceBuilder reads the whole Tempo, BPM included
    // -----------------------------------------------------------------------

    @Test
    void testGetTempoAtStillReturnsTheNearestPrecedingTempoWithItsBpm() {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(FIRST_LINE);
            addNoteWithTempoChange(line, Duration.MINIM, FAR_TEMPO_BPM);
            addNoteWithTempoChange(line, Duration.QUAVER, NEAR_TEMPO_BPM);
            addPlainNote(line);
        });

        var tempo = song.getTempoAt(FIRST_LINE, THIRD_ELEMENT);
        assertThat(tempo.getVisibleTempo()).isEqualTo(NEAR_TEMPO_BPM);
        assertThat(tempo.getTempoType()).isEqualTo(Duration.QUAVER);
    }
}
