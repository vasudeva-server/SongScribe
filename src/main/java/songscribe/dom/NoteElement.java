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
 * pitch.
 *
 * <p>{@link ElementType#newInstance()} builds one for every type whose appearance is a notehead
 * and for no other, which is what makes the narrowing to that arm sound.
 *
 * <p>A note stores whether it is tied to the next note, and nothing more: the note the tie ends
 * on is derived each time ({@link #tie()}, {@link #incomingTie()}). Only a note that passes
 * {@link Tie#canAnchor} is ever tied. Apart from {@link #copyStateFrom} and conversion, only
 * {@link Line#addTie} and {@link Line#removeTie} write whether a note in a line is tied.
 */
public final class NoteElement extends StaffElement implements Dottable {

    private StaffPosition staffPosition = new StaffPosition(0);
    private Dots dots = Dots.NONE;
    private @Nullable Slide slide = null;

    private boolean tiedToNext = false;

    private Stem stem = Stem.AUTO;
    private AccidentalState accidentalState = new AccidentalState.Adopting();

    private final List<Articulation> articulations = new ArrayList<>();

    NoteElement(ElementType type) {
        super(type);
    }

    public Notehead notehead() {
        return ((NoteheadAppearance) getType().appearance()).notehead();
    }

    /**
     * Ask it only of a note whose type {@link ElementType#isNoteWithStem()}.
     */
    public StemmedNotehead stemmedNotehead() {
        return (StemmedNotehead) notehead();
    }

    public StaffPosition getStaffPosition() {
        return staffPosition;
    }

    /**
     * Leaves {@link #accidentalState()} untouched; whether it still holds at the new position is
     * the caller's business.
     */
    public void setStaffPosition(StaffPosition staffPosition) {
        this.staffPosition = staffPosition;
    }

    public Stem stem() {
        return stem;
    }

    /**
     * @param stem {@link Stem#AUTO} for a grace note, whose direction is fixed at up
     */
    public void setStem(Stem stem) {
        this.stem = stem;
    }

    /**
     * Needs no layout pass to have run. A grace note always answers {@code UP}.
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
     * The direction a forced stem is measured against when layout asks whether it points the
     * unnatural way.
     */
    public Direction naturalDirection() {
        return (staffPosition.value() > 0) || getType().isGraceNote() ? Direction.UP : Direction.DOWN;
    }

    private Direction autoDirection() {
        var line = getParentLine();

        if (line == null) {
            return naturalDirection();
        }

        var beam = line.spanRegistry().findBeamJoining(line.getElementIndex(this));

        return beam == null ? naturalDirection() : beam.direction();
    }

    @Override
    public Dots dots() {
        return dots;
    }

    @Override
    public void setDots(Dots dots) {
        this.dots = dots;
    }

    public @Nullable Slide getSlide() {
        return slide;
    }

    public boolean hasFall() {
        return slide == Slide.FALL;
    }

    public boolean hasGlissando() {
        return slide == Slide.GLISSANDO;
    }

    public void setSlide(Slide slide) {
        this.slide = slide;
    }

    public void removeSlide() {
        slide = null;
    }

    /**
     * Answers without checking that a following note exists; a caller that needs the host checks
     * for one itself.
     */
    public boolean isPairedGraceNote() {
        return getType().isGraceNote() && hasGlissando();
    }

    /**
     * @invariant {@code true} only on a note that passes the pitched-note check
     *     ({@link Tie#canAnchor})
     */
    public boolean isTiedToNext() {
        return tiedToNext;
    }

    /**
     * Returns the tie that starts on this note.
     *
     * <p>The end is derived, never stored: it is the first element after this note that is not a
     * legal separator ({@link Tie#isLegalSeparator} — an element with no duration, other than a
     * final double barline or a key change). When this note's line runs out of elements first,
     * the walk continues at the start of the next line of the song, so <b>the end may be in the
     * next line</b>. It crosses that one line break and no more.
     *
     * <p>Each call makes a new {@link Tie}; ties made from the same two notes are equal.
     *
     * @return the tie from this note to its end, or {@code null} when this note stores no tie
     *     ({@link #isTiedToNext()} is {@code false}), is in no line, or the walk reaches no note
     *     to end on — its first non-separator fails the pitched-note check
     *     ({@link Tie#canAnchor}), or the song, or the next line, runs out first
     * @invariant a non-null result's {@link Tie#anchor()} is this note, and
     *     {@code result.end().incomingTie()} equals the result
     */
    public @Nullable Tie tie() {
        var line = getParentLine();

        if (!tiedToNext || line == null) {
            return null;
        }

        var reached = Tie.endAfter(line, line.getElementIndex(this));

        return reached == null ? null : Tie.between(this, reached.element());
    }

    /**
     * Returns the tie that ends on this note.
     *
     * <p>Walks back from this note over legal separators ({@link Tie#isLegalSeparator}), into
     * the end of the previous line of the song when this note's line has nothing else before
     * it. The first element that is not a legal separator is the only note whose tie can end
     * here, so the walk crosses one line break and no more, as {@link #tie()}'s does.
     *
     * @return that note's {@link #tie()} when it ends on this note, or {@code null} when no tie
     *     ends here: this note is in no line, the walk reaches no note, or the note it reaches
     *     is not tied
     * @invariant a non-null result's {@link Tie#end()} is this note, and
     *     {@code result.anchor().tie()} equals the result
     */
    public @Nullable Tie incomingTie() {
        var line = getParentLine();

        if (line == null) {
            return null;
        }

        var candidate = Tie.anchorBefore(line, line.getElementIndex(this));

        if (!(candidate instanceof NoteElement anchor)) {
            return null;
        }

        var tie = anchor.tie();

        //noinspection ObjectEquality
        return tie != null && tie.end() == this ? tie : null;
    }

    public boolean isTiedTo(NoteElement next) {
        var tie = tie();

        return tie != null && tie.equals(next.incomingTie());
    }

    public List<NoteElement> tieChain() {
        var first = this;

        for (var incoming = incomingTie(); incoming != null; incoming = first.incomingTie()) {
            first = incoming.anchor();
        }

        var chain = new ArrayList<NoteElement>();
        chain.add(first);

        for (var outgoing = first.tie(); outgoing != null; outgoing = outgoing.end().tie()) {
            chain.add(outgoing.end());
        }

        return chain;
    }

    /**
     * Called only inside the mutator of {@link Line#addTie}.
     */
    void tieToNext() {
        tiedToNext = true;
    }

    /**
     * Called only inside the mutator of {@link Line#removeTie}.
     */
    void untie() {
        tiedToNext = false;
    }

    /**
     * For a note in no line only; a note in a line is untied through {@link Line#removeTie},
     * which records the change.
     */
    public void untieDetached() {
        if (getParentLine() != null) {
            throw new IllegalStateException("a note in a line is untied through Line.removeTie: " + this);
        }

        tiedToNext = false;
    }

    public List<Articulation> getArticulations() {
        return Collections.unmodifiableList(articulations);
    }

    /**
     * @param articulation on no note, of a type this note does not already carry
     */
    public void addArticulation(Articulation articulation) {
        articulations.add(articulation);
        articulation.setOwner(this);
    }

    public void removeArticulation(Articulation articulation) {
        if (articulations.remove(articulation)) {
            articulation.setOwner(null);
        }
    }

    public @Nullable Articulation findArticulation(ArticulationType type) {
        for (var articulation : articulations) {
            if (articulation.getType() == type) {
                return articulation;
            }
        }

        return null;
    }

    public boolean hasArticulation(ArticulationType type) {
        return findArticulation(type) != null;
    }

    /**
     * @return {@code null} when no articulation overrides the sounding duration and the note
     *     sounds for its whole duration
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

    public void clearArticulations() {
        for (var articulation : articulations) {
            articulation.setOwner(null);
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

    public AccidentalState accidentalState() {
        return accidentalState;
    }

    public ChainedAccidental chainedAccidental() {
        if (accidentalState instanceof ChainedAccidental chained) {
            return chained;
        }

        throw new IllegalStateException("Note is not stamped: " + accidentalState);
    }

    public void choose(Accidental sounding) {
        accidentalState = new AccidentalState.Chosen(sounding);
    }

    public void pitchAs(NoteElement source) {
        accidentalState = source.accidentalState.intent();
    }

    public void adopt() {
        accidentalState = new AccidentalState.Adopting();
    }

    /**
     * Package-private so that only {@link AccidentalChain.Restamp#stamp} writes one.
     */
    void stamp(ChainedAccidental decided) {
        accidentalState = decided;
    }

    public SavedAccidental saveAccidentalState() {
        return new SavedAccidental(this, accidentalState);
    }

    /**
     * A note's accidental state as it stood when {@link #saveAccidentalState} was called; it
     * restores onto that note only.
     */
    public static final class SavedAccidental {

        private final NoteElement note;
        private final AccidentalState state;

        private SavedAccidental(NoteElement note, AccidentalState state) {
            this.note = note;
            this.state = state;
        }

        public NoteElement note() {
            return note;
        }

        /**
         * Records nothing; pass it as the mutator of {@link Line#modifyElement}.
         */
        public void restore() {
            note.accidentalState = state;
        }
    }

    public int getPitch() {
        return DiatonicPitch.at(staffPosition.value()).midi(accidentalState.sounding());
    }

    @Override
    public NoteElement clone() {
        var copy = new NoteElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }

    /**
     * Copies the whole of what a note holds beyond the base class. The tie is taken only when
     * this note passes {@link Tie#canAnchor}, so {@link #isTiedToNext()}'s invariant holds.
     */
    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
        var sourceNote = (NoteElement) source;
        staffPosition = sourceNote.staffPosition;
        accidentalState = sourceNote.accidentalState;
        stem = sourceNote.stem;
        slide = sourceNote.slide;
        tiedToNext = sourceNote.tiedToNext && Tie.canAnchor(this);
        dots = sourceNote.dots;
        copyArticulationsFrom(sourceNote);
    }

    /**
     * The tie is kept only when this note passes {@link Tie#canAnchor}, so a note converted to a
     * grace note is not tied.
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
