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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.SMuFLConstants;

/**
 * Abstract base class for elements that span multiple elements.
 * <p>
 * Spans include ties, trills, crescendo/diminuendo hairpins,
 * tuplet brackets, and first/second endings.
 * <p>
 * Each span has:
 * <ul>
 *   <li>An anchor element (first element in the range)</li>
 *   <li>Methods to determine the range extent</li>
 * </ul>
 * <p>
 * Concrete subclasses will be implemented in Phase 4.
 */
public abstract class Span extends LineElement {

    /**
     * Creates a span reaching from the anchor element to the end element.
     *
     * @param anchorElement The first element in the range
     * @param endElement    The last element in the range
     */
    protected Span(StaffElement anchorElement, StaffElement endElement) {
        this.anchorElement = anchorElement;
        this.endElement = endElement;
    }

    /** The first element in this range. */
    private @Nullable StaffElement anchorElement;

    /** The last element in this range. */
    private @Nullable StaffElement endElement;

    /**
     * Returns the first element in this range.
     */
    public @Nullable StaffElement getAnchorElement() {
        return anchorElement;
    }

    /**
     * Sets the first element in this range.
     */
    public void setAnchorElement(@Nullable StaffElement anchorElement) {
        this.anchorElement = anchorElement;
    }

    /**
     * Returns the last element in this range.
     */
    public @Nullable StaffElement getEndElement() {
        return endElement;
    }

    /**
     * Sets the last element in this range.
     */
    public void setEndElement(@Nullable StaffElement endElement) {
        this.endElement = endElement;
    }

    /**
     * Returns whether this span is in {@code line}, meaning either of its endpoints is.
     * <p>
     * A span is never attached to a line: its inherited {@code parentLine} stays
     * {@code null} for its whole life, and this derives parentage from where the endpoints
     * sit instead. A span whose endpoints straddle a line boundary is therefore in both
     * lines, which is what no single stored pointer could say.
     * <p>
     * Two reference comparisons and nothing else: it allocates nothing and is safe to ask
     * on any path.
     * <p>
     * It answers where the endpoints are, which is not the same question as whether the
     * span is still in the document — a removed span whose endpoints survive still reports
     * {@code true}. A caller that means "is this span still live on that line" must ask
     * {@code line.getSpans()}, as {@code SelectionCoordinator.isOnLine} does.
     */
    public boolean isIn(Line line) {
        return getAnchorLine() == line || getEndLine() == line;
    }

    /**
     * The line the anchor element belongs to, or {@code null} when the anchor is unset or
     * sits in no line. Null-safe so callers deciding which lines a span reaches — including
     * {@link Line#appendChild} and {@link Song#removeSpansBetweenNonAdjacentLines} — express
     * that in one call rather than each repeating the unwrap.
     * <p>
     * This is the endpoint's <em>own</em> line, which is not the same question as where the
     * endpoint sits relative to some other line; for that, ask that line via
     * {@link Line#anchorIndexOf}.
     */
    public @Nullable Line getAnchorLine() {
        var anchor = anchorElement;
        return anchor == null ? null : anchor.getParentLine();
    }

    /** The line the end element belongs to. See {@link #getAnchorLine}. */
    public @Nullable Line getEndLine() {
        var end = endElement;
        return end == null ? null : end.getParentLine();
    }

    /**
     * Returns whether this span is invalidated by the given deletion.
     * <p>
     * A span is invalidated when its anchor or end element is among the deleted elements,
     * because the range can no longer be rendered without both endpoints. Subclasses may override
     * this method if their invalidation condition is more nuanced.
     * <p>
     * This is an implementation detail of the default {@link #outcomeFor}, which is the only
     * caller and the only entry point {@link Line} uses. A new span type should override
     * {@code outcomeFor} instead — a boolean predicate can only ever say "remove me", so a
     * type that implements this one silently forfeits {@link SpanOutcome.Reshape}.
     *
     * @param deletedElements the elements that were removed from the line
     * @return {@code true} if this span should be removed as a result of the deletion
     */
    protected boolean isInvalidatedBy(List<StaffElement> deletedElements) {
        return deletedElements.contains(anchorElement) || deletedElements.contains(endElement);
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * invalidates this span. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByInsertion(int insertedIndex, ElementType insertedType, Line line) {
        return false;
    }

    /**
     * Returns true if deleting the given elements invalidates this span beyond what
     * {@link #isInvalidatedBy} already detects. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByDeletion(List<StaffElement> deletedElements, Line line) {
        return false;
    }

    /**
     * Returns true if replacing {@code oldElement} with {@code newElement} invalidates
     * this span. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByReplacement(StaffElement oldElement, StaffElement newElement, Line line) {
        return false;
    }

    /**
     * Decides what should happen to this span when {@code change} lands on {@code line}.
     * <p>
     * <b>The decision is made before the change lands.</b> {@code line} still holds its
     * pre-change elements; every judgement must be made against
     * {@link ElementChange#projectedElements}, which is what the line will look like
     * afterwards, and never against the line's current elements. Undo replays a step's
     * mutations in reverse, so the companion span mutation this answer produces has to be
     * recorded <em>before</em> the primary element mutation — deciding after the fact would
     * record a span already re-pointed at the new element, and undo would restore it broken.
     * {@code line} is passed only so an implementor can ask the line questions the projection
     * cannot answer on its own, such as which spans it carries.
     * <p>
     * <b>A replacement re-points, it does not delete.</b> {@link Line#setElement} points any
     * span whose anchor or end was the replaced element at the replacement. So under
     * {@link ElementChange.Replacement}, a span whose endpoint is
     * {@link ElementChange.Replacement#oldElement} must be read as having that endpoint
     * become {@link ElementChange.Replacement#newElement} — <em>not</em> as having lost an
     * endpoint. Reading the old element as deleted would silently remove every span whose
     * endpoint is edited.
     * <p>
     * The default is a bridge over the four per-shape predicates — {@link #isInvalidatedBy},
     * {@link #isInvalidatedByInsertion}, {@link #isInvalidatedByDeletion} and
     * {@link #isInvalidatedByReplacement} — routing each change shape to exactly the
     * predicate the line consults for it today, so a subclass that overrides only those
     * keeps behaving as it always has. A subclass overriding {@code outcomeFor} need not
     * implement any of them.
     * <p>
     * A boolean predicate can only ever say "remove me". {@link SpanOutcome.Reshape} is here
     * for a subclass whose span survives an edit in a different shape, and whose
     * {@code [begin, end]} are indices into {@link ElementChange#projectedElements}.
     *
     * @param change the pending change, not yet applied to {@code line}
     * @param line   the line being changed, still in its pre-change state
     */
    public SpanOutcome outcomeFor(ElementChange change, Line line) {
        return isInvalidatedByChange(change, line) ? SpanOutcome.Simple.REMOVE : SpanOutcome.Simple.KEEP;
    }

    /**
     * Routes {@code change} to the one per-shape predicate {@link Line} consulted for it
     * before {@link #outcomeFor} existed. Separated from {@code outcomeFor} so the
     * boolean-to-outcome mapping is written once rather than once per shape.
     */
    private boolean isInvalidatedByChange(ElementChange change, Line line) {
        return switch (change) {
            case ElementChange.Insertion insertion ->
                isInvalidatedByInsertion(insertion.index(), insertion.inserted().getType(), line);

            case ElementChange.Replacement replacement ->
                isInvalidatedByReplacement(replacement.oldElement(), replacement.newElement(), line);

            case ElementChange.Deletion deletion -> {
                var deletedElements = deletion.deletedElements();

                yield isInvalidatedBy(deletedElements) || isInvalidatedByDeletion(deletedElements, line);
            }
        };
    }

    /**
     * Returns whether removing this span as a side effect of an edit warrants a
     * user confirmation prompt. Default is false: spans such as beams, ties, and
     * tuplets are removed silently. Subclasses may override (endings do, since their loss
     * is significant enough to confirm first).
     */
    public boolean requiresInvalidationConfirm() {
        return false;
    }

    /**
     * Returns the width of this span in staff-space units.
     * <p>
     * Computed as the distance from the anchor element's X position to the
     * right edge of the end element, all in staff spaces.
     */
    @Override
    public double getContentWidthSs() {
        var anchor = anchorElement;
        var end = endElement;

        if (anchor == null || end == null) {
            return 0;
        }

        return Math.abs(end.getXSs() - anchor.getXSs()) + end.getContentWidthSs();
    }

    @Override
    public double getContentWidthPx() {
        return DocumentScale.ssToPx(getContentWidthSs());
    }

    @Override
    public double getContentHeightPx() {
        return DocumentScale.ssToPx(getContentHeightSs());
    }

    /**
     * Returns the horizontal span width for collision detection in staff-space units.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    public abstract double getSpanWidthSs(double anchorXSs, double endXSs);

    /**
     * Returns the end element's own glyph width in staff spaces — per type, so a whole note is
     * measured as a whole note (refs #694). Implementations of {@link #getSpanWidthSs} whose span
     * runs past the end element's origin need this to know how far past it to reach.
     *
     * <p>Falls back to the black-notehead width for a range that has no end element yet, which is
     * the same reservation such a range got before the width became per-type.
     */
    protected double getEndElementWidthSs() {
        var end = endElement;

        if (end == null) {
            return SMuFLConstants.NOTE_HEAD_WIDTH_SS;
        }

        return end.getType().getElementWidthSs();
    }

    /**
     * The position of {@code element} within the line it is in, or -1 when there is no
     * such position: the endpoint is unset, or it sits in no line because a removal
     * detached it ({@link Line#removeElement}).
     */
    private static int indexInLine(@Nullable StaffElement element) {
        if (element == null) {
            return -1;
        }

        var line = element.getParentLine();

        if (line == null) {
            return -1;
        }

        return line.getElementIndex(element);
    }

    /**
     * Returns the index of the anchor element within its line.
     * Returns -1 if the anchor element is not set or not in a line.
     */
    public int getAnchorElementIndex() {
        return indexInLine(anchorElement);
    }

    /**
     * Returns the index of the end element within its line.
     * Returns -1 if the end element is not set or not in a line.
     */
    public int getEndElementIndex() {
        return indexInLine(endElement);
    }

    /**
     * A test on a span's endpoints as the asking line resolved them.
     * <p>
     * The bounds are {@link SpanBound}s, not indices, because an endpoint may sit off one of
     * that line's edges or nowhere at all; the queried positions stay {@code int} because a
     * query is always a real in-line position. That asymmetry is what keeps a cross-line
     * half from matching a query for a span genuinely anchored at this line's first or last
     * element.
     */
    @FunctionalInterface
    public interface IndexPredicate {
        boolean test(SpanBound anchorBound, SpanBound endBound);
    }

    /**
     * A predicate matching a span whose anchor-to-end range includes {@code elementIndex}.
     * <p>
     * A half whose far endpoint is off an edge covers every element it passes over on the way
     * there, so a cross-line tie's line-A half contains everything from its anchor to the last
     * element. {@link SpanBound#ABSENT} is rejected outright: an endpoint with no position
     * bounds nothing, so a half-detached span is never reported as covering an element.
     */
    public static IndexPredicate containing(int elementIndex) {
        return (anchorBound, endBound) -> anchorBound.atOrBefore(elementIndex) && endBound.atOrAfter(elementIndex);
    }

    /**
     * A predicate matching a span overlapping the inclusive element index range
     * {@code [begin, end]}.
     * <p>
     * Off-edge bounds read as unbounded in their direction, exactly as in {@link #containing}.
     * An {@link SpanBound#ABSENT} anchor is deliberately <em>not</em> rejected: the trill
     * sweeps in {@link Line#addTrill} and {@link Line#removeTrillsOverlapping} rely on a
     * half-detached span still being found so it can be cleaned up.
     */
    public static IndexPredicate overlapping(int begin, int end) {
        return (anchorBound, endBound) ->
            (anchorBound.isAbsent() || anchorBound.atOrBefore(end)) && endBound.atOrAfter(begin);
    }

    /**
     * A predicate matching a span that overlaps {@code [begin, end]} by more than a
     * single shared endpoint: its end falls past {@code begin} and its anchor falls
     * before {@code end}.
     * <p>
     * Two hairpins may meet — one ends on the element the next begins on, and the
     * wedges back away from that shared column (LilyPond's back-to-back rule). More
     * than that one element in common is a genuine collision.
     * <p>
     * Off-edge and absent bounds read exactly as in {@link #overlapping}, and
     * {@link SpanBound#isAt} is false for all of them — so a half-detached span
     * matches, and the caller treats it as a collision. That is deliberate: a bound
     * with no position cannot be shown to share only an endpoint.
     */
    public static IndexPredicate overlappingBeyondEndpoint(int begin, int end) {
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var overlaps = overlapping(begin, end);

        return (anchorBound, endBound) ->
            overlaps.test(anchorBound, endBound)
                && !endBound.isAt(begin)
                && !anchorBound.isAt(end);
    }

    /**
     * A predicate matching a span that covers {@code elementIndex} strictly inside its
     * range — the bound elements themselves do not count.
     * <p>
     * A text dynamic may sit on a hairpin's bound, where the wedge pads away from it, but
     * never under the wedge, so the editor asks about the strict interior rather than the
     * inclusive range.
     * <p>
     * Off-edge and absent bounds read exactly as in {@link #containing}, which rejects
     * {@link SpanBound#ABSENT} outright, and {@link SpanBound#isAt} is false for
     * {@link SpanBound#BEFORE_LINE} / {@link SpanBound#AFTER_LINE} — so a bound with no
     * position in the asking line cannot exempt an element, and every element the half
     * covers reads as interior.
     */
    public static IndexPredicate strictlyContaining(int elementIndex) {
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var contains = containing(elementIndex);

        return (anchorBound, endBound) ->
            contains.test(anchorBound, endBound)
                && !anchorBound.isAt(elementIndex)
                && !endBound.isAt(elementIndex);
    }

    /**
     * A predicate matching a span whose anchor and end are exactly the given indices.
     * <p>
     * Only a resolved position can equal a queried one — an off-edge bound is never coerced
     * to index 0 or to the last index, so a cross-line half never answers an exact-range query.
     */
    public static IndexPredicate exactly(int anchorIndex, int endIndex) {
        return (anchorBound, endBound) ->
            anchorBound instanceof SpanBound.At(var spanAnchorIndex) && spanAnchorIndex == anchorIndex &&
            endBound instanceof SpanBound.At(var spanEndIndex) && spanEndIndex == endIndex;
    }

    /**
     * Creates a copy of this span anchored to the given elements, carrying over
     * all {@link LineElement}-level user state (offsets, margins, position).
     * <p>
     * This is the single place LineElement-level state is copied, so a new subclass cannot
     * forget it. Subclasses carry their own subclass-specific state in {@link #createCopy}.
     * Does not set {@code parentLine} — no span ever has one; see {@link #isIn} — and does
     * not copy derived caches.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     * @return A new span of the same concrete type as this one
     */
    public final Span copy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = createCopy(newAnchor, newEnd);
        copy.setUserXOffsetSs(getUserXOffsetSs());
        copy.setUserYOffsetSs(getUserYOffsetSs());
        copy.setMarginSs(getMarginTopSs(), getMarginRightSs(), getMarginBottomSs(), getMarginLeftSs());
        copy.setPosition(getPositionSs());
        return copy;
    }

    /**
     * Creates a new instance of this span's concrete subclass, anchored to the
     * given elements and carrying over any subclass-specific state. Called only by
     * {@link #copy}, which layers on the shared {@link LineElement}-level state.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     */
    protected abstract Span createCopy(StaffElement newAnchor, StaffElement newEnd);
}
