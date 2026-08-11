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
import static org.assertj.core.api.Assertions.assertThatCode;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * Round-trip coverage for metric-modulation {@code <direction>} /
 * {@code <metronome>} emission and parsing (Phase 4): the two-{@code <metronome-note>}
 * relation form. Each {@link BeatChange} variant produced by
 * {@link BeatChange#fromLegacyName} must survive the write/read cycle with its
 * {@code duration()} and {@code beat()} intact, bound to the note it marks.
 */
class MusicXmlMetricModulationRoundTripTest extends UnitTest {

    // Every canonical metric-modulation variant SongScribe recognises, keyed by
    // its legacy enum name. Covers dotted and undotted note values on both the
    // left (duration) and right (beat) sides.
    private static final String QUAVER_EQUALS_QUAVER = "QUAVER_EQUALS_QUAVER";
    private static final String DOTTED_CROCHET_EQUALS_MINIM = "DOTTED_CROCHET_EQUALS_MINIM";
    private static final String MINIM_EQUALS_DOTTED_CROCHET = "MINIM_EQUALS_DOTTED_CROCHET";
    private static final String CROTCHET_EQUALS_DOTTED_CROCHET = "CROTCHET_EQUALS_DOTTED_CROCHET";
    private static final String DOTTED_CROCHET_EQUALS_CROCHET = "DOTTED_CROCHET_EQUALS_CROCHET";

    // A three-note line puts the marked note between a plain note before and after
    // it, so both placement (binds to the right note) and non-bleed (neighbours
    // stay unmarked) are checked.
    private static final int NOTE_COUNT = 3;
    private static final int MARKED_INDEX = 1;

    // A two-line song exercises a metric modulation across a system break.
    private static final int TWO_LINE_COUNT = 2;
    private static final int SECOND_LINE_INDEX = 1;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static @Nullable BeatChange beatChangeOf(Line line, int elementIndex) {
        var attachment = line.getElement(elementIndex).findAttachment(BeatChangeAttachment.class);
        return attachment == null ? null : attachment.getBeatChange();
    }

    /**
     * Builds a single-line song of {@link #NOTE_COUNT} quarter notes with
     * {@code beatChange} attached to the note at {@link #MARKED_INDEX}.
     */
    private Song buildBeatChangeSong(BeatChange beatChange) {
        return buildSong(line -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                var note = crotchet();
                line.addElement(note);

                if (i == MARKED_INDEX) {
                    note.addAttachment(new BeatChangeAttachment(note, beatChange));
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        QUAVER_EQUALS_QUAVER,
        DOTTED_CROCHET_EQUALS_MINIM,
        MINIM_EQUALS_DOTTED_CROCHET,
        CROTCHET_EQUALS_DOTTED_CROCHET,
        DOTTED_CROCHET_EQUALS_CROCHET,
    })
    void testBeatChangeVariantRoundTrips(String legacyName) throws Exception {
        var beatChange = BeatChange.fromLegacyName(legacyName);
        var song = buildBeatChangeSong(beatChange);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        var reloadedChange = beatChangeOf(line, MARKED_INDEX);
        assertThat(reloadedChange).as("%s: marked note carries the beat change", legacyName).isNotNull();

        assertThat(reloadedChange.duration())
            .as("%s: duration (left note value)", legacyName)
            .isEqualTo(beatChange.duration());
        assertThat(reloadedChange.beat())
            .as("%s: beat (right note value)", legacyName)
            .isEqualTo(beatChange.beat());

        assertThat(beatChangeOf(line, 0))
            .as("%s: note before is unmarked", legacyName).isNull();
        assertThat(beatChangeOf(line, NOTE_COUNT - 1))
            .as("%s: note after is unmarked", legacyName).isNull();
    }

    @Test
    void testBeatChangeOnLaterLineRoundTrips() throws Exception {
        // A beat change on a note in line 2 exercises the reader's cross-line
        // direction binding (the metric-modulation <direction> and its note
        // straddle a system break).
        var beatChange = BeatChange.fromLegacyName(DOTTED_CROCHET_EQUALS_MINIM);

        var song = buildSong(
            line -> line.addElement(crotchet()),
            line -> {
                var note = crotchet();
                line.addElement(note);
                note.addAttachment(new BeatChangeAttachment(note, beatChange));
            }
        );

        var reloaded = roundTrip(song);

        assertThat(reloaded.getLines()).as("line count").hasSize(TWO_LINE_COUNT);

        var reloadedChange = beatChangeOf(reloaded.getLine(SECOND_LINE_INDEX), 0);
        assertThat(reloadedChange).as("line 2 note carries the beat change").isNotNull();

        assertThat(reloadedChange.duration()).as("duration (left note value)").isEqualTo(beatChange.duration());
        assertThat(reloadedChange.beat()).as("beat (right note value)").isEqualTo(beatChange.beat());
        assertThat(beatChangeOf(reloaded.getLine(0), 0)).as("line 1 note is unmarked").isNull();
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testMetricModulationValidatesAgainstSchema() throws Exception {
        // A dotted-left/undotted-right modulation exercises <metronome-dot/> and the
        // two-note-relation grammar in a single <metronome>.
        var beatChange = BeatChange.fromLegacyName(DOTTED_CROCHET_EQUALS_MINIM);
        var song = buildBeatChangeSong(beatChange);

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("metric-modulation direction output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
