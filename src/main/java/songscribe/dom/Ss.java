package songscribe.dom;

/**
 * A distance in staff spaces, the zoom- and device-independent layout unit. Convert to pixels
 * only at a view boundary, through a {@link songscribe.ui.ViewScale}, which yields a
 * {@link ViewPx}.
 */
public record Ss(double value) {}
