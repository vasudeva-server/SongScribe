package songscribe.dom;

/**
 * A distance in <b>document pixels</b>: pixels at the fixed document scale
 * ({@link DocumentScale#PIXELS_PER_STAFF_SPACE}, i.e. 100% zoom), independent of the view zoom.
 */
public record DocPx(double value) implements PixelDistance {}
