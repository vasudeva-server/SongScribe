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
 * Parses the {@code <direction>} subtree (dynamics-as-direction wedges, metronome
 * tempo / metric-modulation marks, and annotation words) of a MusicXML document on
 * behalf of {@link MusicXmlReader}. Mirrors the writer's
 * {@link MusicXmlDirectionWriter} and {@link MusicXmlHairpinWriter}.
 * <p>
 * This is not a {@link org.xml.sax.helpers.DefaultHandler}: {@code MusicXmlReader}
 * owns the two SAX dispatch switches and delegates each direction-owned
 * {@link Where} state's start/end case here. This class holds no scratch fields of
 * its own — the parse spine lives on the orchestrator, and these handlers read and
 * advance it through the reader-as-context accessors on {@code MusicXmlReader}
 * ({@code setWhere}, {@code valueString}, {@code endTransition}). The injected
 * {@link MetronomeResolver}, {@link WedgeResolver}, and {@link AnnotationResolver}
 * are the same instances the orchestrator holds; the direction states drive their
 * accumulation here while the orchestrator's {@code </part>} flush block drives
 * their flushes. The {@link Where} transition graph is documented on
 * {@link MusicXmlReader}.
 * <p>
 * This class owns the direction-group states: {@code DIRECTION},
 * {@code DIRECTION_TYPE}, {@code WEDGE}, {@code SOUND}, {@code METRONOME},
 * {@code BEAT_UNIT}, {@code BEAT_UNIT_DOT}, {@code PER_MINUTE}, {@code WORDS},
 * {@code METRONOME_NOTE}, {@code METRONOME_TYPE}, {@code METRONOME_DOT}, and
 * {@code METRONOME_RELATION}. The {@code MEASURE} start dispatch that opens a
 * {@code <direction>} (calling {@code annotations.beginDirection}) is part of the
 * cross-group {@code MEASURE} hub and stays inline in the orchestrator.
 */
final class MusicXmlDirectionReader {

    private final MusicXmlReader reader;

    // Measure-level tempo / metric-modulation direction state machine. The same
    // instance the orchestrator holds; the metronome states drive it here while
    // the orchestrator's </part> flush block drives its flushes.
    private final MetronomeResolver metronome;

    // Measure-level hairpin wedge state machine. Same shared instance as above.
    private final WedgeResolver wedges;

    // Measure-level annotation direction state machine. Same shared instance.
    private final AnnotationResolver annotations;

    MusicXmlDirectionReader(
        MusicXmlReader reader,
        MetronomeResolver metronome,
        WedgeResolver wedges,
        AnnotationResolver annotations
    ) {
        this.reader = reader;
        this.metronome = metronome;
        this.wedges = wedges;
        this.annotations = annotations;
    }

    // -------------------------------------------------------------------------
    // startElement direction cases (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    void handleStartDirection(String qName) {
        reader.startTransition(qName, MusicXmlTags.DIRECTION_TYPE, Where.DIRECTION_TYPE);
        // <sound tempo> is write-forward only; the visible tempo is recovered
        // from <metronome>, so this carries no read state.
        reader.startTransition(qName, MusicXmlTags.SOUND, Where.SOUND);
    }

    void handleStartDirectionType(String qName, Attributes attributes) throws SAXException {
        if (qName.equals(MusicXmlTags.WEDGE)) {
            wedges.handleWedge(attributes);
            reader.setWhere(Where.WEDGE);
        } else if (qName.equals(MusicXmlTags.METRONOME)) {
            metronome.beginMetronome(attributes);
            reader.setWhere(Where.METRONOME);
        } else if (qName.equals(MusicXmlTags.WORDS)) {
            // For an annotation direction, the halign and relative-y
            // recover the annotation's alignment and user Y offset; the
            // text arrives at </words>.
            if (annotations.isAnnotationDirection()) {
                annotations.setXAlignment(
                    TextAlignmentMapping.xAlignment(attributes.getValue(MusicXmlTags.ATTR_HALIGN))
                );
                annotations.setUserYOffsetSs(
                    MusicXmlUnits.optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_Y)
                );
            }

            reader.setWhere(Where.WORDS);
        }
    }

    void handleStartMetronome(String qName) {
        if (qName.equals(MusicXmlTags.BEAT_UNIT)) {
            reader.setWhere(Where.BEAT_UNIT);
        } else if (qName.equals(MusicXmlTags.BEAT_UNIT_DOT)) {
            metronome.addBeatUnitDot();
            reader.setWhere(Where.BEAT_UNIT_DOT);
        } else if (qName.equals(MusicXmlTags.PER_MINUTE)) {
            reader.setWhere(Where.PER_MINUTE);
        } else if (qName.equals(MusicXmlTags.METRONOME_NOTE)) {
            metronome.beginMetronomeNote();
            reader.setWhere(Where.METRONOME_NOTE);
        } else if (qName.equals(MusicXmlTags.METRONOME_RELATION)) {
            // <metronome-relation> is always "equals"; its position (between
            // the two note groups) carries no read state beyond marking the
            // modulation form, which the metronome-notes already do.
            reader.setWhere(Where.METRONOME_RELATION);
        }
    }

    void handleStartMetronomeNote(String qName) {
        if (qName.equals(MusicXmlTags.METRONOME_TYPE)) {
            reader.setWhere(Where.METRONOME_TYPE);
        } else if (qName.equals(MusicXmlTags.METRONOME_DOT)) {
            metronome.addMetronomeDot();
            reader.setWhere(Where.METRONOME_DOT);
        }
    }

    // -------------------------------------------------------------------------
    // endElement direction cases (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndBeatUnit(String qName) {
        if (qName.equals(MusicXmlTags.BEAT_UNIT)) {
            metronome.setBeatUnitToken(reader.valueString().trim());
            reader.setWhere(Where.METRONOME);
        }
    }

    void handleEndPerMinute(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.PER_MINUTE)) {
            metronome.setVisibleTempo(MusicXmlUnits.parseIntOrThrow(MusicXmlTags.PER_MINUTE, reader.valueString()));
            reader.setWhere(Where.METRONOME);
        }
    }

    void handleEndMetronomeType(String qName) {
        if (qName.equals(MusicXmlTags.METRONOME_TYPE)) {
            metronome.setMetronomeType(reader.valueString().trim());
            reader.setWhere(Where.METRONOME_NOTE);
        }
    }

    void handleEndMetronomeNote(String qName) {
        if (qName.equals(MusicXmlTags.METRONOME_NOTE)) {
            metronome.endMetronomeNote();
            reader.setWhere(Where.METRONOME);
        }
    }

    void handleEndWords(String qName) {
        if (qName.equals(MusicXmlTags.WORDS)) {
            var words = reader.valueString();

            // An annotation direction's words build its Annotation; a
            // tempo direction's words are its optional description.
            if (annotations.isAnnotationDirection()) {
                annotations.setWords(words);
            } else {
                metronome.setWords(words);
            }

            reader.setWhere(Where.DIRECTION_TYPE);
        }
    }

    void handleEndDirection(String qName) {
        if (qName.equals(MusicXmlTags.DIRECTION)) {
            // Build any accumulated metronome tempo or annotation now; each
            // binds to the next note (see MetronomeResolver /
            // AnnotationResolver). Exactly one builds per direction: a
            // metronome direction carries no placement, an annotation
            // direction carries no <metronome>.
            metronome.endDirection();
            annotations.endDirection();
            reader.setWhere(Where.MEASURE);
        }
    }
}
