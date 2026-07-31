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

import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.io.musicxml.MusicXmlReader.Where;

/**
 * Parses the {@code <note>} subtree (pitch, duration, notations, lyrics, and
 * range-span markers) of a MusicXML document on behalf of {@link MusicXmlReader}.
 * Mirrors the writer's {@link MusicXmlNoteWriter} / {@link MusicXmlNotationsWriter}.
 * <p>
 * This is not a {@link org.xml.sax.helpers.DefaultHandler}: {@code MusicXmlReader}
 * owns the two SAX dispatch switches and delegates each note-owned {@link Where}
 * leaf state's start/end case here. The note-content handlers accumulate onto the
 * injected {@link NoteAccumulator} ({@code note}) and advance the shared state
 * cursor through the reader-as-context accessors on {@code MusicXmlReader}
 * ({@code setWhere}, {@code valueString}, {@code endTransition}). The
 * {@link Where} transition graph is documented on {@link MusicXmlReader}.
 * <p>
 * The {@code startNote} / {@code finishNote} lifecycle stays in the orchestrator
 * (decision 6): {@code finishNote} is the per-note seven-resolver convergence
 * point, so the {@code NOTE} start/end cases remain inline in the orchestrator's
 * switches. This class owns only per-element accumulation states.
 */
final class MusicXmlNoteReader {

    /** Verse number for a {@code <lyric>} that omits its {@code number} attribute. */
    private static final int DEFAULT_LYRIC_VERSE = 1;

    private final MusicXmlReader reader;

    // Accumulates the note being parsed; the same instance the orchestrator holds
    // (it is resolved by the orchestrator-owned finishNote). The note-content
    // handlers here only accumulate onto it — they never touch the span resolver.
    private final NoteAccumulator note;

    MusicXmlNoteReader(MusicXmlReader reader, NoteAccumulator note) {
        this.reader = reader;
        this.note = note;
    }

    // -------------------------------------------------------------------------
    // Note core — startElement (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    /**
     * Dispatches a {@code <note>} child element to its owning leaf state. Several
     * targets ({@code TIE}, {@code TIME_MODIFICATION}, {@code BEAM},
     * {@code NOTATIONS}, {@code LYRIC}) are owned by later internal phases; setting
     * their transition here is intentional (the target case may still be inline in
     * the orchestrator).
     */
    void handleStartNote(String qName, Attributes attributes) throws SAXException {
        if (qName.equals(MusicXmlTags.GRACE)) {
            note.markGrace();
            reader.setWhere(Where.GRACE);
        } else if (qName.equals(MusicXmlTags.REST)) {
            note.markRest();
            reader.setWhere(Where.REST);
        } else if (qName.equals(MusicXmlTags.PITCH)) {
            reader.setWhere(Where.PITCH);
        } else if (qName.equals(MusicXmlTags.DURATION)) {
            reader.setWhere(Where.DURATION);
        } else if (qName.equals(MusicXmlTags.NOTE_TYPE)) {
            reader.setWhere(Where.NOTE_TYPE);
        } else if (qName.equals(MusicXmlTags.DOT)) {
            note.incrementDotCount();
            reader.setWhere(Where.DOT);
        } else if (qName.equals(MusicXmlTags.ACCIDENTAL)) {
            note.setAccidentalParenthesized(
                MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_CAUTIONARY))
                    || MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_PARENTHESES))
            );
            reader.setWhere(Where.ACCIDENTAL);
        } else if (qName.equals(MusicXmlTags.STEM)) {
            note.markStemPresent();
            reader.setWhere(Where.STEM);
        } else if (qName.equals(MusicXmlTags.TIE)) {
            // Sound tie — write-forward only; <tied> is the source of truth
            // for span reconstruction, so this carries no read state.
            reader.setWhere(Where.TIE);
        } else if (qName.equals(MusicXmlTags.TIME_MOD)) {
            reader.setWhere(Where.TIME_MODIFICATION);
        } else if (qName.equals(MusicXmlTags.BEAM)) {
            // Only the primary beam (number="1") drives span collapse;
            // secondary levels and hooks are write-forward (layout re-derives).
            note.setBeamLevelIsOne(
                MusicXmlTags.NUMBER_1.equals(attributes.getValue(MusicXmlTags.ATTR_NUMBER))
            );
            reader.setWhere(Where.BEAM);
        } else if (qName.equals(MusicXmlTags.NOTATIONS)) {
            reader.setWhere(Where.NOTATIONS);
        } else if (qName.equals(MusicXmlTags.LYRIC)) {
            // A <lyric number="N"> opens one verse. Absent number → verse 1
            // (lenient, matching StaffElementIO).
            var numberAttr = attributes.getValue(MusicXmlTags.ATTR_NUMBER);
            var verse = numberAttr != null
                ? MusicXmlUnits.parseIntOrThrow(MusicXmlTags.ATTR_NUMBER, numberAttr)
                : DEFAULT_LYRIC_VERSE;
            note.beginLyric(verse);
            reader.setWhere(Where.LYRIC);
        }
    }

    // -------------------------------------------------------------------------
    // Note core — endElement (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndNoteType(String qName) {
        if (qName.equals(MusicXmlTags.NOTE_TYPE)) {
            note.setTypeToken(reader.valueString().trim());
            reader.setWhere(Where.NOTE);
        }
    }

    void handleEndStem(String qName) {
        if (qName.equals(MusicXmlTags.STEM)) {
            note.setStemUp(MusicXmlTags.STEM_UP.equals(reader.valueString().trim()));
            reader.setWhere(Where.NOTE);
        }
    }

    // -------------------------------------------------------------------------
    // Pitch — startElement (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    void handleStartPitch(String qName) {
        reader.startTransition(qName, MusicXmlTags.STEP, Where.STEP);
        reader.startTransition(qName, MusicXmlTags.ALTER, Where.ALTER);
        reader.startTransition(qName, MusicXmlTags.OCTAVE, Where.OCTAVE);
    }

    // -------------------------------------------------------------------------
    // Pitch — endElement (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndStep(String qName) {
        if (qName.equals(MusicXmlTags.STEP)) {
            var step = reader.valueString().trim();

            if (!step.isEmpty()) {
                note.setStep(step.charAt(0));
            }

            reader.setWhere(Where.PITCH);
        }
    }

    void handleEndOctave(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.OCTAVE)) {
            note.setOctave(MusicXmlUnits.parseIntOrThrow(MusicXmlTags.OCTAVE, reader.valueString()));
            reader.setWhere(Where.PITCH);
        }
    }

    void handleEndAccidental(String qName) {
        if (qName.equals(MusicXmlTags.ACCIDENTAL)) {
            note.setAccidentalToken(reader.valueString().trim());
            reader.setWhere(Where.NOTE);
        }
    }

    // -------------------------------------------------------------------------
    // Range-span mechanics — startElement (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    /**
     * Dispatches a {@code <time-modification>} child. {@code <actual-notes>} and
     * {@code <normal-notes>} own leaf states; {@code <normal-type>} and
     * {@code <normal-dot/>} do not, because neither has children of its own — they
     * are read from the {@code TIME_MODIFICATION} state itself (see
     * {@link #handleEndTimeModification}), which keeps this element pair out of the
     * shared {@code Where} enum.
     */
    void handleStartTimeModification(String qName) {
        if (qName.equals(MusicXmlTags.NORMAL_DOT)) {
            note.incrementNormalDotCount();
            return;
        }

        reader.startTransition(qName, MusicXmlTags.ACTUAL_NOTES, Where.ACTUAL_NOTES);
        reader.startTransition(qName, MusicXmlTags.NORMAL_NOTES, Where.NORMAL_NOTES);
    }

    // -------------------------------------------------------------------------
    // Range-span mechanics — endElement (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndBeam(String qName) {
        if (qName.equals(MusicXmlTags.BEAM)) {
            // Capture the primary-beam value only; secondary levels and
            // hooks are write-forward and ignored on read.
            note.endBeam(reader.valueString().trim());
            reader.setWhere(Where.NOTE);
        }
    }

    void handleEndTie(String qName) {
        if (qName.equals(MusicXmlTags.TIE)) {
            reader.setWhere(Where.NOTE);
        }
    }

    void handleEndActualNotes(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.ACTUAL_NOTES)) {
            note.setActualNotes(MusicXmlUnits.parseIntOrThrow(MusicXmlTags.ACTUAL_NOTES, reader.valueString()));
            reader.setWhere(Where.TIME_MODIFICATION);
        }
    }

    void handleEndNormalNotes(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.NORMAL_NOTES)) {
            note.setNormalNotes(MusicXmlUnits.parseIntOrThrow(MusicXmlTags.NORMAL_NOTES, reader.valueString()));
            reader.setWhere(Where.TIME_MODIFICATION);
        }
    }

    /**
     * Closes {@code <time-modification>} and, along the way, the two of its
     * children that carry no leaf state of their own. The accumulated text is
     * still {@code <normal-type>}'s own, because the orchestrator clears the value
     * buffer at every start tag; {@code </normal-dot>} carries nothing (the dot was
     * counted at its start tag) and falls through untouched.
     */
    void handleEndTimeModification(String qName) {
        if (qName.equals(MusicXmlTags.NORMAL_TYPE)) {
            note.setNormalTypeToken(reader.valueString().trim());
        } else if (qName.equals(MusicXmlTags.TIME_MOD)) {
            reader.setWhere(Where.NOTE);
        }
    }

    // -------------------------------------------------------------------------
    // Notations container + range-span notations — startElement
    // -------------------------------------------------------------------------

    /**
     * Dispatches a {@code <notations>} child to its owning leaf state. Some targets
     * ({@code SLIDE}, {@code TIED}, {@code TUPLET}) capture markers onto
     * {@code note} here; the span/tie/tuplet resolution itself happens later in the
     * orchestrator's {@code finishNote}.
     */
    void handleStartNotations(String qName, Attributes attributes) throws SAXException {
        if (qName.equals(MusicXmlTags.ARTICULATIONS)) {
            reader.setWhere(Where.ARTICULATIONS);
        } else if (qName.equals(MusicXmlTags.FERMATA)) {
            note.markFermata();
            reader.setWhere(Where.FERMATA);
        } else if (qName.equals(MusicXmlTags.DYNAMICS)) {
            reader.setWhere(Where.DYNAMICS);
        } else if (qName.equals(MusicXmlTags.SLIDE)) {
            note.setSlideType(attributes.getValue(MusicXmlTags.ATTR_TYPE));
            reader.setWhere(Where.SLIDE);
        } else if (qName.equals(MusicXmlTags.TIED)) {
            note.addTied(attributes.getValue(MusicXmlTags.ATTR_TYPE));
            reader.setWhere(Where.TIED);
        } else if (qName.equals(MusicXmlTags.TUPLET)) {
            var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

            if (MusicXmlTags.TYPE_START.equals(type)) {
                note.markTupletStart();
                note.captureTupletRelativeY(attributes);
            } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                note.markTupletStop();
            }

            reader.setWhere(Where.TUPLET);
        } else if (qName.equals(MusicXmlTags.ORNAMENTS)) {
            reader.setWhere(Where.ORNAMENTS);
        }
    }

    /**
     * Dispatches an {@code <ornaments>} child. The trill markers accumulate onto
     * {@code note}; the trill-span resolution happens in the orchestrator's
     * {@code finishNote}.
     */
    void handleStartOrnaments(String qName, Attributes attributes) throws SAXException {
        if (qName.equals(MusicXmlTags.TRILL_MARK)) {
            reader.setWhere(Where.TRILL_MARK);
        } else if (qName.equals(MusicXmlTags.WAVY_LINE)) {
            var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

            if (MusicXmlTags.TYPE_START.equals(type)) {
                note.markTrillStart();
                note.captureTrillRelativeY(attributes);
            } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                note.markTrillStop();
            }

            reader.setWhere(Where.WAVY_LINE);
        }
    }

    /** Dispatches an {@code <articulations>} child, marking it onto {@code note}. */
    void handleStartArticulations(String qName) {
        if (qName.equals(MusicXmlTags.ACCENT)) {
            note.markAccent();
            reader.setWhere(Where.ACCENT);
        } else if (qName.equals(MusicXmlTags.STACCATO)) {
            note.markStaccato();
            reader.setWhere(Where.STACCATO);
        } else if (qName.equals(MusicXmlTags.FALLOFF)) {
            note.markFall();
            reader.setWhere(Where.FALLOFF);
        } else if (qName.equals(MusicXmlTags.BREATH_MARK)) {
            note.markBreathMark();
            reader.setWhere(Where.BREATH_MARK);
        }
    }

    /**
     * Handles a per-note {@code <notations><dynamics>} child — NOT the
     * direction-level dynamics. The child element's name is the dynamic symbol
     * itself (e.g. {@code <f/>}, {@code <mf/>}), resolved to a {@link DynamicType}.
     */
    void handleStartDynamics(String qName) {
        var dynamicType = DynamicType.fromSymbol(qName);

        if (dynamicType != null) {
            note.setDynamicType(dynamicType);
        }

        reader.setWhere(Where.DYNAMIC_MARK);
    }

    // -------------------------------------------------------------------------
    // Lyrics — startElement (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    /** Dispatches a {@code <lyric>} child to its owning leaf state. */
    void handleStartLyric(String qName, Attributes attributes) {
        if (qName.equals(MusicXmlTags.SYLLABIC)) {
            reader.setWhere(Where.SYLLABIC);
        } else if (qName.equals(MusicXmlTags.LYRIC_TEXT)) {
            reader.setWhere(Where.LYRIC_TEXT);
        } else if (qName.equals(MusicXmlTags.EXTEND)) {
            // <extend> is an empty element; its type attr is the only data.
            // Absent/unrecognized type → START (see SyllabicMapping).
            note.setLyricExtend(
                SyllabicMapping.forExtendToken(attributes.getValue(MusicXmlTags.ATTR_TYPE))
            );
            reader.setWhere(Where.EXTEND);
        }
    }

    // -------------------------------------------------------------------------
    // Lyrics — endElement (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndSyllabic(String qName) {
        if (qName.equals(MusicXmlTags.SYLLABIC)) {
            note.setLyricSyllabicToken(reader.valueString().trim());
            reader.setWhere(Where.LYRIC);
        }
    }

    void handleEndLyricText(String qName) {
        if (qName.equals(MusicXmlTags.LYRIC_TEXT)) {
            // Not trimmed: the text is emitted inline with no surrounding
            // whitespace, and a trailing compound marker must survive intact.
            note.setLyricText(reader.valueString());
            reader.setWhere(Where.LYRIC);
        }
    }

    void handleEndLyric(String qName) {
        if (qName.equals(MusicXmlTags.LYRIC)) {
            note.endLyric();
            reader.setWhere(Where.NOTE);
        }
    }
}
