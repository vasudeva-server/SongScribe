package songscribe.dom;

/**
 * A distance expressed in <b>document pixels</b> — pixels at the fixed document
 * scale ({@link ScaleContext#DEFAULT_PIXELS_PER_STAFF_SPACE}, i.e. 100% zoom),
 * independent of the current on-screen view zoom.
 * <p>
 * Document pixels are the regime of physical/document quantities (page
 * dimensions, margins, the line width in inches) that must not grow or shrink as
 * the user zooms. Convert to on-screen {@link ViewPx} through a
 * {@link songscribe.ui.ViewScale} at a view boundary.
 */
public record DocPx(double value) {

    /**
     * Rounds to the nearest integer pixel. Use for <b>positions</b>
     * (coordinates), where nearest rounding keeps placement centered.
     */
    public int roundedPx() {
        return (int) Math.round(value);
    }

    /**
     * Rounds up to the next integer pixel. Use for <b>sizes</b> (widths,
     * heights), so content is never clipped at high zoom.
     */
    public int ceilPx() {
        return (int) Math.ceil(value);
    }
}
