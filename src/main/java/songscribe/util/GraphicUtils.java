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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemInfo;

import songscribe.smufl.SMuFLGlyph;


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
     * Draws a horizontal line with round end caps.
     * All coordinates are in the current (local) coordinate system of {@code g2}.
     *
     * @param g2          graphics context
     * @param x1          left X (center of left end cap)
     * @param x2          right X (center of right end cap)
     * @param centerY     vertical center of the line
     * @param thicknessSs line thickness in staff spaces
     */
    public static void fillHorizontalLine(
        Graphics2D g2, double x1, double x2, double centerY, double thicknessSs
    ) {
        var oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke((float) thicknessSs, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(x1, centerY, x2, centerY));
        g2.setStroke(oldStroke);
    }

    /**
     * Draws a vertical line with round end caps.
     * All coordinates are in the current (local) coordinate system of {@code g2}.
     *
     * @param g2          graphics context
     * @param centerX     horizontal center of the line
     * @param y1          top Y (center of top end cap)
     * @param y2          bottom Y (center of bottom end cap)
     * @param thicknessSs line thickness in staff spaces
     */
    public static void fillVerticalLine(
        Graphics2D g2, double centerX, double y1, double y2, double thicknessSs
    ) {
        var oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke((float) thicknessSs, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(centerX, y1, centerX, y2));
        g2.setStroke(oldStroke);
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
     * Returns the glyph outline for the given SMuFL glyph rendered with {@code font},
     * suitable for use as an {@link Area} component.
     */
    public static Shape glyphOutline(Font font, FontRenderContext frc, SMuFLGlyph glyph) {
        return font.createGlyphVector(frc, glyph.asString()).getOutline();
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
}
