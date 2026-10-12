/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component.score;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import org.jspecify.annotations.Nullable;

import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

/**
 * The tooltip of a lint mark: the app's tooltip with an arrow on its top or bottom edge, whose
 * tip touches the mark.
 * <p>
 * On macOS, FlatLaf frames every tooltip with a native rounded window border. That border follows
 * the window's rectangle, so it cannot follow an arrow. This tooltip turns the native frame off
 * and paints its own outline: the rounded body and the arrow as one shape, in the tooltip
 * theme's background, border color and corner radius.
 * <p>
 * The arrow points at the mark wherever the tooltip ends up. The tooltip manager pushes a tooltip
 * that would leave the screen back onto it, so the arrow's place along the edge comes from where
 * the tooltip is on the screen when it paints, not from where it was asked to go.
 */
final class LintToolTip extends JToolTip {

    /** The edge of the tooltip the arrow stands on: the one that faces the mark. */
    enum ArrowEdge {
        TOP,
        BOTTOM
    }

    /**
     * What the arrow points at.
     *
     * @param anchor the point the arrow's tip touches, in the owner's coordinates
     * @param edge   the edge the arrow stands on
     */
    record Target(Point anchor, ArrowEdge edge) { }

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final JComponent owner;
    private final Target target;
    private final Dimension arrowSize;

    /** The look of the popup window this tooltip made transparent, to restore when it leaves. */
    private @Nullable WindowLook replacedWindowLook = null;

    /**
     * Creates the tooltip of a mark of {@code owner}.
     *
     * @param owner  the component whose mark the tooltip is for
     * @param target what the arrow points at
     */
    LintToolTip(JComponent owner, Target target) {
        this.owner = owner;
        this.target = target;
        arrowSize = FlatLafProps.getDimension(FlatLafKey.LINT_TOOLTIP_ARROW_SIZE);
        setComponent(owner);
        setOpaque(false);
        putClientProperty(FlatClientProperties.POPUP_DROP_SHADOW_PAINTED, false);
    }

    /**
     * Returns where the arrow's tip is, from the tooltip's top-left corner, at the tooltip's
     * preferred size and while the tooltip is where it was asked to go. Placing the tooltip at the
     * anchor less this offset puts the tip on the anchor.
     *
     * @return the offset, in pixels
     */
    Point arrowTipOffset() {
        var x = FlatLafProps.getInt(FlatLafKey.LINT_TOOLTIP_ARROW_INSET) + arrowSize.width / 2;
        var y = target.edge() == ArrowEdge.TOP ? 0 : getPreferredSize().height;
        return new Point(x, y);
    }

    /**
     * Returns the border's insets plus the arrow's height on the arrow's edge, so the text keeps
     * clear of the arrow.
     */
    @Override
    public Insets getInsets() {
        return getInsets(new Insets(0, 0, 0, 0));
    }

    @Override
    public Insets getInsets(Insets insets) {
        var borderInsets = super.getInsets(insets);

        if (target.edge() == ArrowEdge.TOP) {
            borderInsets.top += arrowSize.height;
        } else {
            borderInsets.bottom += arrowSize.height;
        }

        return borderInsets;
    }

    /**
     * Makes a separate popup window transparent while this tooltip is in it, so the corners and
     * the space beside the arrow show what is behind. A tooltip inside the owner's window needs
     * nothing: its popup container takes the tooltip's own opacity.
     * <p>
     * Swing and FlatLaf reuse a popup window for later popups, so {@link #removeNotify} gives the
     * window back its look.
     */
    @Override
    public void addNotify() {
        super.addNotify();

        var window = SwingUtilities.getWindowAncestor(this);

        if (!(window instanceof JWindow popupWindow)
            || window == SwingUtilities.getWindowAncestor(owner)
            || !window.getGraphicsConfiguration().getDevice()
                      .isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            return;
        }

        replacedWindowLook = WindowLook.of(popupWindow);
        popupWindow.setBackground(TRANSPARENT);
        popupWindow.getRootPane().setOpaque(false);
        ((JComponent) popupWindow.getContentPane()).setOpaque(false);
    }

    @Override
    public void removeNotify() {
        if (replacedWindowLook != null) {
            replacedWindowLook.restore();
            replacedWindowLook = null;
        }

        super.removeNotify();
    }

    /**
     * Paints the outline, then lets the UI paint the text over it. The UI paints no background,
     * because the tooltip is not opaque.
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        var g2d = (Graphics2D) graphics.create();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            var lineBorder = getBorder() instanceof FlatLineBorder flatLineBorder ? flatLineBorder : null;
            var lineWidth = lineBorder == null ? 0f : lineBorder.getLineThickness();
            var outline = outline(lineWidth);

            g2d.setColor(getBackground());
            g2d.fill(outline);

            if (lineBorder != null) {
                g2d.setColor(lineBorder.getLineColor());
                g2d.setStroke(new BasicStroke(lineWidth));
                g2d.draw(outline);
            }
        } finally {
            g2d.dispose();
        }

        super.paintComponent(graphics);
    }

    /**
     * Paints nothing: {@link #paintComponent} draws the border's line around the outline, which a
     * rectangular border cannot follow.
     */
    @Override
    protected void paintBorder(Graphics graphics) {
    }

    /**
     * Returns the rounded body and the arrow as one shape, inset by half the line width so a line
     * drawn along it stays inside the tooltip.
     */
    private Shape outline(float lineWidth) {
        var halfLineWidth = lineWidth / 2;
        var cornerRadius = UIManager.getInt("ToolTip.borderCornerRadius");
        var arcDiameter = cornerRadius * 2f;
        var left = halfLineWidth;
        var right = getWidth() - halfLineWidth;
        var top = halfLineWidth;
        var bottom = getHeight() - halfLineWidth;
        var bodyTop = target.edge() == ArrowEdge.TOP ? top + arrowSize.height : top;
        var bodyBottom = target.edge() == ArrowEdge.TOP ? bottom : bottom - arrowSize.height;
        var body = new RoundRectangle2D.Float(
            left, bodyTop, right - left, bodyBottom - bodyTop, arcDiameter, arcDiameter);

        // The arrow's base keeps clear of the rounded corners.
        var halfArrowWidth = arrowSize.width / 2f;
        var cornerClearance = cornerRadius + halfArrowWidth;
        var tipX = Math.clamp(anchorX(), left + cornerClearance, right - cornerClearance);
        var baseY = target.edge() == ArrowEdge.TOP ? bodyTop : bodyBottom;
        var tipY = target.edge() == ArrowEdge.TOP ? top : bottom;

        var arrow = new Path2D.Float();
        arrow.moveTo(tipX - halfArrowWidth, baseY);
        arrow.lineTo(tipX, tipY);
        arrow.lineTo(tipX + halfArrowWidth, baseY);
        arrow.closePath();

        var outline = new Area(body);
        outline.add(new Area(arrow));
        return outline;
    }

    /** Returns the anchor's x in this tooltip's coordinates, from where both are on the screen. */
    private float anchorX() {
        var anchorOnScreen = new Point(target.anchor());
        SwingUtilities.convertPointToScreen(anchorOnScreen, owner);
        return anchorOnScreen.x - getLocationOnScreen().x;
    }

    /**
     * The parts of a popup window's look that this tooltip changes to make the window transparent.
     */
    private record WindowLook(JWindow window, Color background, boolean rootPaneOpaque, boolean contentPaneOpaque) {

        static WindowLook of(JWindow window) {
            return new WindowLook(
                window,
                window.getBackground(),
                window.getRootPane().isOpaque(),
                ((JComponent) window.getContentPane()).isOpaque());
        }

        void restore() {
            window.setBackground(background);
            window.getRootPane().setOpaque(rootPaneOpaque);
            ((JComponent) window.getContentPane()).setOpaque(contentPaneOpaque);
        }
    }
}
