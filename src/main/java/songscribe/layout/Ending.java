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

package songscribe.layout;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.dom.CollisionRegion;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;

import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 */
public class Ending extends RangeElement {

    /**
     * Height of volta bracket tick marks in staff-space units.
     */
    public static final double VOLTA_TICK_HEIGHT_SS = 2.0;
    /** Scale of volta label font relative to standard music font size. */
    private static final float LABEL_FONT_SCALE = 0.6f;

    /** Font for volta bracket labels. */
    public static final Font ENDING_FONT = MyFontUtils.getLocalFont(
        "emmentaler-16.otf", NoteGeometry.MUSIC_FONT_SIZE_SS * LABEL_FONT_SCALE);

    /** Horizontal inset of label from left arm of bracket. */
    public static final double LABEL_X_INSET_SS = 0.9;

    /** Vertical gap between the bracket's horizontal line and the top of the label. */
    public static final double LABEL_Y_OFFSET_SS = 0.5;

    /** Cached visual bounds for the "1." label. */
    private static final Rectangle2D LABEL_1_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "1.").getVisualBounds();

    /** Cached visual bounds for the "2." label. */
    private static final Rectangle2D LABEL_2_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "2.").getVisualBounds();

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
        /** Returns the width of this bracket in staff spaces. */
        public double widthSs() {
            return x2Ss - x1Ss;
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
    private int repeatSplitIndex = -1;
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
     * the first and second sub-spans of this ending, computed live from the line, or -1
     * if there is no split element (degenerate single-bracket ending).
     * <p>
     * This recomputes from the current line state rather than relying on a value cached
     * during layout, so it is reliable during MIDI generation.
     */
    public int getSplitIndex(Line line) {
        return findRepeatSplitIndex(line);
    }

    /**
     * Returns the bracket ranges computed during layout.
     */
    public List<BracketRange> getBracketRanges() {
        return bracketRanges;
    }

    /**
     * Computes and stores the bracket ranges for this ending.
     * <p>
     * Determines where each visual bracket starts and ends by examining the
     * element types (barlines, repeats) in the line. The logic mirrors
     * {@code EndingRenderer.renderEndings()} to ensure stacking and rendering
     * use identical positions.
     *
     * @param line     the line containing this ending
     * @param columnFn function that returns the {@link ElementColumn} of an element,
     *                 from which its X position and horizontal extents are read
     * @return the computed bracket ranges (also stored on this Ending)
     */
    public List<BracketRange> computeBracketRanges(
        Line line,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var start = getAnchorElementIndex();
        var end = getEndElementIndex();

        if (start < 0 || end < 0 || start >= line.elementCount()
            || end >= line.elementCount()) {
            bracketRanges = List.of();
            return bracketRanges;
        }

        // Find the repeat element (REPEAT_RIGHT or REPEAT_LEFT_RIGHT) that separates first and second endings
        repeatSplitIndex = IntStream.rangeClosed(start, end)
            .filter(i -> {
                var t = line.getElement(i).getType();
                return t == ElementType.REPEAT_RIGHT || t == ElementType.REPEAT_LEFT_RIGHT;
            })
            .findFirst()
            .orElse(-1);

        var startElement = line.getElement(start);

        // Adjust start leftward if previous element is a barline or repeat
        if (start > 0) {
            var previousElement = line.getElement(start - 1);
            var prevType = previousElement.getType();

            if (prevType.isBarLine() || prevType.isRepeat()) {
                --start;
                startElement = previousElement;
            }
        }

        var endElement = line.getElement(end);
        var ranges = new ArrayList<BracketRange>(2);
        var repeatX = 0.0;

        // First bracket (before repeat, or entire span if no repeat)
        if (start < repeatSplitIndex || repeatSplitIndex == -1) {
            var startColumn = columnFn.apply(startElement);
            var x1 = startColumn.getXSs();
            var startType = startElement.getType();

            // For barlines and repeats, align to the governing thin barline center.
            // For notes/rests, anchor to the element's left extent.
            if (startType.isBarLine() || startType.isRepeat()) {
                x1 += startType.endingAnchorXOffsetSs();
            }
            else {
                x1 = startColumn.getLeftEdgeXSs() - NoteGeometry.ACCIDENTAL_PADDING_SS;
            }

            double x2;

            if (repeatSplitIndex != -1) {
                var repeatElementX = columnFn.apply(
                    line.getElement(repeatSplitIndex)).getXSs();
                x2 = repeatElementX
                    + LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS;
                repeatX = repeatElementX
                    + LineThickness.REPEAT_RIGHT_AFTER_THICK_X_SS
                    - LineThickness.VOLTA_BRACKET_SS / 2;
            }
            else {
                x2 = columnFn.apply(endElement).getXSs();

                if (end + 1 < line.elementCount()) {
                    var nextX = columnFn.apply(
                        line.getElement(end + 1)).getXSs();
                    x2 += (nextX - x2) / 2.0;
                }
                else {
                    x2 += SMuFLConstants.NOTE_HEAD_WIDTH_SS;
                }
            }

            ranges.add(new BracketRange(x1, x2, 1, true));
        }

        // Second bracket (after repeat)
        if (repeatSplitIndex != -1 && end > repeatSplitIndex) {
            var endColumn = columnFn.apply(endElement);
            var x2 = endColumn.getXSs();
            var endType = endElement.getType();

            // Extend to the next barline/repeat if end element is not one
            if (!endType.isBarLine() && !endType.isRepeat()
                && end + 1 < line.elementCount()) {
                var nextElement = line.getElement(end + 1);
                var nextType = nextElement.getType();

                if (nextType.isBarLine() || nextType.isRepeat()) {
                    endType = nextType;
                    x2 = columnFn.apply(nextElement).getXSs();
                }
            }

            boolean hasClosingStroke;

            switch (endType) {
                case REPEAT_RIGHT, REPEAT_LEFT_RIGHT -> {
                    x2 += LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS;
                    hasClosingStroke = true;
                }
                case FINAL_DOUBLE_BARLINE -> {
                    x2 += LineThickness.THIN_BARLINE_SS / 2;
                    hasClosingStroke = true;
                }
                case SINGLE_BARLINE, DOUBLE_BARLINE -> {
                    x2 += LineThickness.THIN_BARLINE_SS / 2;
                    hasClosingStroke = false;
                }
                case REPEAT_LEFT -> hasClosingStroke = false;
                default -> {
                    x2 = endColumn.getRightEdgeXSs() + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
                    hasClosingStroke = false;
                }
            }

            ranges.add(new BracketRange(repeatX, x2, 2, hasClosingStroke));
        }

        bracketRanges = List.copyOf(ranges);
        return bracketRanges;
    }

    /**
     * Returns the height of the volta bracket in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return VOLTA_TICK_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(SMuFLConstants.NOTE_HEAD_WIDTH_SS, endXSs - anchorXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS);
    }

    /**
     * Returns the cached visual bounds of the ending label text in staff-space units.
     *
     * @param number the bracket number (1 or 2)
     * @return visual bounds rectangle in staff-space units
     */
    static Rectangle2D labelBoundsSs(int number) {
        return number == 1 ? LABEL_1_BOUNDS_SS : LABEL_2_BOUNDS_SS;
    }

    /**
     * Computes collision sub-regions for a single visual bracket.
     * <p>
     * Decomposes the bracket into horizontal bar, vertical ticks, and label
     * so that higher stacking layers can nestle into the open space between them.
     * All xOffset values are relative to the element's anchor X (the first bracket's x1).
     *
     * @param bracket  the bracket range to compute regions for
     * @param xBaseSs  horizontal offset of this bracket's x1 from the element anchor X
     * @return list of collision sub-regions
     */
    public List<CollisionRegion> computeCollisionRegions(
        BracketRange bracket,
        double xBaseSs
    ) {
        var spanWidthSs = bracket.widthSs();
        var bracketThicknessSs = LineThickness.VOLTA_BRACKET_SS;
        var regions = new ArrayList<CollisionRegion>(4);

        // Horizontal bar
        regions.add(new CollisionRegion(
            xBaseSs, 0, spanWidthSs, bracketThicknessSs));

        // Left tick
        regions.add(new CollisionRegion(
            xBaseSs, 0, bracketThicknessSs,
            VOLTA_TICK_HEIGHT_SS));

        // Right tick (only if there is a closing stroke)
        if (bracket.hasClosingStroke()) {
            regions.add(new CollisionRegion(
                xBaseSs + spanWidthSs - bracketThicknessSs, 0,
                bracketThicknessSs,
                VOLTA_TICK_HEIGHT_SS));
        }

        // Label (e.g. "1." or "2.")
        var labelBounds = labelBoundsSs(bracket.number());
        var labelWidthSs = labelBounds.getWidth();
        var labelHeightSs = GraphicUtils.inkHeight(labelBounds);
        regions.add(new CollisionRegion(
            xBaseSs + LABEL_X_INSET_SS, 0,
            labelWidthSs, LABEL_Y_OFFSET_SS + labelHeightSs));

        return regions;
    }

    @Override
    public boolean requiresInvalidationConfirm() {
        return true;
    }

    /**
     * Returns true if deleting the given elements invalidates this ending beyond what
     * {@link RangeElement#isInvalidatedBy} already detects.
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
    @SuppressWarnings("SlowListContainsAll")
    public boolean isInvalidatedByDeletion(List<StaffElement> deletedElements, Line line) {
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
                .filter(el -> el.getType().isContentElement())
                .toList();
            var secondContent = IntStream.range(splitIndex + 1, endIndex)
                .mapToObj(line::getElement)
                .filter(el -> el.getType().isContentElement())
                .toList();

            return (!firstContent.isEmpty() && deletedElements.containsAll(firstContent))
                || (!secondContent.isEmpty() && deletedElements.containsAll(secondContent));
        }

        var singleContent = IntStream.range(anchorIndex + 1, endIndex)
            .mapToObj(line::getElement)
            .filter(el -> el.getType().isContentElement())
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
        // non-repeat type (e.g. clef/key signature) invalidates.
        if (oldElement == getAnchorElement()) {
            return (newType.isContentElement() || newType.isBarLine() || newType.isRepeat())
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
            // A note end needs no split compensation, regardless of split type.
            if (newType.isContentElement()) {
                return EndingEffect.None.INSTANCE;
            }

            if (!newType.isBarLine() && !newType.isRepeat()) {
                return new EndingEffect.Invalidate(this);
            }

            if (splitEl != null && splitEl.getType() == ElementType.REPEAT_LEFT_RIGHT) {
                // Split is REPEAT_LEFT_RIGHT: end must remain REPEAT_RIGHT or REPEAT_LEFT_RIGHT
                return (newType == ElementType.REPEAT_RIGHT || newType == ElementType.REPEAT_LEFT_RIGHT)
                    ? EndingEffect.None.INSTANCE
                    : new EndingEffect.CompensateSplit(this, ElementType.REPEAT_RIGHT);
            }

            // Split is REPEAT_RIGHT: end must be isTerminal()
            return newType.isTerminal()
                ? EndingEffect.None.INSTANCE
                : new EndingEffect.CompensateSplit(this, ElementType.REPEAT_LEFT_RIGHT);
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
    public boolean isInvalidatedByReplacement(
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
    public boolean isInvalidatedByInsertion(
        int insertedIndex, ElementType insertedType, Line line
    ) {
        if (!insertedType.isBarLine() && !insertedType.isRepeat()) {
            return false;
        }

        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex < 0 || endIndex < 0) {
            return false;
        }

        if (insertedIndex <= anchorIndex || insertedIndex >= endIndex) {
            return false;
        }

        var splitEl = findRepeatSplitElement(line);

        if (splitEl != null) {
            var splitIndex = line.getElementIndex(splitEl);
            // Inserting at the split boundary is allowed; anywhere else interior is not
            return insertedIndex != splitIndex;
        }

        // No split: any interior barline/repeat invalidates the ending
        return true;
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
