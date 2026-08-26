package songscribe.dom;

/**
 * A distance expressed in <b>document pixels</b> — pixels at the fixed document
 * scale ({@link DocumentScale#PIXELS_PER_STAFF_SPACE}, i.e. 100% zoom),
 * independent of the current on-screen view zoom.
 * <p>
 * Document pixels are the regime of physical/document quantities (page
 * dimensions, margins, the line width in inches) that must not grow or shrink as
 * the user zooms. Convert to on-screen {@link ViewPx} through a
 * {@link songscribe.ui.ViewScale} at a view boundary.
 */
public record DocPx(double value) implements PixelDistance {}
