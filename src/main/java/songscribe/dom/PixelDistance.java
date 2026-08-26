package songscribe.dom;

/**
 * A pixel distance in one of the two pixel regimes, {@link DocPx} or
 * {@link ViewPx}, that shares one rounding rule: sizes round up so content is
 * never clipped, positions round to nearest so placement stays centered. See
 * {@code .claude/guides/spatial-units.md} §"Rounding at the pixel boundary"
 * and {@code docs/zoom.md}.
 * <p>
 * {@link Ss} deliberately does not implement this interface — staff spaces
 * have no integer form, so there is nothing here for it to round to.
 */
public sealed interface PixelDistance permits DocPx, ViewPx {

    double value();

    /**
     * Rounds to the nearest integer pixel. Use for <b>positions</b>
     * (coordinates), where nearest rounding keeps placement centered.
     */
    default int roundedPx() {
        return (int) Math.round(value());
    }

    /**
     * Rounds up to the next integer pixel. Use for <b>sizes</b> (widths,
     * heights), so content is never clipped at high zoom.
     */
    default int ceilPx() {
        return (int) Math.ceil(value());
    }
}
