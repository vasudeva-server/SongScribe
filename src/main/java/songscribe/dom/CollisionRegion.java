package songscribe.dom;

/**
 * A sub-region of a composite element's bounding box used for collision detection.
 * <p>
 * Composite elements (ending brackets, tempo markings) are decomposed into
 * multiple collision regions so that higher stacking layers can nestle into
 * the open space between sub-regions.
 * <p>
 * Each sub-region occupies the vertical range
 * {@code [elementY + yOffsetSs, elementY + yOffsetSs + heightSs]}.
 *
 * @param xOffsetSs horizontal offset from the element's anchor X, in staff spaces
 * @param yOffsetSs vertical offset from the element's top Y to this sub-region's visual top, in staff spaces
 * @param widthSs   width of this sub-region, in staff spaces
 * @param heightSs  height of this sub-region, in staff spaces
 */
public record CollisionRegion(double xOffsetSs, double yOffsetSs, double widthSs, double heightSs) {
}
