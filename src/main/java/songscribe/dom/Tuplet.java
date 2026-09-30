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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

import songscribe.Strings;
import songscribe.message.mutation.ElementField;

/**
 * Represents a tuplet grouping (triplet, quintuplet, etc.).
 * <p>
 * Tuplets group notes that are played in the time normally occupied
 * by a different number of notes. The most common is a triplet (3 notes
 * in the time of 2).
 * <p>
 * The tuplet bracket is typically placed above if stems point down,
 * below if stems point up.
 */
public final class Tuplet extends Span<StaffElement> {

    // The grade and the ratio are set by the constructor and never change afterwards. Changing
    // the grade alone would leave the tuplet printing one ratio and playing another, so there is
    // no setter: a grade change is a remove plus an add, which re-derives both.
    private final TupletGrade grade;
    private final TupletRatio ratio;

    /**
     * Creates a fully specified tuplet grouping: {@code grade} notes played in the time
     * {@code ratio} states.
     * <p>
     * The ratio's written value is a base type plus its dots rather than a tick duration, so
     * it needs no decomposition on the way out to MusicXML.
     *
     * @param anchorElement The first note in the tuplet
     * @param endElement    The last note in the tuplet
     * @param grade         The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param ratio         The time the group occupies
     */
    public Tuplet(StaffElement anchorElement, StaffElement endElement, TupletGrade grade, TupletRatio ratio) {
        super(StaffElement.class, anchorElement, endElement);
        this.grade = grade;
        this.ratio = ratio;
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Tuplet(newAnchor, newEnd, grade, ratio);
    }

    @Override
    protected void copyOwnStateFrom(Span<StaffElement> source) {
    }

    /**
     * Whether this tuplet's anchor draws an upward stem: a stemmed note whose stem points up.
     * An anchor with no stem to draw — a whole note, a rest — never does, whatever direction it
     * would answer. The one statement of the test every bracket edge and the number-only rule
     * read, so the drawn bracket, its reserved room and its hit region agree on which side of
     * the anchor the arm sits.
     *
     * @return {@code true} when the anchor is a stemmed note whose stem points up
     */
    public boolean anchorStemUp() {
        return getAnchorElement() instanceof NoteElement note
            && note.getType().isNoteWithStem()
            && note.direction().isUp();
    }

    /**
     * Returns true when only the number is drawn (no bracket): the tuplet is beamed
     * at both its anchor and end index and its anchor note has upward stems.
     *
     * @return {@code true} when the bracket is omitted and only the number is drawn
     */
    public boolean isNumberOnly() {
        var spans = line().spanRegistry();

        return spans.findBeamAt(requireAnchorElementIndex()) != null
            && spans.findBeamAt(requireEndElementIndex()) != null
            && anchorStemUp();
    }

    /**
     * Decides what happens to this tuplet when {@code change} lands: the group is removed by
     * any structural change strictly inside its bracket, and kept by everything else.
     * <p>
     * A tuplet is a rhythmic claim about exactly the elements between its endpoints, so its
     * survival depends on its interior, not only on its endpoints the way a tie's or a beam's
     * does. An insertion after the anchor and no later than the end element adds time the
     * group did not account for; a deletion overlapping the bracket takes time away; and a
     * replacement any of whose pairs rewrites a written duration inside it, or an in-place
     * modification that does — the distinction {@link ElementField#DURATION_AFFECTING} draws —
     * leaves the group rhythmically invalid (refs #726). An insertion at the anchor's own
     * index, or just past the end element, stays outside and leaves the group alone.
     * <p>
     * A replacement's pairs are judged one at a time, and one unbalancing pair is enough to
     * drop the group; pairs outside the bracket are ignored. Only a pair touching a
     * duration-carrying element counts. A tuplet may contain a
     * barline or a breath mark, which contribute no time to the group, so swapping one of
     * those for another non-duration element cannot unbalance it. Past that gate, any change
     * of element type or dot count drops the group. That is deliberately broader than "the
     * written duration changed": a note swapped for a rest of the same value keeps the group's
     * total time but can starve it of the pitched notes {@link TupletValidator.Reason#TOO_FEW_NOTES}
     * demands, so it goes too. The user re-brackets what they meant.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} whose reason is
     *     {@link Strings#DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED} when {@code change}
     *     invalidates the group, otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        var range = range();

        var invalidated = switch (change) {
            case ElementChange.Insertion insertion ->
                range.begin() < insertion.index() && insertion.index() <= range.end();

            case ElementChange.Deletion deletion -> range.overlaps(deletion.range());

            case ElementChange.Replacement replacement -> {
                var line = replacement.line();

                yield replacement.newElements().entrySet().stream()
                    .anyMatch(pair -> range.contains(pair.getKey())
                        && replacementUnbalances(line.getElement(pair.getKey()), pair.getValue()));
            }

            case ElementChange.Modification modification ->
                range.contains(modification.index())
                    && !Collections.disjoint(modification.fields(), ElementField.DURATION_AFFECTING);
        };

        return removeWhen(invalidated, Strings.DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED);
    }

    @Override
    protected String reportHeadingKey() {
        return Strings.DIALOG_REPORT_TUPLETS_REMOVED;
    }

    /**
     * Whether putting {@code newElement} in {@code oldElement}'s place rewrites a written
     * duration this tuplet was built on — see {@link #outcomeFor}.
     */
    private static boolean replacementUnbalances(StaffElement oldElement, StaffElement newElement) {
        var oldType = oldElement.getType();
        var newType = newElement.getType();

        if (!oldType.isDuration() && !newType.isDuration()) {
            return false;
        }

        return oldType != newType || Dottable.dotsOf(oldElement) != Dottable.dotsOf(newElement);
    }

    /**
     * Returns the tuplet grade (3 for triplet, 5 for quintuplet, etc.).
     *
     * @return the grade, whose number is the printed tuplet number
     */
    public TupletGrade getGrade() {
        return grade;
    }

    /**
     * Returns the time this group occupies.
     *
     * @return the ratio the tuplet was built with
     */
    public TupletRatio getRatio() {
        return ratio;
    }

    /**
     * Scales a written duration inside this tuplet to the time it is performed in:
     * {@code writtenTicks × normalNotes ÷ grade}, where {@code normalNotes} is
     * {@code getRatio().normalNotes()} and {@code grade} is {@code getGrade().number()}.
     *
     * <p>The two callers need different roundings, which is why the caller names one:
     * <ul>
     *   <li>MusicXML export passes {@link RoundingMode#UNNECESSARY}. Its tick base is chosen so
     *       that every duration the validator accepts scales exactly, so an inexact result means
     *       the tick base is wrong, and the exception surfaces that instead of writing a duration
     *       that is off.</li>
     *   <li>MIDI playback passes {@link RoundingMode#HALF_UP}. Its tick base cannot divide by
     *       every grade, so it scales each offset measured from the tuplet's anchor and rounds it
     *       to the nearest tick; rounding the offset rather than each note's duration keeps every
     *       note within half a tick of its exact time, with no drift across the group.</li>
     * </ul>
     *
     * @param writtenTicks the written duration or offset to scale, at least zero, in any tick base
     * @param rounding     how to round a result that is not a whole number of ticks
     * @return the performed duration, in the same tick base as {@code writtenTicks}
     * @throws ArithmeticException when {@code rounding} is {@link RoundingMode#UNNECESSARY} and the
     *     result is not a whole number of ticks, or when the scaled value does not fit in a
     *     {@code long}
     */
    public long performedTicks(long writtenTicks, RoundingMode rounding) {
        var scaled = BigDecimal.valueOf(writtenTicks).multiply(BigDecimal.valueOf(ratio.normalNotes()));

        return scaled.divide(BigDecimal.valueOf(grade.number()), 0, rounding).longValueExact();
    }

    /**
     * {@link Displacement#REPLACE_OVERLAPPING}: a new tuplet replaces every tuplet it overlaps,
     * since two groupings cannot both claim the same notes' time.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.REPLACE_OVERLAPPING;
    }
}
