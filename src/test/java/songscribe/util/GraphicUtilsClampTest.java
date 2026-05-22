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

package songscribe.util;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GraphicUtilsClampTest extends UnitTest {

    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    private MockedStatic<GraphicsEnvironment> geMock;

    @BeforeAll
    static void loadGraphicUtils() {
        // Force GraphicUtils static initialization here, after UnitTest's @BeforeAll has
        // installed FlatLaf (which initializes the AWT toolkit). If the class loads for
        // the first time inside @BeforeEach — while the GraphicsEnvironment mock is
        // active — the static initializer runs against the mock's incomplete setup and
        // throws NPE in createCompatibleImage → createGraphics.
        var _ = GraphicUtils.getDpi();
    }

    @BeforeEach
    void setUp() {
        geMock = mockStatic(GraphicsEnvironment.class);

        var mockEnv = mock(GraphicsEnvironment.class);
        geMock.when(GraphicsEnvironment::getLocalGraphicsEnvironment).thenReturn(mockEnv);

        var mockDevice = mock(GraphicsDevice.class);
        var mockConfig = mock(GraphicsConfiguration.class);
        when(mockEnv.getDefaultScreenDevice()).thenReturn(mockDevice);
        when(mockDevice.getDefaultConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getBounds()).thenReturn(new Rectangle(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
        when(mockConfig.getDefaultTransform()).thenReturn(new AffineTransform());
        when(mockEnv.getScreenDevices()).thenReturn(new GraphicsDevice[]{mockDevice});
    }

    @AfterEach
    void tearDown() {
        geMock.close();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ClampRectangle {

        @Test
        void testRectangleWithinScreenReturnedUnchanged() {
            var bounds = new Rectangle(100, 100, 400, 300);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result).isEqualTo(bounds);
        }

        @Test
        void testOversizeWidthCappedToScreen() {
            var bounds = new Rectangle(100, 100, 2500, 300);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result.width).isEqualTo(SCREEN_WIDTH);
            assertThat(result.height).isEqualTo(300);
        }

        @Test
        void testOversizeHeightCappedToScreen() {
            var bounds = new Rectangle(100, 100, 400, 1500);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result.width).isEqualTo(400);
            assertThat(result.height).isEqualTo(SCREEN_HEIGHT);
        }

        @Test
        void testOffScreenPositionShiftedToFit() {
            var bounds = new Rectangle(1800, 900, 400, 300);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result.width).isEqualTo(400);
            assertThat(result.height).isEqualTo(300);
            assertThat(result.x).isEqualTo(SCREEN_WIDTH - 400);
            assertThat(result.y).isEqualTo(SCREEN_HEIGHT - 300);
        }

        @Test
        void testCombinedOversizeAndOffScreen() {
            var bounds = new Rectangle(1800, 900, 2500, 1500);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result.width).isEqualTo(SCREEN_WIDTH);
            assertThat(result.height).isEqualTo(SCREEN_HEIGHT);
            assertThat(result.x).isEqualTo(0);
            assertThat(result.y).isEqualTo(0);
        }

        @Test
        void testNegativePositionClampedToScreenOrigin() {
            var bounds = new Rectangle(-50, -30, 400, 300);
            var result = GraphicUtils.clampToScreen(bounds);
            assertThat(result.x).isEqualTo(0);
            assertThat(result.y).isEqualTo(0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ClampPointDimension {

        @Test
        void testPointWithinScreenReturnedUnchanged() {
            var point = new Point(100, 100);
            var size = new Dimension(400, 300);
            var result = GraphicUtils.clampToScreen(point, size);
            assertThat(result).isEqualTo(point);
        }

        @Test
        void testOffScreenPointShiftedToFit() {
            var point = new Point(1800, 900);
            var size = new Dimension(400, 300);
            var result = GraphicUtils.clampToScreen(point, size);
            assertThat(result.x).isEqualTo(SCREEN_WIDTH - 400);
            assertThat(result.y).isEqualTo(SCREEN_HEIGHT - 300);
        }
    }
}
