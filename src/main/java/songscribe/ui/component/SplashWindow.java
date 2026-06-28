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

package songscribe.ui.component;

import module java.desktop;

import java.lang.reflect.InvocationTargetException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Version;
import songscribe.error.RuntimeError;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

public class SplashWindow extends JWindow {

    private static final Logger LOG = LoggerFactory.getLogger(SplashWindow.class);
    private static @Nullable BufferedImage splashImage = null;
    private static final Color bg = FlatLafProps.getColor(FlatLafKey.SPLASH_WINDOW_BACKGROUND);

    public SplashWindow() {
        super((Frame) null);
        init();
    }

    public static void loadSplashImage() {
        var image = GraphicUtils.readImageResource("/images/splash.jpg");

        if (image != null) {
            splashImage = image;
        } else {
            throw RuntimeError.missingResource("Failed to load splash image");
        }
    }

    public static JPanel createContentPanel() {
        return createContentPanel(null);
    }

    public static JPanel createContentPanel(@Nullable JComponent extraContent) {
        loadSplashImage();
        var content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);   // paints the background color first

                // FlatLaf leaves AATextInfo null, so this window's default Graphics
                // paints text with no antialiasing and integer metrics, making it wider
                // than Swing measured the labels — clipping the last glyph. Apply the
                // project's unified hints (same as the score) so painted text matches
                // measurement; child labels inherit these hints from this Graphics.
                if (g instanceof Graphics2D g2) {
                    GraphicUtils.setRenderingHints(g2);
                }

                if (splashImage != null) {
                    // Center the image
                    var x = (getWidth() - splashImage.getWidth()) / 2;
                    var y = (getHeight() - splashImage.getHeight()) / 2;
                    g.drawImage(splashImage, x, y, this);
                }
            }

            // Give the panel a proper preferred size based on the image
            @Override
            public Dimension getPreferredSize() {
                if (splashImage != null) {
                    return new Dimension(
                        splashImage.getWidth(null),
                        splashImage.getHeight(null)
                    );
                }

                return new Dimension(0, 0); // fallback size
            }
        };

        content.setBackground(bg);
        content.setOpaque(true);

        // Add a panel for the version number and copyright
        var infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(bg);
        infoPanel.setBorder(UIUtils.spacingBorder(FlatLafKey.SPLASH_WINDOW_PADDING));
        content.add(infoPanel, BorderLayout.SOUTH);

        var versionRow = new JPanel(new BorderLayout());
        versionRow.setOpaque(false);
        versionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionRow.add(
            createLabel("SongScribe Writer " + Version.PUBLIC_VERSION),
            BorderLayout.WEST
        );
        versionRow.add(
            createLabel("©" + Utils.getCurrentYear() + " Sri Chinmoy Centres International"),
            BorderLayout.EAST
        );
        infoPanel.add(versionRow);

        if (extraContent != null) {
            infoPanel.add(extraContent);
        }

        return content;
    }

    public void showSplash() {
        setVisible(true);
    }

    public void closeSplash() {
        Runnable close = () -> {
            setVisible(false);
            dispose();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            close.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(close);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                LOG.error("Failed to close splash window", e);
            }
        }
    }

    private void init() {
        setContentPane(createContentPanel());
        setBackground(bg);
        pack();
        setLocationRelativeTo(null);
    }

    private static JLabel createLabel(String text) {
        var label = new JLabel(text);
        label.setForeground(FlatLafProps.getColor(FlatLafKey.SPLASH_WINDOW_FOREGROUND));
        return label;
    }
}
