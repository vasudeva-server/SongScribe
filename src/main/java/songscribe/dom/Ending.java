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

import org.jspecify.annotations.Nullable;

import songscribe.engraving.EngravingConstants;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 */
public class Ending extends Span {

    /**
     * Height of volta bracket tick marks in staff-space units.
     */
    public static final double VOLTA_TICK_HEIGHT_SS = 2.0;

    /** Horizontal inset of label from left arm of bracket. */
    public static final double LABEL_X_INSET_SS = 0.9;

    /** Vertical gap between the bracket's horizontal line and the top of the label. */
    public static final double LABEL_Y_OFFSET_SS = 0.5;

    /**
     * The x-range and properties of a single visual bracket (first or second ending).
     *
     * @param x1Ss             left edge X in staff spaces
     * @param x2Ss             right edge X in staff spaces
     * @param number           bracket number (1 or 2)
     * @param hasClosingStroke whether the bracket has a right vertical tick
     */
    public record BracketRange(
        double x1Ss, double x2Ss, int number, boolean hasClosingStroke
    ) {
        /**
         * Returns the X the bracket's drawn ink reaches on the right: {@link #x2Ss} itself when a
         * closing stroke gives the top arm a join to rest on, or half a volta stroke width past it
         * when an open arm's round end-cap pushes the ink out to match where a closed bracket's
         * right leg would reach.
         *
         * @return the X the bracket's ink reaches on the right, in staff spaces
         */
        public double inkRightXSs() {
            return hasClosingStroke
                ? x2Ss
                : x2Ss + EngravingConstants.VOLTA_BRACKET_SS / 2;
        }

        /** Returns the label text for this bracket (e.g. "1." or "2."). */
        public String label() {
            return number + ".";
        }
    }

    /**
     * Describes the effect of replacing one element with another on this ending.
     * Used by the UI layer to decide whether to abort, confirm-and-invalidate,
     * or confirm-and-compensate before calling {@link Line#setElement}.
     */
    public sealed interface EndingEffect
        permits EndingEffect.None, EndingEffect.Invalidate,
                EndingEffect.CompensateEnd, EndingEffect.CompensateSplit {

        /** No ending is affected. */
        record None() implements EndingEffect {
            public static final None INSTANCE = new None();
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

    private int yPositionSs = 0;
    private List<BracketRange> bracketRanges = List.of();

    /**
     * Creates an ending bracket.
     *
     * @param anchorElement The first element of the ending
     * @param endElement    The last element of the ending
     */
    public Ending(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    @Override
    protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = new Ending(newAnchor, newEnd);
        copy.yPositionSs = yPositionSs;
        return copy;
    }

    /**
     * Returns the user-adjustable Y offset for this ending bracket.
     */
    public int getYPositionSs() {
        return yPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this ending bracket.
     */
    public void setYPositionSs(int yPositionSs) {
        this.yPositionSs = yPositionSs;
    }

    /**
     * Returns the element index of the REPEAT_RIGHT or REPEAT_LEFT_RIGHT that separates
     * the first and second sub-spans of this ending, computed live from the line.
     * <p>
     * Every ending must have a split: the two brackets are meaningless without a repeat
     * between them. A missing split therefore signals corrupt state — a split-less ending
     * that should have been rejected on import or removed by invalidation — and throws.
     * Callers that merely <em>detect</em> whether a split exists must use the tolerant
     * {@link #findRepeatSplitElement} instead of this method.
     * <p>
     * This recomputes from the current line state rather than relying on a value cached
     * during layout, so it is reliable during MIDI generation.
     */
    public int getSplitIndex(Line line) {
        var splitIndex = findRepeatSplitIndex(line);

        if (splitIndex < 0) {
            throw noSplitElementException(getAnchorElementIndex(), getEndElementIndex());
        }

        return splitIndex;
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
     * @param line the line this ending belongs to
     * @return the opening element's index, or the anchor's own index when nothing in front of
     *     it is a barline or repeat
     */
    public int getOpeningElementIndex(Line line) {
        return openingElementIndex(line, getAnchorElementIndex());
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
        if (!line.hasIndex(anchorIndex)) {
            return anchorIndex;
        }

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
        return type.isBarLine() || type.isRepeat();
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

        if (!endType.isBarLine() && !endType.isRepeat()) {
            return false;
        }

        if (splitType == ElementType.REPEAT_LEFT_RIGHT) {
            return endType == ElementType.REPEAT_RIGHT || endType == ElementType.REPEAT_LEFT_RIGHT;
        }

        return endType.isEndingTerminal();
    }

    /**
     * Builds the exception thrown when an ending is found to have no REPEAT splitting its
     * two brackets — corrupt state that should have been rejected on import or removed by
     * invalidation. Shared by {@link #getSplitIndex} and the layout-side bracket-range
     * computation, which enforces the same invariant.
     *
     * @param anchorIndex index of the ending's anchor element
     * @param endIndex    index of the ending's end element
     */
    public static IllegalStateException noSplitElementException(int anchorIndex, int endIndex) {
        return new IllegalStateException(
            "Ending spanning elements [" + anchorIndex + ',' + endIndex
                + "] has no split element; every ending must have a REPEAT between its two brackets");
    }

    /**
     * Returns the bracket ranges computed during layout.
     */
    public List<BracketRange> getBracketRanges() {
        return bracketRanges;
    }

    /**
     * Stores the bracket ranges computed during layout.
     * <p>
     * Called by {@code songscribe.layout.EndingBracketGeometry}, which owns the geometry:
     * computing the ranges needs layout types this DOM class must not depend on.
     *
     * @param bracketRanges the computed ranges, in bracket order
     */
    public void setBracketRanges(List<BracketRange> bracketRanges) {
        this.bracketRanges = bracketRanges;
    }

    /**
     * Returns the height of the volta bracket in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return VOLTA_TICK_HEIGHT_SS;
    }

    @Override
    public boolean requiresInvalidationConfirm() {
        return true;
    }

    /**
     * Returns true if deleting the given elements invalidates this ending beyond what
     * {@link Span#isInvalidatedBy} already detects.
     * <p>
     * Must be called on the <em>pre-deletion</em> line state so indices are stable.
     * Checks:
     * <ul>
     *   <li>Condition 2: the REPEAT_RIGHT that splits first/second sub-spans is deleted.</li>
     *   <li>Condition 4: all content elements in either sub-span are deleted.</li>
     * </ul>
     *
     * @param deletedElements elements about to be deleted
     * @param line            the owning line (pre-deletion state)
     */
    @Override
    @SuppressWarnings("SlowListContainsAll")
    protected boolean isInvalidatedByDeletion(List<StaffElement> deletedElements, Line line) {
        // Condition 2: REPEAT_RIGHT split element is deleted
        var splitElement = findRepeatSplitElement(line);

        if (splitElement != null && deletedElements.contains(splitElement)) {
            return true;
        }

        // Condition 4: all content elements in either sub-span are deleted
        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex < 0 || endIndex < 0) {
            return false;
        }

        if (splitElement != null) {
            var splitIndex = line.getElementIndex(splitElement);
            var firstContent = IntStream.range(anchorIndex + 1, splitIndex)
                .mapToObj(line::getElement)
                .filter(el -> el.getType().isDuration())
                .toList();
            var secondContent = IntStream.range(splitIndex + 1, endIndex)
                .mapToObj(line::getElement)
                .filter(el -> el.getType().isDuration())
                .toList();

            return (!firstContent.isEmpty() && deletedElements.containsAll(firstContent))
                || (!secondContent.isEmpty() && deletedElements.containsAll(secondContent));
        }

        var singleContent = IntStream.range(anchorIndex + 1, endIndex)
            .mapToObj(line::getElement)
            .filter(el -> el.getType().isDuration())
            .toList();

        return !singleContent.isEmpty() && deletedElements.containsAll(singleContent);
    }

    /**
     * Returns the effect of replacing {@code oldElement} with {@code newElement} on this ending.
     *
     * @param oldElement the element being replaced (still in the line at call time)
     * @param newElement the replacement element
     * @param line       the owning line (pre-replacement state)
     */
    public EndingEffect checkReplacement(
        StaffElement oldElement, StaffElement newElement, Line line
    ) {
        var newType = newElement.getType();

        // Condition 1 — anchor replaced. A note, barline, or repeat (including
        // REPEAT_LEFT_RIGHT) is a valid anchor; only a non-content, non-barline,
        // non-repeat type (e.g. clef/key change) invalidates.
        if (oldElement == getAnchorElement()) {
            return (newType.isDuration() || newType.isBarLine() || newType.isRepeat())
                ? EndingEffect.None.INSTANCE
                : new EndingEffect.Invalidate(this);
        }

        var splitEl = findRepeatSplitElement(line);

        // Condition 2 — split element replaced
        //noinspection ConditionCoveredByFurtherCondition -- false positive
        if (splitEl != null && oldElement == splitEl) {
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
            if (!newType.isDuration() && !newType.isBarLine() && !newType.isRepeat()) {
                return new EndingEffect.Invalidate(this);
            }

            var splitType = splitEl == null ? ElementType.REPEAT_RIGHT : splitEl.getType();

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
     * Returns true if replacing {@code oldElement} with {@code newElement} invalidates
     * this ending. Covers conditions 1, 2, and 3 from issue #261.
     *
     * @param oldElement the element being replaced (still in the line at call time)
     * @param newElement the element that will replace it
     * @param line       the owning line
     */
    @Override
    protected boolean isInvalidatedByReplacement(
        StaffElement oldElement, StaffElement newElement, Line line
    ) {
        return checkReplacement(oldElement, newElement, line) instanceof EndingEffect.Invalidate;
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * invalidates this ending (condition 5 from issue #261).
     * <p>
     * Must be called on the <em>pre-insertion</em> line state.
     *
     * @param insertedIndex the index at which the element will be inserted (pre-insertion)
     * @param insertedType  the type of the element being inserted
     * @param line          the owning line (pre-insertion state)
     */
    @Override
    protected boolean isInvalidatedByInsertion(
        int insertedIndex, ElementType insertedType, Line line
    ) {
        if (!insertedType.isBarLine() && !insertedType.isRepeat()) {
            return false;
        }

        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        // Any interior barline or repeat invalidates the ending, the split boundary
        // included: inserting one immediately before the split element does not take
        // over as the split, it leaves the sub-span carrying two.
        return anchorIndex >= 0 && endIndex >= 0 &&
            insertedIndex > anchorIndex && insertedIndex < endIndex;
    }

    /**
     * Finds the repeat element (REPEAT_RIGHT or REPEAT_LEFT_RIGHT) that splits the first
     * and second sub-spans, scanning live indices between anchor and end.
     * <p>
     * Returns {@code null} if no such element exists or the anchor/end indices are invalid.
     * Shared by {@link #isInvalidatedByDeletion} and the replacement/insertion checks.
     */
    public @Nullable StaffElement findRepeatSplitElement(Line line) {
        var splitIndex = findRepeatSplitIndex(line);

        return splitIndex < 0 ? null : line.getElement(splitIndex);
    }

    /**
     * Finds the live element index of the repeat element (REPEAT_RIGHT or REPEAT_LEFT_RIGHT)
     * that splits the first and second sub-spans, scanning between anchor and end. Returns
     * the index directly so callers avoid a second {@code getElementIndex} lookup.
     * <p>
     * Returns -1 if no such element exists or the anchor/end indices are invalid.
     */
    private int findRepeatSplitIndex(Line line) {
        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex < 0 || endIndex < 0) {
            return -1;
        }

        return IntStream.range(anchorIndex + 1, endIndex)
            .filter(i -> {
                var type = line.getElement(i).getType();
                return type == ElementType.REPEAT_RIGHT || type == ElementType.REPEAT_LEFT_RIGHT;
            })
            .findFirst()
            .orElse(-1);
    }
}
