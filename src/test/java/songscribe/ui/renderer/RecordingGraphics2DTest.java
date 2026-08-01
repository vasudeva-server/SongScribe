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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;

class RecordingGraphics2DTest extends UnitTest {

    // Tolerance for floating-point bounds comparisons; the recording path only ever composes exact
    // scalings and translations, so any real discrepancy shows up far above float rounding noise.
    private static final double BOUNDS_EPSILON = 1e-6;

    private static final float SAMPLE_FLOAT_ARG = 3f;
    private static final double SAMPLE_DOUBLE_ARG = 3.0;
    private static final int SAMPLE_INT_ARG = 3;
    // Must exceed SAMPLE_INT_ARG + SAMPLE_INT_ARG so drawChars/drawBytes' (offset, length) pair,
    // both defaulted to SAMPLE_INT_ARG, stays within the sample char/byte array bounds.
    private static final int SAMPLE_ARRAY_LENGTH = 8;
    private static final int SAMPLE_POINT_COUNT = 3;
    private static final int SAMPLE_IMAGE_SIZE = 3;
    private static final String SAMPLE_STRING_ARG = "x";
    private static final String SAMPLE_CHAR_DATA = SAMPLE_STRING_ARG.repeat(SAMPLE_ARRAY_LENGTH);
    private static final FontRenderContext SAMPLE_FRC = new FontRenderContext(null, true, true);

    private static final int[] SAMPLE_POINTS_X = new int[SAMPLE_ARRAY_LENGTH];
    private static final int[] SAMPLE_POINTS_Y = new int[SAMPLE_ARRAY_LENGTH];

    static {
        for (var i = 0; i < SAMPLE_ARRAY_LENGTH; i++) {
            SAMPLE_POINTS_X[i] = i;
            SAMPLE_POINTS_Y[i] = i;
        }
    }

    private static final BufferedImage SAMPLE_IMAGE =
        new BufferedImage(SAMPLE_IMAGE_SIZE, SAMPLE_IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
    private static final Shape SAMPLE_SHAPE =
        new Rectangle2D.Double(0, 0, SAMPLE_INT_ARG, SAMPLE_INT_ARG);
    private static final GlyphVector SAMPLE_GLYPH_VECTOR =
        BravuraFont.font().createGlyphVector(SAMPLE_FRC, SAMPLE_STRING_ARG);

    /**
     * Every public method on {@link Graphics}/{@link Graphics2D} that a renderer could call but
     * which is not a drawing operation: transform/paint/font/clip/hint state, lifecycle
     * ({@code create}/{@code dispose}), and pure queries. T10 below asserts that every method NOT
     * on this list records ink when invoked — closing the design's one silent-failure mode.
     */
    private static final Set<String> NON_DRAWING_METHOD_KEYS = buildNonDrawingMethodKeys();

    private RecordingGraphics2D graphics;

    @BeforeEach
    void setUp() {
        graphics = new RecordingGraphics2D();
    }

    // ==========================================================================
    // T6 — glyph measurement
    // ==========================================================================

    @Test
    void testGlyphMeasuresToMetadataBBoxScaledByFontSize() {
        graphics.setFont(BravuraFont.font());
        graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 0f, 0f);

        var bbox = SMuFLMetadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);

        if (bbox == null) {
            throw new AssertionError("notehead metadata unexpectedly missing");
        }

        var bounds = graphics.displayList().inkBoundsSs();

        if (bounds == null) {
            throw new AssertionError("drawString recorded no ink");
        }

        assertThat(bounds.getX()).isCloseTo(bbox.left(), within(BOUNDS_EPSILON));
        assertThat(bounds.getY()).isCloseTo(bbox.top(), within(BOUNDS_EPSILON));
        assertThat(bounds.getWidth()).isCloseTo(bbox.width(), within(BOUNDS_EPSILON));
        assertThat(bounds.getHeight()).isCloseTo(bbox.height(), within(BOUNDS_EPSILON));
    }

    @Test
    void testGraceNoteGlyphMeasuresSmallerByGraceNoteScale() {
        var graceFont = BravuraFont.font().deriveFont(BravuraFont.SIZE_SS * ElementType.GRACE_NOTE_SCALE);
        graphics.setFont(graceFont);
        graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 0f, 0f);

        var bbox = SMuFLMetadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);

        if (bbox == null) {
            throw new AssertionError("notehead metadata unexpectedly missing");
        }

        var bounds = graphics.displayList().inkBoundsSs();

        if (bounds == null) {
            throw new AssertionError("drawString recorded no ink");
        }

        assertThat(bounds.getWidth())
            .isCloseTo(bbox.width() * ElementType.GRACE_NOTE_SCALE, within(BOUNDS_EPSILON));
        assertThat(bounds.getHeight())
            .isCloseTo(bbox.height() * ElementType.GRACE_NOTE_SCALE, within(BOUNDS_EPSILON));
    }

    // ==========================================================================
    // T7 — missing-metadata fallback
    // ==========================================================================

    @Test
    void testGlyphWithoutMetadataFallsBackToVisualBoundsInsteadOfThrowing() {
        try (var metadataMock = mockStatic(SMuFLMetadata.class, CALLS_REAL_METHODS)) {
            metadataMock.when(() -> SMuFLMetadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK)).thenReturn(null);

            graphics.setFont(BravuraFont.font());
            graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 0f, 0f);

            var drawables = graphics.displayList().drawables();
            assertThat(drawables).hasSize(1);

            var glyphInk = (DisplayList.GlyphInk) drawables.getFirst();
            var expectedBounds = glyphInk.glyphVector().getVisualBounds();

            assertThat(glyphInk.boundsSs()).isEqualTo(expectedBounds);
        }
    }

    @Test
    void testUnrecognizedStringFallsBackToVisualBoundsAndDoesNotThrow() {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        assertThatCode(() -> graphics.drawString("not a smufl glyph", 0f, 0f)).doesNotThrowAnyException();

        var bounds = graphics.displayList().inkBoundsSs();
        assertThat(bounds).isNotNull();
    }

    // ==========================================================================
    // T8 — shape measurement
    // ==========================================================================

    @Test
    void testFilledShapeMeasuresToGetBounds2D() {
        var rect = new Rectangle2D.Double(2, 3, 10, 20);
        graphics.fill(rect);

        var bounds = graphics.displayList().inkBoundsSs();

        assertThat(bounds).isEqualTo(rect.getBounds2D());
    }

    @Test
    void testStrokedShapeMeasuresToStrokedOutlineIncludingCapRoundBulge() {
        var strokeWidthSs = 4.0;
        var lineLengthSs = 10.0;
        graphics.setStroke(new BasicStroke((float) strokeWidthSs, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        var line = new Line2D.Double(0, 0, lineLengthSs, 0);
        graphics.draw(line);

        var expectedBounds = graphics.getStroke().createStrokedShape(line).getBounds2D();
        var bounds = graphics.displayList().inkBoundsSs();

        if (bounds == null) {
            throw new AssertionError("draw recorded no ink");
        }

        assertThat(bounds).isEqualTo(expectedBounds);

        // The CAP_ROUND bulge extends half the stroke width past each endpoint.
        assertThat(bounds.getMinX()).isCloseTo(-strokeWidthSs / 2, within(BOUNDS_EPSILON));
        assertThat(bounds.getMaxX()).isCloseTo(lineLengthSs + strokeWidthSs / 2, within(BOUNDS_EPSILON));
    }

    // ==========================================================================
    // T9 — transform tracking and create()
    // ==========================================================================

    @Test
    void testDrawRoundedLineTranslateRotateSetTransformRoundTripPlacesInkCorrectly() {
        var thicknessSs = 2.0;
        var lengthSs = 10.0;

        // A vertical line exercises the translate + rotate(90deg) + fill + setTransform-restore
        // sequence GraphicUtils.drawRoundedLine performs around a single fill call.
        GraphicUtils.drawRoundedLine(graphics, 0, 0, 0, lengthSs, thicknessSs);

        var bounds = graphics.displayList().inkBoundsSs();

        if (bounds == null) {
            throw new AssertionError("drawRoundedLine recorded no ink");
        }

        assertThat(bounds.getMinX()).isCloseTo(-thicknessSs / 2, within(BOUNDS_EPSILON));
        assertThat(bounds.getMaxX()).isCloseTo(thicknessSs / 2, within(BOUNDS_EPSILON));
        assertThat(bounds.getMinY()).isCloseTo(0, within(BOUNDS_EPSILON));
        assertThat(bounds.getMaxY()).isCloseTo(lengthSs, within(BOUNDS_EPSILON));

        // drawRoundedLine restores the transform it found, so recording state stays clean for
        // whatever the renderer draws next.
        assertThat(graphics.getTransform()).isEqualTo(new AffineTransform());
    }

    @Test
    void testCreateChildSharesAccumulatorWithParent() {
        var child = (Graphics2D) graphics.create();
        child.fill(SAMPLE_SHAPE);

        assertThat(graphics.displayList().drawables()).hasSize(1);
    }

    // ==========================================================================
    // Glyph vector cache — keyed by (glyph, fontSize)
    // ==========================================================================

    @Test
    void testGlyphVectorCacheReusesVectorForSameGlyphAndFontSizeButNotAcrossSizes() {
        graphics.setFont(BravuraFont.font());
        graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 0f, 0f);
        graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 5f, 5f);

        var drawables = graphics.displayList().drawables();
        var firstVector = ((DisplayList.GlyphInk) drawables.get(0)).glyphVector();
        var secondVector = ((DisplayList.GlyphInk) drawables.get(1)).glyphVector();

        assertThat(secondVector).isSameAs(firstVector);

        graphics.reset();
        graphics.setFont(BravuraFont.font().deriveFont(BravuraFont.SIZE_SS * ElementType.GRACE_NOTE_SCALE));
        graphics.drawString(SMuFLGlyph.NOTEHEAD_BLACK.asString(), 0f, 0f);

        var thirdVector = ((DisplayList.GlyphInk) graphics.displayList().drawables().getFirst()).glyphVector();

        assertThat(thirdVector).isNotSameAs(firstVector);
    }

    // ==========================================================================
    // T10 — recording completeness (mandatory)
    // ==========================================================================

    /**
     * Reflectively enumerates every public method {@link Graphics}/{@link Graphics2D} declare and
     * asserts each one either records ink or is on {@link #NON_DRAWING_METHOD_KEYS}. A method that
     * is neither is exactly the failure mode {@link RecordingGraphics2D}'s class Javadoc warns
     * about: it paints into the 1x1 scratch image, contributes nothing to the display list, and
     * silently clips real artwork.
     */
    @Test
    void testEveryGraphicsMethodRecordsOrIsExplicitlyNonDrawing() {
        var failures = new ArrayList<String>();

        for (var method : Graphics2D.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isSynthetic()) {
                continue;
            }

            var methodKey = methodKey(method);

            if (NON_DRAWING_METHOD_KEYS.contains(methodKey)) {
                continue;
            }

            graphics.reset();
            var sizeBefore = graphics.displayList().drawables().size();

            try {
                invokeWithSampleArgs(method, graphics);
            } catch (ReflectiveOperationException e) {
                failures.add(methodKey + " threw " + rootCause(e));
                continue;
            }

            var sizeAfter = graphics.displayList().drawables().size();

            if (sizeAfter <= sizeBefore) {
                failures.add(methodKey + " recorded no ink and is not on the non-drawing allowlist");
            }
        }

        assertThat(failures).isEmpty();
    }

    private static Throwable rootCause(ReflectiveOperationException e) {
        var cause = e.getCause();
        return cause != null ? cause : e;
    }

    private static String methodKey(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static void invokeWithSampleArgs(Method method, Object target) throws ReflectiveOperationException {
        var paramTypes = method.getParameterTypes();
        @Nullable Object[] args = new @Nullable Object[paramTypes.length];

        for (var i = 0; i < paramTypes.length; i++) {
            args[i] = sampleArg(paramTypes[i]);
        }

        method.invoke(target, args);
    }

    @Nullable
    private static Object sampleArg(Class<?> type) {
        if (type == int.class) {
            return SAMPLE_INT_ARG;
        }

        if (type == float.class) {
            return SAMPLE_FLOAT_ARG;
        }

        if (type == double.class) {
            return SAMPLE_DOUBLE_ARG;
        }

        if (type == boolean.class) {
            return Boolean.TRUE;
        }

        if (type == int[].class) {
            return SAMPLE_POINTS_X;
        }

        if (type == char[].class) {
            return SAMPLE_CHAR_DATA.toCharArray();
        }

        if (type == byte[].class) {
            return SAMPLE_CHAR_DATA.getBytes(StandardCharsets.US_ASCII);
        }

        if (type == String.class) {
            return SAMPLE_STRING_ARG;
        }

        if (type == AttributedCharacterIterator.class) {
            return new AttributedString(SAMPLE_STRING_ARG).getIterator();
        }

        if (type == Polygon.class) {
            return new Polygon(SAMPLE_POINTS_X, SAMPLE_POINTS_Y, SAMPLE_POINT_COUNT);
        }

        if (type == Shape.class) {
            return SAMPLE_SHAPE;
        }

        if (type == GlyphVector.class) {
            return SAMPLE_GLYPH_VECTOR;
        }

        if (type == RenderableImage.class) {
            return mock(RenderableImage.class);
        }

        if (type == Image.class || type == BufferedImage.class || type == RenderedImage.class) {
            return SAMPLE_IMAGE;
        }

        if (type == Color.class) {
            return Color.RED;
        }

        if (type == ImageObserver.class || type == BufferedImageOp.class) {
            return null;
        }

        if (type == AffineTransform.class) {
            return new AffineTransform();
        }

        if (type == RenderingHints.Key.class) {
            return RenderingHints.KEY_ANTIALIASING;
        }

        if (type == Map.class) {
            return new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        if (type == Composite.class) {
            return AlphaComposite.SrcOver;
        }

        if (type == Paint.class) {
            return Color.BLUE;
        }

        if (type == Stroke.class) {
            return new BasicStroke(SAMPLE_FLOAT_ARG);
        }

        if (type == Font.class) {
            return BravuraFont.font();
        }

        if (type == Rectangle.class) {
            return new Rectangle(0, 0, SAMPLE_INT_ARG, SAMPLE_INT_ARG);
        }

        if (type == Object.class) {
            return RenderingHints.VALUE_ANTIALIAS_ON;
        }

        throw new IllegalArgumentException("No sample value registered for " + type);
    }

    private static Set<String> buildNonDrawingMethodKeys() {
        try {
            var nonDrawingMethods = List.of(
                Graphics2D.class.getMethod("create"),
                Graphics.class.getMethod("create", int.class, int.class, int.class, int.class),
                Graphics2D.class.getMethod("dispose"),
                Graphics.class.getMethod("finalize"),
                Graphics.class.getMethod("toString"),
                Graphics2D.class.getMethod("translate", int.class, int.class),
                Graphics2D.class.getMethod("translate", double.class, double.class),
                Graphics2D.class.getMethod("rotate", double.class),
                Graphics2D.class.getMethod("rotate", double.class, double.class, double.class),
                Graphics2D.class.getMethod("scale", double.class, double.class),
                Graphics2D.class.getMethod("shear", double.class, double.class),
                Graphics2D.class.getMethod("transform", AffineTransform.class),
                Graphics2D.class.getMethod("setTransform", AffineTransform.class),
                Graphics2D.class.getMethod("getTransform"),
                Graphics2D.class.getMethod("getColor"),
                Graphics2D.class.getMethod("setColor", Color.class),
                Graphics2D.class.getMethod("setPaintMode"),
                Graphics2D.class.getMethod("setXORMode", Color.class),
                Graphics2D.class.getMethod("getPaint"),
                Graphics2D.class.getMethod("setPaint", Paint.class),
                Graphics2D.class.getMethod("getComposite"),
                Graphics2D.class.getMethod("setComposite", Composite.class),
                Graphics2D.class.getMethod("getBackground"),
                Graphics2D.class.getMethod("setBackground", Color.class),
                Graphics2D.class.getMethod("getStroke"),
                Graphics2D.class.getMethod("setStroke", Stroke.class),
                Graphics2D.class.getMethod("getFont"),
                Graphics2D.class.getMethod("setFont", Font.class),
                Graphics.class.getMethod("getFontMetrics"),
                Graphics2D.class.getMethod("getFontMetrics", Font.class),
                Graphics2D.class.getMethod("getFontRenderContext"),
                Graphics2D.class.getMethod("getClip"),
                Graphics2D.class.getMethod("setClip", Shape.class),
                Graphics2D.class.getMethod("setClip", int.class, int.class, int.class, int.class),
                Graphics2D.class.getMethod("clipRect", int.class, int.class, int.class, int.class),
                Graphics2D.class.getMethod("clip", Shape.class),
                Graphics2D.class.getMethod("getClipBounds"),
                Graphics.class.getMethod("getClipBounds", Rectangle.class),
                Graphics.class.getMethod("getClipRect"),
                Graphics.class.getMethod("hitClip", int.class, int.class, int.class, int.class),
                Graphics2D.class.getMethod("getRenderingHint", RenderingHints.Key.class),
                Graphics2D.class.getMethod("setRenderingHint", RenderingHints.Key.class, Object.class),
                Graphics2D.class.getMethod("getRenderingHints"),
                Graphics2D.class.getMethod("setRenderingHints", Map.class),
                Graphics2D.class.getMethod("addRenderingHints", Map.class),
                Graphics2D.class.getMethod("hit", Rectangle.class, Shape.class, boolean.class),
                Graphics2D.class.getMethod("getDeviceConfiguration"),
                Graphics2D.class.getMethod(
                    "copyArea", int.class, int.class, int.class, int.class, int.class, int.class)
            );

            return nonDrawingMethods.stream()
                .map(RecordingGraphics2DTest::methodKey)
                .collect(Collectors.toSet());
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
