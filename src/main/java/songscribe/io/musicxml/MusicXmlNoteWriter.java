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

import java.io.PrintWriter;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.io.XML;

final class MusicXmlNoteWriter {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlNoteWriter.class);

    private MusicXmlNoteWriter() {}

    static void writeNote(PrintWriter pw, NoteWriteContext ctx) {
        var note = ctx.note();
        var typeToken = ctx.typeToken();
        var type = note.getType();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();
        var spanMarkers = ctx.spanMarkers();

        // Compute position in tenths.
        // default-x: the base layout position (getXSs() stores the layout-assigned
        //   position; for notes this is set per the new layout system).
        // relative-x: the user-set horizontal offset, emitted only when non-zero
        //   (mirrors the legacy writeElement guard).
        var baseXTenths = MusicXmlUnits.ssToTenths(note.getXSs());
        var xOffsetPx = note.getXOffsetPx();

        // Open <note> tag with optional position attributes. relative-x is emitted
        // only when the note carries a user offset (mirrors the legacy guard), so
        // its tenths conversion is computed only in that branch.
        if (xOffsetPx != 0) {
            var relativeXTenths = MusicXmlUnits.ssToTenths(ScaleContext.pxToSs(xOffsetPx));
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, MusicXmlUnits.formatTenths(baseXTenths),
                MusicXmlTags.ATTR_RELATIVE_X, MusicXmlUnits.formatTenths(relativeXTenths)
            );
        } else {
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, MusicXmlUnits.formatTenths(baseXTenths)
            );
        }

        XML.indent();

        // 1. <grace slash="no"/> — grace notes only.
        //    No steal-time-following: SongScribe gives grace notes zero playback
        //    duration and never shortens the host, so emitting a steal would
        //    misrepresent the song.
        if (isGrace) {
            XML.writeEmptyTag(pw, MusicXmlTags.GRACE, MusicXmlTags.ATTR_SLASH, MusicXmlTags.NO);
        }

        // 2. <rest/> | <pitch> — rest has no pitch; pitched and grace notes do.
        if (isRest) {
            XML.writeEmptyTag(pw, MusicXmlTags.REST);
        } else {
            writePitch(pw, note);
        }

        // 3. <duration> — the *performed* duration, which is what MusicXML means
        //    by <duration>: a tuplet member sounds shorter than its <type> says.
        //    Omitted for grace notes (zero playback time).
        if (NoteTypeMapping.hasDuration(type)) {
            var ticks = performedTicks(type, note.getDotCount(), spanMarkers.tuplet());
            XML.writeValue(pw, MusicXmlTags.DURATION, Integer.toString(ticks));
        }

        // 3a. <tie type="stop"/>? + <tie type="start"/>? — sound ties, note-level,
        //     after <duration> and before <type>.  Rests cannot be tied.
        //     Interior notes of a chain emit stop before start to form a closed loop.
        if (!isRest) {
            if (spanMarkers.tieStopsHere()) {
                XML.writeEmptyTag(pw, MusicXmlTags.TIE, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_STOP);
            }

            if (spanMarkers.tieStartsHere()) {
                XML.writeEmptyTag(pw, MusicXmlTags.TIE, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START);
            }
        }

        // 4. <type>
        XML.writeValue(pw, MusicXmlTags.NOTE_TYPE, typeToken);

        // 5. <dot/>×n — grace notes never carry dots.
        var dotCount = isGrace ? 0 : note.getDotCount();

        for (var i = 0; i < dotCount; i++) {
            XML.writeEmptyTag(pw, MusicXmlTags.DOT);
        }

        // 6. <accidental> — not for rests.
        if (!isRest) {
            writeAccidental(pw, note);
        }

        // 6a. <time-modification> — emitted on every note in a tuplet span,
        //     including rests.  After <accidental>, before <stem> per schema.
        var tupletForTimeMod = spanMarkers.tuplet();

        if (tupletForTimeMod != null) {
            writeTimeModification(pw, tupletForTimeMod);
        }

        // 7. <stem>
        writeStem(pw, note, isGrace);

        // 8. <beam number="N"> — one element per active beam level, note-level,
        //    after <stem> and before <notations> per MusicXML 4.0 schema.
        //    Pre-computed values are null for non-beamed notes and for levels
        //    where this note has no beam activity.
        writeBeamValues(pw, spanMarkers.beamLevelValues());

        // 9. <notations>
        MusicXmlNotationsWriter.writeNotations(pw, ctx);

        // 10. <lyric>×n — one per verse.
        MusicXmlNotationsWriter.writeLyrics(pw, note);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTE);
    }

    private static void writePitch(PrintWriter pw, StaffElement note) {
        var pitch = PitchSpelling.spell(note.getStaffPosition());
        var alterSemitones = PitchSpelling.soundingAlterFor(note);

        XML.writeBeginTag(pw, MusicXmlTags.PITCH);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.STEP, String.valueOf(pitch.step()));

        if (alterSemitones != 0) {
            XML.writeValue(pw, MusicXmlTags.ALTER, Integer.toString(alterSemitones));
        }

        XML.writeValue(pw, MusicXmlTags.OCTAVE, Integer.toString(pitch.octave()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PITCH);
    }

    private static void writeAccidental(PrintWriter pw, StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var token = AccidentalMapping.forAccidental(accidental);

        // A parenthesized accidental is a cautionary: cautionary="yes" parentheses="yes".
        if (note.isAccidentalInParentheses()) {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, token,
                MusicXmlTags.ATTR_CAUTIONARY, MusicXmlTags.YES,
                MusicXmlTags.ATTR_PARENTHESES, MusicXmlTags.YES
            );
        } else {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, token);
        }
    }

    private static void writeStem(PrintWriter pw, StaffElement note, boolean isGrace) {
        if (isGrace) {
            // Grace-note stems always go up. Emit a computed stem-tip position so
            // external renderers can draw the stem without re-running layout.
            // Stem-tip tenths = note-head tenths above middle line + extension.
            //   noteHeadTenths = staffPosition × -MusicXmlUnits.TENTHS_PER_STAFF_POSITION
            //   (negative because staffPosition increases downward but MusicXML Y
            //    is positive upward, and origin B4 = staffPosition 0 = middle line)
            var stemTipTenths = note.getStaffPosition() * -MusicXmlUnits.TENTHS_PER_STAFF_POSITION + MusicXmlUnits.GRACE_STEM_EXTENSION_TENTHS;
            XML.writeValue(pw, MusicXmlTags.STEM, MusicXmlTags.STEM_UP,
                MusicXmlTags.ATTR_DEFAULT_Y, Integer.toString(stemTipTenths)
            );

        } else if (!note.isStemDirectionAuto()) {
            var stemDir = note.isUpper() ? MusicXmlTags.STEM_UP : MusicXmlTags.STEM_DOWN;
            XML.writeValue(pw, MusicXmlTags.STEM, stemDir);
        }
    }

    private static void writeBeamValues(PrintWriter pw, String[] beamLevelValues) {
        // An empty array means the note is not part of any beam group — nothing to emit.
        if (beamLevelValues.length == 0) {
            return;
        }

        for (var level = 0; level < beamLevelValues.length; level++) {
            var value = beamLevelValues[level];

            // Empty-string entries are the sentinel for "no beam at this level".
            if (!value.isEmpty()) {
                XML.writeValue(pw, MusicXmlTags.BEAM, value,
                    MusicXmlTags.ATTR_NUMBER, Integer.toString(level + 1));
            }
        }
    }

    /**
     * The performed tick count for one note: its written duration, scaled by
     * {@code normalNotes / grade} when the note belongs to a resolved tuplet.
     *
     * <p>{@link NoteTypeMapping#DIVISIONS} is chosen so that this scaling is
     * exact for every note value, dot count and tuplet ratio the validator
     * accepts, so an inexact result means {@code DIVISIONS} itself is wrong.
     * That throws rather than rounding, mirroring {@link NoteTypeMapping#ticks}.
     *
     * @param tuplet the tuplet this note belongs to, or {@code null} if none
     */
    private static int performedTicks(ElementType type, int dotCount, @Nullable Tuplet tuplet) {
        var writtenTicks = NoteTypeMapping.ticks(type, dotCount);

        // An unresolved tuplet states no ratio to scale by — the post-load pass
        // derives one, so until then the written duration is all there is.
        if (tuplet == null || !tuplet.isResolved()) {
            return writtenTicks;
        }

        var grade = tuplet.getGrade();
        var product = writtenTicks * tuplet.getNormalNotes();

        if (product % grade != 0) {
            throw new ArithmeticException(
                "Performed duration is not an integer for " + type + " dotCount=" + dotCount
                + " in a " + grade + ':' + tuplet.getNormalNotes() + " tuplet"
                + " (DIVISIONS=" + NoteTypeMapping.DIVISIONS + " is wrong)"
            );
        }

        return product / grade;
    }

    /**
     * Emits the {@code <time-modification>} element for a note belonging to
     * {@code tuplet}: {@code <actual-notes>} is the tuplet's grade, and — once
     * the tuplet's ratio is known — {@code <normal-notes>}, {@code <normal-type>}
     * and one {@code <normal-dot/>} per dot describe the written value whose time
     * the group occupies. Child order follows the MusicXML schema.
     *
     * <p>{@code <normal-type>} is the discriminator the reader trusts: a file
     * carrying it states its own ratio, so the reader takes {@code <normal-notes>}
     * at face value. A tuplet whose ratio is still unresolved therefore emits
     * neither, and falls back to the conventional power-of-two
     * {@code <normal-notes>} so the document stays schema-valid; the reader will
     * hand such a tuplet to the post-load pass to have its ratio derived.
     *
     * <p>Emitted on every note in a tuplet span (including rests), after
     * {@code <accidental>} and before {@code <stem>} per the MusicXML schema.
     */
    private static void writeTimeModification(PrintWriter pw, Tuplet tuplet) {
        var grade = tuplet.getGrade();
        var noteValue = tuplet.getNoteValue();
        var normalTypeToken = noteValue == null ? null : NoteTypeMapping.typeToken(noteValue);

        XML.writeBeginTag(pw, MusicXmlTags.TIME_MOD);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.ACTUAL_NOTES, Integer.toString(grade));

        if (normalTypeToken == null) {
            // Reaching here means a tuplet escaped the load pass without a ratio, which the
            // model says cannot happen. Say so rather than letting a placeholder ratio go to
            // disk unremarked: 2:1 is not the compound duplet a 2 over a dotted beat means.
            LOG.warn("Writing a tuplet with grade {} whose ratio was never resolved;"
                + " falling back to a placeholder <normal-notes>", grade);
            XML.writeValue(pw, MusicXmlTags.NORMAL_NOTES,
                Integer.toString(largestPowerOfTwoBelowGrade(grade)));
        } else {
            var dotCount = tuplet.getNoteValueDots();

            XML.writeValue(pw, MusicXmlTags.NORMAL_NOTES, Integer.toString(tuplet.getNormalNotes()));
            XML.writeValue(pw, MusicXmlTags.NORMAL_TYPE, normalTypeToken);

            for (var i = 0; i < dotCount; i++) {
                XML.writeEmptyTag(pw, MusicXmlTags.NORMAL_DOT);
            }
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.TIME_MOD);
    }

    /**
     * Returns the largest power of two strictly less than {@code grade}
     * (3→2, 5→4, 6→4, 7→4) — the placeholder {@code <normal-notes>} for a
     * tuplet whose real ratio is not yet resolved. The schema requires the
     * element, and no better value exists before the post-load pass has run.
     */
    private static int largestPowerOfTwoBelowGrade(int grade) {
        var result = 1;

        while (result * 2 < grade) {
            result *= 2;
        }

        return result;
    }
}
