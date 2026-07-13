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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link ScoreComponent}:
 * {@link ScoreComponent#resolveContentX} and {@link ScoreComponent#setMargin}.
 * <p>
 * Uses {@link TitleComponent} as the minimal concrete subclass (its constructor
 * calls no rendering code beyond {@code setMarginBottom}).
 */
class ScoreComponentTest extends UnitTest {

    /**
     * Creates a real {@code Graphics2D} backed by an off-screen image.
     * Caller is responsible for disposing it.
     */
    private static java.awt.Graphics2D createGraphics() {
        var img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    // -------------------------------------------------------------------------
    // resolveContentX (row 24)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ResolveContentX {

        private TitleComponent component;

        @BeforeEach
        void setUp() {
            component = new TitleComponent();
        }

        /**
         * When {@code contentXPx >= 0} (set via {@link ScoreComponent#setContentX}),
         * {@code resolveContentX} returns that value unchanged without looking up
         * the song or measuring text.
         */
        @Test
        void testExplicitContentXReturnedDirectly() {
            final float explicitX = 42.5f;
            component.setContentX(explicitX);
            var g2 = createGraphics();

            try {
                var result = component.resolveContentX("any text", g2);
                assertThat(result)
                    .as("explicit contentX >= 0 should be returned as-is")
                    .isEqualTo(explicitX);
            } finally {
                g2.dispose();
            }
        }

        /**
         * When {@code contentXPx < 0} (default), {@code resolveContentX} computes
         * a centered X as {@code (lineWidthPx - textWidth) / 2}.
         * <p>
         * Tested with an empty-string text (width == 0) so the expected value is
         * exactly {@code lineWidthPx / 2} without font-metric dependencies.
         */
        @Test
        void testDefaultCenteringFormula() {
            // contentXPx defaults to -1 (centering mode)
            var song = new Song();
            component.setSong(song);

            var g2 = createGraphics();

            try {
                // Empty text → textWidth == 0 → centered X = lineWidthPx / 2
                var result = component.resolveContentX("", g2);
                var lineWidthPx = song.getLineWidthPx();
                var expected = (float) (lineWidthPx / 2.0);
                assertThat(result)
                    .as("centering formula: (lineWidthPx - 0) / 2 for empty text")
                    .isEqualTo(expected);
            } finally {
                g2.dispose();
            }
        }

        /**
         * The boundary between explicit and centering mode is at contentXPx == 0:
         * {@code contentXPx == 0} is treated as an explicit left-edge position and
         * returned as-is (0.0f), not as centering.
         */
        @Test
        void testZeroContentXReturnedDirectly() {
            component.setContentX(0f);
            var g2 = createGraphics();

            try {
                var result = component.resolveContentX("some text", g2);
                assertThat(result)
                    .as("contentX == 0 is a valid explicit position, not centering")
                    .isEqualTo(0f);
            } finally {
                g2.dispose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // setMargin — both overloads (row 25)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetMargin {

        private TitleComponent component;

        @BeforeEach
        void setUp() {
            component = new TitleComponent();
        }

        /**
         * {@link ScoreComponent#setMargin(int)} sets all four margin fields to the
         * same value.
         */
        @Test
        void testUniformMarginSetsAllSides() {
            final int margin = 10;
            component.setMargin(margin);

            assertThat(component.getMarginTop())
                .as("marginTop after uniform setMargin")
                .isEqualTo(margin);
            assertThat(component.getMarginRight())
                .as("marginRight after uniform setMargin")
                .isEqualTo(margin);
            assertThat(component.getMarginBottom())
                .as("marginBottom after uniform setMargin")
                .isEqualTo(margin);
            assertThat(component.getMarginLeft())
                .as("marginLeft after uniform setMargin")
                .isEqualTo(margin);
        }

        /**
         * {@link ScoreComponent#setMargin(int, int, int, int)} sets each margin
         * independently (CSS-style top/right/bottom/left).
         */
        @Test
        void testCssStyleMarginSetsEachSideIndependently() {
            final int top = 1;
            final int right = 2;
            final int bottom = 3;
            final int left = 4;
            component.setMargin(top, right, bottom, left);

            assertThat(component.getMarginTop())
                .as("marginTop after CSS-style setMargin")
                .isEqualTo(top);
            assertThat(component.getMarginRight())
                .as("marginRight after CSS-style setMargin")
                .isEqualTo(right);
            assertThat(component.getMarginBottom())
                .as("marginBottom after CSS-style setMargin")
                .isEqualTo(bottom);
            assertThat(component.getMarginLeft())
                .as("marginLeft after CSS-style setMargin")
                .isEqualTo(left);
        }

        /**
         * The two overloads are independent: calling the CSS-style overload after
         * the uniform one does not leave any side at the previous uniform value.
         */
        @Test
        void testCssStyleOverridesPreviousUniformMargin() {
            component.setMargin(99);   // uniform
            component.setMargin(1, 2, 3, 4);  // CSS-style override

            assertThat(component.getMarginTop()).isEqualTo(1);
            assertThat(component.getMarginRight()).isEqualTo(2);
            assertThat(component.getMarginBottom()).isEqualTo(3);
            assertThat(component.getMarginLeft()).isEqualTo(4);
        }
    }

    // -------------------------------------------------------------------------
    // getViewScale — read-on-demand fallback (Phase 6, gap 1A)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetViewScale {

        private TitleComponent component;

        @BeforeEach
        void setUp() {
            component = new TitleComponent();
        }

        /**
         * A component with no owning {@link ScoreView} (a detached dialog preview)
         * must fall back to {@link ViewScale#IDENTITY} — factor 1.0 — regardless of
         * any other view's current zoom, so it always measures at natural size.
         */
        @Test
        void testDetachedComponentReturnsIdentity() {
            assertThat(component.getViewScale()).isSameAs(ViewScale.IDENTITY);
            assertThat(component.getViewScale().factor()).isEqualTo(1.0);
        }

        /**
         * A component attached to a {@link ScoreView} must read that view's own
         * {@link ViewScale} on demand, not a stale snapshot taken at attach time.
         */
        @Test
        void testAttachedComponentReturnsOwningViewsViewScale() {
            var scoreView = new ScoreView(null);
            scoreView.getViewScale().setZoomPercent(200);
            component.setScoreView(scoreView);

            assertThat(component.getViewScale()).isSameAs(scoreView.getViewScale());
            assertThat(component.getViewScale().getZoomPercent()).isEqualTo(200);
        }
    }
}
