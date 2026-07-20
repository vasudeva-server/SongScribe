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

package songscribe.ui.component;

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

/**
 * Paint-only pill shown over the score while paste mode is active, naming the
 * mode and its exits. Added directly to {@link MainFrame}'s {@link JLayeredPane}
 * by {@code PasteModeManager} — never as the glass pane, which {@link ActivationGate}
 * owns exclusively.
 * <p>
 * Deliberately has no mouse listeners: a listener-free component is never selected
 * as an AWT mouse-event target, so clicks (including ones over the pill) fall through
 * to the score underneath, exactly as paste-mode placement requires.
 * <p>
 * Bounds are set externally by the owning {@code PasteModeManager} (there is no layout
 * manager for a {@link JLayeredPane} child); centering the pill over the score's visible
 * viewport is computed fresh on every paint via {@link SwingUtilities#convertRectangle}.
 */
public final class PasteOverlay extends JComponent {

    // Vertical offset from the viewport's top edge down to the pill's top edge.
    private static final int TOP_MARGIN_PX = 10;

    private static final int HORIZONTAL_PADDING_PX = 16;
    private static final int VERTICAL_PADDING_PX = 8;
    private static final int LINE_GAP_PX = 4;

    private static final float TITLE_FONT_SIZE_PX = 14f;
    private static final float HINT_FONT_SIZE_PX = 12f;

    private final ScoreView scoreView;

    public PasteOverlay(ScoreView scoreView) {
        this.scoreView = scoreView;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        var scrollPane = scoreView.getScoreScrollPane();

        if (scrollPane == null) {
            return;
        }

        var viewportBoundsPx = SwingUtilities.convertRectangle(
            scrollPane, scrollPane.getViewport().getBounds(), this);

        var g2 = (Graphics2D) g.create();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            var title = Strings.get(Strings.PASTE_MODE_TITLE);
            var hint = Strings.get(Strings.PASTE_MODE_HINT);

            var baseFont = getFont();
            var titleFont = baseFont.deriveFont(Font.BOLD, TITLE_FONT_SIZE_PX);
            var hintFont = baseFont.deriveFont(Font.PLAIN, HINT_FONT_SIZE_PX);

            var titleMetrics = g2.getFontMetrics(titleFont);
            var hintMetrics = g2.getFontMetrics(hintFont);

            var textWidthPx = Math.max(
                titleMetrics.stringWidth(title), hintMetrics.stringWidth(hint));
            var pillWidthPx = textWidthPx + HORIZONTAL_PADDING_PX * 2;
            var pillHeightPx = titleMetrics.getHeight() + hintMetrics.getHeight()
                + LINE_GAP_PX + VERTICAL_PADDING_PX * 2;

            var pillXPx = viewportBoundsPx.x + (viewportBoundsPx.width - pillWidthPx) / 2;
            var pillYPx = viewportBoundsPx.y + TOP_MARGIN_PX;

            var arcPx = FlatLafProps.getInt(FlatLafKey.PASTE_OVERLAY_ARC);
            g2.setColor(FlatLafProps.getColor(FlatLafKey.PASTE_OVERLAY_BACKGROUND));
            g2.fillRoundRect(pillXPx, pillYPx, pillWidthPx, pillHeightPx, arcPx, arcPx);

            g2.setColor(FlatLafProps.getColor(FlatLafKey.PASTE_OVERLAY_FOREGROUND));

            g2.setFont(titleFont);
            var titleYPx = pillYPx + VERTICAL_PADDING_PX + titleMetrics.getAscent();
            g2.drawString(title, pillXPx + (pillWidthPx - titleMetrics.stringWidth(title)) / 2, titleYPx);

            g2.setFont(hintFont);
            var hintYPx = titleYPx + titleMetrics.getDescent() + LINE_GAP_PX + hintMetrics.getAscent();
            g2.drawString(hint, pillXPx + (pillWidthPx - hintMetrics.stringWidth(hint)) / 2, hintYPx);
        } finally {
            g2.dispose();
        }
    }
}
