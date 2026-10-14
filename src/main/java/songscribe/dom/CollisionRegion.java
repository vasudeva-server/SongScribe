package songscribe.dom;

/**
 * A sub-region of a composite element's bounding box used for collision detection, so that
 * higher stacking layers can nestle into the open space between sub-regions.
 */
public record CollisionRegion(double xOffsetSs, double yOffsetSs, double widthSs, double heightSs) {
}
