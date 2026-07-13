package songscribe.dom;

/**
 * A distance expressed in <b>view pixels</b> — on-screen pixels at the current
 * view zoom.
 * <p>
 * View pixels are what Swing components, mouse input, and overlay bounds live
 * in. They are produced from {@link Ss} or {@link DocPx} through a
 * {@link songscribe.ui.ViewScale}, which folds in the current zoom factor.
 */
public record ViewPx(double value) {

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
