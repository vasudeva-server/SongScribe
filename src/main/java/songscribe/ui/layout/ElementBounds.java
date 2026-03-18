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

import org.jspecify.annotations.Nullable;

/**
 * Represents the complete box model for a layout element (CSS-like).
 * <p>
 * Box model layers (from inner to outer):
 * <ul>
 *   <li><b>contentBounds</b>: Actual drawn pixels (ink bounds)</li>
 *   <li><b>paddingBounds</b>: Content + padding (used for hit testing/intersection)</li>
 *   <li><b>marginBounds</b>: Padding + margin (space element claims for layout)</li>
 *   <li><b>visualBounds</b>: Full visual extent (may exceed margin for decorative elements)</li>
 * </ul>
 * <p>
 * Example: For a first/second ending bracket, the content bounds would be the
 * bracket glyph, but the visual bounds would extend horizontally to include
 * the connecting line that spans to the next ending.
 */
public final class ElementBounds {

    private final Rectangle2D contentBounds;
    private final Rectangle2D paddingBounds;
    private final Rectangle2D marginBounds;
    private final @Nullable Rectangle2D visualBounds;

    /**
     * Creates element bounds with all four box model layers.
     *
     * @param contentBounds Actual drawn pixels (ink bounds)
     * @param paddingBounds Content + padding (hit testing)
     * @param marginBounds  Padding + margin (layout spacing)
     * @param visualBounds  Full visual extent (decorative overflow), null if same as margin
     */
    public ElementBounds(
        Rectangle2D contentBounds,
        Rectangle2D paddingBounds,
        Rectangle2D marginBounds,
        @Nullable Rectangle2D visualBounds
    ) {
        this.contentBounds = contentBounds;
        this.paddingBounds = paddingBounds;
        this.marginBounds = marginBounds;
        this.visualBounds = visualBounds;
    }

    /**
     * Creates element bounds where visual bounds equals margin bounds.
     */
    public ElementBounds(
        Rectangle2D contentBounds,
        Rectangle2D paddingBounds,
        Rectangle2D marginBounds
    ) {
        this(contentBounds, paddingBounds, marginBounds, null);
    }

    /**
     * Creates element bounds with no padding (padding equals content).
     */
    public static ElementBounds withMarginOnly(
        Rectangle2D contentBounds,
        Rectangle2D marginBounds
    ) {
        return new ElementBounds(contentBounds, contentBounds, marginBounds);
    }

    /**
     * Creates element bounds with uniform padding and margin.
     *
     * @param contentBounds The content rectangle
     * @param padding       Uniform padding in pixels
     * @param margin        Uniform margin in pixels (added to padding bounds)
     */
    public static ElementBounds uniform(
        Rectangle2D contentBounds,
        double padding,
        double margin
    ) {
        var paddingBounds = new Rectangle2D.Double(
            contentBounds.getX() - padding,
            contentBounds.getY() - padding,
            contentBounds.getWidth() + 2 * padding,
            contentBounds.getHeight() + 2 * padding
        );

        var marginBounds = new Rectangle2D.Double(
            paddingBounds.getX() - margin,
            paddingBounds.getY() - margin,
            paddingBounds.getWidth() + 2 * margin,
            paddingBounds.getHeight() + 2 * margin
        );

        return new ElementBounds(contentBounds, paddingBounds, marginBounds);
    }

    /**
     * Creates element bounds with content only (no padding or margin).
     */
    public static ElementBounds contentOnly(Rectangle2D contentBounds) {
        return new ElementBounds(contentBounds, contentBounds, contentBounds);
    }

    /**
     * Returns the content bounds (actual drawn pixels).
     */
    public Rectangle2D getContentBounds() {
        return contentBounds;
    }

    /**
     * Returns the padding bounds (content + padding, used for hit testing).
     */
    public Rectangle2D getPaddingBounds() {
        return paddingBounds;
    }

    /**
     * Returns the margin bounds (padding + margin, used for layout spacing).
     */
    public Rectangle2D getMarginBounds() {
        return marginBounds;
    }

    /**
     * Returns the visual bounds (full visual extent including decorative elements).
     * Falls back to margin bounds if not explicitly set.
     */
    public Rectangle2D getVisualBounds() {
        return visualBounds != null ? visualBounds : marginBounds;
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
     * Returns whether the given point is within the padding bounds (hit testing).
     */
    public boolean containsForHitTest(double x, double y) {
        return paddingBounds.contains(x, y);
    }

    /**
     * Returns whether this element's margin bounds intersect with another's.
     */
    public boolean intersectsMargin(ElementBounds other) {
        return marginBounds.intersects(other.marginBounds);
    }

    /**
     * Returns whether this element's padding bounds intersect with another's.
     */
    public boolean intersectsPadding(ElementBounds other) {
        return paddingBounds.intersects(other.paddingBounds);
    }

    /**
     * Returns a new ElementBounds translated by the given offset.
     */
    public ElementBounds translate(double dx, double dy) {
        return new ElementBounds(
            translateRect(contentBounds, dx, dy),
            translateRect(paddingBounds, dx, dy),
            translateRect(marginBounds, dx, dy),
            visualBounds != null ? translateRect(visualBounds, dx, dy) : null
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
     * Returns the content size as "width×height" string.
     */
    public String getContentSizeString() {
        return (int) Math.round(contentBounds.getWidth()) + "×" +
               (int) Math.round(contentBounds.getHeight());
    }

    /**
     * Returns the padding as CSS shorthand (e.g., "4px", "4px 8px", "4px 8px 4px 8px").
     */
    public String getPaddingCss() {
        return formatCssSpacing(
            contentBounds.getMinY() - paddingBounds.getMinY(),
            paddingBounds.getMaxX() - contentBounds.getMaxX(),
            paddingBounds.getMaxY() - contentBounds.getMaxY(),
            contentBounds.getMinX() - paddingBounds.getMinX()
        );
    }

    /**
     * Returns the margin as CSS shorthand (e.g., "4px", "4px 8px", "4px 8px 4px 8px").
     */
    public String getMarginCss() {
        return formatCssSpacing(
            paddingBounds.getMinY() - marginBounds.getMinY(),
            marginBounds.getMaxX() - paddingBounds.getMaxX(),
            marginBounds.getMaxY() - paddingBounds.getMaxY(),
            paddingBounds.getMinX() - marginBounds.getMinX()
        );
    }

    /**
     * Formats spacing values as CSS shorthand.
     */
    private static String formatCssSpacing(double top, double right, double bottom, double left) {
        int t = Math.abs((int) Math.round(top));
        int r = Math.abs((int) Math.round(right));
        int b = Math.abs((int) Math.round(bottom));
        int l = Math.abs((int) Math.round(left));

        // All zero
        if (t == 0 && r == 0 && b == 0 && l == 0) {
            return "0";
        }

        // All same
        if (t == r && r == b && b == l) {
            return t + "px";
        }

        // Top/bottom same, left/right same
        if (t == b && l == r) {
            return t + "px " + r + "px";
        }

        // Top different, left/right same, bottom different
        if (l == r) {
            return t + "px " + r + "px " + b + "px";
        }

        // All different
        return t + "px " + r + "px " + b + "px " + l + "px";
    }

    @Override
    public String toString() {
        return "ElementBounds{content=" + rectToString(contentBounds) +
            ", padding=" + rectToString(paddingBounds) +
            ", margin=" + rectToString(marginBounds) +
            (visualBounds != null ? ", visual=" + rectToString(visualBounds) : "") +
            "}";
    }

    private static String rectToString(Rectangle2D r) {
        return String.format("[%.1f,%.1f,%.1f,%.1f]",
            r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
