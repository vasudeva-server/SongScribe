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
import java.util.List;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
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
    private final TupletNormalTime ratio;

    /** The heading a removed or refused tuplet is reported under. */
    private static final String REPORT_HEADING_KEY = Strings.DIALOG_REPORT_TUPLETS_REMOVED;

    /**
     * Creates a fully specified tuplet grouping: {@code grade} notes played in the time
     * {@code ratio} states. Its two callers are the two ways to make a tuplet:
     * {@link #spanning} and {@link #spanningStated} call it once {@link TupletValidator} has
     * accepted the span, and {@link #createCopy} over the endpoints of a tuplet it already
     * accepted.
     * <p>
     * The ratio's written value is a base type plus its dots rather than a tick duration, so
     * it needs no decomposition on the way out to MusicXML.
     *
     * @param anchorElement The first note in the tuplet
     * @param endElement    The last note in the tuplet
     * @param grade         The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param ratio         The time the group occupies
     */
    private Tuplet(StaffElement anchorElement, StaffElement endElement, TupletGrade grade, TupletNormalTime ratio) {
        super(StaffElement.class, anchorElement, endElement);
        this.grade = grade;
        this.ratio = ratio;
    }

    /**
     * Where a tuplet would sit: a range of a line, and the beat in effect at its anchor.
     * <p>
     * The beat travels with the range because resolving it costs a walk over every element
     * before the anchor ({@link Song#beatUnitAt}). A caller judging one range resolves it once
     * through {@link #at}, however many grades it tries; a caller judging every tuplet of a song
     * takes its sites from {@link TupletValidator#sitesFrom}, which carries the beat forward.
     *
     * @param line  the line the tuplet would be on, one of its song's lines
     * @param range the range the tuplet would cover, from its anchor to its last element; both
     *              of its indices are indices of {@code line}
     * @param beat  the beat in effect at {@code range.begin()} of {@code line}, including a beat
     *              the anchor itself defines
     */
    public record Site(Line line, ElementRange range, BeatUnit beat) {

        /**
         * The site of {@code range} of {@code line}, with the beat looked up.
         *
         * @param line  a line of its song
         * @param range a range of {@code line}
         * @return the site, carrying {@link Song#beatUnitAt} at {@code range.begin()}
         * @throws IllegalStateException if {@code line} is not one of its song's lines
         */
        public static Site at(Line line, ElementRange range) {
            return new Site(line, range, line.getSong().beatUnitAt(line.index(), range.begin()));
        }
    }

    /**
     * Returns a tuplet of {@code grade} over {@code site}, with the ratio derived from the span
     * and its beat, when {@link TupletValidator#validate} accepts the span; or the reason it does
     * not. This is the form for a tuplet whose ratio nobody stated: the user's toggle, and a
     * file that records only the printed number. It costs the span's length.
     * <p>
     * The tuplet is built, not added: the line's span registry is unchanged. Adding it replaces
     * every tuplet it overlaps ({@link Displacement#REPLACE_OVERLAPPING}), and reports each.
     *
     * @param site       the range and the beat at its anchor
     * @param grade      N, the printed tuplet number
     * @param strictness how much of the rule set to enforce
     * @return {@link Spanning.Spanned} with a new tuplet of {@code grade} over
     *     {@code site.range()}, anchored on its first element and ending on its last, with the
     *     derived ratio, absorbing nothing; or {@link Spanning.Refused} with
     *     {@link Spanning.Cause#RANGE}, under {@link Strings#DIALOG_REPORT_TUPLETS_REMOVED}, and
     *     the reason of the first check {@link TupletValidator#validate} fails, in the order it
     *     states
     */
    public static Spanning<Tuplet> spanning(Site site, TupletGrade grade, TupletValidator.Strictness strictness) {
        var context = TupletValidator.measure(site);

        return spanningFor(site, grade, TupletValidator.validate(context, grade, strictness));
    }

    /**
     * Returns a tuplet of {@code grade} over {@code site} with the ratio {@code stated}, when
     * {@link TupletValidator#validateStated} accepts it there; or the reason it does not. This is
     * the form for a tuplet that already carries a ratio: one a file states, and one on the
     * clipboard. It costs the span's length.
     * <p>
     * The tuplet is built, not added: the line's span registry is unchanged. Adding it replaces
     * every tuplet it overlaps ({@link Displacement#REPLACE_OVERLAPPING}), and reports each.
     *
     * @param site       the range and the beat at its anchor
     * @param grade      N, as stated
     * @param stated     M and V, as stated
     * @param strictness how much of the rule set to enforce
     * @return {@link Spanning.Spanned} with a new tuplet of {@code grade} over
     *     {@code site.range()}, anchored on its first element and ending on its last, with
     *     {@code stated} as its ratio, absorbing nothing; or {@link Spanning.Refused} with
     *     {@link Spanning.Cause#RANGE}, under {@link Strings#DIALOG_REPORT_TUPLETS_REMOVED}, and
     *     the reason of the first check {@link TupletValidator#validateStated} fails, in the
     *     order it states
     */
    public static Spanning<Tuplet> spanningStated(
        Site site, TupletGrade grade, TupletNormalTime stated, TupletValidator.Strictness strictness
    ) {
        var context = TupletValidator.measure(site);

        return spanningFor(site, grade, TupletValidator.validateStated(context, grade, stated, strictness));
    }

    /** The answer for {@code site} once {@link TupletValidator} has given {@code verdict}. */
    private static Spanning<Tuplet> spanningFor(Site site, TupletGrade grade, TupletValidator.Result verdict) {
        return switch (verdict) {
            case TupletValidator.Result.Valid(var ratio) -> {
                var line = site.line();
                var range = site.range();
                var tuplet = new Tuplet(line.getElement(range.begin()), line.getElement(range.end()), grade, ratio);

                yield new Spanning.Spanned<>(tuplet, List.of());
            }

            case TupletValidator.Result.Invalid(var reasonKey) ->
                new Spanning.Refused<>(reasonKey, Spanning.Cause.RANGE, REPORT_HEADING_KEY);
        };
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Tuplet(newAnchor, newEnd, grade, ratio);
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
     * Returns true when only the number is drawn (no bracket): one beam covers the whole tuplet
     * and its anchor draws an upward stem ({@link #anchorStemUp}). A beam is a contiguous range,
     * so one beam that holds both the anchor and the end covers the tuplet; a tuplet under two
     * beams, one at each end, draws its bracket.
     *
     * @return {@code true} when the bracket is omitted and only the number is drawn
     */
    public boolean isNumberOnly() {
        return line().spanRegistry().sameBeamAt(requireAnchorElementIndex(), requireEndElementIndex())
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
     * leaves the group rhythmically invalid. An insertion at the anchor's own
     * index, or just past the end element, stays outside and leaves the group alone.
     * <p>
     * A replacement's pairs are judged one at a time, and one unbalancing pair is enough to
     * drop the group; pairs outside the bracket are ignored. Only a pair touching a
     * duration-carrying element counts. A tuplet may contain a
     * barline or a breath mark, which contribute no time to the group, so swapping one of
     * those for another non-duration element cannot unbalance it. Past that gate, any change
     * of element type or dot count drops the group. That is deliberately broader than "the
     * written duration changed": a note swapped for a rest of the same value keeps the group's
     * total time but can starve it of the pitched notes {@link TupletValidator} demands, so it
     * goes too. The user re-brackets what they meant.
     * <p>
     * A modification that writes {@link ElementField#FERMATA} on an element inside the tuplet,
     * endpoints included, removes it for a reason of its own: a fermata leaves the group's
     * timing indefinite. The sweep runs before the change lands, and a
     * {@link ElementChange.Modification} names only the fields it rewrites, so this arm cannot
     * tell adding a fermata from removing one. It does not need to: a kept tuplet never holds a
     * fermata, so every fermata write inside one is an addition.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} whose reason is
     *     {@link Strings#DIALOG_REPORT_REASON_FERMATA} for a fermata written inside the group,
     *     or {@link Strings#DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED} when {@code change}
     *     otherwise invalidates it; otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        var range = range();

        return switch (change) {
            case ElementChange.Insertion insertion ->
                interiorChangedWhen(range.begin() < insertion.index() && insertion.index() <= range.end());

            case ElementChange.Deletion deletion -> interiorChangedWhen(range.overlaps(deletion.range()));

            case ElementChange.Replacement replacement -> {
                var line = replacement.line();

                yield interiorChangedWhen(replacement.newElements().entrySet().stream()
                    .anyMatch(pair -> range.contains(pair.getKey())
                        && replacementUnbalances(line.getElement(pair.getKey()), pair.getValue())));
            }

            case ElementChange.Modification modification -> outcomeForModification(modification, range);
        };
    }

    /**
     * The modification arm of {@link #outcomeFor}: a fermata written inside {@code range}
     * removes the tuplet for the fermata reason, and a duration-affecting field written inside
     * it for the interior reason.
     *
     * @param modification the pending modification
     * @param range        this tuplet's range in the pre-modification line
     * @return the outcome {@link #outcomeFor} states for {@code modification}
     */
    private static SpanOutcome outcomeForModification(ElementChange.Modification modification, ElementRange range) {
        if (!range.contains(modification.index())) {
            return SpanOutcome.Keep.INSTANCE;
        }

        var fields = modification.fields();

        if (fields.contains(ElementField.FERMATA)) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_FERMATA);
        }

        return interiorChangedWhen(!Collections.disjoint(fields, ElementField.DURATION_AFFECTING));
    }

    /**
     * The {@link #outcomeFor} answer for a change that does or does not unbalance the group's
     * interior.
     *
     * @param invalidated whether the change unbalances the group
     * @return a {@link SpanOutcome.Remove} with
     *     {@link Strings#DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED} when {@code invalidated},
     *     otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    private static SpanOutcome interiorChangedWhen(boolean invalidated) {
        return outcomeWhen(invalidated, Strings.DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED);
    }

    @Override
    protected String reportHeadingKey() {
        return REPORT_HEADING_KEY;
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
    public TupletNormalTime getRatio() {
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
