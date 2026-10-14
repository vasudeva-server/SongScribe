package songscribe.dom;

/**
 * A distance in view pixels: on-screen pixels at the current view zoom, produced from {@link Ss}
 * or {@link DocPx} through a {@link songscribe.ui.ViewScale}.
 */
public record ViewPx(double value) implements PixelDistance {}
