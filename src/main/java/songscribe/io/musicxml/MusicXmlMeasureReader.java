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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.io.musicxml.MusicXmlReader.Where;

/**
 * Parses the part / measure / attributes / key / barline subtree of a MusicXML
 * document on behalf of {@link MusicXmlReader}. Mirrors the writer's
 * {@link MusicXmlMeasureWriter}.
 * <p>
 * This is not a {@link org.xml.sax.helpers.DefaultHandler}: {@code MusicXmlReader}
 * owns the two SAX dispatch switches and delegates each measure-owned
 * {@link Where} leaf state's start/end case here. This class holds no scratch
 * fields of its own — the line and key-carry spine lives on the orchestrator, and
 * these handlers read and advance it through the reader-as-context accessors on
 * {@code MusicXmlReader} ({@code setWhere}, {@code valueString},
 * {@code songOrNull}, {@code getCurrentLine}, {@code applyFifthsToLine}, and the
 * key-carry getters/setters). The {@code <barline>} states drive the injected
 * {@link BarlineParser}. The {@link Where} transition graph is documented on
 * {@link MusicXmlReader}.
 * <p>
 * The {@code MEASURE} start dispatch is a cross-group hub (it fans out to the
 * note, direction, barline, and {@code <print new-system>} line-start branches)
 * and, like the {@code SCORE_PARTWISE} hub, stays inline in the orchestrator
 * rather than moving here — see decision 6 in the refactor plan. This class owns
 * only the pure measure-group leaf states: {@code PART_LIST}, {@code SCORE_PART},
 * {@code PART}, {@code ATTRIBUTES}, {@code KEY}, {@code FIFTHS}, {@code BARLINE},
 * {@code BAR_STYLE}, and the {@code MEASURE} end transition.
 */
final class MusicXmlMeasureReader {

    private final MusicXmlReader reader;

    // Parses <barline> elements (bar-style/repeat/ending accumulation and the
    // REPEAT_LEFT_RIGHT straddling pair). The same instance the orchestrator
    // holds; the BARLINE/BAR_STYLE states drive it here while the orchestrator's
    // convergence methods (startNewLine, </part> flush) drive its flushes.
    private final BarlineParser barlines;

    MusicXmlMeasureReader(MusicXmlReader reader, BarlineParser barlines) {
        this.reader = reader;
        this.barlines = barlines;
    }

    // -------------------------------------------------------------------------
    // startElement measure cases (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    void handleStartPartList(String qName) {
        reader.startTransition(qName, MusicXmlTags.SCORE_PART, Where.SCORE_PART);
    }

    void handleStartPart(String qName) {
        reader.startTransition(qName, MusicXmlTags.MEASURE, Where.MEASURE);
    }

    void handleStartAttributes(String qName) {
        reader.startTransition(qName, MusicXmlTags.KEY, Where.KEY);
    }

    void handleStartKey(String qName) {
        reader.startTransition(qName, MusicXmlTags.FIFTHS, Where.FIFTHS);
    }

    void handleStartBarline(String qName, Attributes attributes) {
        switch (qName) {
            case MusicXmlTags.BAR_STYLE -> reader.setWhere(Where.BAR_STYLE);
            case MusicXmlTags.ENDING ->
                // <ending> is an empty element; collect it and stay in BARLINE.
                // Resolution to the barline's StaffElement happens once the
                // barline is appended (see BarlineParser.processBarline /
                // EndingResolver.attachBarlineEndings).
                barlines.addEndingMarker(new EndingResolver.EndingMarker(
                    attributes.getValue(MusicXmlTags.ATTR_NUMBER),
                    attributes.getValue(MusicXmlTags.ATTR_TYPE)
                ));
            case MusicXmlTags.REPEAT ->
                barlines.setRepeatDirection(attributes.getValue(MusicXmlTags.ATTR_DIRECTION));
        }
    }

    // -------------------------------------------------------------------------
    // endElement measure cases (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndBarStyle(String qName) {
        if (qName.equals(MusicXmlTags.BAR_STYLE)) {
            barlines.setBarStyle(reader.valueString());
            reader.setWhere(Where.BARLINE);
        }
    }

    void handleEndBarline(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.BARLINE)) {
            barlines.processBarline();
            reader.setWhere(Where.MEASURE);
        }
    }

    void handleEndFifths(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.FIFTHS)) {
            var fifths = MusicXmlUnits.parseIntOrThrow(MusicXmlTags.FIFTHS, reader.valueString());
            var song = reader.songOrNull();

            if (song == null) {
                throw new SAXException("Unexpected <fifths> outside <score-partwise>");
            }

            if (reader.songDefaultKeySet()) {
                // A later line's key change: advance the running key and
                // apply it to the line now being built.
                reader.setRunningFifths(fifths);

                var currentLine = reader.getCurrentLine();

                if (currentLine != null) {
                    reader.applyFifthsToLine(currentLine, fifths);
                }
            } else {
                // Measure 1: the song default, which also seeds the running
                // key. Line 1 itself is materialized from the default when
                // it is added, so it is not set here.
                song.setDefaultKeyAccidentalCount(KeySignatureMapping.accidentalCount(fifths));
                song.setDefaultKeyType(KeySignatureMapping.keyType(fifths));
                reader.setRunningFifths(fifths);
                reader.setSongDefaultKeySet(true);
            }

            reader.setWhere(Where.KEY);
        }
    }
}
