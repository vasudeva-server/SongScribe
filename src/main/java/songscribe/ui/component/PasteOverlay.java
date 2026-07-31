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

import com.formdev.flatlaf.FlatClientProperties;
import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

/**
 * Pill shown over the score while paste mode is active, naming the mode and its
 * exits. Added directly to {@link MainFrame}'s {@link JLayeredPane} by
 * {@code PasteModeManager} — never as the glass pane, which {@link ActivationGate}
 * owns exclusively.
 * <p>
 * Deliberately has no mouse listeners: a listener-free component is never selected
 * as an AWT mouse-event target, so clicks (including ones over the pill) fall through
 * to the score underneath, exactly as paste-mode placement requires.
 * <p>
 * Bounds are set externally by the owning {@code PasteModeManager} (there is no layout
 * manager for a {@link JLayeredPane} child). This component in turn positions the pill
 * itself: centered over the score's visible viewport, sized to the pill's Swing-computed
 * preferred size, recomputed in {@link #doLayout}.
 */
public final class PasteOverlay extends JComponent {

    // Vertical offset from the viewport's top edge down to the pill's top edge.
    private static final int TOP_MARGIN_PX = 10;

    private final ScoreView scoreView;
    private final Pill pill;

    public PasteOverlay(ScoreView scoreView) {
        this.scoreView = scoreView;
        setOpaque(false);
        setLayout(null);

        pill = new Pill();
        add(pill);
    }

    @Override
    public void doLayout() {
        var scrollPane = scoreView.getScoreScrollPane();

        if (scrollPane == null) {
            return;
        }

        var viewportBoundsPx = SwingUtilities.convertRectangle(
            scrollPane, scrollPane.getViewport().getBounds(), this);

        var pillSizePx = pill.getPreferredSize();
        var pillXPx = viewportBoundsPx.x + (viewportBoundsPx.width - pillSizePx.width) / 2;
        var pillYPx = viewportBoundsPx.y + TOP_MARGIN_PX;

        pill.setBounds(pillXPx, pillYPx, pillSizePx.width, pillSizePx.height);
    }

    /** Rounded background painted behind the title/hint labels. */
    private static final class Pill extends JPanel {

        private static final int HORIZONTAL_PADDING_PX = 16;
        private static final int VERTICAL_PADDING_PX = 8;
        private static final int LINE_GAP_PX = 4;

        Pill() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(
                VERTICAL_PADDING_PX, HORIZONTAL_PADDING_PX, VERTICAL_PADDING_PX, HORIZONTAL_PADDING_PX));

            var foreground = FlatLafProps.getColor(FlatLafKey.PASTE_OVERLAY_FOREGROUND);

            var titleLabel = new JLabel(Strings.get(Strings.PASTE_MODE_TITLE));
            titleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "medium");
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
            titleLabel.setForeground(foreground);
            titleLabel.setAlignmentX(CENTER_ALIGNMENT);

            var hintLabel = new JLabel(Strings.get(Strings.PASTE_MODE_HINT));
            hintLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
            hintLabel.setForeground(foreground);
            hintLabel.setAlignmentX(CENTER_ALIGNMENT);

            add(titleLabel);
            add(Box.createVerticalStrut(LINE_GAP_PX));
            add(hintLabel);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();

            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                var arcPx = FlatLafProps.getInt(FlatLafKey.PASTE_OVERLAY_ARC);
                g2.setColor(FlatLafProps.getColor(FlatLafKey.PASTE_OVERLAY_BACKGROUND));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arcPx, arcPx);
            } finally {
                g2.dispose();
            }

            super.paintComponent(g);
        }
    }
}
