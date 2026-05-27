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
            final double contentXSs = 2.0;
            final double contentYSs = 3.0;
            final double contentWidthSs = 4.0;
            final double contentHeightSs = 1.5;
            final double paddingSs = 0.5;
            final double marginSs = 0.25;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            // content layer
            assertThat(bounds.getContentBounds()).isEqualTo(content);

            // padding = content expanded by paddingSs on all sides
            final double expectedPaddingX = contentXSs - paddingSs;
            final double expectedPaddingY = contentYSs - paddingSs;
            final double expectedPaddingW = contentWidthSs + 2 * paddingSs;
            final double expectedPaddingH = contentHeightSs + 2 * paddingSs;
            assertThat(bounds.getPaddingBounds()).isEqualTo(
                new Rectangle2D.Double(expectedPaddingX, expectedPaddingY, expectedPaddingW, expectedPaddingH));

            // margin = padding expanded by marginSs on all sides
            final double expectedMarginX = expectedPaddingX - marginSs;
            final double expectedMarginY = expectedPaddingY - marginSs;
            final double expectedMarginW = expectedPaddingW + 2 * marginSs;
            final double expectedMarginH = expectedPaddingH + 2 * marginSs;
            assertThat(bounds.getMarginBounds()).isEqualTo(
                new Rectangle2D.Double(expectedMarginX, expectedMarginY, expectedMarginW, expectedMarginH));

            // visualBounds falls back to margin when not set
            assertThat(bounds.getVisualBounds()).isEqualTo(bounds.getMarginBounds());
        }

        @Test
        void testWithMarginProducesCorrectLayersAndNoTopMargin() {
            final double contentXSs = 1.0;
            final double contentYSs = 2.0;
            final double contentWidthSs = 6.0;
            final double contentHeightSs = 2.0;
            final double leftMarginSs = 0.5;
            final double bottomMarginSs = 0.75;
            final double rightMarginSs = 0.25;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var margin = new Margin(leftMarginSs, bottomMarginSs, rightMarginSs);
            var bounds = ElementBoundsSs.withMargin(content, margin);

            // padding == content (no padding layer)
            assertThat(bounds.getPaddingBounds()).isEqualTo(content);

            // margin: left and right expand, bottom expands, top NOT expanded (y stays at contentY)
            final double expectedMarginX = contentXSs - leftMarginSs;
            final double expectedMarginY = contentYSs; // no top margin
            final double expectedMarginW = contentWidthSs + leftMarginSs + rightMarginSs;
            final double expectedMarginH = contentHeightSs + bottomMarginSs; // only bottom
            assertThat(bounds.getMarginBounds()).isEqualTo(
                new Rectangle2D.Double(expectedMarginX, expectedMarginY, expectedMarginW, expectedMarginH));
        }

        @Test
        void testWithMarginOnlyProducesCorrectLayers() {
            final double contentXSs = 0.0;
            final double contentYSs = 0.0;
            final double contentWidthSs = 3.0;
            final double contentHeightSs = 1.0;
            final double marginXSs = -1.0;
            final double marginYSs = -0.5;
            final double marginWidthSs = 5.0;
            final double marginHeightSs = 2.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentWidthSs, contentHeightSs);
            var marginRect = new Rectangle2D.Double(marginXSs, marginYSs, marginWidthSs, marginHeightSs);
            var bounds = ElementBoundsSs.withMarginOnly(content, marginRect);

            // padding == content
            assertThat(bounds.getPaddingBounds()).isEqualTo(content);
            assertThat(bounds.getMarginBounds()).isEqualTo(marginRect);
        }

        @Test
        void testContentOnlyProducesAllLayersEqualToContent() {
            final double contentXSs = 5.0;
            final double contentYSs = 1.0;
            final double contentWidthSs = 2.0;
            final double contentHeightSs = 3.0;

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
            final double thisBottomMarginSs = 2.0;
            final double belowTopMarginSs = 1.0;

            var above = boundsWithBottomMarginSs(thisBottomMarginSs);
            var below = boundsWithTopMarginSs(belowTopMarginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(thisBottomMarginSs);
        }

        @Test
        void testBelowTopMarginWinsWhenLarger() {
            final double thisBottomMarginSs = 0.5;
            final double belowTopMarginSs = 1.5;

            var above = boundsWithBottomMarginSs(thisBottomMarginSs);
            var below = boundsWithTopMarginSs(belowTopMarginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(belowTopMarginSs);
        }

        @Test
        void testEqualMarginsProduceThatValue() {
            final double marginSs = 1.25;

            var above = boundsWithBottomMarginSs(marginSs);
            var below = boundsWithTopMarginSs(marginSs);

            assertThat(above.collapsedMarginWith(below)).isEqualTo(marginSs);
        }

        // Builds bounds where the margin extends `bottomMarginSs` below the content.
        // Content: (0, 0, 1, 1); margin bottom extends down by bottomMarginSs.
        private static ElementBoundsSs boundsWithBottomMarginSs(double bottomMarginSs) {
            final double xSs = 0.0;
            final double ySs = 0.0;
            final double sizeSs = 1.0;
            var content = new Rectangle2D.Double(xSs, ySs, sizeSs, sizeSs);
            var marginRect = new Rectangle2D.Double(xSs, ySs, sizeSs, sizeSs + bottomMarginSs);
            return ElementBoundsSs.withMarginOnly(content, marginRect);
        }

        // Builds bounds where the margin extends `topMarginSs` above the content.
        // Content: (0, topMarginSs, 1, 1); marginY = 0 so topMargin = topMarginSs.
        private static ElementBoundsSs boundsWithTopMarginSs(double topMarginSs) {
            final double xSs = 0.0;
            final double sizeSs = 1.0;
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
            final double paddingSs = 0.5;
            final double contentXSs = 2.0;
            final double contentYSs = 2.0;
            final double contentSizeSs = 2.0;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, 0.0);

            // Center of content is well inside padding
            final double centerX = contentXSs + contentSizeSs / 2;
            final double centerY = contentYSs + contentSizeSs / 2;
            assertThat(bounds.containsForHitTest(centerX, centerY)).isTrue();
        }

        @Test
        void testPointInMarginButOutsidePaddingReturnsFalse() {
            final double paddingSs = 0.25;
            final double marginSs = 1.0;
            final double contentXSs = 2.0;
            final double contentYSs = 2.0;
            final double contentSizeSs = 2.0;
            final double pastPaddingEdgeSs = 0.1;

            var content = new Rectangle2D.Double(contentXSs, contentYSs, contentSizeSs, contentSizeSs);
            var bounds = ElementBoundsSs.uniform(content, paddingSs, marginSs);

            // A point inside margin but outside padding: just beyond the padding edge
            final double justOutsidePaddingX = contentXSs - paddingSs - pastPaddingEdgeSs;
            final double insideY = contentYSs + contentSizeSs / 2;
            assertThat(bounds.containsForHitTest(justOutsidePaddingX, insideY)).isFalse();
        }

        @Test
        void testPointFullyOutsideReturnsFalse() {
            final double paddingSs = 0.5;
            final double contentXSs = 2.0;
            final double contentYSs = 2.0;
            final double contentSizeSs = 2.0;

            final double farLeftXSs = -100.0;

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
            final double sizeSSs = 1.0;
            final double marginSs = 1.0;

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + marginSs, 0.0, sizeSSs, sizeSSs);
            // A's margin extends right by marginSs; B's margin extends left by marginSs → they touch/overlap
            var boundsA = ElementBoundsSs.uniform(contentA, 0.0, marginSs);
            var boundsB = ElementBoundsSs.uniform(contentB, 0.0, marginSs);

            assertThat(boundsA.intersectsMargin(boundsB)).isTrue();
        }

        @Test
        void testIntersectsMarginReturnsFalseWhenMarginsDontOverlap() {
            final double sizeSSs = 1.0;
            final double marginSs = 0.1;
            final double gapSs = 1.0; // gap >> margin, so margins don't touch

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + gapSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, 0.0, marginSs);
            var boundsB = ElementBoundsSs.uniform(contentB, 0.0, marginSs);

            assertThat(boundsA.intersectsMargin(boundsB)).isFalse();
        }

        @Test
        void testIntersectsPaddingReturnsTrueWhenPaddingOverlaps() {
            final double sizeSSs = 2.0;
            final double paddingSs = 0.5;
            final double overlapNudgeSs = 0.1;

            // Place B at xSs = sizeSSs + paddingSs so padding edges touch (just barely overlapping)
            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + paddingSs - overlapNudgeSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, paddingSs, 0.0);
            var boundsB = ElementBoundsSs.uniform(contentB, paddingSs, 0.0);

            assertThat(boundsA.intersectsPadding(boundsB)).isTrue();
        }

        @Test
        void testIntersectsPaddingReturnsFalseWhenPaddingDontOverlap() {
            final double sizeSSs = 2.0;
            final double paddingSs = 0.25;
            final double gapSs = 2.0; // far apart

            var contentA = new Rectangle2D.Double(0.0, 0.0, sizeSSs, sizeSSs);
            var contentB = new Rectangle2D.Double(sizeSSs + gapSs, 0.0, sizeSSs, sizeSSs);
            var boundsA = ElementBoundsSs.uniform(contentA, paddingSs, 0.0);
            var boundsB = ElementBoundsSs.uniform(contentB, paddingSs, 0.0);

            assertThat(boundsA.intersectsPadding(boundsB)).isFalse();
        }
    }
}
