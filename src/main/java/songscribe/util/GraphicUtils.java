/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.util;

import module java.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemInfo;


public final class GraphicUtils {

    private static final Logger LOG = LoggerFactory.getLogger(GraphicUtils.class);

    /**
     * A {@link FontRenderContext} derived from the default screen device with the
     * application's standard rendering hints applied. Use for layout-time glyph
     * measurement so that text advances match what is actually rendered on screen.
     * Initialised in the static block below alongside {@code dpi}.
     */
    public static final FontRenderContext SCREEN_FRC;

    private static final FlatSVGIcon.ColorFilter THEME_AWARE_SVG_ICON_FILTER =
        new FlatSVGIcon.ColorFilter(
            (component, color) -> {
                var foreground = component != null
                    ? component.getForeground()
                    : UIManager.getColor("Component.foreground");
                return foreground != null ? foreground : color;
            }
        );

    public enum Unit {
        UNDETERMINED(-1),
        INCH(0),
        CM(1);

        private final int value;

        Unit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Unit create(boolean isMetric) {
            return isMetric ? CM : INCH;
        }

        public static Unit fromValue(int value) {
            return Arrays.stream(values())
                .filter(unit -> unit.value == value)
                .findFirst()
                .orElse(UNDETERMINED);
        }

        public String description() {
            return switch (this) {
                case INCH -> "inch";
                case CM -> "cm";
                default -> "";
            };
        }

        public int convertToPixels(double length) {
            return GraphicUtils.convertToPixels(length, this);
        }

        public boolean isMetric() {
            return this == CM;
        }
    }

    public static final double CM_PER_INCH = 2.54;

    private static final double MILLIMETERS_PER_INCH = 25.4;

    // Standard non-HiDPI resolution; used in headless environments where the screen device is unavailable
    private static final int HEADLESS_DPI = 96;

    private static final int dpi;

    private static MediaTracker mediaTracker = new MediaTracker(new JLabel());

    static {
        if (GraphicsEnvironment.isHeadless()) {
            dpi = HEADLESS_DPI;
            var graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            setRenderingHints(graphics);
            SCREEN_FRC = graphics.getFontRenderContext();
            graphics.dispose();
        } else {
            var graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            var graphicsDevice = graphicsEnvironment.getDefaultScreenDevice();
            var config = graphicsDevice.getDefaultConfiguration();
            dpi = computePhysicalDpi(graphicsDevice);

            var image = config.createCompatibleImage(1, 1);
            var graphics = image.createGraphics();
            setRenderingHints(graphics);
            SCREEN_FRC = graphics.getFontRenderContext();
            graphics.dispose();
            image.flush();
        }
    }

    /**
     * Computes the physical DPI of the default screen.
     * <p>
     * On macOS this reads the main display's physical width directly from
     * CoreGraphics. On other platforms, or when the physical size is unavailable,
     * it falls back to {@link Toolkit#getScreenResolution()}.
     */
    private static int computePhysicalDpi(GraphicsDevice gd) {
        var bounds = gd.getDefaultConfiguration().getBounds();

        if (SystemInfo.isMacOS) {
            var widthMm = macPhysicalWidthMm();

            if (widthMm > 0) {
                var widthInches = widthMm / MILLIMETERS_PER_INCH;
                var computedDpi = (int) Math.round(bounds.width / widthInches);
                LOG.info("Screen: {}x{} px, physical width {} mm, DPI {}",
                    bounds.width, bounds.height, String.format("%.1f", widthMm), computedDpi);
                return computedDpi;
            }
        }

        var fallbackDpi = Toolkit.getDefaultToolkit().getScreenResolution();
        LOG.info("Screen: {}x{} px, DPI {} (system fallback)", bounds.width, bounds.height, fallbackDpi);
        return fallbackDpi;
    }

    /**
     * Reads the main display's physical width in millimetres directly from
     * CoreGraphics ({@code CGDisplayScreenSize(CGMainDisplayID())}) via the FFM
     * API. Returns {@code 0} if the value is unavailable for any reason (missing
     * symbols, native error, or a non-macOS host).
     */
    private static double macPhysicalWidthMm() {
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var coreGraphics = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);

            var mainDisplayId = linker.downcallHandle(
                coreGraphics.find("CGMainDisplayID").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT));

            var cgSizeLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("width"),
                ValueLayout.JAVA_DOUBLE.withName("height"));

            var screenSize = linker.downcallHandle(
                coreGraphics.find("CGDisplayScreenSize").orElseThrow(),
                FunctionDescriptor.of(cgSizeLayout, ValueLayout.JAVA_INT));

            var displayId = (int) mainDisplayId.invoke();
            var size = (MemorySegment) screenSize.invoke((SegmentAllocator) arena, displayId);

            // width is the first field of the CGSize struct (offset 0).
            return size.get(ValueLayout.JAVA_DOUBLE, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private GraphicUtils() {}

    public static int getDpi() {
        return dpi;
    }

    private static String getScaledImagePath(String path) {
        return path.replace(".png", "@2x.png");
    }

    @Nullable
    public static BufferedImage readImageResource(String path) {
        try (var stream = GraphicUtils.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException(
                    "Image resource not found: " + path
                );
            }
            return ImageIO.read(stream);
        } catch (FileNotFoundException e) {
            LOG.error("Image resource not found: {}", path);
            return null;
        } catch (IOException e) {
            LOG.error("Error reading image resource '{}'", path, e);
            return null;
        }
    }

    @Nullable
    public static BufferedImage readImage(String path) {
        return readImage(new File(Utils.getResourcePath("images/" + path)));
    }

    @Nullable
    public static BufferedImage readImage(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    public static BufferedImage readImage(URL url) {
        try {
            return ImageIO.read(url);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean writeImage(
        BufferedImage image,
        String extension,
        File file
    ) throws IOException {
        return ImageIO.write(image, extension, file);
    }

    public static void setRenderingHints(Graphics2D g2) {
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g2.setRenderingHint(
            RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_NORMALIZE
        );

        // Always on, matching the score's drawing hints (ScoreComponent.initGraphics)
        // so that layout-time measurement via SCREEN_FRC agrees with what is drawn.
        g2.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON
        );
    }

    @Nullable
    public static Image getImage(File file) {
        return getImage(file.getAbsolutePath());
    }

    @Nullable
    public static Image getImage(String fileName) {
        var lowResolution = readImage(fileName);

        if (lowResolution == null) {
            return null;
        }

        var highResolution = readImage(getScaledImagePath(fileName));

        var image = highResolution != null
            ? new BaseMultiResolutionImage(lowResolution, highResolution)
            : (Image) lowResolution;

        try {
            mediaTracker.addImage(image, 0);
            mediaTracker.waitForID(0);
        } catch (InterruptedException ignored) {}

        return image;
    }

    public static FlatSVGIcon getScaledSVGIcon(String filename, int size) {
        return getScaledSVGIcon(filename, size, false);
    }

    public static FlatSVGIcon getScaledSVGIcon(String filename, int size, boolean isThemed) {
        var icon = new FlatSVGIcon("icons/" + filename);

        if (isThemed) {
            icon.setColorFilter(THEME_AWARE_SVG_ICON_FILTER);
        }

        return getScaledSVGIcon(icon, size);
    }

    public static FlatSVGIcon getScaledSVGIcon(
        FlatSVGIcon icon,
        int size
    ) {
        var scale =
            (float) size / Math.max(icon.getIconWidth(), icon.getIconHeight());

        if (scale != 1.0f) {
            return icon.derive(scale);
        }

        return icon;
    }

    public static void setMediaTracker(MediaTracker mt) {
        mediaTracker = mt;
    }

    /**
     * Draws a line of the given thickness with fully rounded ends, at any angle, with the rounded
     * ends contained within the endpoints (the line spans exactly {@code [start, end]}).
     *
     * <p>This is the single rounded-line primitive — staff lines, ledger lines, and glissandos
     * all go through it. It fills a round rect rather than stroking a {@link Line2D} with
     * {@code CAP_ROUND}: a round cap bulges half the thickness past each endpoint, making the line
     * render longer than its coordinates. The round rect is built in the line's own frame and
     * placed via the {@code g2} transform (translate to the start, rotate to the line angle), so it
     * spans exactly {@code [start, end]} at any angle ({@code arc == thickness}). Filling the
     * untransformed rect this way avoids the per-call {@code Path2D} that
     * {@code createTransformedShape} would otherwise allocate on a hot path.
     *
     * <p>For a connected multi-segment path (e.g. tuplet/ending brackets) use {@link #drawPath}
     * instead, so corners join cleanly. Butt-capped lines (e.g. lyric connectors) must not use
     * either method.
     */
    public static void drawRoundedLine(
        Graphics2D g2, double x1, double y1, double x2, double y2, double thicknessSs
    ) {
        var dxSs = x2 - x1;
        var dySs = y2 - y1;
        var lengthSs = Math.hypot(dxSs, dySs);
        var lineShape = new RoundRectangle2D.Double(
            0, -thicknessSs / 2.0, lengthSs, thicknessSs, thicknessSs, thicknessSs);

        var savedTransform = g2.getTransform();
        g2.translate(x1, y1);
        g2.rotate(Math.atan2(dySs, dxSs));
        g2.fill(lineShape);
        g2.setTransform(savedTransform);
    }

    /**
     * Strokes an open polyline through the given points as a single {@link Path2D}, using
     * {@code CAP_ROUND} end caps and {@code JOIN_ROUND} corners so connected segments meet cleanly
     * — unlike separate {@link #drawRoundedLine} calls, whose round caps only overlap at corners.
     *
     * <p>A round cap bulges half the line width past each endpoint. To keep the stroked path
     * beginning and ending exactly at the first and last points given, those two points are pulled
     * inward by half the line width along their own segment before stroking. Interior points are
     * corners (joins), not caps, and are left untouched.
     *
     * <p>Callers needing a gap in the path (e.g. a tuplet bracket's number gap) must split it into
     * separate {@code drawPath} calls.
     *
     * @param points at least two points; the path is drawn open (not closed). The first two and the
     *               last two points should differ — a zero-length first/last segment cannot be
     *               inset, so that end's round cap bulges half a width past the endpoint instead of
     *               landing on it. Fewer than two points draws nothing.
     */
    public static void drawPath(Graphics2D g2, Point2D[] points, double thicknessSs) {
        if (points.length < 2) {
            return;
        }

        var halfWidthSs = thicknessSs / 2.0;
        var lastIndex = points.length - 1;
        var startSs = capInset(points[0], points[1], halfWidthSs);
        var endSs = capInset(points[lastIndex], points[lastIndex - 1], halfWidthSs);

        var path = new Path2D.Double();
        path.moveTo(startSs.getX(), startSs.getY());

        for (var i = 1; i < lastIndex; i++) {
            path.lineTo(points[i].getX(), points[i].getY());
        }

        path.lineTo(endSs.getX(), endSs.getY());

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.STROKE)) {
            g2.setStroke(new BasicStroke(
                (float) thicknessSs, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
    }

    /**
     * Returns {@code point} moved {@code halfWidthSs} toward {@code toward}, so a {@code CAP_ROUND}
     * cap centered on the result reaches back to exactly {@code point}. A zero-length segment
     * ({@code point} equals {@code toward}) has no direction to inset along, so {@code point} is
     * returned unchanged rather than producing {@code NaN}.
     */
    private static Point2D capInset(Point2D point, Point2D toward, double halfWidthSs) {
        var dxSs = toward.getX() - point.getX();
        var dySs = toward.getY() - point.getY();
        var lengthSs = Math.hypot(dxSs, dySs);

        if (lengthSs == 0) {
            return point;
        }

        return new Point2D.Double(
            point.getX() + dxSs / lengthSs * halfWidthSs,
            point.getY() + dySs / lengthSs * halfWidthSs);
    }

    /**
     * Takes pixel and returns inches (rounded to 2 decimal places) or mm (rounded to int).
     */
    public static double convertFromPixels(int pixels, Unit unit) {
        var result = (double) pixels / GraphicUtils.getDpi();

        if (unit.isMetric()) {
            // Round to nearest mm
            return Math.round(result * CM_PER_INCH * 10) / 10d;
        }

        // Round to 2 decimal places
        return Math.round(result * 100) / 100d;
    }

    /**
     * Takes inches or mm and returns pixels
     */
    public static int convertToPixels(double length, Unit unit) {
        var result = length * GraphicUtils.getDpi();

        if (unit.isMetric()) {
            result /= CM_PER_INCH * 10;
        }

        return (int) Math.round(result);
    }

    /**
     * Clamps {@code location} so that a window of {@code windowSize} fits entirely
     * within the screen that contains the point. If no screen contains the point,
     * the default screen is used.
     */
    public static Point clampToScreen(Point location, Dimension windowSize) {
        var clamped = clampToScreen(new Rectangle(location, windowSize));
        return clamped.getLocation();
    }

    /**
     * Clamps the given rectangle to fit within the screen that contains its
     * top-left corner. Both size and position are adjusted: size is capped to
     * screen dimensions, then position is shifted so the rectangle is fully
     * on-screen.
     */
    public static Rectangle clampToScreen(Rectangle bounds) {
        var graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var screen = graphicsEnvironment.getDefaultScreenDevice().getDefaultConfiguration().getBounds();

        for (var device : graphicsEnvironment.getScreenDevices()) {
            var deviceBounds = device.getDefaultConfiguration().getBounds();

            if (deviceBounds.contains(bounds.getLocation())) {
                screen = deviceBounds;
                break;
            }
        }

        var width = Math.min(bounds.width, screen.width);
        var height = Math.min(bounds.height, screen.height);
        var clampedX = Math.clamp(bounds.x, screen.x, screen.x + screen.width - width);
        var clampedY = Math.clamp(bounds.y, screen.y, screen.y + screen.height - height);

        return new Rectangle(clampedX, clampedY, width, height);
    }

    /**
     * Given a text block with one or more lines, calculates the width.
     */
    public static double getTextBlockWidth(
        String text,
        Graphics2D g2
    ) {
        if (text.isEmpty()) {
            return 0d;
        }

        var context = g2.getFontRenderContext();
        var font = g2.getFont();
        var maxWidth = 0d;
        var lines = text.split("\n");

        for (var line : lines) {
            if (!line.isEmpty()) {
                var layout = new TextLayout(line, font, context);
                maxWidth = Math.max(maxWidth, layout.getBounds().getWidth());
            }
        }

        return maxWidth;
    }

    /**
     * Returns the tight height of a text block with {@code lineCount} lines in the
     * given {@code metrics}: each line is ascent + descent tall, with the font's
     * leading inserted only between lines, never below the last descender.
     */
    public static int getTextBlockHeight(FontMetrics metrics, int lineCount) {
        var glyphHeight = metrics.getAscent() + metrics.getDescent();
        return lineCount * glyphHeight + (lineCount - 1) * metrics.getLeading();
    }

    /**
     * Returns the ink height above the baseline for a glyph's visual bounds.
     * <p>
     * Visual bounds extend upward from the baseline into negative Y, so the
     * top must be negated to yield a positive height. The result is in the
     * same units the font was sized in.
     */
    public static double inkHeight(Rectangle2D visualBounds) {
        return -visualBounds.getY();
    }

    /**
     * Returns the ink pixel bounds of {@code text} rendered in {@code font} under
     * {@link #SCREEN_FRC}, or {@code null} if the text is empty.
     * <p>
     * Uses {@link GlyphVector#getPixelBounds} rather than
     * {@link FontMetrics#stringWidth}, because advance width alone does not account
     * for glyphs whose ink extends past their advance (e.g. italic descenders) or
     * before the drawing origin (the negative left bearing of a "W"). The returned
     * rectangle's {@code width} is the full ink span, and {@code x} is the ink's
     * offset from the origin.
     */
    @Nullable
    public static Rectangle inkBounds(String text, Font font) {
        if (text.isEmpty()) {
            return null;
        }

        return font.createGlyphVector(SCREEN_FRC, text).getPixelBounds(SCREEN_FRC, 0, 0);
    }

    /**
     * Fractional sibling of {@link #inkBounds}: returns the visual (ink) bounds of
     * {@code text} rendered in {@code font} under {@link #SCREEN_FRC}, or
     * {@code null} if the text is empty.
     * <p>
     * Uses {@link GlyphVector#getVisualBounds} — a resolution-independent outline
     * extent — rather than {@link GlyphVector#getPixelBounds}, which snaps to whole
     * device pixels. Prefer this variant wherever the measurement must scale
     * linearly with the font size (e.g. zoomed text), because pixel snapping makes
     * the result jump as the font sweeps across pixel boundaries. Like
     * {@link #inkBounds}, the returned rectangle's width is the full ink span and
     * its {@code x} the ink's offset from the drawing origin (negative for the left
     * bearing of a "W").
     */
    @Nullable
    public static Rectangle2D visualBounds(String text, Font font) {
        if (text.isEmpty()) {
            return null;
        }

        return font.createGlyphVector(SCREEN_FRC, text).getVisualBounds();
    }

    /**
     * Extra padding beyond the font's nominal ascent/descent, needed so glyph ink
     * that overshoots those metrics is not clipped by a text component's bounds.
     */
    public record InkPadding(int top, int bottom) {
        public static final InkPadding NONE = new InkPadding(0, 0);
    }

    /**
     * Extra ink above the baseline that overshoots the font's nominal {@code ascent},
     * or 0 when {@code lineBounds} is null (empty line) or the ink stays within it.
     */
    public static int extraInkAbove(@Nullable Rectangle lineBounds, int ascent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (int) inkHeight(lineBounds) - ascent);
    }

    /**
     * Extra ink below the baseline that overshoots the font's nominal {@code descent},
     * or 0 when {@code lineBounds} is null (empty line) or the ink stays within it.
     */
    public static int extraInkBelow(@Nullable Rectangle lineBounds, int descent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (lineBounds.y + lineBounds.height) - descent);
    }

    /**
     * Some fonts (e.g. script fonts like Sign Painter) render glyph ink beyond the
     * font's nominal ascent/descent — a "y" descender extending past
     * {@link FontMetrics#getDescent()}, for instance. Since only the first and last
     * lines of a text block sit at its top and bottom edges, only their ink can be
     * clipped; excess ink on interior lines bleeds into the inter-line gap instead.
     */
    public static InkPadding inkPadding(List<String> textLines, FontMetrics metrics) {
        if (textLines.isEmpty()) {
            return InkPadding.NONE;
        }

        var font = metrics.getFont();
        var firstLineBounds = inkBounds(textLines.getFirst(), font);

        // A single line is both first and last; reuse its bounds rather than
        // building the same glyph vector twice.
        var lastLineBounds = textLines.size() == 1
            ? firstLineBounds
            : inkBounds(textLines.getLast(), font);

        return new InkPadding(
            extraInkAbove(firstLineBounds, metrics.getAscent()),
            extraInkBelow(lastLineBounds, metrics.getDescent())
        );
    }

    /**
     * The top half of {@link #inkPadding} for callers that only position the text
     * baseline, so the last line's glyph vector is not built unnecessarily.
     */
    public static int topInkPadding(List<String> textLines, FontMetrics metrics) {
        if (textLines.isEmpty()) {
            return 0;
        }

        var firstLineBounds = inkBounds(textLines.getFirst(), metrics.getFont());
        return extraInkAbove(firstLineBounds, metrics.getAscent());
    }
}
