/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component.score;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * Base class for the overlays that belong to a line but cannot be drawn by it — the hover
 * preview element, the paste-mode insertion marker and the slide previews.
 * <p>
 * These overlays span the full legal staff-position range, which is wider than the
 * content-hugging bounds a {@link LineComponent} is laid out to, and Swing clips a component to
 * its own bounds. Each overlay is therefore a child of an {@link OverlayHost} — the level in
 * the hierarchy whose bounds are never the binding constraint — sized to exactly the ink it
 * draws, so the damaged region is the ink and nothing more.
 *
 * <h2>Coordinate spaces</h2>
 * <ul>
 *   <li>{@link #getInkBoundsSs()} reports ink in <b>staff spaces relative to the target line's
 *       origin</b>, which is the space the renderers already work in.</li>
 *   <li>{@link #updateBounds()} converts that to <b>host view pixels</b> and sets this
 *       component's bounds.</li>
 *   <li>{@link #renderOverlay(Graphics2D)} draws in the <b>line's</b> staff-space coordinates;
 *       {@link #paintComponent(Graphics)} has already established the translate and scale that
 *       map them onto this component's own top-left origin.</li>
 * </ul>
 *
 * <h2>Deliberate omissions</h2>
 * This class registers <b>no</b> mouse or mouse-motion listeners, and subclasses must not
 * either: a listener-free component is never selected as an AWT mouse-event target, so clicks
 * over an overlay fall through to the {@link LineComponent} beneath, exactly as note placement
 * and selection require. {@code PasteOverlay} documents the same requirement.
 */
public abstract class LineOverlayComponent extends JComponent {

    /**
     * Inflation applied to every side of the pixel-aligned ink rectangle.
     * <p>
     * Java2D antialiasing only tints pixels the shape actually intersects, so rounding the ink
     * rectangle outwards to pixel boundaries already contains the AA fringe. This extra pixel
     * covers {@code STROKE_NORMALIZE} grid snapping and glyph hinting, both of which can nudge
     * ink by up to half a device pixel.
     * <p>
     * Stroke width is <b>geometry, not antialiasing</b>: it is folded into the recorded ink
     * bounds by the display list. This pad must never be relied on to absorb it.
     */
    private static final int INK_PAD_PX = 1;

    private OverlayHost host;

    /**
     * The line this overlay is attached to, or null when it has none. Needed for the line's
     * layout-derived geometry ({@code getMiddleLineYSs}, {@code calculateInsertionXSs}) and as
     * the origin every Ss coordinate is relative to.
     */
    @Nullable
    private LineComponent targetLine;

    /** The target line's origin in host pixels as of the last successful bounds update. */
    @Nullable
    private Point lastLineOriginPx;

    /** The zoomed scale as of the last successful bounds update. */
    private double lastViewPxPerSs;

    /** The ink rectangle the current bounds were computed from, in line-relative Ss. */
    @Nullable
    private Rectangle2D lastInkBoundsSs;

    /** The target line's origin expressed in this component's own pixel coordinates. */
    private final Point lineOriginInComponentPx = new Point();

    /**
     * Whether this overlay suppresses the system cursor while visible — see
     * {@link #setHidesCursor(boolean)}.
     */
    private boolean hidesCursor;

    protected LineOverlayComponent(OverlayHost host) {
        this.host = host;
        setOpaque(false);

        // Swing defaults a fresh component to visible, but an overlay has no target and no
        // bounds until something sets one — and updateHostCursor() reads isVisible() across the
        // host's children, so a never-targeted overlay that still claims to be visible would
        // suppress the system cursor over the whole score on another overlay's behalf. Nothing
        // paints either way (the bounds are 0x0); this keeps the flag honest rather than relying
        // on the first validation pass to correct it.
        setVisible(false);
    }

    /**
     * Declares this component a validate root, so that an overlay is treated as the top of its
     * own layout subtree rather than as a participant in the host's.
     *
     * <p><b>This does not currently suppress re-invalidation of the host.</b> The intent was
     * that the {@link #setBounds} calls made from the host's {@code validateTree()} would not
     * mark the host invalid again and provoke a second validation pass, but AWT gates that
     * suppression in {@code Container.invalidateParent()} behind the
     * {@code java.awt.smartInvalidate} system property, which defaults to {@code false} and is
     * not set by this project's build or launch scripts. With it unset, the parent chain is
     * invalidated regardless of what this method returns.
     *
     * <p>Correctness does not depend on the suppression — overlay bounds are refreshed from the
     * host's {@code validateTree()} after {@code super.validateTree()} has laid the subtree out,
     * and an extra validation pass costs a repeat of that same idempotent work. Enabling
     * {@code smartInvalidate} would change AWT invalidation behavior application-wide, so it is
     * deliberately left alone rather than switched on for this optimization.
     */
    @Override
    public boolean isValidateRoot() {
        return true;
    }

    /** Returns the container this overlay is a child of and computes its coordinates against. */
    public final OverlayHost getHost() {
        return host;
    }

    /**
     * Re-homes this overlay onto a different host, removing it from the old one and adding it
     * to the new one.
     * <p>
     * Under pagination a {@code LinePanel} can be reparented onto a different page, at which
     * point the overlay's target no longer descends from its host and the overlay must follow
     * the line rather than merely hide.
     */
    public final void setHost(OverlayHost newHost) {
        if (newHost == host) {
            return;
        }

        host.removeOverlay(this);

        // Recompute against the host being left before adopting the new one, so the old host
        // stops suppressing the cursor on this overlay's behalf.
        updateHostCursor();
        host = newHost;
        forgetCachedGeometry();
        newHost.addOverlay(this);
        updateBounds();
    }

    /** Returns the line this overlay is attached to, or null when it has none. */
    public final @Nullable LineComponent getTargetLine() {
        return targetLine;
    }

    /** Attaches this overlay to {@code line}, or detaches it when {@code line} is null. */
    public final void setTargetLine(@Nullable LineComponent line) {
        if (line == targetLine) {
            return;
        }

        targetLine = line;
        forgetCachedGeometry();
        updateBounds();
    }

    /**
     * The ink rectangle this overlay's current bounds were computed from, in line-relative staff
     * spaces, or null while it is hidden.
     * <p>
     * Current whenever {@link #renderOverlay(Graphics2D)} runs: the overlay is only made visible
     * after a successful recompute, and any change to the ink goes through
     * {@link #inkDidChange()}. A subclass whose drawn geometry is derivable from its bounds
     * reads it here rather than calling {@link #getInkBoundsSs()} a second time per paint.
     */
    protected final @Nullable Rectangle2D getLastInkBoundsSs() {
        return lastInkBoundsSs;
    }

    /**
     * Returns this overlay's ink bounds in staff spaces, relative to the target line's origin,
     * or null when there is nothing to draw.
     * <p>
     * The rectangle must cover every pixel {@link #renderOverlay(Graphics2D)} touches,
     * <b>including stroke width</b>, which is geometry rather than antialiasing.
     */
    protected abstract @Nullable Rectangle2D getInkBoundsSs();

    /**
     * Draws this overlay in the target line's staff-space coordinates.
     * <p>
     * The graphics context has already had its rendering hints, scale and translate applied by
     * {@link #paintComponent(Graphics)}; implementations must not re-apply the zoom factor.
     */
    protected abstract void renderOverlay(Graphics2D g2);

    /**
     * Sets whether this overlay suppresses the system cursor on its target line while visible,
     * applying the change immediately.
     * <p>
     * Off until something turns it on, which today only the hover preview does: it draws exactly
     * where the system arrow would otherwise sit, obscuring the ink it exists to show, so the
     * arrow is suppressed while the pointer rests on it and restored the moment the mouse moves
     * again — see {@code PreviewElementManager.cursorDidMove}.
     * <p>
     * Both this component's cursor and the host's have to be set. The host covers the line
     * underneath, but wherever this overlay's own ink lies it is itself the deepest component
     * under the pointer, and Swing resolves the cursor from the deepest component regardless of
     * whether that component has mouse listeners.
     * <p>
     * Package-private rather than protected: this is driven by the managers that own the
     * overlays, not overridden or called by subclasses, and no subclass outside this package has
     * business reaching for it.
     */
    final void setHidesCursor(boolean hides) {
        if (hides == hidesCursor) {
            return;
        }

        hidesCursor = hides;
        setCursor(hides ? UIUtils.HIDDEN_CURSOR : null);
        updateHostCursor();
    }

    /**
     * Re-resolves this overlay's target from its owning manager's current state.
     * <p>
     * Called after the line components have been recreated, so an overlay recovers on the spot
     * instead of waiting for the mouse-motion event that would normally retarget it. Without
     * this, the preview would wink out on every edit that rebuilds the layout and stay hidden
     * until the pointer moved. The default is a no-op for overlays with no external target
     * state.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void retarget() {
        // Overridden by overlays whose target is owned by a manager.
    }

    /**
     * Recomputes this overlay's bounds from its ink and its target line's current position, and
     * hides it when there is nothing to draw.
     * <p>
     * Cheap enough to call from every validation pass: when neither the line's origin within
     * the host nor the zoom has changed, it costs a point comparison and returns. Subclasses
     * whose ink itself changed must call {@link #inkDidChange()} instead.
     */
    public final void updateBounds() {
        var line = targetLine;
        var hostComponent = host.getHostComponent();

        // A line component from a previous layout rebuild is stale — it is no longer in the
        // host's hierarchy, so its origin there is meaningless. Under pagination this also
        // catches a line reparented onto a different page than this overlay's host.
        if (line == null || !SwingUtilities.isDescendingFrom(line, hostComponent)) {
            hideOverlay();
            return;
        }

        var lineOriginPx = SwingUtilities.convertPoint(line, 0, 0, hostComponent);
        var viewPxPerSs = line.getViewPixelsPerStaffSpace();

        if (isVisible() && lineOriginPx.equals(lastLineOriginPx) && viewPxPerSs == lastViewPxPerSs) {
            return;
        }

        var inkSs = getInkBoundsSs();

        if (inkSs == null) {
            hideOverlay();
            return;
        }

        // Round outwards to whole device pixels before padding, so the pad is a true extra
        // pixel rather than part of the rounding.
        var leftPx = (int) Math.floor(lineOriginPx.x + inkSs.getMinX() * viewPxPerSs) - INK_PAD_PX;
        var topPx = (int) Math.floor(lineOriginPx.y + inkSs.getMinY() * viewPxPerSs) - INK_PAD_PX;
        var rightPx = (int) Math.ceil(lineOriginPx.x + inkSs.getMaxX() * viewPxPerSs) + INK_PAD_PX;
        var bottomPx = (int) Math.ceil(lineOriginPx.y + inkSs.getMaxY() * viewPxPerSs) + INK_PAD_PX;

        lastLineOriginPx = lineOriginPx;
        lastViewPxPerSs = viewPxPerSs;
        lastInkBoundsSs = inkSs;

        // Derived from the final bounds rather than from the ink origin, so the paint translate
        // carries no rounding error relative to the bounds actually set.
        lineOriginInComponentPx.setLocation(lineOriginPx.x - leftPx, lineOriginPx.y - topPx);

        setBounds(leftPx, topPx, rightPx - leftPx, bottomPx - topPx);
        setVisible(true);
        updateHostCursor();
    }

    /**
     * Forces a full bounds recompute on the next {@link #updateBounds()}, bypassing the
     * unchanged-position early-out. Subclasses call this whenever the ink they would draw
     * changes.
     */
    protected final void inkDidChange() {
        forgetCachedGeometry();
        updateBounds();
    }

    /**
     * Establishes the transform that maps the target line's staff-space coordinates onto this
     * component's own pixel origin, then hands off to {@link #renderOverlay(Graphics2D)}.
     * <p>
     * The rendering hints are applied through {@link GraphicUtils#setRenderingHints} — the same
     * call used when a line paints itself and when a display list is recorded. Glyph vectors are
     * bound to the {@link FontRenderContext} they were created under, so hints
     * that differ between record and replay shift glyph ink subtly. Do not substitute a
     * hand-rolled hint set here.
     */
    @Override
    protected final void paintComponent(Graphics g) {
        var line = targetLine;

        if (line == null || lastInkBoundsSs == null) {
            return;
        }

        var g2 = (Graphics2D) g.create();

        try {
            GraphicUtils.setRenderingHints(g2);

            // The display list is in the line's coordinate space; this component's graphics
            // origin is its own top-left, so shift back to where the line's origin falls.
            g2.translate(lineOriginInComponentPx.x, lineOriginInComponentPx.y);

            var scale = line.getViewPixelsPerStaffSpace();
            g2.scale(scale, scale);

            renderOverlay(g2);
        } finally {
            g2.dispose();
        }
    }

    private void hideOverlay() {
        forgetCachedGeometry();

        // Already hidden means the host cursor already reflects this overlay's absence, and
        // recomputing it would walk the host's children for nothing. The host's validateTree()
        // calls updateBounds() on every overlay whenever any one of them moves, so the
        // inactive ones land here on every pass.
        if (!isVisible()) {
            return;
        }

        setVisible(false);

        // After setVisible, never before: the recompute reads this overlay's own visibility.
        updateHostCursor();
    }

    /**
     * Recomputes {@code hostComponent}'s cursor from the overlays currently parented to it,
     * suppressing the system cursor while any visible one has opted in via
     * {@link #setHidesCursor(boolean)}.
     * <p>
     * The host, not the target line, is the right place: it is the shared ancestor of both the
     * line and this overlay, so hiding the cursor there covers the line underneath as well as
     * the overlay's own ink (which additionally carries a hidden cursor of its own — see
     * {@link #setHidesCursor(boolean)}).
     * <p>
     * Derived from the children on every call rather than from the caller's own state, because a
     * host is shared by every overlay attached to it: one overlay showing or hiding must not
     * restore the cursor out from under another that is still suppressing it.
     * <p>
     * Clears to null rather than to {@link Cursor#getDefaultCursor()} so the host keeps
     * inheriting from its own ancestors; planting an explicit default is what breaks
     * inheritance for everything underneath.
     */
    private void updateHostCursor() {
        var hostComponent = host.getHostComponent();
        var hideCursor = false;

        // Indexed rather than getComponents(), which returns a defensive copy: this runs on
        // every overlay show/hide, and the same traversal is used by the host's own bounds
        // refresh, so both read the hierarchy the same way.
        for (var i = 0; i < hostComponent.getComponentCount(); i++) {
            if (hostComponent.getComponent(i) instanceof LineOverlayComponent overlay
                && overlay.isVisible()
                && overlay.hidesCursor) {
                hideCursor = true;
                break;
            }
        }

        hostComponent.setCursor(hideCursor ? UIUtils.HIDDEN_CURSOR : null);
    }

    private void forgetCachedGeometry() {
        lastLineOriginPx = null;
        lastViewPxPerSs = 0;
        lastInkBoundsSs = null;
    }
}
