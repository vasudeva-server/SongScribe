package songscribe.dom;

/**
 * A distance expressed in <b>staff spaces</b> — the layout unit in which the
 * distance between two adjacent staff lines is exactly {@code 1.0}.
 * <p>
 * Staff spaces are the zoom- and device-independent unit the layout is authored
 * in. Convert to on-screen pixels only at a view boundary, through a
 * {@link songscribe.ui.ViewScale}, which yields a {@link ViewPx}.
 */
public record Ss(double value) {}
