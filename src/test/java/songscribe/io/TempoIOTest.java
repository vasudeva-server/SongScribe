/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.io;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;

import static org.assertj.core.api.Assertions.assertThat;

class TempoIOTest extends UnitTest {

    private static final int BPM = 100;
    private static final String DESCRIPTION = "Allegro";

    /**
     * Feeds one complete v1.1 {@code <tempo>} element that asks to hide the mark, tag by tag,
     * the way the SAX handlers in {@link SongIO} and {@link StaffElementIO} do.
     *
     * @param description the value of the {@code tempodescription} tag, which may be empty
     * @return the tempo the closing tag answers
     */
    private static Tempo readHiddenTempo(String description) throws Exception {
        var reader = new TempoIO.TempoReader();
        reader.startElement11(TempoIO.XML_TEMPO);

        writeValue(reader, TempoIO.XML_VISIBLE_TEMPO, Integer.toString(BPM));
        writeValue(reader, TempoIO.XML_TEMPO_TYPE, Duration.CROTCHET.name());
        writeValue(reader, TempoIO.XML_TEMPO_DESCRIPTION, description);
        writeValue(reader, TempoIO.XML_DONT_SHOW_TEMPO, "");

        var tempo = reader.endElement11(TempoIO.XML_TEMPO);
        assertThat(tempo).isNotNull();

        return tempo;
    }

    /** Feeds one child tag of the tempo element: open, characters, close. */
    private static void writeValue(TempoIO.TempoReader reader, String tag, String value) throws Exception {
        reader.startElement11(tag);
        reader.characters(value.toCharArray(), 0, value.length());
        reader.endElement11(tag);
    }

    @Test
    void testHiddenTempoWithNoDescriptionIsShownWithItsBeatAndBpmKept() throws Exception {
        var tempo = readHiddenTempo("");

        assertThat(tempo.getMarking()).isEqualTo(new TempoMarking.Metronome(""));

        // The repair touches the marking and nothing else, so the beat and the BPM survive it.
        assertThat(tempo.getVisibleTempo()).isEqualTo(BPM);
        assertThat(tempo.getTempoType()).isEqualTo(Duration.CROTCHET);
    }

    @Test
    void testHiddenTempoWithADescriptionStaysTextOnly() throws Exception {
        var tempo = readHiddenTempo(DESCRIPTION);

        assertThat(tempo.getMarking()).isEqualTo(new TempoMarking.TextOnly(DESCRIPTION));
    }

    @Test
    void testHiddenTempoWhoseDescriptionIsOnlyWhitespaceIsShown() throws Exception {
        var tempo = readHiddenTempo("   ");

        assertThat(tempo.getMarking()).isEqualTo(new TempoMarking.Metronome(""));
    }

    @Test
    void testDescriptionIsStrippedOfSurroundingWhitespace() throws Exception {
        var tempo = readHiddenTempo("  " + DESCRIPTION + "  ");

        assertThat(tempo.getMarking()).isEqualTo(new TempoMarking.TextOnly(DESCRIPTION));
    }
}
