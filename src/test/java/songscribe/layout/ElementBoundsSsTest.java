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

import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class ElementBoundsSsTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 5: factories — exact layer rects
    // -----------------------------------------------------------------------

    @Nested
    class Factories {

        @Test
        void testUniformProducesCorrectLayers() {
            final var contentXSs = 2.0;
            final var contentYSs = 3.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 1.5;
            final var paddingSs = 0.5;
            final var marginSs = 0.25;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            // content layer
            assertThat(bounds.getContentBounds()).isEqualTo(content);

            // padding = content expanded by paddingSs on all sides
            final var expectedPaddingX = contentXSs - paddingSs;
            final var expectedPaddingY = contentYSs - paddingSs;
            final var expectedPaddingW = contentWidthSs + 2 * paddingSs;
            final var expectedPaddingH = contentHeightSs + 2 * paddingSs;
            assertThat(bounds.getPaddingBounds()).isEqualTo(
                new Rectangle2D.Double(expectedPaddingX, expectedPaddingY, expectedPaddingW, expectedPaddingH));

            // margin = padding expanded by marginSs on all sides
            final var expectedMarginX = expectedPaddingX - marginSs;
            final var expectedMarginY = expectedPaddingY - marginSs;
            final var expectedMarginW = expectedPaddingW + 2 * marginSs;
            final var expectedMarginH = expectedPaddingH + 2 * marginSs;
            assertThat(bounds.getMarginBounds()).isEqualTo(
                new Rectangle2D.Double(expectedMarginX, expectedMarginY, expectedMarginW, expectedMarginH));

            // visualBounds falls back to margin when not set
            assertThat(bounds.getVisualBounds()).isEqualTo(bounds.getMarginBounds());
        }

        @Test
        void testWithMarginProducesCorrectLayersAndNoTopMargin() {
            final var contentXSs = 1.0;
            final var contentYSs = 2.0;
            final var contentWidthSs = 6.0;
            final var contentHeightSs = 2.0;
            final var leftMarginSs = 0.5;
            final var bottomMarginSs = 0.75;
            final var rightMarginSs = 0.25;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var margin = new Margin(leftMarginSs, bottomMarginSs, rightMarginSs);
            var bounds = ElementBoundsSs.withMargin(content, margin);

            // padding == content (no padding layer)
            assertThat(bounds.getPaddingBounds()).isEqualTo(content);

            // margin: left and right expand, bottom expands, top NOT expanded (y stays at contentY)
            final var expectedMarginX = contentXSs - leftMarginSs;
            final var expectedMarginY = contentYSs; // no top margin
            final var expectedMarginW = contentWidthSs + leftMarginSs + rightMarginSs;
            final var expectedMarginH = contentHeightSs + bottomMarginSs; // only bottom
            assertThat(bounds.getMarginBounds()).isEqualTo(
                new Rectangle2D.Double(expectedMarginX, expectedMarginY, expectedMarginW, expectedMarginH));
        }

        @Test
        void testWithMarginOnlyProducesCorrectLayers() {
            final var contentXSs = 0.0;
            final var contentYSs = 0.0;
            final var contentWidthSs = 3.0;
            final var contentHeightSs = 1.0;
            final var marginXSs = -1.0;
            final var marginYSs = -0.5;
            final var marginWidthSs = 5.0;
            final var marginHeightSs = 2.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var marginRect = new Rectangle2D.Double(marginXSs, marginYSs, marginWidthSs, marginHeightSs);
            var bounds = ElementBoundsSs.withMarginOnly(content, marginRect);

            // padding == content
            assertThat(bounds.getPaddingBounds()).isEqualTo(content);
            assertThat(bounds.getMarginBounds()).isEqualTo(marginRect);
        }

        @Test
        void testContentOnlyProducesAllLayersEqualToContent() {
            final var contentXSs = 5.0;
            final var contentYSs = 1.0;
            final var contentWidthSs = 2.0;
            final var contentHeightSs = 3.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.contentOnly(content);

            assertThat(bounds.getContentBounds()).isEqualTo(content);
            assertThat(bounds.getPaddingBounds()).isEqualTo(content);
            assertThat(bounds.getMarginBounds()).isEqualTo(content);
            assertThat(bounds.getVisualBounds()).isEqualTo(content);
        }
    }

    // -----------------------------------------------------------------------
    // Row 6: collapsedMarginWith — CSS margin collapse = max(thisBottom, belowTop)
    // -----------------------------------------------------------------------

    @Nested
    class CollapsedMarginWith {

        // thisBottomMargin = getMarginBottomSs() - getBottomSs()
        //                  = (marginY + marginH) - (contentY + contentH)
        // belowTopMargin   = below.getTopSs() - below.getMarginTopSs()
        //                  = below.contentY - below.marginY

        @Test
        void testThisBottomMarginWinsWhenLarger() {
            final var thisBottomMarginSs = 2.0;
            final var belowTopMarginSs = 1.0;

            var above = boundsWithBottomMarginSs(thisBottomMarginSs);
            var below = boundsWithTopMarginSs(belowTopMarginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(thisBottomMarginSs);
        }

        @Test
        void testBelowTopMarginWinsWhenLarger() {
            final var thisBottomMarginSs = 0.5;
            final var belowTopMarginSs = 1.5;

            var above = boundsWithBottomMarginSs(thisBottomMarginSs);
            var below = boundsWithTopMarginSs(belowTopMarginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(belowTopMarginSs);
        }

        @Test
        void testEqualMarginsProduceThatValue() {
            final var marginSs = 1.25;

            var above = boundsWithBottomMarginSs(marginSs);
            var below = boundsWithTopMarginSs(marginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(marginSs);
        }

        // Builds bounds where the margin extends `bottomMarginSs` below the content.
        // Content: (0, 0, 1, 1); margin bottom extends down by bottomMarginSs.
        private static ElementBoundsSs boundsWithBottomMarginSs(double bottomMarginSs) {
            final var xSs = 0.0;
            final var ySs = 0.0;
            final var sizeSs = 1.0;
            var content = new Rectangle2D.Double(xSs, ySs, sizeSs, sizeSs);
            var marginRect = new Rectangle2D.Double(xSs, ySs, sizeSs, sizeSs + bottomMarginSs);
            return ElementBoundsSs.withMarginOnly(content, marginRect);
        }

        // Builds bounds where the margin extends `topMarginSs` above the content.
        // Content: (0, topMarginSs, 1, 1); marginY = 0 so topMargin = topMarginSs.
        private static ElementBoundsSs boundsWithTopMarginSs(double topMarginSs) {
            final var xSs = 0.0;
            final var sizeSs = 1.0;
            var content = new Rectangle2D.Double(xSs, topMarginSs, sizeSs, sizeSs);
            var marginRect = new Rectangle2D.Double(xSs, 0.0, sizeSs, topMarginSs + sizeSs);
            return ElementBoundsSs.withMarginOnly(content, marginRect);
        }
    }

    // -----------------------------------------------------------------------
    // Row 7: containsForHitTest — delegates to padding bounds
    // -----------------------------------------------------------------------

    @Nested
    class ContainsForHitTest {

        @Test
        void testPointInsidePaddingReturnsTrue() {
            final var paddingSs = 0.5;
            final var contentXSs = 2.0;
            final var contentYSs = 2.0;
            final var contentSizeSs = 2.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, 0.0);

            // Center of content is well inside padding
            final var centerX = contentXSs + contentSizeSs / 2;
            final var centerY = contentYSs + contentSizeSs / 2;
            assertThat(bounds.containsForHitTest(centerX, centerY)).isTrue();
        }

        @Test
        void testPointInMarginButOutsidePaddingReturnsFalse() {
            final var paddingSs = 0.25;
            final var marginSs = 1.0;
            final var contentXSs = 2.0;
            final var contentYSs = 2.0;
            final var contentSizeSs = 2.0;
            final var pastPaddingEdgeSs = 0.1;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            // A point inside margin but outside padding: just beyond the padding edge
            final var justOutsidePaddingX = contentXSs - paddingSs - pastPaddingEdgeSs;
            final var insideY = contentYSs + contentSizeSs / 2;
            assertThat(bounds.containsForHitTest(justOutsidePaddingX, insideY)).isFalse();
        }

        @Test
        void testPointFullyOutsideReturnsFalse() {
            final var paddingSs = 0.5;
            final var contentXSs = 2.0;
            final var contentYSs = 2.0;
            final var contentSizeSs = 2.0;

            final var farLeftXSs = -100.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, 0.0);

            // Far to the left of all bounds
            assertThat(bounds.containsForHitTest(farLeftXSs, contentYSs)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 8: intersectsMargin / intersectsPadding — layer-specific
    // -----------------------------------------------------------------------

    @Nested
    class Intersects {

        @Test
        void testIntersectsMarginReturnsTrueWhenMarginsOverlap() {
            // two elements whose margin rects overlap but content does not
            final var sizeSSs = 1.0;
            final var marginSs = 1.0;

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + marginSs, 0.0, sizeSSs, sizeSSs);
            // A's margin extends right by marginSs; B's margin extends left by marginSs → they touch/overlap
            var boundsA = ElementBoundsSs.uniform(contentA, 0.0, marginSs);
            var boundsB = ElementBoundsSs.uniform(contentB, 0.0, marginSs);

            assertThat(boundsA.intersectsMargin(boundsB)).isTrue();
        }

        @Test
        void testIntersectsMarginReturnsFalseWhenMarginsDontOverlap() {
            final var sizeSSs = 1.0;
            final var marginSs = 0.1;
            final var gapSs = 1.0; // gap >> margin, so margins don't touch

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + gapSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, 0.0, marginSs);
            var boundsB = ElementBoundsSs.uniform(contentB, 0.0, marginSs);

            assertThat(boundsA.intersectsMargin(boundsB)).isFalse();
        }

        @Test
        void testIntersectsPaddingReturnsTrueWhenPaddingOverlaps() {
            final var sizeSSs = 2.0;
            final var paddingSs = 0.5;
            final var overlapNudgeSs = 0.1;

            // Place B at xSs = sizeSSs + paddingSs so padding edges touch (just barely overlapping)
            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + paddingSs - overlapNudgeSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, paddingSs, 0.0);
            var boundsB = ElementBoundsSs.uniform(contentB, paddingSs, 0.0);

            assertThat(boundsA.intersectsPadding(boundsB)).isTrue();
        }

        @Test
        void testIntersectsPaddingReturnsFalseWhenPaddingDontOverlap() {
            final var sizeSSs = 2.0;
            final var paddingSs = 0.25;
            final var gapSs = 2.0; // far apart

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + gapSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, paddingSs, 0.0);
            var boundsB = ElementBoundsSs.uniform(contentB, paddingSs, 0.0);

            assertThat(boundsA.intersectsPadding(boundsB)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 9: translate(dx,dy) shifts all four layers (incl. nullable visual)
    // -----------------------------------------------------------------------

    @Nested
    class Translate {

        @Test
        void testTranslateShiftsAllFourLayersWhenVisualExplicitlySet() {
            final var contentXSs = 1.0;
            final var contentYSs = 2.0;
            final var contentWidthSs = 3.0;
            final var contentHeightSs = 1.5;
            final var paddingSs = 0.5;
            final var marginSs = 0.25;
            final var visualExtensionSs = 0.75;
            final var dxSs = 2.0;
            final var dySs = 3.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var padding = new Rectangle2D.Double(
                contentXSs - paddingSs, contentYSs - paddingSs,
                contentWidthSs + 2 * paddingSs, contentHeightSs + 2 * paddingSs);
            var margin = new Rectangle2D.Double(
                padding.getX() - marginSs, padding.getY() - marginSs,
                padding.getWidth() + 2 * marginSs, padding.getHeight() + 2 * marginSs);
            // Visual extends beyond the margin on all sides.
            var visual = new Rectangle2D.Double(
                margin.getX() - visualExtensionSs, margin.getY() - visualExtensionSs,
                margin.getWidth() + 2 * visualExtensionSs, margin.getHeight() + 2 * visualExtensionSs);

            var bounds = new ElementBoundsSs(content, padding, margin, visual);
            var translated = bounds.translate(dxSs, dySs);

            assertThat(translated.getContentBounds()).isEqualTo(new Rectangle2D.Double(
                content.getX() + dxSs, content.getY() + dySs, content.getWidth(), content.getHeight()));
            assertThat(translated.getPaddingBounds()).isEqualTo(new Rectangle2D.Double(
                padding.getX() + dxSs, padding.getY() + dySs, padding.getWidth(), padding.getHeight()));
            assertThat(translated.getMarginBounds()).isEqualTo(new Rectangle2D.Double(
                margin.getX() + dxSs, margin.getY() + dySs, margin.getWidth(), margin.getHeight()));
            assertThat(translated.getVisualBounds()).isEqualTo(new Rectangle2D.Double(
                visual.getX() + dxSs, visual.getY() + dySs, visual.getWidth(), visual.getHeight()));
        }

        @Test
        void testTranslateWithNullVisualFallsBackToShiftedMargin() {
            final var contentXSs = 0.0;
            final var contentYSs = 0.0;
            final var contentWidthSs = 2.0;
            final var contentHeightSs = 2.0;
            final var marginSs = 0.25;
            final var dxSs = 1.5;
            final var dySs = 2.5;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, 0.0, marginSs);
            var translated = bounds.translate(dxSs, dySs);

            // Visual is null; getVisualBounds() must fall back to the shifted margin.
            assertThat(translated.getVisualBounds()).isEqualTo(translated.getMarginBounds());
        }
    }

    // -----------------------------------------------------------------------
    // Row 10: getVisualBounds() — explicit-visual branch vs margin fallback
    // -----------------------------------------------------------------------

    @Nested
    class GetVisualBounds {

        @Test
        void testExplicitVisualRectIsReturnedWhenSet() {
            final var contentXSs = 1.0;
            final var contentYSs = 1.0;
            final var contentSizeSs = 2.0;
            final var visualXSs = 0.0;
            final var visualYSs = 0.0;
            final var visualWidthSs = 5.0;
            final var visualHeightSs = 5.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var visual = new Rectangle2D.Double(visualXSs, visualYSs, visualWidthSs, visualHeightSs);
            var bounds = new ElementBoundsSs(content, content, content, visual);

            assertThat(bounds.getVisualBounds()).isEqualTo(visual);
        }

        @Test
        void testNoVisualFallsBackToMarginBounds() {
            final var contentXSs = 2.0;
            final var contentYSs = 3.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 2.0;
            final var marginSs = 0.5;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, 0.0, marginSs);

            assertThat(bounds.getVisualBounds()).isEqualTo(bounds.getMarginBounds());
        }
    }

    // -----------------------------------------------------------------------
    // Row 13: getPaddingCss / getMarginCss — correct differentials
    // -----------------------------------------------------------------------

    @Nested
    class PaddingAndMarginCss {

        @Test
        void testGetPaddingCssUniformPaddingReturnsOneToken() {
            final var contentXSs = 2.0;
            final var contentYSs = 2.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 4.0;
            final var paddingSs = 2.0;
            final var marginSs = 5.0;
            final var expectedTokenSs = (int) paddingSs;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            assertThat(bounds.getPaddingCss()).isEqualTo(expectedTokenSs + "ss");
        }

        @Test
        void testGetPaddingCssAsymmetricPaddingReturnsFourTokens() {
            final var contentXSs = 5.0;
            final var contentYSs = 5.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 4.0;
            final var topPaddingSs = 1.0;
            final var rightPaddingSs = 2.0;
            final var bottomPaddingSs = 3.0;
            final var leftPaddingSs = 4.0;
            final var expectedTop = (int) topPaddingSs;
            final var expectedRight = (int) rightPaddingSs;
            final var expectedBottom = (int) bottomPaddingSs;
            final var expectedLeft = (int) leftPaddingSs;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var padding = new Rectangle2D.Double(
                contentXSs - leftPaddingSs,
                contentYSs - topPaddingSs,
                contentWidthSs + leftPaddingSs + rightPaddingSs,
                contentHeightSs + topPaddingSs + bottomPaddingSs);
            var bounds = new ElementBoundsSs(content, padding, padding);

            assertThat(bounds.getPaddingCss())
                .isEqualTo(expectedTop + "ss " + expectedRight + "ss " + expectedBottom + "ss " + expectedLeft + "ss");
        }

        @Test
        void testGetMarginCssUniformMarginReturnsOneToken() {
            final var contentXSs = 2.0;
            final var contentYSs = 2.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 4.0;
            final var marginSs = 3.0;
            final var expectedTokenSs = (int) marginSs;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, 0.0, marginSs);

            assertThat(bounds.getMarginCss()).isEqualTo(expectedTokenSs + "ss");
        }

        @Test
        void testGetMarginCssAsymmetricMarginReturnsFourTokens() {
            final var contentXSs = 10.0;
            final var contentYSs = 10.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 4.0;
            final var topMarginSs = 2.0;
            final var rightMarginSs = 4.0;
            final var bottomMarginSs = 6.0;
            final var leftMarginSs = 8.0;
            final var expectedTop = (int) topMarginSs;
            final var expectedRight = (int) rightMarginSs;
            final var expectedBottom = (int) bottomMarginSs;
            final var expectedLeft = (int) leftMarginSs;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var margin = new Rectangle2D.Double(
                contentXSs - leftMarginSs,
                contentYSs - topMarginSs,
                contentWidthSs + leftMarginSs + rightMarginSs,
                contentHeightSs + topMarginSs + bottomMarginSs);
            var bounds = new ElementBoundsSs(content, content, margin);

            assertThat(bounds.getMarginCss())
                .isEqualTo(expectedTop + "ss " + expectedRight + "ss " + expectedBottom + "ss " + expectedLeft + "ss");
        }
    }

    // -----------------------------------------------------------------------
    // Row 11: coordinate accessors — content and margin edges
    // -----------------------------------------------------------------------

    @Nested
    class CoordinateAccessors {

        @Test
        void testAccessorsReturnExactContentAndMarginCoordinates() {
            final var contentXSs = 3.0;
            final var contentYSs = 5.0;
            final var contentWidthSs = 4.0;
            final var contentHeightSs = 2.0;
            final var paddingSs = 0.5;
            final var marginSs = 0.25;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            // Content-derived accessors
            assertThat(bounds.getTopSs()).isEqualTo(contentYSs);
            assertThat(bounds.getBottomSs()).isEqualTo(contentYSs + contentHeightSs);
            assertThat(bounds.getLeftSs()).isEqualTo(contentXSs);
            assertThat(bounds.getRightSs()).isEqualTo(contentXSs + contentWidthSs);

            // Margin-derived accessors (margin = padding + marginSs on each side)
            final var expectedMarginTopSs = contentYSs - paddingSs - marginSs;
            final var expectedMarginBottomSs = contentYSs + contentHeightSs + paddingSs + marginSs;
            assertThat(bounds.getMarginTopSs()).isEqualTo(expectedMarginTopSs);
            assertThat(bounds.getMarginBottomSs()).isEqualTo(expectedMarginBottomSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 12: formatCssSpacing — 5-branch CSS shorthand, all tokens use "ss"
    // Tested through the public getPaddingCss() caller.
    // -----------------------------------------------------------------------

    @Nested
    class FormatCssSpacing {

        // Shared fixture content rect; all padding amounts are integer-valued so
        // Math.round() is a no-op and expected strings are exact.
        private static final double CONTENT_X_SS = 2.0;
        private static final double CONTENT_Y_SS = 2.0;
        private static final double CONTENT_WIDTH_SS = 4.0;
        private static final double CONTENT_HEIGHT_SS = 4.0;

        private static Rectangle2D content() {
            return new Rectangle2D.Double(CONTENT_X_SS, CONTENT_Y_SS, CONTENT_WIDTH_SS, CONTENT_HEIGHT_SS);
        }

        /** Builds bounds with the given asymmetric padding amounts (int-valued). */
        private static ElementBoundsSs withPadding(
            double topSs, double rightSs, double bottomSs, double leftSs) {
            var c = content();
            var p = new Rectangle2D.Double(
                c.getX() - leftSs,
                c.getY() - topSs,
                c.getWidth() + leftSs + rightSs,
                c.getHeight() + topSs + bottomSs);
            return new ElementBoundsSs(c, p, p);
        }

        @Test
        void testAllZeroPaddingReturnsZero() {
            // padding == content → all differentials are 0
            assertThat(ElementBoundsSs.contentOnly(content()).getPaddingCss()).isEqualTo("0");
        }

        @Test
        void testAllSamePaddingReturnsOneToken() {
            final var uniformPaddingSs = 3.0;
            final var expected = (int) uniformPaddingSs;
            var bounds = ElementBoundsSs.uniform(content(), uniformPaddingSs, 0.0);
            assertThat(bounds.getPaddingCss()).isEqualTo(expected + "ss");
        }

        @Test
        void testTopBottomSameLeftRightSameReturnsTwoTokens() {
            final var topBottomPaddingSs = 2.0;
            final var leftRightPaddingSs = 4.0;
            final var expectedVertical = (int) topBottomPaddingSs;
            final var expectedHorizontal = (int) leftRightPaddingSs;
            var bounds = withPadding(topBottomPaddingSs, leftRightPaddingSs, topBottomPaddingSs, leftRightPaddingSs);
            assertThat(bounds.getPaddingCss()).isEqualTo(expectedVertical + "ss " + expectedHorizontal + "ss");
        }

        @Test
        void testLeftRightSameTopBottomDifferentReturnsThreeTokens() {
            final var topPaddingSs = 1.0;
            final var sidesPaddingSs = 2.0;
            final var bottomPaddingSs = 3.0;
            final var expectedTop = (int) topPaddingSs;
            final var expectedSides = (int) sidesPaddingSs;
            final var expectedBottom = (int) bottomPaddingSs;
            var bounds = withPadding(topPaddingSs, sidesPaddingSs, bottomPaddingSs, sidesPaddingSs);
            assertThat(bounds.getPaddingCss())
                .isEqualTo(expectedTop + "ss " + expectedSides + "ss " + expectedBottom + "ss");
        }

        @Test
        void testAllDifferentReturnsFourTokens() {
            final var topPaddingSs = 1.0;
            final var rightPaddingSs = 2.0;
            final var bottomPaddingSs = 3.0;
            final var leftPaddingSs = 4.0;
            final var expectedTop = (int) topPaddingSs;
            final var expectedRight = (int) rightPaddingSs;
            final var expectedBottom = (int) bottomPaddingSs;
            final var expectedLeft = (int) leftPaddingSs;
            var bounds = withPadding(topPaddingSs, rightPaddingSs, bottomPaddingSs, leftPaddingSs);
            assertThat(bounds.getPaddingCss())
                .isEqualTo(expectedTop + "ss " + expectedRight + "ss " + expectedBottom + "ss " + expectedLeft + "ss");
        }
    }
}
