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
package songscribe.ui.dialog;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import songscribe.Strings;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.SplashWindow;
import songscribe.util.DesktopUtils;
import songscribe.util.Utils;

public class AboutDialog extends BaseDialog {

    public static final String WEB = "http://www.songscribe.org";
    private static final String ACKNOWLEDGEMENTS_URL =
        "https://github.com/vasudeva-server/SongScribe/blob/main/THIRD_PARTY_LICENSES.txt";

    public AboutDialog() {
        super(Strings.get(Strings.DIALOG_ABOUT_TITLE), true, DialogCategory.INFORMATIONAL);

        var gap = FlatLafProps.getInt(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);

        var divider = new JPanel();
        divider.setBackground(Color.LIGHT_GRAY);
        divider.setPreferredSize(new Dimension(0, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);

        var linksRow = new JPanel(new BorderLayout());
        linksRow.setOpaque(false);
        linksRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        linksRow.add(createLink(WEB, WEB), BorderLayout.WEST);
        linksRow.add(
            createLink(Strings.get(Strings.DIALOG_ABOUT_ACKNOWLEDGEMENTS), ACKNOWLEDGEMENTS_URL),
            BorderLayout.EAST
        );

        var extras = new JPanel();
        extras.setLayout(new BoxLayout(extras, BoxLayout.Y_AXIS));
        extras.setOpaque(false);
        extras.setAlignmentX(Component.LEFT_ALIGNMENT);
        extras.add(Box.createVerticalStrut(gap));
        extras.add(divider);
        extras.add(Box.createVerticalStrut(gap));
        extras.add(linksRow);

        contentPanel.add(BorderLayout.CENTER, SplashWindow.createContentPanel(extras));
    }

    @Override
    protected String getContentPaddingKey() {
        return FlatLafKeys.DIALOG_NO_PADDING;
    }

    private JLabel createLink(String text, String url) {
        var label = new JLabel(text);

        if (DesktopUtils.isDesktopSupported()) {
            label.setForeground(UIManager.getColor("Component.linkColor"));
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Utils.openWebPage(url);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    contentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    contentPanel.setCursor(Cursor.getDefaultCursor());
                }
            });
        }

        return label;
    }
}
