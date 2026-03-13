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

package songscribe.ui.layout;

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;

/**
 * Abstract base class for elements within a staff line.
 * <p>
 * LineElement provides a DOM-like hierarchy for elements rendered within a Line component.
 * Each element has:
 * <ul>
 *   <li>A position relative to the parent line origin</li>
 *   <li>Content and margin bounds (simplified 2-layer box model)</li>
 *   <li>Optional parent/child relationships for composite elements</li>
 * </ul>
 * <p>
 * The hierarchy supports CSS-style margin collapsing between adjacent elements.
 */
public abstract class LineElement {

    /** Reference to the Line that contains this element. */
    private @Nullable Line parentLine;

    /** Parent element (null for direct children of Line). */
    private @Nullable LineElement parentElement;

    /** Position relative to parent line origin. */
    private @NotNull Point2D position = new Point2D.Double(0, 0);

    /**
     * User's manual horizontal offset from the layout-calculated position.
     * <p>
     * Final X position = calculated position + userXOffset
     * <p>
     * Default is 0 (no user adjustment). Positive values move right, negative left.
     */
    private double userXOffset = 0;

    /**
     * User's manual vertical offset from the layout-calculated position.
     * <p>
     * Final Y position = calculated position + userYOffset
     * <p>
     * Default is 0 (no user adjustment). Positive values move down, negative up.
     */
    private double userYOffset = 0;

    /** CSS-style margins: top, right, bottom, left (in pixels). */
    private double marginTop = 0;
    private double marginRight = 0;
    private double marginBottom = 0;
    private double marginLeft = 0;

    /** Child elements (for composite elements like notes with articulations). */
    private final List<LineElement> children = new ArrayList<>();

    // ========================================================================
    // Abstract Methods
    // ========================================================================

    /**
     * Returns the intrinsic width of this element's content.
     * Subclasses must implement based on their specific content.
     */
    public abstract double getContentWidth();

    /**
     * Returns the intrinsic height of this element's content.
     * Subclasses must implement based on their specific content.
     */
    public abstract double getContentHeight();

    // ========================================================================
    // Parent/Line Relationships
    // ========================================================================

    /**
     * Returns the Line that contains this element.
     */
    public @Nullable Line getParentLine() {
        return parentLine;
    }

    /**
     * Sets the Line that contains this element.
     */
    public void setParentLine(@Nullable Line parentLine) {
        this.parentLine = parentLine;
    }

    /**
     * Returns the parent element (null for direct children of Line).
     */
    public @Nullable LineElement getParentElement() {
        return parentElement;
    }

    /**
     * Sets the parent element.
     */
    public void setParentElement(@Nullable LineElement parentElement) {
        this.parentElement = parentElement;
    }

    // ========================================================================
    // Position
    // ========================================================================

    /**
     * Returns the position relative to the parent line origin.
     */
    public @NotNull Point2D getPosition() {
        return position;
    }

    /**
     * Returns the X position relative to the parent line origin.
     */
    public double getX() {
        return position.getX();
    }

    /**
     * Returns the Y position relative to the parent line origin.
     */
    public double getY() {
        return position.getY();
    }

    /**
     * Sets the position relative to the parent line origin.
     */
    public void setPosition(@NotNull Point2D position) {
        this.position = position;
    }

    /**
     * Sets the position relative to the parent line origin.
     */
    public void setPosition(double x, double y) {
        this.position = new Point2D.Double(x, y);
    }

    // ========================================================================
    // User Offsets
    // ========================================================================

    /**
     * Returns the user's horizontal offset from the calculated position.
     */
    public double getUserXOffset() {
        return userXOffset;
    }

    /**
     * Sets the user's horizontal offset from the calculated position.
     */
    public void setUserXOffset(double userXOffset) {
        this.userXOffset = userXOffset;
    }

    /**
     * Returns the user's vertical offset from the calculated position.
     */
    public double getUserYOffset() {
        return userYOffset;
    }

    /**
     * Sets the user's vertical offset from the calculated position.
     */
    public void setUserYOffset(double userYOffset) {
        this.userYOffset = userYOffset;
    }

    // ========================================================================
    // Margins
    // ========================================================================

    /**
     * Sets uniform margin on all sides.
     */
    public void setMargin(double margin) {
        this.marginTop = margin;
        this.marginRight = margin;
        this.marginBottom = margin;
        this.marginLeft = margin;
    }

    /**
     * Sets CSS-style margins.
     *
     * @param top    Top margin in pixels
     * @param right  Right margin in pixels
     * @param bottom Bottom margin in pixels
     * @param left   Left margin in pixels
     */
    public void setMargin(double top, double right, double bottom, double left) {
        this.marginTop = top;
        this.marginRight = right;
        this.marginBottom = bottom;
        this.marginLeft = left;
    }

    public double getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(double marginTop) {
        this.marginTop = marginTop;
    }

    public double getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(double marginRight) {
        this.marginRight = marginRight;
    }

    public double getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(double marginBottom) {
        this.marginBottom = marginBottom;
    }

    public double getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(double marginLeft) {
        this.marginLeft = marginLeft;
    }

    // ========================================================================
    // Bounds Calculation
    // ========================================================================

    /**
     * Returns the content bounds (actual drawn content) in line-relative coordinates.
     */
    public @NotNull Rectangle2D getContentBounds() {
        return new Rectangle2D.Double(
            position.getX(),
            position.getY(),
            getContentWidth(),
            getContentHeight()
        );
    }

    /**
     * Returns the margin bounds (content + margin) in line-relative coordinates.
     */
    public @NotNull Rectangle2D getMarginBounds() {
        return new Rectangle2D.Double(
            position.getX() - marginLeft,
            position.getY() - marginTop,
            getContentWidth() + marginLeft + marginRight,
            getContentHeight() + marginTop + marginBottom
        );
    }

    /**
     * Returns the complete Bounds object for this element.
     */
    public @NotNull Bounds getBounds() {
        return new Bounds(getContentBounds(), getMarginBounds());
    }

    // ========================================================================
    // Hit Testing
    // ========================================================================

    /**
     * Returns whether the given point is within this element's hit test area.
     * Uses default expansion from {@link Bounds#DEFAULT_HIT_EXPANSION}.
     *
     * @param x X coordinate in line-relative coordinates
     * @param y Y coordinate in line-relative coordinates
     */
    public boolean containsPoint(double x, double y) {
        return containsPoint(x, y, Bounds.DEFAULT_HIT_EXPANSION);
    }

    /**
     * Returns whether the given point is within this element's hit test area.
     *
     * @param x         X coordinate in line-relative coordinates
     * @param y         Y coordinate in line-relative coordinates
     * @param expansion Pixels to expand content bounds for hit testing
     */
    public boolean containsPoint(double x, double y, double expansion) {
        var bounds = getContentBounds();
        return x >= bounds.getX() - expansion &&
               x <= bounds.getX() + bounds.getWidth() + expansion &&
               y >= bounds.getY() - expansion &&
               y <= bounds.getY() + bounds.getHeight() + expansion;
    }

    // ========================================================================
    // Margin Collapsing
    // ========================================================================

    /**
     * Calculates the collapsed vertical margin between this element and the one below.
     * CSS-style: the larger margin wins.
     *
     * @param below The element below this one
     * @return The effective margin between the two elements
     */
    public double collapsedVerticalMarginWith(@NotNull LineElement below) {
        return Math.max(this.marginBottom, below.marginTop);
    }

    /**
     * Calculates the collapsed horizontal margin between this element and the one to its right.
     * CSS-style: the larger margin wins.
     *
     * @param right The element to the right of this one
     * @return The effective margin between the two elements
     */
    public double collapsedHorizontalMarginWith(@NotNull LineElement right) {
        return Math.max(this.marginRight, right.marginLeft);
    }

    // ========================================================================
    // Child Management
    // ========================================================================

    /**
     * Returns an unmodifiable view of this element's children.
     */
    public @NotNull List<LineElement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the number of children.
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Returns whether this element has children.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Adds a child element.
     *
     * @param child The element to add
     */
    public void addChild(@NotNull LineElement child) {
        child.setParentElement(this);
        child.setParentLine(this.parentLine);
        children.add(child);
    }

    /**
     * Removes a child element.
     *
     * @param child The element to remove
     * @return true if the child was removed
     */
    public boolean removeChild(@NotNull LineElement child) {
        if (children.remove(child)) {
            child.setParentElement(null);
            return true;
        }

        return false;
    }

    /**
     * Removes all children.
     */
    public void clearChildren() {
        for (var child : children) {
            child.setParentElement(null);
        }

        children.clear();
    }

    /**
     * Returns the child at the given index.
     *
     * @param index Index of the child
     * @return The child element
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public @NotNull LineElement getChild(int index) {
        return children.get(index);
    }
}
