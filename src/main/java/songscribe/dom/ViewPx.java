package songscribe.dom;

/**
 * A distance expressed in <b>view pixels</b> — on-screen pixels at the current
 * view zoom.
 * <p>
 * View pixels are what Swing components, mouse input, and overlay bounds live
 * in. They are produced from {@link Ss} or {@link DocPx} through a
 * {@link songscribe.ui.ViewScale}, which folds in the current zoom factor.
 */
public record ViewPx(double value) implements PixelDistance {}
