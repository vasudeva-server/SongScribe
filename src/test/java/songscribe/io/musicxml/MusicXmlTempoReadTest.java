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
package songscribe.io.musicxml;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.TempoMarking;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

class MusicXmlTempoReadTest extends UnitTest {

    private static final int BPM = 92;

    private static final String DESCRIPTION = "Meno mosso";

    /**
     * A {@code <metronome print-object="no">} with the given sibling {@code <words>} text, or
     * with no {@code <words>} at all when {@code description} is empty.
     *
     * @param description the text of the {@code <words>}, {@code ""} for no {@code <words>}
     * @return the {@code <direction>} body, ready for a measure
     */
    private static String hiddenMetronome(String description) {
        var words = description.isEmpty()
            ? ""
            : "        " + openTag(MusicXmlTags.DIRECTION_TYPE) + '\n'
                + "          " + element(MusicXmlTags.WORDS, description) + '\n'
                + "        " + closeTag(MusicXmlTags.DIRECTION_TYPE) + '\n';

        return
            "      " + openTag(MusicXmlTags.DIRECTION) + '\n' +
            "        " + openTag(MusicXmlTags.DIRECTION_TYPE) + '\n' +
            "          " + openTag(
                MusicXmlTags.METRONOME,
                MusicXmlTags.ATTR_PRINT_OBJECT,
                MusicXmlTags.NO
            ) + '\n' +
            "            " + element(MusicXmlTags.BEAT_UNIT, NoteTypeMapping.TYPE_QUARTER) + '\n' +
            "            " + element(MusicXmlTags.PER_MINUTE, Integer.toString(BPM)) + '\n' +
            "          " + closeTag(MusicXmlTags.METRONOME) + '\n' +
            "        " + closeTag(MusicXmlTags.DIRECTION_TYPE) + '\n' +
            words +
            "      " + closeTag(MusicXmlTags.DIRECTION) + '\n';
    }

    @Test
    void testHiddenTempoWithNoDescriptionIsShownWithItsBeatAndBpmKept() throws Exception {
        var tempo = parse(scoreWithMeasureBody(hiddenMetronome(""))).getTempo();

        assertThat(tempo.marking()).isEqualTo(new TempoMarking.Metronome(""));

        // The repair touches the marking and nothing else, so the beat and the BPM survive it.
        assertThat(tempo.visibleTempo()).isEqualTo(BPM);
        assertThat(tempo.tempoType()).isEqualTo(Duration.CROTCHET);
    }

    @Test
    void testHiddenTempoWithADescriptionStaysTextOnly() throws Exception {
        var tempo = parse(scoreWithMeasureBody(hiddenMetronome(DESCRIPTION))).getTempo();

        assertThat(tempo.marking()).isEqualTo(new TempoMarking.TextOnly(DESCRIPTION));
    }
}
