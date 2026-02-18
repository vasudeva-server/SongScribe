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

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;

/**
 * Simplified box model with 2 layers: content and margin.
 * <p>
 * This is a streamlined alternative to {@link ElementBounds} that removes the padding
 * and visual layers. Hit testing uses content bounds expanded by a configurable amount.
 * <p>
 * Box model layers:
 * <ul>
 *   <li><b>contentBounds</b>: Actual drawn pixels (ink bounds)</li>
 *   <li><b>marginBounds</b>: Content + margin (space element claims for layout)</li>
 * </ul>
 */
public final class Bounds {

    /** Default expansion for hit testing (in pixels). */
    public static final double DEFAULT_HIT_EXPANSION = 3.0;

    private final @NotNull Rectangle2D contentBounds;
    private final @NotNull Rectangle2D marginBounds;

    /**
     * Creates bounds with content and margin layers.
     *
     * @param contentBounds Actual drawn pixels (ink bounds)
     * @param marginBounds  Content + margin (layout spacing)
     */
    public Bounds(@NotNull Rectangle2D contentBounds, @NotNull Rectangle2D marginBounds) {
        this.contentBounds = contentBounds;
        this.marginBounds = marginBounds;
    }

    /**
     * Creates bounds with content only (no margin).
     */
    public static Bounds contentOnly(@NotNull Rectangle2D contentBounds) {
        return new Bounds(contentBounds, contentBounds);
    }

    /**
     * Creates bounds with uniform margin around content.
     *
     * @param contentBounds The content rectangle
     * @param margin        Uniform margin in pixels
     */
    public static Bounds withUniformMargin(@NotNull Rectangle2D contentBounds, double margin) {
        var marginBounds = new Rectangle2D.Double(
            contentBounds.getX() - margin,
            contentBounds.getY() - margin,
            contentBounds.getWidth() + 2 * margin,
            contentBounds.getHeight() + 2 * margin
        );
        return new Bounds(contentBounds, marginBounds);
    }

    /**
     * Creates bounds with CSS-style margins (top, right, bottom, left).
     *
     * @param contentBounds The content rectangle
     * @param top           Top margin in pixels
     * @param right         Right margin in pixels
     * @param bottom        Bottom margin in pixels
     * @param left          Left margin in pixels
     */
    public static Bounds withMargin(
        @NotNull Rectangle2D contentBounds,
        double top,
        double right,
        double bottom,
        double left
    ) {
        var marginBounds = new Rectangle2D.Double(
            contentBounds.getX() - left,
            contentBounds.getY() - top,
            contentBounds.getWidth() + left + right,
            contentBounds.getHeight() + top + bottom
        );
        return new Bounds(contentBounds, marginBounds);
    }

    /**
     * Creates bounds with a Margin object (left, bottom, right).
     * Note: Margin uses left/bottom/right only (no top margin).
     *
     * @param contentBounds The content rectangle
     * @param margin        The margin specification
     * @return New bounds with the specified margins
     */
    public static Bounds withMargin(@NotNull Rectangle2D contentBounds, @NotNull Margin margin) {
        var marginBounds = new Rectangle2D.Double(
            contentBounds.getX() - margin.left(),
            contentBounds.getY(),
            contentBounds.getWidth() + margin.left() + margin.right(),
            contentBounds.getHeight() + margin.bottom()
        );
        return new Bounds(contentBounds, marginBounds);
    }

    /**
     * Returns the content bounds (actual drawn pixels).
     */
    public @NotNull Rectangle2D getContentBounds() {
        return contentBounds;
    }

    /**
     * Returns the margin bounds (content + margin, used for layout spacing).
     */
    public @NotNull Rectangle2D getMarginBounds() {
        return marginBounds;
    }

    /**
     * Returns the top Y coordinate of the content bounds.
     */
    public double getTop() {
        return contentBounds.getY();
    }

    /**
     * Returns the bottom Y coordinate of the content bounds.
     */
    public double getBottom() {
        return contentBounds.getY() + contentBounds.getHeight();
    }

    /**
     * Returns the left X coordinate of the content bounds.
     */
    public double getLeft() {
        return contentBounds.getX();
    }

    /**
     * Returns the right X coordinate of the content bounds.
     */
    public double getRight() {
        return contentBounds.getX() + contentBounds.getWidth();
    }

    /**
     * Returns the width of the content bounds.
     */
    public double getWidth() {
        return contentBounds.getWidth();
    }

    /**
     * Returns the height of the content bounds.
     */
    public double getHeight() {
        return contentBounds.getHeight();
    }

    /**
     * Returns the top Y coordinate of the margin bounds.
     */
    public double getMarginTop() {
        return marginBounds.getY();
    }

    /**
     * Returns the bottom Y coordinate of the margin bounds.
     */
    public double getMarginBottom() {
        return marginBounds.getY() + marginBounds.getHeight();
    }

    /**
     * Returns the left X coordinate of the margin bounds.
     */
    public double getMarginLeft() {
        return marginBounds.getX();
    }

    /**
     * Returns the right X coordinate of the margin bounds.
     */
    public double getMarginRight() {
        return marginBounds.getX() + marginBounds.getWidth();
    }

    /**
     * Returns whether the given point is within the hit test area.
     * Uses default expansion of {@link #DEFAULT_HIT_EXPANSION} pixels.
     */
    public boolean containsPoint(double x, double y) {
        return containsPoint(x, y, DEFAULT_HIT_EXPANSION);
    }

    /**
     * Returns whether the given point is within the hit test area.
     *
     * @param x         X coordinate
     * @param y         Y coordinate
     * @param expansion Pixels to expand content bounds for hit testing
     */
    public boolean containsPoint(double x, double y, double expansion) {
        return x >= contentBounds.getX() - expansion &&
               x <= contentBounds.getX() + contentBounds.getWidth() + expansion &&
               y >= contentBounds.getY() - expansion &&
               y <= contentBounds.getY() + contentBounds.getHeight() + expansion;
    }

    /**
     * Returns whether this element's margin bounds intersect with another's.
     */
    public boolean intersectsMargin(@NotNull Bounds other) {
        return marginBounds.intersects(other.marginBounds);
    }

    /**
     * Returns a new Bounds translated by the given offset.
     */
    public Bounds translate(double dx, double dy) {
        return new Bounds(
            translateRect(contentBounds, dx, dy),
            translateRect(marginBounds, dx, dy)
        );
    }

    private static Rectangle2D translateRect(Rectangle2D rect, double dx, double dy) {
        return new Rectangle2D.Double(
            rect.getX() + dx,
            rect.getY() + dy,
            rect.getWidth(),
            rect.getHeight()
        );
    }

    /**
     * Calculates the collapsed margin between this element's bottom and another's top.
     * CSS-style margin collapsing: the larger margin wins.
     *
     * @param below The element below this one
     * @return The effective margin between the two elements
     */
    public double collapsedMarginWith(@NotNull Bounds below) {
        double thisBottomMargin = getMarginBottom() - getBottom();
        double belowTopMargin = below.getTop() - below.getMarginTop();
        return Math.max(thisBottomMargin, belowTopMargin);
    }

    /**
     * Converts the margin bounds to a java.awt.geom.Area for complex collision detection.
     * Used by the layout engine for handling irregular shapes like L-shaped ending brackets.
     *
     * @return An Area representing the margin bounds
     */
    public @NotNull Area toArea() {
        return new Area(marginBounds);
    }

    /**
     * Returns the margin as CSS shorthand (e.g., "4px", "4px 8px", "4px 8px 4px 8px").
     */
    public String getMarginCss() {
        int t = Math.abs((int) Math.round(contentBounds.getMinY() - marginBounds.getMinY()));
        int r = Math.abs((int) Math.round(marginBounds.getMaxX() - contentBounds.getMaxX()));
        int b = Math.abs((int) Math.round(marginBounds.getMaxY() - contentBounds.getMaxY()));
        int l = Math.abs((int) Math.round(contentBounds.getMinX() - marginBounds.getMinX()));

        if (t == 0 && r == 0 && b == 0 && l == 0) {
            return "0";
        }

        if (t == r && r == b && b == l) {
            return t + "px";
        }

        if (t == b && l == r) {
            return t + "px " + r + "px";
        }

        if (l == r) {
            return t + "px " + r + "px " + b + "px";
        }

        return t + "px " + r + "px " + b + "px " + l + "px";
    }

    @Override
    public String toString() {
        return "Bounds{content=" + rectToString(contentBounds) +
            ", margin=" + rectToString(marginBounds) + "}";
    }

    private static String rectToString(Rectangle2D r) {
        return String.format("[%.1f,%.1f,%.1f,%.1f]",
            r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
