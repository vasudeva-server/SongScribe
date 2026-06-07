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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class GraphicsStateTest extends UnitTest {

    private java.awt.Graphics2D g2;

    @BeforeEach
    void setUp() {
        var img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        g2 = img.createGraphics();
    }

    // save(COLOR) + close() restores the original color after modification
    @Test
    void testSaveColorRestoresOnClose() {
        var original = Color.BLACK;
        g2.setColor(original);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(Color.RED);
            assertThat(g2.getColor()).isEqualTo(Color.RED);
        }

        assertThat(g2.getColor()).isEqualTo(original);
    }

    // save(STROKE) + close() restores the original stroke after modification
    @Test
    void testSaveStrokeRestoresOnClose() {
        var original = new BasicStroke(1f);
        g2.setStroke(original);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.STROKE)) {
            g2.setStroke(new BasicStroke(5f));
        }

        assertThat(g2.getStroke()).isEqualTo(original);
    }

    // save(FONT) + close() restores the original font after modification
    @Test
    void testSaveFontRestoresOnClose() {
        var original = g2.getFont();

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.FONT)) {
            g2.setFont(original.deriveFont(Font.BOLD, 24f));
        }

        assertThat(g2.getFont()).isEqualTo(original);
    }

    // save(TRANSFORM) + close() restores the original transform after modification
    @Test
    void testSaveTransformRestoresOnClose() {
        var original = g2.getTransform();

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            g2.scale(2.0, 2.0);
            assertThat(g2.getTransform()).isNotEqualTo(original);
        }

        assertThat(g2.getTransform()).isEqualTo(original);
    }

    // save(CLIP) + close() restores null clip (unconditional restore, per the asymmetric contract)
    @Test
    void testSaveClipRestoresNullClipUnconditionally() {
        // BufferedImage Graphics2D starts with no clip region
        assertThat(g2.getClip()).isNull();

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.CLIP)) {
            g2.setClip(new Rectangle2D.Double(0, 0, 50, 50));
            assertThat(g2.getClip()).isNotNull();
        }

        // clip is restored unconditionally (null is a valid "no clipping" value)
        assertThat(g2.getClip()).isNull();
    }

    // save(ANTIALIASING) + close() restores the original rendering hint
    @Test
    void testSaveAntialiasingRestoresOnClose() {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.ANTIALIASING)) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        assertThat(g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING))
            .isEqualTo(RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // Only saved properties are restored; unsaved ones retain their modified value
    @Test
    void testOnlySavedPropertyIsRestored() {
        var originalColor = Color.BLACK;
        g2.setColor(originalColor);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(Color.RED);
            // Also modify the transform — it was not saved
            g2.scale(3.0, 3.0);
        }

        // color is restored
        assertThat(g2.getColor()).isEqualTo(originalColor);
        // transform is NOT restored (was not in the saved set)
        assertThat(g2.getTransform()).isNotEqualTo(new AffineTransform());
    }

    // save() with multiple properties restores all of them
    @Test
    void testSaveMultiplePropertiesRestoresAll() {
        var originalColor = Color.BLACK;
        var originalStroke = new BasicStroke(1f);
        g2.setColor(originalColor);
        g2.setStroke(originalStroke);

        try (var ignored = GraphicsState.save(g2,
            GraphicsState.Property.COLOR,
            GraphicsState.Property.STROKE)) {
            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(8f));
        }

        assertThat(g2.getColor()).isEqualTo(originalColor);
        assertThat(g2.getStroke()).isEqualTo(originalStroke);
    }
}
