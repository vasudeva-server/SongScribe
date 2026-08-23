/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Every span query expressed over three accessors, so the "iterate spans, filter by type,
 * resolve both endpoint indices, compare" loop exists in exactly one place.
 * <p>
 * An implementor supplies only the spans to query and how each span's endpoints resolve to
 * positions; {@link #findSpans(Class, Span.IndexPredicate)}, {@link #findFirstSpan(Class,
 * Span.IndexPredicate)}, and {@link #findSpans(Class)} are the only methods here that iterate
 * over positions, and every typed query stated in terms of an index delegates to one of them
 * with a {@link Span.IndexPredicate}.
 * <p>
 * {@link #findTiesTouching} is the one exception, and iterates itself: it matches on the
 * endpoint elements rather than on where they resolve to, which is the only question a
 * cross-line tie can answer about itself in both of its lines (#493).
 */
public interface SpanLookup {

    /** The spans to query, in the order they were added. */
    List<Span> getSpans();

    /**
     * Where the anchor element sits <i>relative to this lookup's own line</i>: an
     * {@link SpanBound.At} when the anchor is in this line, otherwise which side of this line
     * it fell off, or {@link SpanBound#ABSENT} when it has no position at all.
     * <p>
     * Every query below resolves endpoints through this method and {@link #endIndexOf}, never
     * through {@link Span#getAnchorElementIndex()}, which answers from whichever line the
     * endpoint itself belongs to. The receiver has to decide because a position in some other
     * line is indistinguishable from a real one: a tie whose anchor sits at index 7 of the
     * previous line would otherwise report "7" to this line and be read as covering this
     * line's element 7. Only the line being asked knows that the answer is "off my left edge",
     * and only that answer lets it draw and query its own half of the span.
     */
    SpanBound anchorIndexOf(Span span);

    /**
     * Where the end element sits relative to this lookup's own line. See
     * {@link #anchorIndexOf} for why the receiver decides.
     */
    SpanBound endIndexOf(Span span);

    // --- the only three methods that iterate over positions -----------------

    /**
     * Returns every span of {@code type} whose resolved endpoint indices satisfy {@code matches}.
     */
    default <T extends Span> List<T> findSpans(Class<T> type, Span.IndexPredicate matches) {
        var result = new ArrayList<T>();

        for (var span : getSpans()) {
            if (type.isInstance(span) && matches.test(anchorIndexOf(span), endIndexOf(span))) {
                result.add(type.cast(span));
            }
        }

        return result;
    }

    /**
     * Returns the first span of {@code type} whose resolved endpoint indices satisfy
     * {@code matches}, or {@code null} if there is none. Short-circuits on the first match
     * and allocates nothing.
     */
    default <T extends Span> @Nullable T findFirstSpan(Class<T> type, Span.IndexPredicate matches) {
        for (var span : getSpans()) {
            if (type.isInstance(span) && matches.test(anchorIndexOf(span), endIndexOf(span))) {
                return type.cast(span);
            }
        }

        return null;
    }

    /**
     * Returns every span of {@code type}, whatever its endpoints resolve to.
     * <p>
     * Filters by type only and resolves no endpoint positions — unlike routing through
     * {@link #findSpans(Class, Span.IndexPredicate)} with an always-true predicate, which
     * would still resolve both endpoints of every matching span because Java evaluates
     * arguments eagerly.
     */
    default <T extends Span> List<T> findSpans(Class<T> type) {
        var result = new ArrayList<T>();

        for (var span : getSpans()) {
            if (type.isInstance(span)) {
                result.add(type.cast(span));
            }
        }

        return result;
    }

    // --- expressed via those three ------------------------------------------

    /**
     * Returns whether any span of {@code type} satisfies {@code matches}.
     */
    default boolean hasSpan(Class<? extends Span> type, Span.IndexPredicate matches) {
        return findFirstSpan(type, matches) != null;
    }

    // --- typed queries -----------------------------------------------------

    /**
     * Returns all {@link Beam} spans overlapping [begin, end] inclusive.
     */
    default List<Beam> findBeamsOverlapping(int begin, int end) {
        return findSpans(Beam.class, Span.overlapping(begin, end));
    }

    /**
     * Returns the first {@link Beam} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any beam.
     */
    default @Nullable Beam findBeamAt(int elementIndex) {
        return findFirstSpan(Beam.class, Span.containing(elementIndex));
    }

    /**
     * Returns true if one and the same {@link Beam} covers both {@code firstIndex} and
     * {@code secondIndex}.
     */
    default boolean sameBeamAt(int firstIndex, int secondIndex) {
        var beamAtFirst = findBeamAt(firstIndex);

        //noinspection ObjectEquality
        return beamAtFirst != null && beamAtFirst == findBeamAt(secondIndex);
    }

    /**
     * Returns all {@link Tie} spans in this line.
     */
    default List<Tie> findTies() {
        return findSpans(Tie.class);
    }

    /**
     * Returns the first {@link Tie} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tie.
     */
    default @Nullable Tie findTieAt(int elementIndex) {
        return findFirstSpan(Tie.class, Span.containing(elementIndex));
    }

    /**
     * Returns the {@link Tie} span whose anchor is exactly {@code anchorIndex}
     * and end is exactly {@code endIndex}, or {@code null} if no tie spans that exact
     * range. Unlike {@link #findTieAt(int)}, this disambiguates chained ties that share
     * an endpoint note — {@code findTieAt} would return whichever overlapping tie comes
     * first, not necessarily the one matching a specific selection.
     */
    default @Nullable Tie findExactTie(int anchorIndex, int endIndex) {
        return findFirstSpan(Tie.class, Span.exactly(anchorIndex, endIndex));
    }

    /**
     * Returns the {@link Tie} whose two endpoints are exactly the elements {@code anchor} and
     * {@code end}, or {@code null} if there is none.
     * <p>
     * The identity counterpart of {@link #findExactTie}, and the only way to ask for a
     * cross-line tie: that tie's far bound is a direction rather than a position, so it can
     * never equal a queried index and no index predicate can name it (#493). Identity also
     * keeps a chained tie that merely ends on one of the two notes from being mistaken for
     * this one, which is what {@code findExactTie} uses both indices for.
     */
    default @Nullable Tie findTieBetween(StaffElement anchor, StaffElement end) {
        for (var tie : findTiesTouching(anchor)) {
            //noinspection ObjectEquality
            if (tie.getEndElement() == end) {
                return tie;
            }
        }

        return null;
    }

    /**
     * Returns every {@link Tie} in this lookup that has {@code element} as one of its two
     * endpoint elements, in the order the ties were added.
     * <p>
     * The one method here that matches on endpoint elements rather than on resolved indices,
     * so it is the one that iterates without going through the three above — see
     * {@link #findTieBetween} for why an index predicate cannot answer this.
     */
    default List<Tie> findTiesTouching(StaffElement element) {
        var result = new ArrayList<Tie>();

        for (var span : getSpans()) {
            //noinspection ObjectEquality
            if (span instanceof Tie tie
                && (tie.getAnchorElement() == element || tie.getEndElement() == element)) {
                result.add(tie);
            }
        }

        return result;
    }

    /**
     * Returns true if one and the same {@link Tie} covers both {@code firstIndex} and
     * {@code secondIndex}.
     */
    default boolean sameTieAt(int firstIndex, int secondIndex) {
        var tieAtFirst = findTieAt(firstIndex);

        //noinspection ObjectEquality
        return tieAtFirst != null && tieAtFirst == findTieAt(secondIndex);
    }

    /**
     * Returns the first {@link Tuplet} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tuplet.
     */
    default @Nullable Tuplet findTupletAt(int elementIndex) {
        return findFirstSpan(Tuplet.class, Span.containing(elementIndex));
    }

    /**
     * Returns all {@link Tuplet} spans overlapping [begin, end] inclusive.
     */
    default List<Tuplet> findTupletsOverlapping(int begin, int end) {
        return findSpans(Tuplet.class, Span.overlapping(begin, end));
    }

    /**
     * Returns true if the given note index falls strictly inside any hairpin (crescendo or
     * diminuendo), its bound elements excluded.
     * <p>
     * This is the editor's rule for where a text dynamic may go: a dynamic may sit on a
     * hairpin's anchor or end element, but never under the wedge. See
     * {@code docs/hairpin-editing.md}.
     */
    default boolean isInsideHairpin(int noteIndex) {
        return hasSpan(Hairpin.class, Span.strictlyContaining(noteIndex));
    }

    /**
     * Finds every trill span overlapping {@code [beginIndex, endIndex]}.
     */
    default List<Trill> findTrillsOverlapping(int beginIndex, int endIndex) {
        return findSpans(Trill.class, Span.overlapping(beginIndex, endIndex));
    }

    /**
     * Returns whether any trill span overlaps {@code [beginIndex, endIndex]}.
     * Cheaper than {@link #findTrillsOverlapping} when only presence matters: it
     * short-circuits and allocates no intermediate lists.
     */
    default boolean hasTrillOverlapping(int beginIndex, int endIndex) {
        return hasSpan(Trill.class, Span.overlapping(beginIndex, endIndex));
    }

    /**
     * Returns all {@link Ending} spans in this line.
     */
    default List<Ending> findEndings() {
        return findSpans(Ending.class);
    }

    /**
     * Returns the {@link Ending} that spans {@code elementIndex}, or null if none.
     */
    default @Nullable Ending findEndingAt(int elementIndex) {
        return findFirstSpan(Ending.class, Span.containing(elementIndex));
    }

    /**
     * Returns true if {@code elementIndex} falls inside any ending.
     */
    default boolean isInsideAnyEnding(int elementIndex) {
        return hasSpan(Ending.class, Span.containing(elementIndex));
    }
}
