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

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * Base class for the overlays that belong to a line but cannot be drawn by it — the hover
 * preview element, the paste-mode insertion marker and the slide previews.
 * <p>
 * Each overlay is a child of an {@link OverlayHost}, sized to exactly the ink it draws, because
 * the overlays span a staff-position range wider than a {@link LineComponent}'s bounds.
 *
 * <h2>No mouse listeners</h2>
 * This class registers no mouse or mouse-motion listeners: a listener-free component is never
 * an AWT mouse-event target, so clicks over an overlay fall through to the
 * {@link LineComponent} beneath, as note placement and selection require.
 */
public abstract class LineOverlayComponent extends JComponent {

    /**
     * Covers {@code STROKE_NORMALIZE} grid snapping and glyph hinting, which can nudge ink by up
     * to half a device pixel. Stroke width is <b>geometry, not antialiasing</b>: it is folded
     * into the recorded ink bounds, and this pad must never be relied on to absorb it.
     */
    private static final int INK_PAD_PX = 1;

    private OverlayHost host;

    /**
     * Null when it has none.
     */
    @Nullable
    private LineComponent targetLine;

    @Nullable
    private Point lastLineOriginPx;

    private double lastViewPxPerSs;

    @Nullable
    private Rectangle2D lastInkBoundsSs;

    private final Point lineOriginInComponentPx = new Point();

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
     * <b>This does not currently suppress re-invalidation of the host:</b> AWT gates that behind
     * the {@code java.awt.smartInvalidate} system property, which this project does not set.
     * Correctness does not depend on the suppression, and enabling the property would change AWT
     * invalidation application-wide.
     */
    @Override
    public boolean isValidateRoot() {
        return true;
    }

    public final OverlayHost getHost() {
        return host;
    }

    /**
     * Under pagination a {@code LinePanel} can be reparented onto another page, so the overlay
     * must follow the line rather than merely hide.
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

    /** Null when it has none. */
    public final @Nullable LineComponent getTargetLine() {
        return targetLine;
    }

    /** Detaches the overlay when {@code line} is null. */
    public final void setTargetLine(@Nullable LineComponent line) {
        if (line == targetLine) {
            return;
        }

        targetLine = line;
        forgetCachedGeometry();
        updateBounds();
    }

    /**
     * Null while the overlay is hidden. Current whenever {@link #renderOverlay(Graphics2D)} runs.
     */
    protected final @Nullable Rectangle2D getLastInkBoundsSs() {
        return lastInkBoundsSs;
    }

    /**
     * Null when there is nothing to draw. In staff spaces relative to the target line's origin;
     * must cover every pixel {@link #renderOverlay(Graphics2D)} touches, <b>including stroke
     * width</b>.
     */
    protected abstract @Nullable Rectangle2D getInkBoundsSs();

    /**
     * Draws in the line's staff-space coordinates; {@link #paintComponent(Graphics)} has applied
     * the hints, scale and translate, so implementations must not re-apply the zoom factor.
     */
    protected abstract void renderOverlay(Graphics2D g2);

    /**
     * Both this component's cursor and the host's must be set: the host covers the line
     * underneath, but over this overlay's own ink it is the deepest component under the pointer,
     * and Swing resolves the cursor from it regardless of mouse listeners. Package-private:
     * driven by the managers that own the overlays.
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
     * Called after the line components are recreated, so an overlay recovers without waiting for
     * a mouse-motion event. The default is a no-op.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void retarget() {
        // Overridden by overlays whose target is owned by a manager.
    }

    /**
     * Cheap enough for every validation pass. Subclasses whose ink changed must call
     * {@link #inkDidChange()} instead.
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

    protected final void inkDidChange() {
        forgetCachedGeometry();
        updateBounds();
    }

    /**
     * Hints must come from {@link GraphicUtils#setRenderingHints}, as when a line paints itself
     * and when a display list is recorded: glyph vectors are bound to the
     * {@link FontRenderContext} they were created under, so differing hints shift glyph ink.
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
     * The host is the right place: it is the shared ancestor of the line and this overlay.
     * Derived from the children on every call, because a host is shared by every overlay
     * attached to it and one must not restore the cursor out from under another that is still
     * suppressing it. Clears to null, not {@link Cursor#getDefaultCursor()}, which would break
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
