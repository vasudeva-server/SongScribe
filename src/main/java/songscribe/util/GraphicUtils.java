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

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import songscribe.ui.graphics.HiDPIScaledImage;
import songscribe.ui.graphics.RetinaImage;

public final class GraphicUtils {

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

        @NotNull
        @Contract(pure = true)
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

        public double convertFromPixels(int pixels) {
            return GraphicUtils.convertFromPixels(pixels, this);
        }

        public boolean isMetric() {
            return this == CM;
        }
    }

    public static final double CM_PER_INCH = 2.54;

    // This will be > 1 on Retina/HiDPI displays
    private static final int screenScaleFactor;

    // Since querying for Retina displays happens a lot, cache the result
    private static final boolean isRetina;

    private static final int dpi;

    private static MediaTracker mediaTracker = new MediaTracker(new JLabel());

    static {
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var config = ge.getDefaultScreenDevice().getDefaultConfiguration();
        screenScaleFactor = (int) config.getDefaultTransform().getScaleX();
        isRetina = screenScaleFactor > 1;
        dpi = Toolkit.getDefaultToolkit().getScreenResolution();
    }

    private GraphicUtils() {}

    public static int getScreenScaleFactor() {
        return screenScaleFactor;
    }

    public static boolean isRetina() {
        return isRetina;
    }

    public static int getDpi() {
        return dpi;
    }

    /**
     * Computes a DPI-aware stroke width.
     * Returns an integral width to avoid anti-aliasing artifacts on standard DPI displays.
     *
     * @param desiredWidth The desired stroke width in physical pixels
     * @return Stroke width scaled for current display, rounded to nearest integer
     */
    public static float getDpiAwareStrokeWidth(float desiredWidth) {
        return Math.round(desiredWidth * screenScaleFactor);
    }

    @NotNull
    @Contract(pure = true)
    public static String getScaledImagePath(@NotNull String path) {
        // Add @<scaleFactor>x to the path.
        return path.replace(".png", "@" + screenScaleFactor + "x.png");
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
            Log.error("Image resource not found: " + path);
            return null;
        } catch (IOException e) {
            Log.error("Error reading image resource '" + path + '\'', e);
            return null;
        }
    }

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

    /**
     * Creates a custom cursor from a PNG resource, with HiDPI support.
     *
     * @param resourcePath classpath-relative path to the 1x PNG (e.g. "/cursors/scissors.png")
     * @param hotSpot      hot-spot in logical (1x) coordinates
     * @param name         cursor name for accessibility
     * @return the cursor, or null if the image could not be loaded
     */
    @Nullable
    public static Cursor createCustomCursor(
        @NotNull String resourcePath,
        @NotNull Point hotSpot,
        @NotNull String name
    ) {
        BufferedImage image;
        var scaledHotSpot = new Point(hotSpot);

        if (isRetina) {
            image = readImageResource(getScaledImagePath(resourcePath));

            if (image != null) {
                scaledHotSpot.x *= screenScaleFactor;
                scaledHotSpot.y *= screenScaleFactor;
            }
        } else {
            image = null;
        }

        if (image == null) {
            image = readImageResource(resourcePath);
            scaledHotSpot.setLocation(hotSpot);
        }

        if (image == null) {
            return null;
        }

        return Toolkit.getDefaultToolkit().createCustomCursor(image, scaledHotSpot, name);
    }

    public static boolean writeImage(
        BufferedImage image,
        String extension,
        File file
    ) throws IOException {
        return ImageIO.write(image, extension, file);
    }

    public static void setRenderingHints(@NotNull Graphics2D g2) {
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT
        );

        g2.setRenderingHint(
            RenderingHints.KEY_STROKE_CONTROL,
            isRetina
                ? RenderingHints.VALUE_STROKE_PURE
                : RenderingHints.VALUE_STROKE_NORMALIZE
        );

        if (isRetina) {
            g2.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON
            );
        }
    }

    // Draws an image at the specified location. If the image is a HiDPIScaledImage,
    // it will be drawn at half size since it's scaled to 2x.
    public static void drawImage(
        Graphics g,
        Image image,
        int x,
        int y,
        ImageObserver observer
    ) {
        if (image instanceof HiDPIScaledImage) {
            var g2 = (Graphics2D) g.create(
                x,
                y,
                image.getWidth(observer),
                image.getHeight(observer)
            );
            g2.scale(0.5, 0.5);
            var img = ((HiDPIScaledImage) image).getDelegate();

            if (img == null) {
                img = image;
            }

            g2.drawImage(img, 0, 0, observer);
            g2.scale(1, 1);
            g2.dispose();
        } else {
            g.drawImage(image, x, y, observer);
        }
    }

    public static Image getImage(@NotNull File file) {
        return getImage(file.getAbsolutePath());
    }

    @Nullable
    public static Image getImage(String fileName) {
        Image img = null;

        // If we are on a retina screen, use a scaled (@2x) image.
        // Otherwise, use the original image.
        if (isRetina) {
            img = readImage(getScaledImagePath(fileName));

            if (img != null) {
                img = RetinaImage.createFrom(img);
            }
        }

        if (img == null) {
            img = readImage(fileName);

            if (img == null) {
                return null;
            }
        }

        try {
            mediaTracker.addImage(img, 0);
            mediaTracker.waitForID(0);
        } catch (InterruptedException ignored) {}

        return img;
    }

    public static FlatSVGIcon getScaledSVGIcon(String filename, int size) {
        var icon = new FlatSVGIcon("icons/" + filename);
        return getScaledSVGIcon(icon, size);
    }

    public static FlatSVGIcon getScaledSVGIcon(
        @NotNull FlatSVGIcon icon,
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
     * Snaps a local X coordinate to the nearest device pixel boundary.
     * Transforms to absolute device space, rounds, and inverse-transforms back to
     * local coordinates. This ensures crisp rendering of vertical elements (stems,
     * barlines) regardless of the current graphics translation or Retina scaling.
     *
     * @param g2     the graphics context with the current transform
     * @param localX the X coordinate in local (user) space
     * @return the adjusted X coordinate in local space, snapped to a device pixel
     */
    public static double snapXToDevicePixel(@NotNull Graphics2D g2, double localX) {
        var transform = g2.getTransform();
        var devicePt = new Point2D.Double();
        transform.transform(new Point2D.Double(localX, 0), devicePt);
        devicePt.x = Math.round(devicePt.x);

        try {
            transform.inverseTransform(devicePt, devicePt);
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return localX;
        }

        return devicePt.x;
    }

    /**
     * Snaps a local Y coordinate to the nearest device pixel boundary.
     * Transforms to absolute device space, rounds, and inverse-transforms back to
     * local coordinates. This ensures crisp rendering of horizontal elements
     * regardless of the current graphics translation or Retina scaling.
     *
     * @param g2     the graphics context with the current transform
     * @param localY the Y coordinate in local (user) space
     * @return the adjusted Y coordinate in local space, snapped to a device pixel
     */
    public static double snapYToDevicePixel(@NotNull Graphics2D g2, double localY) {
        var transform = g2.getTransform();
        var devicePt = new Point2D.Double();
        transform.transform(new Point2D.Double(0, localY), devicePt);
        devicePt.y = Math.round(devicePt.y);

        try {
            transform.inverseTransform(devicePt, devicePt);
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return localY;
        }

        return devicePt.y;
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
     * Given a text block with one or more lines, calculates the width.
     */
    public static double getTextBlockWidth(
        @NotNull String text,
        @NotNull Graphics2D g2
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
}
