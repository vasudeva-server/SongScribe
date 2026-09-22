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
package songscribe.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.StaffPosition;
import songscribe.smufl.Notehead;
import songscribe.smufl.StemmedNotehead;

/**
 * A sounding element: a note or a grace note, drawn from a notehead. The one leaf that has a
 * pitch, and so the only one that carries the state a pitch brings with it — a staff position,
 * an accidental, a slide, articulations and augmentation dots. A {@link StructuralElement} has
 * none of these and answers none of the questions below.
 *
 * <p>Answers its notehead from its type's {@link NoteheadAppearance}, so a question only a note
 * can answer — which notehead, which stem anchors — is total here and always answered from what
 * the type declares. {@link ElementType#newInstance()} builds one for every type whose
 * appearance is a notehead and for no other, which is what makes the narrowing to that arm
 * sound.
 *
 * <p>The staff position is a {@link StaffPosition}, counted in half staff spaces from the
 * middle staff line, Y-down: 0 is the middle line, negative is higher on the page. B4 sits on
 * the middle line:
 * <table>
 * <tr><th>Pitch<th>Value
 * <tr><td>D5<td>-2
 * <tr><td>C5<td>-1
 * <tr><td>B4<td>0
 * <tr><td>A4<td>1
 * <tr><td>G4<td>2
 * </table>
 *
 * <p>A note carries its sounding alteration itself, as its {@link AccidentalState}, so a
 * detached note and a clipboard clone have a pitch without a line. Which state a note is in,
 * and when, is the contract of {@link AccidentalState}; how the chain walk decides a stamped
 * note is in {@code docs/accidentals.md}.
 */
public final class NoteElement extends StaffElement implements Dottable {

    private StaffPosition staffPosition = new StaffPosition(0);
    private Dots dots = Dots.NONE;
    private @Nullable Slide slide = null;
    private Stem stem = Stem.AUTO;
    private AccidentalState accidentalState = new AccidentalState.Adopting();

    /** Articulations applied to this note (staccato, accent, etc.) */
    private final List<Articulation> articulations = new ArrayList<>();

    NoteElement(ElementType type) {
        super(type);
    }

    /**
     * @return the notehead this note is drawn with
     */
    public Notehead notehead() {
        return ((NoteheadAppearance) getType().appearance()).notehead();
    }

    /**
     * The notehead, narrowed to the arm a stem attaches to. Ask it only of a note whose type
     * {@link ElementType#isNoteWithStem()}: a whole note has no stemmed notehead to answer.
     *
     * @return this note's notehead as a {@link StemmedNotehead}
     */
    public StemmedNotehead stemmedNotehead() {
        return (StemmedNotehead) notehead();
    }

    // ========================================================================
    // Staff position
    // ========================================================================

    /**
     * Returns where this note's head sits on the staff, which is its written pitch class and
     * octave.
     *
     * @return the staff position; see the class Javadoc for the lattice
     */
    public StaffPosition getStaffPosition() {
        return staffPosition;
    }

    /**
     * Moves this note's head to {@code staffPosition}, changing its written pitch. Nothing else
     * about the note follows: its {@link #accidentalState()} is untouched, and whether that
     * state still holds at the new position is the caller's business.
     *
     * @param staffPosition the new staff position
     * @effects changes what {@link #getStaffPosition()} and {@link #getPitch()} answer
     */
    public void setStaffPosition(StaffPosition staffPosition) {
        this.staffPosition = staffPosition;
    }

    // ========================================================================
    // Stem
    // ========================================================================

    /**
     * Returns who decides this note's stem direction.
     *
     * @return the notator's forced direction, or {@link Stem#AUTO} when the score decides
     */
    public Stem stem() {
        return stem;
    }

    /**
     * Writes who decides this note's stem direction.
     *
     * @param stem the forced direction, or {@link Stem#AUTO} to hand the decision back to the
     *     score; {@link Stem#AUTO} for a grace note, whose direction is fixed at up and reads
     *     nothing here — the stem action and the file readers both refuse to force one
     * @effects changes what {@link #stem()} answers, and so what {@link #direction()} answers
     *     for this note and for every auto stem in a beam this note is a member of
     */
    public void setStem(Stem stem) {
        this.stem = stem;
    }

    /**
     * Returns the direction this note's stem points.
     *
     * <p>Total: every note answers, whether or not it has a stem to draw, is in a line, or has
     * been laid out. Nothing here needs geometry, so no layout pass has to run first. A grace
     * note's stem is always up: it is never a beam member, whatever beam's span it sits in, its
     * flag is always drawn up, and no notator's choice reaches it, so it answers {@code UP}
     * before the stem is consulted. For any other note a forced stem answers the notator's
     * choice, and an auto stem answers what the score decides:
     *
     * <ul>
     *   <li>a beam member answers its beam's group direction ({@link Beam#direction()});</li>
     *   <li>any other note answers its {@link #naturalDirection()}: up below the middle line,
     *       down on it and above it;</li>
     *   <li>a note in no line has no beam to belong to and answers its natural direction.</li>
     * </ul>
     *
     * @return the stem direction, never null
     * @invariant a grace note answers {@link Direction#UP}; any other note whose {@link #stem()}
     *     is {@link Stem.Forced} answers that arm's direction
     */
    public Direction direction() {
        if (getType().isGraceNote()) {
            return Direction.UP;
        }

        return switch (stem) {
            case Stem.Forced forced -> forced.direction();
            case Stem.Auto _ -> autoDirection();
        };
    }

    /**
     * The direction a stem takes from the note's own position: up below the middle line, down
     * on it and above it, and always up for a grace note. The half of {@link #direction()} that
     * ignores beams and the notator, and the direction a forced stem is measured against when
     * layout asks whether it points the unnatural way.
     *
     * @return the natural direction, never null
     */
    public Direction naturalDirection() {
        return (staffPosition.value() > 0) || getType().isGraceNote() ? Direction.UP : Direction.DOWN;
    }

    private Direction autoDirection() {
        var line = getParentLine();

        if (line == null) {
            return naturalDirection();
        }

        var beam = line.spanRegistry().findBeamAt(line.getElementIndex(this));

        return beam == null ? naturalDirection() : beam.direction();
    }

    // ========================================================================
    // Augmentation dots
    // ========================================================================

    @Override
    public Dots dots() {
        return dots;
    }

    @Override
    public void setDots(Dots dots) {
        this.dots = dots;
    }

    // ========================================================================
    // Slide
    // ========================================================================

    /**
     * Returns the slide leaving this note.
     *
     * @return the slide, or {@code null} when the note has none
     */
    public @Nullable Slide getSlide() {
        return slide;
    }

    /**
     * @return {@code true} when this note trails off with a {@link Slide#FALL}
     */
    public boolean hasFall() {
        return slide == Slide.FALL;
    }

    /**
     * @return {@code true} when this note connects to the following one with a
     *     {@link Slide#GLISSANDO}
     */
    public boolean hasGlissando() {
        return slide == Slide.GLISSANDO;
    }

    /**
     * Writes a slide on this note, replacing any it had.
     *
     * @param slide the slide to write
     * @effects changes what {@link #getSlide()}, {@link #hasFall()}, {@link #hasGlissando()} and
     *     {@link #isPairedGraceNote()} answer
     */
    public void setSlide(Slide slide) {
        this.slide = slide;
    }

    /**
     * Removes this note's slide, if it has one.
     *
     * @effects leaves {@link #getSlide()} answering {@code null}
     */
    public void removeSlide() {
        slide = null;
    }

    /**
     * Whether this element is a grace note paired with the note that follows it — a grace
     * note carrying a connecting {@link Slide#GLISSANDO}. The pairing is what the element itself
     * can say about it, and it says so whether or not a following note is actually there;
     * the containing run adds only a bounds check on the grace note's own index (see
     * {@link LyricRun#isPairedGraceNote}). A caller that needs the host to work with checks
     * for one itself, as {@link LyricRun#syncGraceHostMelisma} does.
     *
     * @return {@code true} when this is a grace note carrying a glissando
     */
    public boolean isPairedGraceNote() {
        return getType().isGraceNote() && hasGlissando();
    }

    // ========================================================================
    // Articulations
    // ========================================================================

    /**
     * Returns an unmodifiable view of the articulations on this note.
     *
     * @return the articulations, in the order they were added
     */
    public List<Articulation> getArticulations() {
        return Collections.unmodifiableList(articulations);
    }

    /**
     * Adds an articulation to this note, making the note its parent.
     *
     * @param articulation an articulation on no note, of a type this note does not already
     *     carry
     * @effects appends it to {@link #getArticulations()} and to this element's children
     */
    public void addArticulation(Articulation articulation) {
        articulations.add(articulation);
        addChild(articulation);
    }

    /**
     * Removes an articulation from this note. An articulation this note does not hold is left
     * alone.
     *
     * @param articulation the articulation to remove
     * @effects removes it from {@link #getArticulations()} and from this element's children
     */
    public void removeArticulation(Articulation articulation) {
        if (articulations.remove(articulation)) {
            removeChild(articulation);
        }
    }

    /**
     * Returns this note's articulation of the given type. A note carries at most one of any
     * type, so this is the articulation rather than the first of several.
     *
     * @param type the articulation type to look for
     * @return the articulation, or {@code null} if this note has none of that type
     */
    public @Nullable Articulation findArticulation(ArticulationType type) {
        for (var articulation : articulations) {
            if (articulation.getType() == type) {
                return articulation;
            }
        }

        return null;
    }

    /**
     * @param type the articulation type to look for
     * @return {@code true} if this note has an articulation of the given type
     */
    public boolean hasArticulation(ArticulationType type) {
        return findArticulation(type) != null;
    }

    /**
     * Returns how much of this note's played duration it sounds for, when an articulation
     * shortens it.
     *
     * @return the percentage from the first articulation that overrides the sounding duration,
     *     or {@code null} when none of them does and the note sounds for its whole duration
     */
    public @Nullable Integer findMidiDurationOverride() {
        for (var articulation : articulations) {
            var midiDurationPercent = articulation.getType().getMidiDurationPercent();

            if (midiDurationPercent != null) {
                return midiDurationPercent;
            }
        }

        return null;
    }

    /**
     * Removes all articulations from this note.
     *
     * @effects leaves {@link #getArticulations()} empty
     */
    public void clearArticulations() {
        for (var articulation : articulations) {
            removeChild(articulation);
        }

        articulations.clear();
    }

    /**
     * Replaces this note's articulations with deep copies of the source note's articulations,
     * reparented to this note.
     *
     * @param source the note whose articulations to copy
     * @effects replaces {@link #getArticulations()} with copies of {@code source}'s
     */
    public void copyArticulationsFrom(NoteElement source) {
        clearArticulations();

        for (var articulation : source.getArticulations()) {
            addArticulation(articulation.copy());
        }
    }

    // ========================================================================
    // Accidental
    // ========================================================================

    /**
     * Returns what this note knows about its own accidental.
     *
     * @return the state; {@link AccidentalState.Adopting} on a freshly constructed note
     */
    public AccidentalState accidentalState() {
        return accidentalState;
    }

    /**
     * Returns the chain walk's decision for this note.
     *
     * @return the chained accidental
     * @throws IllegalStateException unless the walk has decided the note
     */
    public ChainedAccidental chainedAccidental() {
        if (accidentalState instanceof ChainedAccidental chained) {
            return chained;
        }

        throw new IllegalStateException("Note is not stamped: " + accidentalState);
    }

    /**
     * Returns the alteration this note sounds: a stamped note's chained accidental's
     * alteration, or a chosen note's.
     *
     * @return the sounding alteration
     * @throws IllegalStateException on an {@link AccidentalState.Adopting} note, which has no
     *     sounding alteration of its own
     */
    public Accidental soundingAccidental() {
        return switch (accidentalState) {
            case ChainedAccidental chained -> chained.accidental();
            case AccidentalState.Chosen(var sounding) -> sounding;
            case AccidentalState.Adopting _ -> throw new IllegalStateException(
                "An adopting note has no sounding accidental: " + this);
        };
    }

    /**
     * Returns the accidental drawn beside this note's head. A chosen note draws its alteration
     * so the preview note shows the accidental the notator pressed.
     *
     * @return a stamped and written note's accidental; a chosen note's alteration; {@code null}
     *     for a stamped note that is not written and for an adopting note
     */
    public @Nullable Accidental drawnAccidental() {
        return switch (accidentalState) {
            case ChainedAccidental chained -> chained.isWritten() ? chained.accidental() : null;
            case AccidentalState.Chosen(var sounding) -> sounding;
            case AccidentalState.Adopting _ -> null;
        };
    }

    /**
     * Pitches this note with {@code sounding}, for the chain walk to decide.
     *
     * @param sounding the alteration the note sounds
     * @effects {@link #accidentalState()} is {@link AccidentalState.Chosen} with
     *     {@code sounding} afterwards
     */
    public void choose(Accidental sounding) {
        accidentalState = new AccidentalState.Chosen(sounding);
    }

    /**
     * Pitches this note as {@code source} is pitched, for the chain walk to decide.
     *
     * @param source the note whose pitch to take
     * @effects this note is {@link AccidentalState.Chosen} with {@code source}'s sounding
     *     alteration, or {@link AccidentalState.Adopting} when {@code source} is adopting
     */
    public void pitchAs(NoteElement source) {
        accidentalState = switch (source.accidentalState) {
            case ChainedAccidental chained -> new AccidentalState.Chosen(chained.accidental());
            case AccidentalState.Chosen chosen -> chosen;
            case AccidentalState.Adopting adopting -> adopting;
        };
    }

    /**
     * Takes away this note's own pitch, so it sounds what is in force where it lands.
     *
     * @effects {@link #accidentalState()} is {@link AccidentalState.Adopting} afterwards
     */
    public void adopt() {
        accidentalState = new AccidentalState.Adopting();
    }

    /**
     * Gives this note the chain walk's decision. Package-private so that only
     * {@link AccidentalChain.Restamp#stamp} writes one.
     *
     * @param decided the walk's decision for this note
     */
    void stamp(ChainedAccidental decided) {
        accidentalState = decided;
    }

    /**
     * Saves this note's accidental state, so that it can be put back later.
     *
     * @return a saved state that {@link SavedAccidental#restore} puts back on this note only
     */
    public SavedAccidental saveAccidentalState() {
        return new SavedAccidental(this, accidentalState);
    }

    /**
     * A note's accidental state as it stood when {@link #saveAccidentalState} was called. It
     * can put that state back on that note only, so no caller can give a note a state it did
     * not hold before.
     */
    public static final class SavedAccidental {

        private final NoteElement note;
        private final AccidentalState state;

        private SavedAccidental(NoteElement note, AccidentalState state) {
            this.note = note;
            this.state = state;
        }

        /**
         * @return the note whose state this is
         */
        public NoteElement note() {
            return note;
        }

        /**
         * Puts the saved state back on the note, plainly: nothing is recorded, so a caller
         * inside a modification bracket passes this as the mutator of
         * {@link Line#modifyElement}.
         *
         * @effects {@code note().accidentalState()} is the saved state afterwards
         */
        public void restore() {
            note.accidentalState = state;
        }
    }

    // ========================================================================
    // Pitch
    // ========================================================================

    /**
     * Returns the MIDI pitch this note sounds: its staff position's diatonic pitch, altered by
     * its {@link #soundingAccidental()}. No line and no key are consulted.
     *
     * @return the MIDI pitch
     * @throws IllegalStateException on an {@link AccidentalState.Adopting} note, which has no
     *     sounding alteration and so no pitch
     */
    public int getPitch() {
        return DiatonicPitch.at(staffPosition.value()).midi(soundingAccidental());
    }

    @Override
    public NoteElement clone() {
        var copy = new NoteElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }

    /**
     * Copies the state a pitch brings with it: the staff position, the accidental state, the
     * stem, the slide, the articulations and the dot count. The whole of what a note holds beyond the base
     * class, so an in-place restore through {@link #copyStateFrom} leaves nothing of the undone
     * edit behind.
     *
     * @param source the note whose state to take; always a {@code NoteElement}, because a copy
     *     names an element of the same class
     */
    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
        var sourceNote = (NoteElement) source;
        staffPosition = sourceNote.staffPosition;
        accidentalState = sourceNote.accidentalState;
        stem = sourceNote.stem;
        slide = sourceNote.slide;
        dots = sourceNote.dots;
        copyArticulationsFrom(sourceNote);
    }

    /**
     * Takes from a converted note everything {@link #copySubtypeStateFrom} copies, so that a
     * crotchet turned into a quaver keeps its pitch, accidental state, stem, slide and
     * articulations; from any other source, only the dots, which is all such a source has that
     * a note can hold.
     */
    @Override
    protected void applySubtypeConversionFrom(StaffElement source) {
        if (source instanceof NoteElement sourceNote) {
            copySubtypeStateFrom(sourceNote);
        } else {
            dots = Dottable.dotsOf(source);
        }
    }

    @Override
    public String toString() {
        return "NoteElement{type=" + getType().name() + ", staffPosition=" + staffPosition.value() + '}';
    }
}
