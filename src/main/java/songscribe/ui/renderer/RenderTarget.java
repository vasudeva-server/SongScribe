package songscribe.ui.renderer;

/**
 * Identifies the destination for a rendering operation, used to select
 * appropriate thickness values and snapping behaviour.
 */
public enum RenderTarget {
    /** Screen display: apply optical compensation and device-pixel snapping. */
    SCREEN,
    /** High-resolution export (PDF, image): use raw SMuFL values, no snapping. */
    EXPORT
}
