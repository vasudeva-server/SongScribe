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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

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

    /**
     * The line this element is in right now, or {@code null} when it is in no line —
     * either not yet added, or detached by a removal. Removals maintain it, so this is
     * the authoritative answer to "is this element live in the document?"; no membership
     * scan is needed to decide.
     * <p>
     * Two kinds of element reach this field; a {@link Span} never does. A {@code StaffElement}
     * lives in {@code Line.elements}, and {@code Line.attach}/{@code Line.detach} are the only
     * writers of its {@code parentLine}. Its children — {@code Articulation},
     * {@code FermataAttachment} and the rest — get theirs from {@code LineElement.addChild} and
     * {@code removeChild}, and from {@code propagateParentLine} when the host attaches or
     * detaches. A {@code Span} (tie, beam, tuplet, …) lives in {@code Line.spans} instead; its
     * {@code parentLine} stays null for its whole life, and {@code Span.isIn(Line)} derives
     * parentage from its endpoints.
     * <p>
     * For an element in Line's {@code elements} list the invariant is
     * {@code parentLine == L ⟺ L.elements contains this}, holding at
     * modification-bracket boundaries. Inside a bracket a re-parent may briefly have
     * attached to B while A's list still holds the element; {@code Line.detach}'s
     * {@code != this} guard is what makes that ordering-independent.
     * <p>
     * A span has no such two-way tie. It is never attached, so a cross-line span can be
     * in two lines at once, and the derived invariant is one-directional:
     * {@code span.isIn(L)} ⟹ {@code L.spans} contains the span. The converse does not
     * hold — a span whose endpoints are both detached is in no line yet remains in the
     * list it was added to.
     */
    private @Nullable Line parentLine;

    /** Parent element (null for direct children of Line). */
    private @Nullable LineElement parentElement;

    /** Position relative to parent line origin. */
    private Point2D positionSs = new Point2D.Double(0, 0);

    /**
     * User's manual horizontal offset from the layout-calculated position.
     * <p>
     * Final X position = calculated position + userXOffsetSs
     * <p>
     * Default is 0 (no user adjustment). Positive values move right, negative left.
     */
    private double userXOffsetSs = 0;

    /**
     * User's manual vertical offset from the layout-calculated position.
     * <p>
     * Final Y position = calculated position + userYOffsetSs
     * <p>
     * Default is 0 (no user adjustment). Positive values move down, negative up.
     */
    private double userYOffsetSs = 0;

    /** CSS-style margins: top, right, bottom, left (in staff spaces). */
    private double marginTopSs = 0;
    private double marginRightSs = 0;
    private double marginBottomSs = 0;
    private double marginLeftSs = 0;

    /** Child elements (for composite elements like notes with articulations). */
    private final List<LineElement> children = new ArrayList<>();

    // ========================================================================
    // Abstract Methods
    // ========================================================================

    /**
     * Returns the intrinsic width of this element's content, in staff spaces.
     *
     * @return the width the element draws, in the unit the document model holds its
     *     dimensions in; a caller needing pixels converts through {@link DocumentScale}
     */
    public abstract double getContentWidthSs();

    /**
     * Returns the intrinsic height of this element's content, in staff spaces.
     *
     * @return the height the element draws, in the unit the document model holds its
     *     dimensions in; a caller needing pixels converts through {@link DocumentScale}
     */
    public abstract double getContentHeightSs();

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
     * Sets the Line that contains this element. For an element in Line's {@code elements}
     * list the only callers are {@code Line.attach} and {@code Line.detach}; calling it
     * elsewhere breaks the invariant documented on {@link #parentLine}. Package-private
     * so the compiler enforces that, rather than leaving it to a reader of this comment.
     */
    void setParentLine(@Nullable Line parentLine) {
        this.parentLine = parentLine;
    }

    /** Pushes {@code line} down the child chain; sub-elements have no line of their own. */
    void propagateParentLine(@Nullable Line line) {
        for (var child : getChildren()) {
            child.setParentLine(line);
            child.propagateParentLine(line);
        }
    }

    /**
     * Returns the parent element (null for direct children of Line).
     */
    public @Nullable LineElement getParentElement() {
        return parentElement;
    }

    /**
     * Sets the parent element. Package-private alongside {@link #setParentLine}: the
     * element tree is built by {@link #addChild} / {@link #removeChild}, which own both
     * pointers together.
     */
    void setParentElement(@Nullable LineElement parentElement) {
        this.parentElement = parentElement;
    }

    // ========================================================================
    // Position
    // ========================================================================

    /**
     * Returns the position relative to the parent line origin.
     */
    public Point2D getPositionSs() {
        return positionSs;
    }

    /**
     * Returns the X position relative to the parent line origin.
     */
    public double getXSs() {
        return positionSs.getX();
    }

    /**
     * Returns the Y position relative to the parent line origin.
     */
    public double getYSs() {
        return positionSs.getY();
    }

    /**
     * Sets the position relative to the parent line origin.
     */
    public void setPosition(Point2D positionSs) {
        this.positionSs = positionSs;
    }

    /**
     * Sets the position relative to the parent line origin.
     */
    public void setPosition(double xSs, double ySs) {
        positionSs = new Point2D.Double(xSs, ySs);
    }

    // ========================================================================
    // User Offsets
    // ========================================================================

    /**
     * Returns the user's horizontal offset from the calculated position.
     */
    public double getUserXOffsetSs() {
        return userXOffsetSs;
    }

    /**
     * Sets the user's horizontal offset from the calculated position.
     */
    public void setUserXOffsetSs(double userXOffsetSs) {
        this.userXOffsetSs = userXOffsetSs;
    }

    /**
     * Returns the user's vertical offset from the calculated position.
     */
    public double getUserYOffsetSs() {
        return userYOffsetSs;
    }

    /**
     * Sets the user's vertical offset from the calculated position.
     */
    public void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    // ========================================================================
    // Margins
    // ========================================================================

    /**
     * Sets uniform margin on all sides.
     */
    public void setMarginSs(double margin) {
        marginTopSs = margin;
        marginRightSs = margin;
        marginBottomSs = margin;
        marginLeftSs = margin;
    }

    /**
     * Sets CSS-style margins.
     *
     * @param top    Top margin in staff spaces
     * @param right  Right margin in staff spaces
     * @param bottom Bottom margin in staff spaces
     * @param left   Left margin in staff spaces
     */
    public void setMarginSs(double top, double right, double bottom, double left) {
        marginTopSs = top;
        marginRightSs = right;
        marginBottomSs = bottom;
        marginLeftSs = left;
    }

    public double getMarginTopSs() {
        return marginTopSs;
    }

    public void setMarginTopSs(double marginTopSs) {
        this.marginTopSs = marginTopSs;
    }

    public double getMarginRightSs() {
        return marginRightSs;
    }

    public void setMarginRightSs(double marginRightSs) {
        this.marginRightSs = marginRightSs;
    }

    public double getMarginBottomSs() {
        return marginBottomSs;
    }

    public void setMarginBottomSs(double marginBottomSs) {
        this.marginBottomSs = marginBottomSs;
    }

    public double getMarginLeftSs() {
        return marginLeftSs;
    }

    public void setMarginLeftSs(double marginLeftSs) {
        this.marginLeftSs = marginLeftSs;
    }

    // ========================================================================
    // Bounds Calculation
    // ========================================================================





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
    public double collapsedVerticalMarginWith(LineElement below) {
        return Math.max(marginBottomSs, below.marginTopSs);
    }

    /**
     * Calculates the collapsed horizontal margin between this element and the one to its right.
     * CSS-style: the larger margin wins.
     *
     * @param right The element to the right of this one
     * @return The effective margin between the two elements
     */
    public double collapsedHorizontalMarginWith(LineElement right) {
        return Math.max(marginRightSs, right.marginLeftSs);
    }

    // ========================================================================
    // Child Management
    // ========================================================================

    /**
     * Returns an unmodifiable view of this element's children.
     */
    public List<LineElement> getChildren() {
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
    public void addChild(LineElement child) {
        child.parentElement = this;
        child.parentLine = parentLine;
        children.add(child);
    }

    /**
     * Removes a child element.
     *
     * @param child The element to remove
     */
    public void removeChild(LineElement child) {
        if (children.remove(child)) {
            child.parentElement = null;
            child.parentLine = null;
        }
    }

    /**
     * Returns the child at the given index.
     *
     * @param index Index of the child
     * @return The child element
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public LineElement getChild(int index) {
        return children.get(index);
    }
}
