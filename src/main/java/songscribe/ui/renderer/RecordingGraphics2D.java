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

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.font.TextMeasurement;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;

/**
 * A {@link Graphics2D} that records what a renderer draws instead of painting it, producing a
 * {@link DisplayList} whose union is the renderer's exact ink bounds.
 * <p>
 * <b>State is not shadowed.</b> Every state method goes straight to a delegate, and every drawing
 * method reads {@link #getTransform()} back from it at draw time. Caching the transform would
 * misplace ledger lines and the insertion marker, because {@code GraphicUtils.drawRoundedLine}
 * rotates the transform around a single {@code fill} call.
 * <p>
 * <b>No drawing method may delegate.</b> A drawing method wired to the delegate would contribute
 * nothing to the display list and clip the artwork with no error. Every drawing method funnels
 * through {@link #record}.
 * <p>
 * <b>Record and replay are only equivalent while renderers are transform-independent.</b> A
 * renderer may use the transform to position ink, but must never branch on
 * {@code getTransform()}; otherwise the recorded ink differs from the replayed ink.
 * <p>
 * Instances are reusable: {@link #reset()} clears the accumulated ink and restores the identity
 * transform.
 */
public final class RecordingGraphics2D extends Graphics2D {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingGraphics2D.class);

    /**
     * Keeping the delegate's image 1×1 makes any painting that escapes into it a no-op.
     */
    private static final int SCRATCH_SIZE = 1;

    /**
     * The key is total — a glyph vector depends on the glyph and the size and nothing else — so
     * nothing is ever invalidated. Deliberately not a cache of composed display lists keyed by
     * element configuration: a forgotten field in that key paints a stale glyph.
     */
    private static final Map<GlyphKey, GlyphVector> GLYPH_VECTOR_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, SMuFLGlyph> GLYPHS_BY_STRING = glyphsByString();

    private static final Set<String> LOGGED_UNMEASURABLE = ConcurrentHashMap.newKeySet();

    private final BufferedImage scratchImage;
    private final Graphics2D delegate;
    private final List<DisplayList.Drawable> drawables;

    public RecordingGraphics2D() {
        scratchImage = new BufferedImage(SCRATCH_SIZE, SCRATCH_SIZE, BufferedImage.TYPE_INT_ARGB);
        delegate = scratchImage.createGraphics();
        drawables = new ArrayList<>();
    }

    /**
     * Shares the accumulator with its parent, so ink drawn through a {@code create()} child is not
     * discarded.
     */
    private RecordingGraphics2D(
        BufferedImage scratchImage,
        Graphics2D delegate,
        List<DisplayList.Drawable> drawables
    ) {
        this.scratchImage = scratchImage;
        this.delegate = delegate;
        this.drawables = drawables;
    }

    private record GlyphKey(SMuFLGlyph glyph, float fontSize) {
    }

    private static Map<String, SMuFLGlyph> glyphsByString() {
        var byString = new HashMap<String, SMuFLGlyph>();

        for (var glyph : SMuFLGlyph.values()) {
            byString.putIfAbsent(glyph.asString(), glyph);
        }

        return Map.copyOf(byString);
    }

    public void reset() {
        drawables.clear();
        delegate.setTransform(new AffineTransform());
        delegate.setClip(null);
    }

    /** The ink recorded so far, detached from this instance so a later {@link #reset()} cannot alter it. */
    public DisplayList displayList() {
        return new DisplayList(List.copyOf(drawables));
    }

    private void record(DisplayList.Drawable drawable) {
        drawables.add(drawable);
    }

    private void recordFill(Shape shape) {
        record(new DisplayList.ShapeInk(getTransform().createTransformedShape(shape)));
    }

    private void recordDraw(Shape shape) {
        recordFill(getStroke().createStrokedShape(shape));
    }

    private void recordGlyph(GlyphVector glyphVector, Rectangle2D glyphBoundsSs, float x, float y) {
        var placement = getTransform();
        placement.translate(x, y);
        record(new DisplayList.GlyphInk(
            glyphVector,
            placement,
            placement.createTransformedShape(glyphBoundsSs).getBounds2D()));
    }

    /**
     * Measures from SMuFL metadata, not the outline: the layout treats the metadata as
     * authoritative, so {@code getVisualBounds} would disagree with the layout the overlay sits in.
     */
    private static Rectangle2D metadataBoundsSs(SMuFLGlyph glyph, float fontSize) {
        var bbox = SMuFLMetadata.bboxSs(glyph);
        var scale = fontSize / BravuraFont.SIZE_SS;

        return new Rectangle2D.Double(
            bbox.leftSs() * scale,
            bbox.topSs() * scale,
            bbox.widthSs() * scale,
            bbox.heightSs() * scale);
    }

    private static GlyphVector glyphVector(SMuFLGlyph glyph, Font font) {
        return GLYPH_VECTOR_CACHE.computeIfAbsent(
            new GlyphKey(glyph, font.getSize2D()),
            key -> recordableGlyphVector(key.glyph().asString(), font));
    }

    /**
     * Shapes through {@link TextMeasurement}, because the recorded vector is replayed and so
     * carries its context's positioning decisions. Fractional metrics are load-bearing: with them
     * off, glyph positions round to whole font units, and the replay zoom magnifies that into
     * stair-stepping. {@link TextMeasurement} and {@code LineOverlayComponent.paintComponent} both
     * apply {@link GraphicUtils#setRenderingHints}, so recording and replay agree.
     */
    private static GlyphVector recordableGlyphVector(String str, Font font) {
        return TextMeasurement.glyphVector(str, font);
    }

    /**
     * Skipping would clip visible artwork with no error, and throwing is not an option: this runs
     * on the EDT during mouse tracking.
     */
    private static Rectangle2D fallbackBoundsSs(String str, GlyphVector glyphVector) {
        if (LOGGED_UNMEASURABLE.add(str)) {
            LOG.warn("No SMuFL metadata for drawn string '{}'; measuring its outline instead", str);
        }

        return glyphVector.getVisualBounds();
    }

    @Override
    public void drawString(String str, float x, float y) {
        if (str.isEmpty()) {
            return;
        }

        var font = getFont();
        var glyph = GLYPHS_BY_STRING.get(str);

        if (glyph != null) {
            recordGlyph(
                glyphVector(glyph, font),
                metadataBoundsSs(glyph, font.getSize2D()),
                x,
                y);
            return;
        }

        var glyphVector = recordableGlyphVector(str, font);
        recordGlyph(glyphVector, fallbackBoundsSs(str, glyphVector), x, y);
    }

    @Override
    public void drawString(String str, int x, int y) {
        drawString(str, (float) x, (float) y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        drawString(iteratorText(iterator), x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        drawString(iteratorText(iterator), (float) x, (float) y);
    }

    private static String iteratorText(AttributedCharacterIterator iterator) {
        var text = new StringBuilder();

        for (var c = iterator.first(); c != CharacterIterator.DONE; c = iterator.next()) {
            text.append(c);
        }

        return text.toString();
    }

    @Override
    public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
        recordGlyph(glyphVector, glyphVector.getVisualBounds(), x, y);
    }

    @Override
    public void fill(Shape shape) {
        recordFill(shape);
    }

    @Override
    public void draw(Shape shape) {
        recordDraw(shape);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        recordDraw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        recordDraw(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        recordFill(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        // Recorded rather than ignored: a display list may only over-report, never under-report.
        recordFill(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        recordDraw(new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        recordFill(new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        recordDraw(new Ellipse2D.Double(x, y, width, height));
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        recordFill(new Ellipse2D.Double(x, y, width, height));
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        recordDraw(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.OPEN));
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        recordFill(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.PIE));
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int pointCount) {
        recordDraw(polyline(xPoints, yPoints, pointCount, false));
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        recordDraw(polyline(xPoints, yPoints, pointCount, true));
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        recordFill(polyline(xPoints, yPoints, pointCount, true));
    }

    private static Path2D polyline(int[] xPoints, int[] yPoints, int pointCount, boolean close) {
        var path = new Path2D.Double();

        for (var i = 0; i < pointCount; i++) {
            if (i == 0) {
                path.moveTo(xPoints[0], yPoints[0]);
            } else {
                path.lineTo(xPoints[i], yPoints[i]);
            }
        }

        if (close && pointCount > 0) {
            path.closePath();
        }

        return path;
    }

    @Override
    public boolean drawImage(@Nullable Image image, int x, int y, @Nullable ImageObserver observer) {
        return recordImage(image, x, y, observer);
    }

    @Override
    public boolean drawImage(
        @Nullable Image image, int x, int y, @Nullable Color background, @Nullable ImageObserver observer
    ) {
        return recordImage(image, x, y, observer);
    }

    @Override
    public boolean drawImage(
        @Nullable Image image, int x, int y, int width, int height, @Nullable ImageObserver observer
    ) {
        recordFill(new Rectangle2D.Double(x, y, width, height));
        return true;
    }

    @Override
    public boolean drawImage(
        @Nullable Image image,
        int x,
        int y,
        int width,
        int height,
        @Nullable Color background,
        @Nullable ImageObserver observer
    ) {
        recordFill(new Rectangle2D.Double(x, y, width, height));
        return true;
    }

    @Override
    public boolean drawImage(
        @Nullable Image image,
        int dx1,
        int dy1,
        int dx2,
        int dy2,
        int sx1,
        int sy1,
        int sx2,
        int sy2,
        @Nullable ImageObserver observer
    ) {
        recordFill(destinationRect(dx1, dy1, dx2, dy2));
        return true;
    }

    @Override
    public boolean drawImage(
        @Nullable Image image,
        int dx1,
        int dy1,
        int dx2,
        int dy2,
        int sx1,
        int sy1,
        int sx2,
        int sy2,
        @Nullable Color background,
        @Nullable ImageObserver observer
    ) {
        recordFill(destinationRect(dx1, dy1, dx2, dy2));
        return true;
    }

    @Override
    public boolean drawImage(
        @Nullable Image image, @Nullable AffineTransform transform, @Nullable ImageObserver observer
    ) {
        var imageRect = imageRect(image, observer);

        if (imageRect == null) {
            return true;
        }

        recordFill(transform == null ? imageRect : transform.createTransformedShape(imageRect));

        return true;
    }

    @Override
    public void drawImage(@Nullable BufferedImage image, @Nullable BufferedImageOp op, int x, int y) {
        recordImage(image, x, y, null);
    }

    @Override
    public void drawRenderedImage(@Nullable RenderedImage image, @Nullable AffineTransform transform) {
        if (image == null) {
            return;
        }

        var imageRect = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        recordFill(transform == null ? imageRect : transform.createTransformedShape(imageRect));
    }

    @Override
    public void drawRenderableImage(@Nullable RenderableImage image, @Nullable AffineTransform transform) {
        if (image == null) {
            return;
        }

        var imageRect = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        recordFill(transform == null ? imageRect : transform.createTransformedShape(imageRect));
    }

    /**
     * True means the image was rendered completely, as the {@link Graphics} contract requires of
     * the {@code drawImage} overrides.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean recordImage(@Nullable Image image, int x, int y, @Nullable ImageObserver observer) {
        var imageRect = imageRect(image, observer);

        if (imageRect == null) {
            return true;
        }

        recordFill(new Rectangle2D.Double(x, y, imageRect.getWidth(), imageRect.getHeight()));

        return true;
    }

    @Nullable
    private static Rectangle2D imageRect(@Nullable Image image, @Nullable ImageObserver observer) {
        if (image == null) {
            return null;
        }

        var width = image.getWidth(observer);
        var height = image.getHeight(observer);

        // A not-yet-loaded image reports -1 and has no measurable ink.
        if (width <= 0 || height <= 0) {
            return null;
        }

        return new Rectangle2D.Double(0, 0, width, height);
    }

    private static Rectangle2D destinationRect(int dx1, int dy1, int dx2, int dy2) {
        var x = Math.min(dx1, dx2);
        var y = Math.min(dy1, dy2);

        return new Rectangle2D.Double(x, y, Math.abs(dx2 - dx1), Math.abs(dy2 - dy1));
    }

    /**
     * The child shares this instance's accumulator; a bare delegate would discard every draw.
     */
    @Override
    public Graphics create() {
        return new RecordingGraphics2D(scratchImage, (Graphics2D) delegate.create(), drawables);
    }

    /**
     * Records nothing: the copy cannot cover ink outside what is already in the list.
     */
    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        // Intentionally records nothing — see the Javadoc above.
    }

    @Override
    public void translate(int x, int y) {
        delegate.translate(x, y);
    }

    @Override
    public void translate(double tx, double ty) {
        delegate.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        delegate.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        delegate.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        delegate.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        delegate.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform transform) {
        delegate.transform(transform);
    }

    @Override
    public void setTransform(AffineTransform transform) {
        delegate.setTransform(transform);
    }

    @Override
    public AffineTransform getTransform() {
        return delegate.getTransform();
    }

    @Override
    public Color getColor() {
        return delegate.getColor();
    }

    @Override
    public void setColor(Color color) {
        delegate.setColor(color);
    }

    @Override
    public void setPaintMode() {
        delegate.setPaintMode();
    }

    @Override
    public void setXORMode(Color color) {
        delegate.setXORMode(color);
    }

    @Override
    public Paint getPaint() {
        return delegate.getPaint();
    }

    @Override
    public void setPaint(Paint paint) {
        delegate.setPaint(paint);
    }

    @Override
    public Composite getComposite() {
        return delegate.getComposite();
    }

    @Override
    public void setComposite(Composite composite) {
        delegate.setComposite(composite);
    }

    @Override
    public Color getBackground() {
        return delegate.getBackground();
    }

    @Override
    public void setBackground(Color color) {
        delegate.setBackground(color);
    }

    @Override
    public Stroke getStroke() {
        return delegate.getStroke();
    }

    @Override
    public void setStroke(Stroke stroke) {
        delegate.setStroke(stroke);
    }

    @Override
    public Font getFont() {
        return delegate.getFont();
    }

    @Override
    public void setFont(Font font) {
        delegate.setFont(font);
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        return delegate.getFontMetrics(font);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return delegate.getFontRenderContext();
    }

    @Override
    public Shape getClip() {
        return delegate.getClip();
    }

    @Override
    public void setClip(Shape clip) {
        delegate.setClip(clip);
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        delegate.setClip(x, y, width, height);
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        delegate.clipRect(x, y, width, height);
    }

    @Override
    public void clip(Shape shape) {
        delegate.clip(shape);
    }

    @Override
    public Rectangle getClipBounds() {
        return delegate.getClipBounds();
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return delegate.getRenderingHint(key);
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        delegate.setRenderingHint(key, value);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return delegate.getRenderingHints();
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        delegate.setRenderingHints(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        delegate.addRenderingHints(hints);
    }

    @Override
    public boolean hit(Rectangle rect, Shape shape, boolean onStroke) {
        return delegate.hit(rect, shape, onStroke);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return delegate.getDeviceConfiguration();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
