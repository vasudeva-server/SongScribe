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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import jakarta.xml.bind.JAXBElement;
import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.Accidental;
import org.audiveris.proxymusic.AccidentalValue;
import org.audiveris.proxymusic.Articulations;
import org.audiveris.proxymusic.Dynamics;
import org.audiveris.proxymusic.Empty;
import org.audiveris.proxymusic.Fermata;
import org.audiveris.proxymusic.Notations;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Ornaments;
import org.audiveris.proxymusic.Pitch;
import org.audiveris.proxymusic.Slide;
import org.audiveris.proxymusic.Stem;
import org.audiveris.proxymusic.StemValue;
import org.audiveris.proxymusic.Step;
import org.audiveris.proxymusic.Tied;
import org.audiveris.proxymusic.TimeModification;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

import songscribe.dom.DiatonicPitch;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.NoteValue;
import songscribe.dom.StaffElement;
// The document model's stem arms, imported by their nested names: the simple name Stem is the
// ProxyMusic <stem> node here.
import songscribe.dom.Stem.Auto;
import songscribe.dom.Stem.Forced;
import songscribe.dom.Tuplet;
import songscribe.error.RuntimeError;

/**
 * Builds one ProxyMusic {@link Note} from one {@link StaffElement}, and records the pair into
 * {@link BuildIndex#notes()}.
 *
 * <p>The children of {@code <notations>} are an unordered heterogeneous list, so this class keeps
 * them in rank order.
 */
final class NoteBuilder {

    // Order of the <notations> children. The schema accepts any order (the content model is an
    // unbounded choice), so this ranking is what fixes the emitted order.
    private static final int ORDER_TIED = 0;
    private static final int ORDER_SLIDE = 1;
    private static final int ORDER_TUPLET = 2;
    private static final int ORDER_ORNAMENTS = 3;
    private static final int ORDER_ARTICULATIONS = 4;
    private static final int ORDER_DYNAMICS = 5;
    private static final int ORDER_FERMATA = 6;

    private static final int ORDER_OTHER = 7;

    private NoteBuilder() {}

    /**
     * Returns {@code null} for an element that produces no note: a breath mark (folded into the
     * preceding note) or anything else without a note value.
     */
    static @Nullable Note buildNote(BuildContext ctx, Line line, int elementIndex) {
        var element = line.getElement(elementIndex);
        var type = element.getType();

        if (type.isBreathMark()) {
            foldBreathMark(ctx, line, elementIndex);
            return null;
        }

        var value = type.noteValue();

        if (value == null) {
            return null;
        }

        var typeToken = NoteTypeMapping.typeToken(value);
        var factory = ctx.factory();
        var pmNote = factory.createNote();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();

        // The tuplet this element belongs to, which both <duration> and <time-modification>
        // need. It is a property of the element's position in the line, not of a span walk:
        // every member of the group carries the same time modification.
        //
        // A grace note may sit inside the span without joining the group: it keeps its written
        // value and takes no <time-modification>.
        var tuplet = isGrace ? null : line.spanRegistry().findTupletAt(elementIndex);

        // relative-x is the user's own horizontal offset, emitted only when non-zero, and
        // declared by SupportedFeature.NOTE_RELATIVE_X. No default-x: an element
        // carries no layout position, and layout is solved from scratch on load.
        var userXOffsetSs = element.getUserXOffsetSs();

        if (userXOffsetSs != 0) {
            pmNote.setRelativeX(MusicXmlUnits.ssAsTenths(userXOffsetSs));
        }

        // <grace slash="no"/> — no steal-time-following: SongScribe gives grace notes zero
        // playback duration and never shortens the host, so emitting a steal would
        // misrepresent the song.
        if (isGrace) {
            var grace = factory.createGrace();
            grace.setSlash(YesNo.NO);
            pmNote.setGrace(grace);
        }

        // Every element with a note value is a note or a rest, and both take dots.
        var writtenDots = ((Dottable) element).dots();
        var note = element instanceof NoteElement pitched ? pitched : null;

        if (note == null) {
            pmNote.setRest(factory.createRest());
        } else {
            pmNote.setPitch(buildPitch(ctx, note));
        }

        // <duration> is the *performed* duration, which is what MusicXML means by it: a tuplet
        // member sounds shorter than its <type> says. Grace notes have none.
        if (NoteTypeMapping.hasDuration(type)) {
            pmNote.setDuration(BigDecimal.valueOf(performedTicks(value, writtenDots, tuplet)));
        }

        var noteType = factory.createNoteType();
        noteType.setValue(typeToken);
        pmNote.setType(noteType);

        var dotCount = isGrace ? 0 : writtenDots.count();

        for (var i = 0; i < dotCount; i++) {
            pmNote.getDot().add(factory.createEmptyPlacement());
        }

        if (note != null) {
            pmNote.setAccidental(buildAccidental(ctx, note));
        }

        if (tuplet != null) {
            pmNote.setTimeModification(buildTimeModification(ctx, tuplet));
        }

        if (note != null) {
            pmNote.setStem(buildStem(ctx, note));
        }

        buildNotations(ctx, element, pmNote);

        ctx.index().notes().put(element, pmNote);

        return pmNote;
    }

    /**
     * Every pass that attaches a notation goes through here, so one note never grows two
     * {@code <notations>} elements.
     */
    static Notations notationsOf(BuildContext ctx, Note pmNote) {
        var notations = pmNote.getNotations();

        if (!notations.isEmpty()) {
            return notations.getFirst();
        }

        var created = ctx.factory().createNotations();
        notations.add(created);

        return created;
    }

    /**
     * Members of equal rank keep their insertion order, so a tie chain's
     * {@code <tied type="stop">} stays before its {@code <tied type="start">}.
     */
    static void addNotation(BuildContext ctx, Note pmNote, Object member) {
        var members = notationsOf(ctx, pmNote).getTiedOrSlurOrTuplet();
        var order = notationOrder(member);

        for (var i = 0; i < members.size(); i++) {
            if (notationOrder(members.get(i)) > order) {
                members.add(i, member);
                return;
            }
        }

        members.add(member);
    }

    private static int notationOrder(Object member) {
        return switch (member) {
            case Tied ignored -> ORDER_TIED;
            case Slide ignored -> ORDER_SLIDE;
            case org.audiveris.proxymusic.Tuplet ignored -> ORDER_TUPLET;
            case Ornaments ignored -> ORDER_ORNAMENTS;
            case Articulations ignored -> ORDER_ARTICULATIONS;
            case Dynamics ignored -> ORDER_DYNAMICS;
            case Fermata ignored -> ORDER_FERMATA;
            default -> ORDER_OTHER;
        };
    }

    /**
     * A note can reach this twice, and both must land in the same element.
     */
    private static Articulations articulationsOf(BuildContext ctx, Note pmNote) {
        for (var member : notationsOf(ctx, pmNote).getTiedOrSlurOrTuplet()) {
            if (member instanceof Articulations articulations) {
                return articulations;
            }
        }

        var created = ctx.factory().createArticulations();
        addNotation(ctx, pmNote, created);

        return created;
    }

    private static void buildNotations(BuildContext ctx, StaffElement element, Note pmNote) {
        var factory = ctx.factory();

        if (element instanceof NoteElement note) {
            var articulations = note.getArticulations();
            var hasFall = note.hasFall();

            if (!articulations.isEmpty() || hasFall) {
                var members = articulationsOf(ctx, pmNote).getAccentOrStrongAccentOrStaccato();

                for (var articulation : articulations) {
                    members.add(switch (articulation.getType()) {
                        case ACCENT -> factory.createArticulationsAccent(factory.createEmptyPlacement());
                        case STACCATO -> factory.createArticulationsStaccato(factory.createEmptyPlacement());
                    });
                }

                if (hasFall) {
                    members.add(factory.createArticulationsFalloff(factory.createEmptyLine()));
                }
            }
        }

        var dynamic = element.findAttachment(DynamicAttachment.class);

        if (dynamic != null) {
            var pmDynamics = factory.createDynamics();
            pmDynamics.setPlacement(AboveBelow.ABOVE);
            pmDynamics.getPOrPpOrPpp().add(dynamicMark(ctx, dynamic.getType()));
            addNotation(ctx, pmNote, pmDynamics);
        }

        if (element.findAttachment(FermataAttachment.class) != null) {
            addNotation(ctx, pmNote, factory.createFermata());
        }
    }

    /**
     * A breath mark with no preceding note is an invariant failure.
     */
    private static void foldBreathMark(BuildContext ctx, Line line, int elementIndex) {
        var precedingNote = elementIndex == 0
            ? null
            : ctx.index().notes().get(line.getElement(elementIndex - 1));

        if (precedingNote == null) {
            throw RuntimeError.exit(
                "Breath mark at line " + line.index() + ", element " + elementIndex
                    + " has no preceding note");
        }

        var factory = ctx.factory();
        articulationsOf(ctx, precedingNote).getAccentOrStrongAccentOrStaccato()
            .add(factory.createArticulationsBreathMark(factory.createBreathMark()));
    }

    /**
     * {@code <alter>} is emitted only when the note actually sounds altered; a natural sign is an
     * accidental, not an alteration.
     */
    private static Pitch buildPitch(BuildContext ctx, NoteElement element) {
        var spelling = DiatonicPitch.at(element.getStaffPosition().value());
        var alterSemitones = PitchSpelling.soundingAlterFor(element);
        var pmPitch = ctx.factory().createPitch();

        pmPitch.setStep(Step.fromValue(spelling.pitchClass().name()));

        if (alterSemitones != 0) {
            pmPitch.setAlter(BigDecimal.valueOf(alterSemitones));
        }

        pmPitch.setOctave(spelling.octave());

        return pmPitch;
    }

    /**
     * Returns {@code null} when {@code element} draws no accidental.
     */
    private static @Nullable Accidental buildAccidental(BuildContext ctx, NoteElement element) {
        var accidental = element.accidentalState().drawn();

        if (accidental == null) {
            return null;
        }

        var pmAccidental = ctx.factory().createAccidental();
        pmAccidental.setValue(AccidentalValue.fromValue(AccidentalMapping.forAccidental(accidental)));

        return pmAccidental;
    }

    /**
     * Returns {@code null} when the stem direction is automatic and the reader can derive it.
     */
    private static @Nullable Stem buildStem(BuildContext ctx, NoteElement element) {
        if (element.getType().isGraceNote()) {
            var pmStem = ctx.factory().createStem();
            pmStem.setValue(StemValue.UP);
            pmStem.setDefaultY(BigDecimal.valueOf(
                element.getStaffPosition().value() * -MusicXmlUnits.TENTHS_PER_STAFF_POSITION
                    + MusicXmlUnits.GRACE_STEM_EXTENSION_TENTHS));

            return pmStem;
        }

        return switch (element.stem()) {
            case Auto _ -> null;
            case Forced forced -> {
                var pmStem = ctx.factory().createStem();
                pmStem.setValue(forced.direction().isUp() ? StemValue.UP : StemValue.DOWN);
                yield pmStem;
            }
        };
    }

    private static TimeModification buildTimeModification(BuildContext ctx, Tuplet tuplet) {
        var factory = ctx.factory();
        var ratio = tuplet.getRatio();
        var value = ratio.noteValue().value();
        var timeModification = factory.createTimeModification();

        timeModification.setActualNotes(BigInteger.valueOf(tuplet.getGrade().number()));
        timeModification.setNormalNotes(BigInteger.valueOf(ratio.normalNotes()));
        timeModification.setNormalType(NoteTypeMapping.typeToken(value));

        var normalDots = timeModification.getNormalDot();

        for (var i = 0; i < ratio.noteValue().dots().count(); i++) {
            normalDots.add(factory.createEmpty());
        }

        return timeModification;
    }

    /**
     * Must be wrapped by its factory method: a bare {@link Empty} in the list does not marshal.
     */
    private static JAXBElement<Empty> dynamicMark(
        BuildContext ctx, DynamicAttachment.DynamicType dynamicType) {
        var factory = ctx.factory();
        var empty = factory.createEmpty();

        return switch (dynamicType) {
            case PIANISSIMO -> factory.createDynamicsPp(empty);
            case PIANO -> factory.createDynamicsP(empty);
            case MEZZO_PIANO -> factory.createDynamicsMp(empty);
            case MEZZO_FORTE -> factory.createDynamicsMf(empty);
            case FORTE -> factory.createDynamicsF(empty);
            case FORTISSIMO -> factory.createDynamicsFf(empty);
        };
    }

    /**
     * Throws rather than rounds: {@link NoteTypeMapping#DIVISIONS} makes the scaling exact for
     * every accepted note value, dots and tuplet ratio, so an inexact result means
     * {@code DIVISIONS} is wrong.
     *
     * @param tuplet the tuplet this note belongs to, or {@code null} if none
     */
    private static int performedTicks(NoteValue value, Dots dots, @Nullable Tuplet tuplet) {
        var writtenTicks = dots.augment(NoteTypeMapping.baseTicks(value));

        if (tuplet == null) {
            return writtenTicks;
        }

        try {
            return Math.toIntExact(tuplet.performedTicks(writtenTicks, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException e) {
            var message = "Performed duration is not an integer for " + value + " dots=" + dots
                + " in a " + tuplet.getGrade().number() + ':' + tuplet.getRatio().normalNotes() + " tuplet"
                + " (DIVISIONS=" + NoteTypeMapping.DIVISIONS + " is wrong)";

            throw (ArithmeticException) new ArithmeticException(message).initCause(e);
        }
    }
}
