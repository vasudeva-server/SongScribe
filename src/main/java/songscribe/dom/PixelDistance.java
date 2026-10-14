package songscribe.dom;

/**
 * A pixel distance in one of the two pixel regimes, {@link DocPx} or {@link ViewPx}, that shares
 * one rounding rule: sizes round up so content is never clipped, positions round to nearest. See
 * {@code .claude/guides/spatial-units.md} §"Rounding at the pixel boundary".
 * {@link Ss} does not implement it: staff spaces have no integer form.
 */
public sealed interface PixelDistance permits DocPx, ViewPx {

    double value();

    default int positionPx() {
        return (int) Math.round(value());
    }

    default int sizePx() {
        return (int) Math.ceil(value());
    }
}
