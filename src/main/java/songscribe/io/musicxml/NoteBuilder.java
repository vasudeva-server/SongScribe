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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;

/**
 * Builds one ProxyMusic {@link Note} from one {@link StaffElement}, and records the pair into
 * {@link BuildIndex#notes()} so the later adjustment passes can reach the node without walking
 * the measure's heterogeneous child list.
 *
 * <p><b>Scope.</b> Everything here is decided by the element alone: pitch, duration, type, dots,
 * accidental, stem, time modification, and the {@code <notations>} content the note itself owns
 * (articulations, dynamics, fermata). Anything that walks a line's spans and touches more than
 * one node — ties, beams, tuplet brackets, trills, glissandos — is an adjustment pass, and
 * attaches through {@link #addNotation}. Hairpins are {@code <direction>} children and endings
 * are {@code <barline>} children, so neither is a note concern at all.
 *
 * <p><b>Child order is not this class's problem.</b> {@code Note} is a fully typed sequence
 * rather than a heterogeneous list, so JAXB emits {@code <grace>}, {@code <pitch>},
 * {@code <duration>}, {@code <type>}, {@code <dot>}, {@code <accidental>},
 * {@code <time-modification>}, {@code <stem>} and the rest in schema order whatever order the
 * setters are called in. The numbered ordering comments in the streaming writer documented
 * something the type system now guarantees.
 *
 * <p>The one order that is <em>not</em> guaranteed is inside {@code <notations>}, whose children
 * live in a heterogeneous {@code List<Object>}. The schema permits any order there, so a
 * misordered list marshals and validates — and round-trips — while differing textually from what
 * the streaming writer produced. {@link #addNotation} keeps that list in the streaming writer's
 * order for every pass that uses it.
 */
final class NoteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(NoteBuilder.class);

    // Order of the <notations> children, as the streaming writer emitted them. The schema
    // accepts any order (the content model is an unbounded choice), so nothing but this
    // ranking keeps the two writers textually comparable.
    private static final int ORDER_TIED = 0;
    private static final int ORDER_SLIDE = 1;
    private static final int ORDER_TUPLET = 2;
    private static final int ORDER_ORNAMENTS = 3;
    private static final int ORDER_ARTICULATIONS = 4;
    private static final int ORDER_DYNAMICS = 5;
    private static final int ORDER_FERMATA = 6;

    /** Rank for a notation type the streaming writer never emitted: appended after all of them. */
    private static final int ORDER_OTHER = 7;

    private NoteBuilder() {}

    /**
     * Builds the {@code <note>} for the element at {@code elementIndex} of {@code line}, records
     * it in {@link BuildIndex#notes()}, and returns it — or returns {@code null} for an element
     * that produces no note.
     *
     * <p>Two kinds of element produce none. A breath mark is folded into the preceding note's
     * {@code <articulations>} rather than emitted in its own right (see
     * {@link #foldBreathMark}). Anything else without a {@code <type>} token — a barline, a
     * repeat — is not a note at all; the caller emits those as {@code <barline>} children.
     */
    static @Nullable Note buildNote(BuildContext ctx, Line line, int elementIndex) {
        var element = line.getElements().get(elementIndex);
        var type = element.getType();

        if (type.isBreathMark()) {
            foldBreathMark(ctx, line, elementIndex);
            return null;
        }

        var typeToken = NoteTypeMapping.typeToken(type);

        if (typeToken == null) {
            return null;
        }

        var factory = ctx.factory();
        var pmNote = factory.createNote();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();

        // The tuplet this element belongs to, which both <duration> and <time-modification>
        // need. It is a property of the element's position in the line, not of a span walk:
        // every member of the group carries the same time modification.
        //
        // A grace note may sit inside the span without joining the group: it keeps its written
        // value and takes no <time-modification> (refs #592).
        var tuplet = isGrace ? null : line.findTupletAt(elementIndex);

        // default-x is the layout-assigned position; relative-x is the user's own horizontal
        // offset, emitted only when non-zero.
        pmNote.setDefaultX(MusicXmlUnits.ssAsTenths(element.getXSs()));

        var xOffsetPx = element.getXOffsetPx();

        if (xOffsetPx != 0) {
            pmNote.setRelativeX(MusicXmlUnits.ssAsTenths(ScaleContext.pxToSs(xOffsetPx)));
        }

        // <grace slash="no"/> — no steal-time-following: SongScribe gives grace notes zero
        // playback duration and never shortens the host, so emitting a steal would
        // misrepresent the song.
        if (isGrace) {
            var grace = factory.createGrace();
            grace.setSlash(YesNo.NO);
            pmNote.setGrace(grace);
        }

        if (isRest) {
            pmNote.setRest(factory.createRest());
        } else {
            pmNote.setPitch(buildPitch(ctx, element));
        }

        // <duration> is the *performed* duration, which is what MusicXML means by it: a tuplet
        // member sounds shorter than its <type> says. Grace notes have none.
        if (NoteTypeMapping.hasDuration(type)) {
            pmNote.setDuration(BigDecimal.valueOf(performedTicks(type, element.getDotCount(), tuplet)));
        }

        var noteType = factory.createNoteType();
        noteType.setValue(typeToken);
        pmNote.setType(noteType);

        // Grace notes never carry dots.
        var dotCount = isGrace ? 0 : element.getDotCount();

        for (var i = 0; i < dotCount; i++) {
            pmNote.getDot().add(factory.createEmptyPlacement());
        }

        if (!isRest) {
            pmNote.setAccidental(buildAccidental(ctx, element));
        }

        // Emitted on every note in a tuplet span, rests included.
        if (tuplet != null) {
            pmNote.setTimeModification(buildTimeModification(ctx, tuplet));
        }

        pmNote.setStem(buildStem(ctx, element, isGrace));

        buildNotations(ctx, element, pmNote);

        ctx.index().notes().put(element, pmNote);

        return pmNote;
    }

    // -------------------------------------------------------------------------
    // <notations> — the shared attachment point
    // -------------------------------------------------------------------------

    /**
     * Returns {@code pmNote}'s {@link Notations}, creating it on first use so a note with no
     * notations emits no element. Every pass that attaches a notation goes through here (or
     * through {@link #addNotation}, which calls it), so one note never grows two
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
     * Attaches {@code member} to {@code pmNote}'s {@code <notations>}, inserted at the position
     * its element type occupies in the streaming writer's emission order.
     *
     * <p>Order matters here in a way it does not elsewhere in a {@code <note>}: the children of
     * {@code <notations>} are a heterogeneous {@code List<Object>} whose schema content model is
     * an unbounded choice, so any order validates and any order round-trips. Only a textual
     * comparison against the old writer's output can see a difference — which is exactly the
     * comparison the cutover rests on. Inserting by rank means the adjustment passes may run in
     * whatever order suits them.
     *
     * <p>Members of equal rank keep their insertion order, so a tie chain's
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

    /** The rank of one {@code <notations>} child; see {@link #addNotation}. */
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
     * Returns {@code pmNote}'s {@link Articulations}, creating and attaching it on first use.
     * A note can reach this twice — once for its own accent, staccato or fall, and again when a
     * following breath mark is folded onto it — and both must land in the same element.
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

    /**
     * Builds the {@code <notations>} content the note owns by itself: articulations (accent,
     * staccato, fall), dynamics and fermata. The span-borne notations — tied, slide, tuplet
     * bracket, ornaments — are attached later by the adjustment passes.
     */
    private static void buildNotations(BuildContext ctx, StaffElement element, Note pmNote) {
        var factory = ctx.factory();
        var articulations = element.getArticulations();
        var hasFall = element.hasFall();

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
     * Folds the breath mark at {@code elementIndex} into the preceding note's
     * {@code <articulations>}, or does nothing when no note precedes it.
     *
     * <p>MusicXML has no standalone breath mark: it is an articulation of the note it follows.
     * The streaming writer expressed that as a peek — each note asked whether the next element
     * was a breath mark, and the loop then skipped the breath mark so it was not emitted twice.
     * Both halves are gone. No {@code Note} is ever created for a breath mark, so there is
     * nothing to skip, and the fold is a lookup of the note the preceding element produced.
     *
     * <p>The lookup is deliberately the <em>immediately</em> preceding element of the same line.
     * An element that produced no note produced no place to put the mark: a barline separates
     * the two, and searching further back would move the mark into an earlier measure, while a
     * second breath mark in a row would duplicate it onto one note. Both cases drop the mark,
     * which is what the streaming writer's peek did.
     */
    private static void foldBreathMark(BuildContext ctx, Line line, int elementIndex) {
        if (elementIndex == 0) {
            return;
        }

        var precedingNote = ctx.index().notes().get(line.getElements().get(elementIndex - 1));

        if (precedingNote == null) {
            return;
        }

        var factory = ctx.factory();
        articulationsOf(ctx, precedingNote).getAccentOrStrongAccentOrStaccato()
            .add(factory.createArticulationsBreathMark(factory.createBreathMark()));
    }

    // -------------------------------------------------------------------------
    // The note's own children
    // -------------------------------------------------------------------------

    /**
     * Builds {@code <pitch>} for {@code element}. {@code <alter>} is emitted only when the note
     * actually sounds altered — a natural sign is an accidental, not an alteration.
     */
    private static Pitch buildPitch(BuildContext ctx, StaffElement element) {
        var spelling = PitchSpelling.spell(element.getStaffPosition());
        var alterSemitones = PitchSpelling.soundingAlterFor(element);
        var pmPitch = ctx.factory().createPitch();

        pmPitch.setStep(Step.fromValue(String.valueOf(spelling.step())));

        if (alterSemitones != 0) {
            pmPitch.setAlter(BigDecimal.valueOf(alterSemitones));
        }

        pmPitch.setOctave(spelling.octave());

        return pmPitch;
    }

    /**
     * Builds {@code <accidental>} for {@code element}, or returns {@code null} when it carries
     * none. A parenthesized accidental is a cautionary one: {@code cautionary="yes"
     * parentheses="yes"}.
     */
    private static @Nullable Accidental buildAccidental(BuildContext ctx, StaffElement element) {
        var accidental = element.getAccidental();

        if (accidental == null) {
            return null;
        }

        var pmAccidental = ctx.factory().createAccidental();
        pmAccidental.setValue(AccidentalValue.fromValue(AccidentalMapping.forAccidental(accidental)));

        if (element.isAccidentalInParentheses()) {
            pmAccidental.setCautionary(YesNo.YES);
            pmAccidental.setParentheses(YesNo.YES);
        }

        return pmAccidental;
    }

    /**
     * Builds {@code <stem>} for {@code element}, or returns {@code null} when the stem direction
     * is automatic and the reader can derive it.
     *
     * <p>A grace-note stem always goes up and carries a computed stem-tip position so external
     * renderers can draw it without re-running layout. The tip is the note-head's height above
     * the middle line plus the standard extension; the sign is negated because staff positions
     * increase downward while MusicXML's Y is positive upward.
     */
    private static @Nullable Stem buildStem(BuildContext ctx, StaffElement element, boolean isGrace) {
        if (!isGrace && element.isStemDirectionAuto()) {
            return null;
        }

        var pmStem = ctx.factory().createStem();

        if (isGrace) {
            pmStem.setValue(StemValue.UP);
            pmStem.setDefaultY(BigDecimal.valueOf(
                element.getStaffPosition() * -MusicXmlUnits.TENTHS_PER_STAFF_POSITION
                    + MusicXmlUnits.GRACE_STEM_EXTENSION_TENTHS));
        } else {
            pmStem.setValue(element.isUpper() ? StemValue.UP : StemValue.DOWN);
        }

        return pmStem;
    }

    /**
     * Builds the {@code <time-modification>} for a note belonging to {@code tuplet}:
     * {@code <actual-notes>} is the tuplet's grade, and — once the tuplet's ratio is known —
     * {@code <normal-notes>}, {@code <normal-type>} and one {@code <normal-dot/>} per dot
     * describe the written value whose time the group occupies.
     *
     * <p>{@code <normal-type>} is the discriminator the reader trusts: a file carrying it states
     * its own ratio, so the reader takes {@code <normal-notes>} at face value. A tuplet whose
     * ratio is still unresolved therefore emits neither, and falls back to the conventional
     * power-of-two {@code <normal-notes>} so the document stays schema-valid; the reader hands
     * such a tuplet to the post-load pass to have its ratio derived.
     */
    private static TimeModification buildTimeModification(BuildContext ctx, Tuplet tuplet) {
        var factory = ctx.factory();
        var grade = tuplet.getGrade();
        var noteValue = tuplet.getNoteValue();
        var normalTypeToken = noteValue == null ? null : NoteTypeMapping.typeToken(noteValue);
        var timeModification = factory.createTimeModification();

        timeModification.setActualNotes(BigInteger.valueOf(grade));

        if (normalTypeToken == null) {
            // Reaching here means a tuplet escaped the load pass without a ratio, which the
            // model says cannot happen. Say so rather than letting a placeholder ratio go to
            // disk unremarked: 2:1 is not the compound duplet a 2 over a dotted beat means.
            LOG.warn("Writing a tuplet with grade {} whose ratio was never resolved;"
                + " falling back to a placeholder <normal-notes>", grade);
            timeModification.setNormalNotes(BigInteger.valueOf(largestPowerOfTwoBelowGrade(grade)));
        } else {
            timeModification.setNormalNotes(BigInteger.valueOf(tuplet.getNormalNotes()));
            timeModification.setNormalType(normalTypeToken);

            var normalDots = timeModification.getNormalDot();

            for (var i = 0; i < tuplet.getNoteValueDots(); i++) {
                normalDots.add(factory.createEmpty());
            }
        }

        return timeModification;
    }

    /**
     * The {@code <dynamics>} child for {@code dynamicType}. Each mark is an empty element of its
     * own name, and the list holding them is a {@code List<JAXBElement<?>>}, so every one has to
     * be wrapped by its factory method — a bare {@link Empty} in that list does not marshal.
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

    // -------------------------------------------------------------------------
    // Value helpers
    // -------------------------------------------------------------------------

    /**
     * The performed tick count for one note: its written duration, scaled by
     * {@code normalNotes / grade} when the note belongs to a resolved tuplet.
     *
     * <p>{@link NoteTypeMapping#DIVISIONS} is chosen so that this scaling is exact for every
     * note value, dot count and tuplet ratio the validator accepts, so an inexact result means
     * {@code DIVISIONS} itself is wrong. That throws rather than rounding, mirroring
     * {@link NoteTypeMapping#ticks}.
     *
     * @param tuplet the tuplet this note belongs to, or {@code null} if none
     */
    private static int performedTicks(ElementType type, int dotCount, @Nullable Tuplet tuplet) {
        var writtenTicks = NoteTypeMapping.ticks(type, dotCount);

        // An unresolved tuplet states no ratio to scale by — the post-load pass derives one, so
        // until then the written duration is all there is.
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
     * Returns the largest power of two strictly less than {@code grade} (3→2, 5→4, 6→4, 7→4) —
     * the placeholder {@code <normal-notes>} for a tuplet whose real ratio is not yet resolved.
     * The schema requires the element, and no better value exists before the post-load pass has
     * run.
     */
    private static int largestPowerOfTwoBelowGrade(int grade) {
        var result = 1;

        while (result * 2 < grade) {
            result *= 2;
        }

        return result;
    }
}
