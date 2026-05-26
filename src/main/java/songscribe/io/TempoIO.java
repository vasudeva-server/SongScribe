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

import java.io.PrintWriter;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Duration;
import songscribe.dom.Tempo;

public final class TempoIO {

    // version 1.1
    public static final String XML_TEMPO = "tempo";
    // version 1.0
    public static final String XML_TEMPO_CHANGE = "tempochange";
    public static final String XML_POS = "position";
    public static final String XML_VISIBLE_TEMPO = "visibletempo";
    public static final String XML_TEMPO_TYPE = "tempotype";
    public static final String XML_TEMPO_DESCRIPTION = "tempodescription";
    public static final String XML_DONT_SHOW_TEMPO = "dontshowtempo";

    // Maps v1.0 XML names (no underscores) to the canonical Duration constants.
    private static final Map<String, Duration> LEGACY_TEMPO_DURATION_NAMES = Map.of(
        "SEMIBREVE", Duration.SEMI_BREVE,
        "MINIMDOTTED", Duration.MINIM_DOTTED,
        "CROTCHETDOTTED", Duration.CROTCHET_DOTTED,
        "QUAVERDOTTED", Duration.QUAVER_DOTTED
    );

    private TempoIO() {}

    public static void writeTempo(Tempo t, PrintWriter pw, int indent) {
        for (var i = 0; i < indent; i++) {
            pw.print(' ');
        }

        pw.println('<' + XML_TEMPO + '>');
        XML.setIndent(indent + 2);
        XML.writeValue(
            pw,
            XML_VISIBLE_TEMPO,
            Integer.toString(t.getVisibleTempo())
        );
        XML.writeValue(pw, XML_TEMPO_TYPE, t.getTempoType().name());
        XML.writeValue(pw, XML_TEMPO_DESCRIPTION, t.getTempoDescription());

        if (!t.shouldShowTempo()) {
            XML.writeEmptyTag(pw, XML_DONT_SHOW_TEMPO);
        }

        for (var i = 0; i < indent; i++) {
            pw.print(' ');
        }

        pw.println("</" + XML_TEMPO + '>');
    }

    public static class TempoReader {

        @Nullable
        private Tempo tempo = null;
        private int pos10 = 0;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(20);

        public void startElement10(String qName) {
            if (qName.equals(XML_TEMPO_CHANGE)) {
                tempo = new Tempo();
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        public void startElement11(String qName) {
            if (qName.equals(XML_TEMPO)) {
                tempo = new Tempo();
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        @Nullable
        public Tempo endElement10(String qName) {
            if (qName.equals(XML_TEMPO_CHANGE)) {
                return tempo;
            }
            if (tempo == null) {
                return null;
            }
            //noinspection PointlessNullCheck
            if (lastTag != null && qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_POS -> pos10 = Integer.parseInt(str);
                    case XML_VISIBLE_TEMPO -> tempo.setVisibleTempo(
                        Integer.parseInt(str)
                    );
                    case XML_TEMPO_TYPE -> {
                        var upper = str.toUpperCase();
                        var duration = LEGACY_TEMPO_DURATION_NAMES.get(upper);

                        if (duration == null) {
                            duration = Duration.valueOf(upper);
                        }

                        tempo.setTempoType(duration);
                    }
                    case XML_TEMPO_DESCRIPTION -> tempo.setTempoDescription(
                        str
                    );
                    case XML_DONT_SHOW_TEMPO -> tempo.setShowTempo(false);
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        @Nullable
        public Tempo endElement11(String qName) {
            if (qName.equals(XML_TEMPO)) {
                return tempo;
            }
            if (tempo == null) {
                return null;
            }
            //noinspection PointlessNullCheck
            if (lastTag != null && qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_VISIBLE_TEMPO -> tempo.setVisibleTempo(
                        Integer.parseInt(str)
                    );
                    case XML_TEMPO_TYPE -> tempo.setTempoType(
                        Duration.valueOf(str)
                    );
                    case XML_TEMPO_DESCRIPTION -> tempo.setTempoDescription(
                        str
                    );
                    case XML_DONT_SHOW_TEMPO -> tempo.setShowTempo(false);
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
