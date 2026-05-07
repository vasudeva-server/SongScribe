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
public final class ElementBoundsSs {

    private final Rectangle2D contentBoundsSs;
    private final Rectangle2D paddingBoundsSs;
    private final Rectangle2D marginBoundsSs;
    private final @Nullable Rectangle2D visualBoundsSs;

    /**
     * Creates element bounds with all four box model layers.
     *
     * @param contentBounds Actual drawn pixels (ink bounds)
     * @param paddingBounds Content + padding (hit testing)
     * @param marginBounds  Padding + margin (layout spacing)
     * @param visualBounds  Full visual extent (decorative overflow), null if same as margin
     */
    public ElementBoundsSs(
        Rectangle2D contentBounds,
        Rectangle2D paddingBounds,
        Rectangle2D marginBounds,
        @Nullable Rectangle2D visualBounds
    ) {
        contentBoundsSs = contentBounds;
        paddingBoundsSs = paddingBounds;
        marginBoundsSs = marginBounds;
        visualBoundsSs = visualBounds;
    }

    /**
     * Creates element bounds where visual bounds equals margin bounds.
     */
    public ElementBoundsSs(
        Rectangle2D contentBounds,
        Rectangle2D paddingBounds,
        Rectangle2D marginBounds
    ) {
        this(contentBounds, paddingBounds, marginBounds, null);
    }

    /**
     * Creates element bounds with no padding (padding equals content).
     */
    public static ElementBoundsSs withMarginOnly(
        Rectangle2D contentBounds,
        Rectangle2D marginBounds
    ) {
        return new ElementBoundsSs(contentBounds, contentBounds, marginBounds);
    }

    /**
     * Creates element bounds with uniform padding and margin.
     *
     * @param contentBounds The content rectangle
     * @param paddingSs     Uniform padding in staff spaces
     * @param marginSs      Uniform margin in staff spaces (added to padding bounds)
     */
    public static ElementBoundsSs uniform(
        Rectangle2D contentBounds,
        double paddingSs,
        double marginSs
    ) {
        var paddingBounds = new Rectangle2D.Double(
            contentBounds.getX() - paddingSs,
            contentBounds.getY() - paddingSs,
            contentBounds.getWidth() + 2 * paddingSs,
            contentBounds.getHeight() + 2 * paddingSs
        );

        var marginBounds = new Rectangle2D.Double(
            paddingBounds.getX() - marginSs,
            paddingBounds.getY() - marginSs,
            paddingBounds.getWidth() + 2 * marginSs,
            paddingBounds.getHeight() + 2 * marginSs
        );

        return new ElementBoundsSs(contentBounds, paddingBounds, marginBounds);
    }

    /**
     * Creates element bounds with content only (no padding or margin).
     */
    public static ElementBoundsSs contentOnly(Rectangle2D contentBounds) {
        return new ElementBoundsSs(contentBounds, contentBounds, contentBounds);
    }

    /**
     * Creates element bounds with a Margin object (no padding layer).
     *
     * @param contentBounds The content rectangle
     * @param margin        The margin specification in staff spaces
     * @return New bounds with the specified margins and padding equal to content
     */
    public static ElementBoundsSs withMargin(Rectangle2D contentBounds, Margin margin) {
        var marginBounds = new Rectangle2D.Double(
            contentBounds.getX() - margin.leftSs(),
            contentBounds.getY(),
            contentBounds.getWidth() + margin.leftSs() + margin.rightSs(),
            contentBounds.getHeight() + margin.bottomSs()
        );
        return withMarginOnly(contentBounds, marginBounds);
    }

    /**
     * Returns the content bounds (actual drawn pixels).
     */
    public Rectangle2D getContentBounds() {
        return contentBoundsSs;
    }

    /**
     * Returns the padding bounds (content + padding, used for hit testing).
     */
    public Rectangle2D getPaddingBounds() {
        return paddingBoundsSs;
    }

    /**
     * Returns the margin bounds (padding + margin, used for layout spacing).
     */
    public Rectangle2D getMarginBounds() {
        return marginBoundsSs;
    }

    /**
     * Returns the visual bounds (full visual extent including decorative elements).
     * Falls back to margin bounds if not explicitly set.
     */
    public Rectangle2D getVisualBounds() {
        return visualBoundsSs != null ? visualBoundsSs : marginBoundsSs;
    }

    /**
     * Returns the top Y coordinate of the content bounds.
     */
    public double getTopSs() {
        return contentBoundsSs.getY();
    }

    /**
     * Returns the bottom Y coordinate of the content bounds.
     */
    public double getBottomSs() {
        return contentBoundsSs.getY() + contentBoundsSs.getHeight();
    }

    /**
     * Returns the left X coordinate of the content bounds.
     */
    public double getLeftSs() {
        return contentBoundsSs.getX();
    }

    /**
     * Returns the right X coordinate of the content bounds.
     */
    public double getRightSs() {
        return contentBoundsSs.getX() + contentBoundsSs.getWidth();
    }

    /**
     * Returns the top Y coordinate of the margin bounds.
     */
    public double getMarginTopSs() {
        return marginBoundsSs.getY();
    }

    /**
     * Returns the bottom Y coordinate of the margin bounds.
     */
    public double getMarginBottomSs() {
        return marginBoundsSs.getY() + marginBoundsSs.getHeight();
    }

    /**
     * Returns whether the given point is within the padding bounds (hit testing).
     */
    public boolean containsForHitTest(double xSs, double ySs) {
        return paddingBoundsSs.contains(xSs, ySs);
    }

    /**
     * Returns whether this element's margin bounds intersect with another's.
     */
    public boolean intersectsMargin(ElementBoundsSs other) {
        return marginBoundsSs.intersects(other.marginBoundsSs);
    }

    /**
     * Returns whether this element's padding bounds intersect with another's.
     */
    public boolean intersectsPadding(ElementBoundsSs other) {
        return paddingBoundsSs.intersects(other.paddingBoundsSs);
    }

    /**
     * Calculates the collapsed margin between this element's bottom and another's top.
     * CSS-style margin collapsing: the larger margin wins.
     *
     * @param below The element below this one
     * @return The effective margin between the two elements in staff spaces
     */
    public double collapsedMarginWith(ElementBoundsSs below) {
        var thisBottomMargin = getMarginBottomSs() - getBottomSs();
        var belowTopMargin = below.getTopSs() - below.getMarginTopSs();
        return Math.max(thisBottomMargin, belowTopMargin);
    }

    /**
     * Converts the margin bounds to a java.awt.geom.Area for complex collision detection.
     *
     * @return An Area representing the margin bounds
     */
    public Area toArea() {
        return new Area(marginBoundsSs);
    }

    /**
     * Returns a new ElementBoundsSs translated by the given offset.
     */
    public ElementBoundsSs translate(double dxSs, double dySs) {
        return new ElementBoundsSs(
            translateRect(contentBoundsSs, dxSs, dySs),
            translateRect(paddingBoundsSs, dxSs, dySs),
            translateRect(marginBoundsSs, dxSs, dySs),
            visualBoundsSs != null ? translateRect(visualBoundsSs, dxSs, dySs) : null
        );
    }

    private static Rectangle2D translateRect(Rectangle2D rect, double dxSs, double dySs) {
        return new Rectangle2D.Double(
            rect.getX() + dxSs,
            rect.getY() + dySs,
            rect.getWidth(),
            rect.getHeight()
        );
    }

    /**
     * Returns the content size as "width×height" string.
     */
    public String getContentSizeString() {
        return (int) Math.round(contentBoundsSs.getWidth()) + "×" +
               (int) Math.round(contentBoundsSs.getHeight());
    }

    /**
     * Returns the padding as CSS shorthand (e.g., "4ss", "4ss 8ss", "4ss 8ss 4ss 8ss").
     */
    public String getPaddingCss() {
        return formatCssSpacing(
            contentBoundsSs.getMinY() - paddingBoundsSs.getMinY(),
            paddingBoundsSs.getMaxX() - contentBoundsSs.getMaxX(),
            paddingBoundsSs.getMaxY() - contentBoundsSs.getMaxY(),
            contentBoundsSs.getMinX() - paddingBoundsSs.getMinX()
        );
    }

    /**
     * Returns the margin as CSS shorthand (e.g., "4ss", "4ss 8ss", "4ss 8ss 4ss 8ss").
     */
    public String getMarginCss() {
        return formatCssSpacing(
            paddingBoundsSs.getMinY() - marginBoundsSs.getMinY(),
            marginBoundsSs.getMaxX() - paddingBoundsSs.getMaxX(),
            marginBoundsSs.getMaxY() - paddingBoundsSs.getMaxY(),
            paddingBoundsSs.getMinX() - marginBoundsSs.getMinX()
        );
    }

    /**
     * Formats spacing values as CSS shorthand.
     */
    private static String formatCssSpacing(double top, double right, double bottom, double left) {
        var t = Math.abs((int) Math.round(top));
        var r = Math.abs((int) Math.round(right));
        var b = Math.abs((int) Math.round(bottom));
        var l = Math.abs((int) Math.round(left));

        // All zero
        if (t == 0 && r == 0 && b == 0 && l == 0) {
            return "0";
        }

        // All same
        if (t == r && r == b && b == l) {
            return t + "ss";
        }

        // Top/bottom same, left/right same
        if (t == b && l == r) {
            return t + "px " + r + "ss";
        }

        // Top different, left/right same, bottom different
        if (l == r) {
            return t + "px " + r + "px " + b + "ss";
        }

        // All different
        return t + "px " + r + "px " + b + "px " + l + "ss";
    }

    @Override
    public String toString() {
        return "ElementBoundsSs{content=" + rectToString(contentBoundsSs) +
            ", padding=" + rectToString(paddingBoundsSs) +
            ", margin=" + rectToString(marginBoundsSs) +
            (visualBoundsSs != null ? ", visual=" + rectToString(visualBoundsSs) : "") +
            '}';
    }

    private static String rectToString(Rectangle2D r) {
        return String.format("[%.1f,%.1f,%.1f,%.1f]",
            r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
