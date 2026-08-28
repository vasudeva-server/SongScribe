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
     * This distance as a whole-pixel <b>coordinate</b>: where something sits,
     * not how large it is. The whole pixel nearest the true coordinate is the
     * one that keeps placement centered, so the error is at most half a pixel
     * in either direction.
     *
     * @return the whole-pixel coordinate this distance names
     * @invariant the result differs from {@link #value()} by at most 0.5
     */
    default int positionPx() {
        return (int) Math.round(value());
    }

    /**
     * This distance as a whole-pixel <b>extent</b>: how large something is, not
     * where it sits. An extent covers every pixel the content touches, so a
     * fractional pixel counts as a whole one and nothing is clipped.
     *
     * @return the whole-pixel extent covering this distance
     * @invariant the result is never less than {@link #value()}
     */
    default int sizePx() {
        return (int) Math.ceil(value());
    }
}
