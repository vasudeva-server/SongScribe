package songscribe.dom;

/**
 * A pixel distance in one of the two pixel regimes, {@link DocPx} or {@link ViewPx}, that shares
 * one rounding rule: sizes round up so content is never clipped, positions round to nearest.
 * There is deliberately no single rounding operation, so every call site must say which rule it
 * means.
 * <p>
 * {@link Ss} does not implement it: staff spaces have no integer form, so a staff-space value
 * has nothing to round until it names the pixel regime it enters.
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
