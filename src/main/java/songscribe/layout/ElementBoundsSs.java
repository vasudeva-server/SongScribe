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

package songscribe.layout;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

/**
 * The CSS-like box model of a layout element: ink bounds, padding (hit testing), margin (space
 * the element claims) and visual bounds (which may exceed the margin).
 */
public final class ElementBoundsSs {

    private final Rectangle2D contentBoundsSs;
    private final Rectangle2D paddingBoundsSs;
    private final Rectangle2D marginBoundsSs;
    private final @Nullable Rectangle2D visualBoundsSs;

    /**
     * @param visualBounds null if same as margin
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

    public ElementBoundsSs(
        Rectangle2D contentBounds,
        Rectangle2D paddingBounds,
        Rectangle2D marginBounds
    ) {
        this(contentBounds, paddingBounds, marginBounds, null);
    }

    public static ElementBoundsSs withMarginOnly(
        Rectangle2D contentBounds,
        Rectangle2D marginBounds
    ) {
        return new ElementBoundsSs(contentBounds, contentBounds, marginBounds);
    }

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

    public static ElementBoundsSs contentOnly(Rectangle2D contentBounds) {
        return new ElementBoundsSs(contentBounds, contentBounds, contentBounds);
    }

    public static ElementBoundsSs withMargin(Rectangle2D contentBounds, Margin margin) {
        var marginBounds = new Rectangle2D.Double(
            contentBounds.getX() - margin.leftSs(),
            contentBounds.getY(),
            contentBounds.getWidth() + margin.leftSs() + margin.rightSs(),
            contentBounds.getHeight() + margin.bottomSs()
        );
        return withMarginOnly(contentBounds, marginBounds);
    }

    public Rectangle2D getContentBounds() {
        return contentBoundsSs;
    }

    public Rectangle2D getPaddingBounds() {
        return paddingBoundsSs;
    }

    public Rectangle2D getMarginBounds() {
        return marginBoundsSs;
    }

    public Rectangle2D getVisualBounds() {
        return visualBoundsSs != null ? visualBoundsSs : marginBoundsSs;
    }

    public double getTopSs() {
        return contentBoundsSs.getY();
    }

    public double getBottomSs() {
        return contentBoundsSs.getY() + contentBoundsSs.getHeight();
    }

    public double getLeftSs() {
        return contentBoundsSs.getX();
    }

    public double getRightSs() {
        return contentBoundsSs.getX() + contentBoundsSs.getWidth();
    }

    public double getMarginTopSs() {
        return marginBoundsSs.getY();
    }

    public double getMarginBottomSs() {
        return marginBoundsSs.getY() + marginBoundsSs.getHeight();
    }

    public boolean containsForHitTest(double xSs, double ySs) {
        return paddingBoundsSs.contains(xSs, ySs);
    }

    public boolean intersectsMargin(ElementBoundsSs other) {
        return marginBoundsSs.intersects(other.marginBoundsSs);
    }

    public boolean intersectsPadding(ElementBoundsSs other) {
        return paddingBoundsSs.intersects(other.paddingBoundsSs);
    }

    public double collapsedMarginWith(ElementBoundsSs below) {
        var thisBottomMargin = getMarginBottomSs() - getBottomSs();
        var belowTopMargin = below.getTopSs() - below.getMarginTopSs();
        return Math.max(thisBottomMargin, belowTopMargin);
    }

    public Area toArea() {
        return new Area(marginBoundsSs);
    }

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

    public String getContentSizeString() {
        return (int) Math.round(contentBoundsSs.getWidth()) + "×" +
               (int) Math.round(contentBoundsSs.getHeight());
    }

    public String getPaddingCss() {
        return formatCssSpacing(
            contentBoundsSs.getMinY() - paddingBoundsSs.getMinY(),
            paddingBoundsSs.getMaxX() - contentBoundsSs.getMaxX(),
            paddingBoundsSs.getMaxY() - contentBoundsSs.getMaxY(),
            contentBoundsSs.getMinX() - paddingBoundsSs.getMinX()
        );
    }

    public String getMarginCss() {
        return formatCssSpacing(
            paddingBoundsSs.getMinY() - marginBoundsSs.getMinY(),
            marginBoundsSs.getMaxX() - paddingBoundsSs.getMaxX(),
            marginBoundsSs.getMaxY() - paddingBoundsSs.getMaxY(),
            paddingBoundsSs.getMinX() - marginBoundsSs.getMinX()
        );
    }

    private static String formatCssSpacing(double top, double right, double bottom, double left) {
        var t = Math.abs((int) Math.round(top));
        var r = Math.abs((int) Math.round(right));
        var b = Math.abs((int) Math.round(bottom));
        var l = Math.abs((int) Math.round(left));

        if (t == 0 && r == 0 && b == 0 && l == 0) {
            return "0";
        }

        if (t == r && r == b && b == l) {
            return t + "ss";
        }

        if (t == b && l == r) {
            return t + "ss " + r + "ss";
        }

        if (l == r) {
            return t + "ss " + r + "ss " + b + "ss";
        }

        return t + "ss " + r + "ss " + b + "ss " + l + "ss";
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
