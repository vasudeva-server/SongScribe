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

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.BeatUnit;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;

public final class TempoIO {

    // version 1.1
    static final String XML_TEMPO = "tempo";
    // version 1.0
    private static final String XML_TEMPO_CHANGE = "tempochange";
    private static final String XML_POS = "position";
    static final String XML_VISIBLE_TEMPO = "visibletempo";
    static final String XML_TEMPO_TYPE = "tempotype";
    static final String XML_TEMPO_DESCRIPTION = "tempodescription";
    static final String XML_DONT_SHOW_TEMPO = "dontshowtempo";

    // Maps v1.0 XML names (no underscores) to the canonical Duration constants.
    private static final Map<String, BeatUnit> LEGACY_TEMPO_DURATION_NAMES = Map.of(
        "SEMIBREVE", BeatUnit.SEMI_BREVE,
        "MINIMDOTTED", BeatUnit.MINIM_DOTTED,
        "CROTCHETDOTTED", BeatUnit.CROTCHET_DOTTED,
        "QUAVERDOTTED", BeatUnit.QUAVER_DOTTED
    );

    private TempoIO() {}

    public static class TempoReader {

        private static final Logger LOG = LoggerFactory.getLogger(TempoReader.class);

        // Whether a tempo element is open. A Tempo is a value built once, at the closing tag,
        // so there is no half-built instance to stand in for this.
        private boolean inTempo = false;
        private int pos10 = 0;

        // Every value the file states separately, held until the closing tag. A tempo is built
        // from all of them at once, and no tag is guaranteed to arrive, so each starts at the
        // default a tempo has when nothing states one.
        private int visibleTempo = Tempo.DEFAULT_BPM;
        private BeatUnit tempoType = Tempo.DEFAULT_TYPE;
        private String description = "";
        private boolean hideMetronome = false;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(20);

        public void startElement10(String qName) {
            startElement(qName, XML_TEMPO_CHANGE);
        }

        public void startElement11(String qName) {
            startElement(qName, XML_TEMPO);
        }

        private void startElement(String qName, String tempoTag) {
            if (qName.equals(tempoTag)) {
                inTempo = true;
                visibleTempo = Tempo.DEFAULT_BPM;
                tempoType = Tempo.DEFAULT_TYPE;
                description = "";
                hideMetronome = false;
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        // Shared by the v1.0 and v1.1 read paths so an unknown token is rejected identically on both.
        private static BeatUnit resolveTempoDuration(String str) throws SAXException {
            var upper = str.toUpperCase();
            var duration = LEGACY_TEMPO_DURATION_NAMES.get(upper);

            if (duration == null) {
                try {
                    duration = BeatUnit.valueOf(upper);
                } catch (IllegalArgumentException e) {
                    throw DocumentValidation.corrupt(LOG, "Corrupt document: unknown tempo duration: '{}'", str);
                }
            }

            return duration;
        }

        /**
         * @return {@code null} when no tempo element was opened
         */
        @Nullable
        private Tempo finishTempo() {
            if (!inTempo) {
                return null;
            }

            var read = TempoMarking.fromFile(description, hideMetronome);

            if (read.repaired()) {
                LOG.warn("Showing a hidden tempo that carries no description");
            }

            inTempo = false;
            return new Tempo(visibleTempo, tempoType, read.marking());
        }

        @Nullable
        public Tempo endElement10(String qName) throws SAXException {
            if (qName.equals(XML_TEMPO_CHANGE)) {
                return finishTempo();
            }

            return endTempoChild(qName);
        }

        @Nullable
        public Tempo endElement11(String qName) throws SAXException {
            if (qName.equals(XML_TEMPO)) {
                return finishTempo();
            }

            return endTempoChild(qName);
        }

        /**
         * @return {@code null} always: a tempo is answered only by the closing tempo tag
         */
        @Nullable
        private Tempo endTempoChild(String qName) throws SAXException {
            if (!inTempo) {
                return null;
            }

            //noinspection PointlessNullCheck
            if (lastTag != null && qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_POS -> pos10 = Integer.parseInt(str);
                    case XML_VISIBLE_TEMPO -> visibleTempo = Integer.parseInt(str);
                    case XML_TEMPO_TYPE -> tempoType = resolveTempoDuration(str);
                    case XML_TEMPO_DESCRIPTION -> description = str;
                    case XML_DONT_SHOW_TEMPO -> hideMetronome = true;
                    default -> { }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        public void characters(char[] ch, int start, int length) {
            if (lastTag != null) {
                value.append(ch, start, length);
            }
        }

        public int getPos10() {
            return pos10;
        }
    }
}
