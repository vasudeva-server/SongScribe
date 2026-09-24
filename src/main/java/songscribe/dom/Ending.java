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

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 *
 * @invariant a bar whose type {@link ElementType#closesRepeat()} lies strictly between the
 *            anchor and the end — the split at which the two brackets part. Every creator
 *            outside this class goes through {@link #spanning}, which answers no ending for a
 *            range without one. Every edit that would remove the split — deleting it, or
 *            replacing it with a bar that does not close a repeat — invalidates the ending
 *            instead, so no attached ending is ever without its split
 */
public final class Ending extends Span<StaffElement> {

    /**
     * Height of volta bracket tick marks in staff-space units.
     */
    public static final double VOLTA_TICK_HEIGHT_SS = 2.0;

    /** Horizontal inset of label from left arm of bracket. */
    public static final double LABEL_X_INSET_SS = 0.9;

    /** Vertical gap between the bracket's horizontal line and the top of the label. */
    public static final double LABEL_Y_OFFSET_SS = 0.5;

    /**
     * Describes the effect of replacing one element with another on this ending.
     * Used by the UI layer to decide whether to abort, confirm-and-invalidate,
     * or confirm-and-compensate before calling {@link Line#setElement}.
     */
    public sealed interface EndingEffect
        permits EndingEffect.None, EndingEffect.Invalidate,
                EndingEffect.CompensateEnd, EndingEffect.CompensateSplit {

        /** No ending is affected. */
        enum None implements EndingEffect {
            INSTANCE
        }

        /** The ending would be invalidated and removed. UI shows Confirm-I. */
        record Invalidate(Ending ending) implements EndingEffect {}

        /**
         * The split is being changed in a way that requires the end element to also change.
         * UI shows Confirm-R and, on Yes, applies primary split change + compensating end change.
         */
        record CompensateEnd(Ending ending, ElementType newEndType) implements EndingEffect {}

        /**
         * The end is being changed in a way that requires the split element to also change.
         * UI shows Confirm-R and, on Yes, applies primary end change + compensating split change.
         */
        record CompensateSplit(Ending ending, ElementType newSplitType) implements EndingEffect {}
    }

    /**
     * Creates an ending bracket over two elements the caller has already checked hold a split
     * between them.
     *
     * @param anchorElement The first element of the ending
     * @param endElement    The last element of the ending
     */
    private Ending(StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
    }

    /**
     * Returns an ending over {@code span} of {@code line}, when the range holds the split the
     * class invariant requires. This is the one way to build an ending outside this class, so
     * the editor and the file readers, which may each hold a range that has no split, get the
     * same answer to whether it may carry one.
     * <p>
     * The ending is built, not added: {@code line}'s span registry is unchanged.
     *
     * @param line the line the ending would be on
     * @param span the range the ending would cover; both of its indices are indices of
     *     {@code line}
     * @return an ending anchored on the element at {@code span.begin()} and ending on the element
     *     at {@code span.end()}, or {@code null} when no bar strictly inside {@code span}
     *     {@link ElementType#closesRepeat()}
     */
    public static @Nullable Ending spanning(Line line, ElementRange span) {
        if (closingRepeatsBetween(line, span).findFirst().isEmpty()) {
            return null;
        }

        return new Ending(line.getElement(span.begin()), line.getElement(span.end()));
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Ending(newAnchor, newEnd);
    }

    /**
     * Returns the element index of the bar that separates the first and second sub-spans of
     * this ending — the one {@link #getSplitElement} answers — computed live from the line
     * rather than from a value cached during layout, so it is reliable during MIDI generation.
     *
     * @return the split bar's index in this ending's line
     */
    public int getSplitIndex() {
        return line().getElementIndex(getSplitElement());
    }

    /**
     * Returns the bar that separates the first and second sub-spans of this ending — the
     * element {@link #getSplitIndex} indexes — computed live from the line.
     *
     * @return the first bar between the anchor and the end whose type
     *     {@link ElementType#closesRepeat()}, which the class invariant guarantees exists
     */
    public BarElement getSplitElement() {
        var span = new ElementRange(requireAnchorElementIndex(), requireEndElementIndex());

        return closingRepeatsBetween(line(), span).findFirst().orElseThrow();
    }

    /**
     * The bars strictly inside {@code span} whose type closes a repeat, in line order.
     */
    private static Stream<BarElement> closingRepeatsBetween(Line line, ElementRange span) {
        return IntStream.range(span.begin() + 1, span.end())
            .mapToObj(line::getElement)
            .filter(BarElement.class::isInstance)
            .map(BarElement.class::cast)
            .filter(bar -> bar.getType().closesRepeat());
    }

    /**
     * Returns the index of the element this ending's first bracket opens on: a volta opens on
     * the bar group that begins its repeated section, so an anchor sitting on a note reaches
     * back to the bar in front of it.
     * <p>
     * Grace notes, slides and breath marks are transparent here, as they are everywhere else
     * an ending reasons about its neighbours. An anchor that is itself a barline or repeat is
     * already the opening element, so asking twice gives the same answer as asking once —
     * which is what lets the editor move a selection's anchor onto the bar at creation and
     * layout ask again at paint time without walking back a second bar.
     *
     * @return the opening element's index, or the anchor's own index when nothing in front of
     *     it is a barline or repeat
     */
    public int getOpeningElementIndex() {
        return openingElementIndex(line(), requireAnchorElementIndex());
    }

    /**
     * Returns the index of the element a bracket anchored at {@code anchorIndex} opens on.
     * <p>
     * The static form exists for the editor, which asks the question while deciding where an
     * ending's anchor belongs — before there is an ending to ask. See
     * {@link #getOpeningElementIndex} for the rule.
     *
     * @param line        the line to search
     * @param anchorIndex the index a bracket is anchored at
     * @return the opening element's index, or {@code anchorIndex} when nothing in front of it
     *     is a barline or repeat
     */
    public static int openingElementIndex(Line line, int anchorIndex) {
        if (opensOnItsOwn(line.getElement(anchorIndex).getType())) {
            return anchorIndex;
        }

        for (var i = anchorIndex - 1; i >= 0; i--) {
            var type = line.getElement(i).getType();

            if (type.isNonContentElement()) {
                continue;
            }

            return opensOnItsOwn(type) ? i : anchorIndex;
        }

        return anchorIndex;
    }

    private static boolean opensOnItsOwn(ElementType type) {
        return type.isBar();
    }

    /**
     * Whether an ending split by {@code splitType} may end on {@code endType}.
     * <p>
     * The split and the end state the same repeat structure from its two sides, so they are
     * not independent. A {@link ElementType#REPEAT_LEFT_RIGHT} split leaves the second bracket
     * inside a repeated section, which only a right repeat can close; a
     * {@link ElementType#REPEAT_RIGHT} split closes its section at the split, so the second
     * bracket ends wherever a section may end. An end that is a note belongs to neither case:
     * the bracket stops in mid-air and constrains nothing.
     *
     * @param splitType the type of the repeat splitting the two brackets
     * @param endType   the type of the element the ending ends on
     * @return whether the pair is a structure an ending may hold
     */
    public static boolean isValidEnd(ElementType splitType, ElementType endType) {
        if (endType.isDuration()) {
            return true;
        }

        if (!endType.isBar()) {
            return false;
        }

        if (splitType == ElementType.REPEAT_LEFT_RIGHT) {
            return endType == ElementType.REPEAT_RIGHT || endType == ElementType.REPEAT_LEFT_RIGHT;
        }

        return endType.isEndingTerminal();
    }

    @Override
    public boolean requiresInvalidationConfirm() {
        return true;
    }

    /** {@link Displacement#COEXIST}: adding an ending leaves every other ending in place. */
    @Override
    protected Displacement displacement() {
        return Displacement.COEXIST;
    }

    /**
     * Decides what happens to this ending when {@code change} lands (the conditions of issue
     * #261):
     * <ul>
     *   <li>an insertion removes it when it puts a barline or repeat strictly inside it
     *       ({@link #insertionAddsInteriorBar});</li>
     *   <li>a deletion removes it when it takes an endpoint ({@link #isEndpointDeletedBy}), the
     *       split, or every content element of either bracket ({@link #deletionBreaksBrackets});</li>
     *   <li>a replacement removes it when {@link #checkReplacement} answers
     *       {@link EndingEffect.Invalidate};</li>
     *   <li>an in-place modification keeps it.</li>
     * </ul>
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return {@link SpanOutcome.Simple#REMOVE} when {@code change} invalidates the ending,
     *     otherwise {@link SpanOutcome.Simple#KEEP}
     */
    @Override
    public SpanOutcome.Simple outcomeFor(ElementChange change) {
        var invalidated = switch (change) {
            case ElementChange.Insertion insertion ->
                insertionAddsInteriorBar(insertion.index(), insertion.inserted().getType());

            case ElementChange.Deletion deletion ->
                isEndpointDeletedBy(deletion) || deletionBreaksBrackets(deletion.deletedElements());

            case ElementChange.Replacement replacement ->
                checkReplacement(replacement) instanceof EndingEffect.Invalidate;

            case ElementChange.Modification _ -> false;
        };

        return removeWhen(invalidated);
    }

    /**
     * Returns true if deleting {@code deletedElements} takes the split that parts this ending's
     * two brackets, or every content element of either bracket.
     * <p>
     * Must be called on the <em>pre-deletion</em> line state so indices are stable.
     *
     * @param deletedElements elements about to be deleted
     */
    @SuppressWarnings("SlowListContainsAll")
    private boolean deletionBreaksBrackets(List<StaffElement> deletedElements) {
        var line = line();

        // Condition 2: the split element is deleted
        var splitElement = getSplitElement();

        if (deletedElements.contains(splitElement)) {
            return true;
        }

        // Condition 4: all content elements in either sub-span are deleted
        var splitIndex = line.getElementIndex(splitElement);
        var firstContent = IntStream.range(requireAnchorElementIndex() + 1, splitIndex)
            .mapToObj(line::getElement)
            .filter(el -> el.getType().isDuration())
            .toList();
        var secondContent = IntStream.range(splitIndex + 1, requireEndElementIndex())
            .mapToObj(line::getElement)
            .filter(el -> el.getType().isDuration())
            .toList();

        return (!firstContent.isEmpty() && deletedElements.containsAll(firstContent))
            || (!secondContent.isEmpty() && deletedElements.containsAll(secondContent));
    }

    /**
     * Returns the effect of {@code replacement} on this ending.
     *
     * @param replacement the pending replacement, not yet applied to this ending's line
     */
    public EndingEffect checkReplacement(ElementChange.Replacement replacement) {
        var oldElement = replacement.oldElement();
        var newElement = replacement.newElement();
        var newType = newElement.getType();

        // Condition 1 — anchor replaced. A note, barline, or repeat (including
        // REPEAT_LEFT_RIGHT) is a valid anchor; only a non-content, non-barline,
        // non-repeat type (e.g. clef/key change) invalidates.
        if (oldElement == getAnchorElement()) {
            return (newType.isDuration() || newType.isBar())
                ? EndingEffect.None.INSTANCE
                : new EndingEffect.Invalidate(this);
        }

        var splitEl = getSplitElement();

        // Condition 2 — split element replaced
        if (oldElement == splitEl) {
            if (newType == ElementType.REPEAT_RIGHT) {
                // REPEAT_RIGHT → REPEAT_RIGHT: no change needed
                if (splitEl.getType() == ElementType.REPEAT_RIGHT) {
                    return EndingEffect.None.INSTANCE;
                }
                // REPEAT_LEFT_RIGHT → REPEAT_RIGHT: end must become SINGLE_BARLINE
                return new EndingEffect.CompensateEnd(this, ElementType.SINGLE_BARLINE);
            }

            if (newType == ElementType.REPEAT_LEFT_RIGHT) {
                // REPEAT_LEFT_RIGHT → REPEAT_LEFT_RIGHT: no change needed
                if (splitEl.getType() == ElementType.REPEAT_LEFT_RIGHT) {
                    return EndingEffect.None.INSTANCE;
                }
                // REPEAT_RIGHT → REPEAT_LEFT_RIGHT: end must become REPEAT_RIGHT
                return new EndingEffect.CompensateEnd(this, ElementType.REPEAT_RIGHT);
            }

            // Any other type: invalidate
            return new EndingEffect.Invalidate(this);
        }

        // Condition 3 — end element replaced
        if (oldElement == getEndElement()) {
            if (!newType.isDuration() && !newType.isBar()) {
                return new EndingEffect.Invalidate(this);
            }

            var splitType = splitEl.getType();

            if (isValidEnd(splitType, newType)) {
                return EndingEffect.None.INSTANCE;
            }

            // The pair is not one an ending may hold, so the split moves to the type that
            // makes it legal rather than the replacement being refused.
            var compensatingSplit = splitType == ElementType.REPEAT_LEFT_RIGHT
                ? ElementType.REPEAT_RIGHT
                : ElementType.REPEAT_LEFT_RIGHT;

            return new EndingEffect.CompensateSplit(this, compensatingSplit);
        }

        return EndingEffect.None.INSTANCE;
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * puts a barline or repeat strictly inside this ending (condition 5 from issue #261).
     * <p>
     * Must be called on the <em>pre-insertion</em> line state.
     *
     * @param insertedIndex the index at which the element will be inserted (pre-insertion)
     * @param insertedType  the type of the element being inserted
     */
    private boolean insertionAddsInteriorBar(int insertedIndex, ElementType insertedType) {
        if (!insertedType.isBar()) {
            return false;
        }

        var anchorIndex = requireAnchorElementIndex();
        var endIndex = requireEndElementIndex();

        // Any interior barline or repeat invalidates the ending, the split boundary
        // included: inserting one immediately before the split element does not take
        // over as the split, it leaves the sub-span carrying two.
        return insertedIndex > anchorIndex && insertedIndex < endIndex;
    }

}
