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

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.Calendar;

import javax.swing.*;

import org.jdesktop.layout.GroupLayout;
import org.jdesktop.layout.LayoutStyle;

import songscribe.Version;
import songscribe.data.MyDesktop;
import songscribe.ui.component.MainFrame;
import songscribe.util.Utils;

public class AboutDialog extends StandardDialog {

    public static final String WEB = "http://www.songscribe.org";
    public static final String LICENSE = "GPL (General Public License)";

    public AboutDialog(MainFrame mainFrame) {
        super("About");
        var tabPane = new JTabbedPane();
        tabPane.addTab("About", makeAboutPanel());

        try {
            tabPane.add(
                "Read me",
                createTextPane(
                    "file:" + Utils.getResourcePath("help/About.html")
                )
            );
        } catch (IOException e) {
            // Ignore
        }

        try {
            tabPane.add(
                "License agreement",
                createTextPane("file:license.txt")
            );
        } catch (IOException e) {
            //    // Ignore
        }

        try {
            tabPane.add(
                "Acknowledgements",
                createTextPane(
                    "file:" +
                        Utils.getResourcePath("help/Acknowledgements.html")
                )
            );
        } catch (IOException e) {
            //    // Ignore
        }

        var southPanel = new JPanel(new BorderLayout());
        southPanel.add(okButton, BorderLayout.SOUTH);
        contentPanel.add(BorderLayout.CENTER, tabPane);
        contentPanel.add(BorderLayout.SOUTH, southPanel);
    }

    private static Component createTextPane(String url) throws IOException {
        var textPane = new JTextPane();
        textPane.setPage(url);
        textPane.setEditable(false);
        var textScroll = new JScrollPane(textPane);
        textScroll.setPreferredSize(new Dimension(400, 200));
        return textScroll;
    }

    private JPanel makeAboutPanel() {
        var iconLabel = new JLabel();
        var progNameLabel = new JLabel();
        var versionLabel = new JLabel();
        var version = new JLabel();
        var copyrightLabel = new JLabel();
        var copyRight1 = new JLabel();
        var copyRight2 = new JLabel();
        var licenseLabel = new JLabel();
        var license = new JLabel();
        var webLabel = new JLabel();
        var web = new JLabel();
        var emailLabel = new JLabel();
        var email = new JLabel();

        iconLabel.setIcon(new ImageIcon(mainFrame.getIconImage()));

        progNameLabel.setFont(new Font("Arial", Font.PLAIN, 30));
        progNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        progNameLabel.setText(mainFrame.appName);

        versionLabel.setFont(new Font("Arial", Font.BOLD, 14));
        versionLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        versionLabel.setText("Version:");

        version.setFont(new Font("Arial", Font.PLAIN, 14));
        version.setText(Version.PUBLIC_VERSION);

        copyrightLabel.setFont(new Font("Arial", Font.BOLD, 14));
        copyrightLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        copyrightLabel.setText("Copyright:");

        copyRight1.setFont(new Font("Arial", Font.PLAIN, 14));
        copyRight1.setText(
            "© " +
                Calendar.getInstance().get(Calendar.YEAR) +
                " Sri Chinmoy Centres International"
        );

        copyRight2.setFont(new Font("Arial", Font.PLAIN, 14));
        copyRight2.setText("All rights reserved.");

        licenseLabel.setFont(new Font("Arial", Font.BOLD, 14));
        licenseLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        licenseLabel.setText("License:");

        license.setFont(new Font("Arial", Font.PLAIN, 14));
        license.setText(LICENSE);

        webLabel.setFont(new Font("Arial", Font.BOLD, 14));
        webLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        webLabel.setText("Web:");

        web.setFont(new Font("Arial", Font.PLAIN, 14));
        web.setText(WEB);

        if (MyDesktop.isDesktopSupported()) {
            web.setForeground(new Color(0, 0, 204));
            web.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        webMouseClicked();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        webMouseEntered();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        webMouseExited();
                    }
                }
            );
        }

        emailLabel.setFont(new Font("Arial", Font.BOLD, 14));
        emailLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        emailLabel.setText("E-mail:");

        email.setFont(new Font("Arial", Font.PLAIN, 12));
        email.setText(ReportBugDialog.BUG_EMAIL);

        if (MyDesktop.isDesktopSupported()) {
            email.setForeground(new Color(0, 0, 204));
            email.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        emailMouseClicked();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        webMouseEntered();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        webMouseExited();
                    }
                }
            );
        }

        var aboutPanel = new JPanel();
        var layout = new GroupLayout(aboutPanel);
        aboutPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    layout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(iconLabel)
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.TRAILING)
                                .add(
                                    GroupLayout.LEADING,
                                    layout
                                        .createSequentialGroup()
                                        .add(6, 6, 6)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.LEADING
                                                )
                                                .add(
                                                    versionLabel,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    copyrightLabel,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    82,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    licenseLabel,
                                                    GroupLayout.PREFERRED_SIZE,
                                                    82,
                                                    GroupLayout.PREFERRED_SIZE
                                                )
                                                .add(
                                                    webLabel,
                                                    GroupLayout.PREFERRED_SIZE,
                                                    82,
                                                    GroupLayout.PREFERRED_SIZE
                                                )
                                                .add(
                                                    emailLabel,
                                                    GroupLayout.PREFERRED_SIZE,
                                                    82,
                                                    GroupLayout.PREFERRED_SIZE
                                                )
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.TRAILING
                                                )
                                                .add(
                                                    version,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    copyRight1,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    license,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    copyRight2,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    web,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                                .add(
                                                    email,
                                                    GroupLayout.DEFAULT_SIZE,
                                                    197,
                                                    Short.MAX_VALUE
                                                )
                                        )
                                )
                                .add(
                                    progNameLabel,
                                    GroupLayout.DEFAULT_SIZE,
                                    291,
                                    Short.MAX_VALUE
                                )
                        )
                        .addContainerGap()
                )
        );
        layout.setVerticalGroup(
            layout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    layout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.LEADING)
                                .add(
                                    iconLabel,
                                    GroupLayout.DEFAULT_SIZE,
                                    190,
                                    Short.MAX_VALUE
                                )
                                .add(
                                    layout
                                        .createSequentialGroup()
                                        .add(
                                            progNameLabel,
                                            GroupLayout.PREFERRED_SIZE,
                                            43,
                                            GroupLayout.PREFERRED_SIZE
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.BASELINE
                                                )
                                                .add(versionLabel)
                                                .add(version)
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.BASELINE
                                                )
                                                .add(copyrightLabel)
                                                .add(copyRight1)
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(copyRight2)
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.BASELINE
                                                )
                                                .add(licenseLabel)
                                                .add(license)
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.BASELINE
                                                )
                                                .add(webLabel)
                                                .add(web)
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.BASELINE
                                                )
                                                .add(emailLabel)
                                                .add(email)
                                        )
                                )
                        )
                        .addContainerGap()
                )
        );
        return aboutPanel;
    }

    private void webMouseExited() {
        contentPanel.setCursor(Cursor.getDefaultCursor());
    }

    private void webMouseEntered() {
        contentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void webMouseClicked() {
        Utils.openWebPage(mainFrame, WEB);
    }

    private void emailMouseClicked() {
        Utils.openEmail(
            mainFrame,
            ReportBugDialog.BUG_EMAIL + "?SUBJECT=SongScribe comment"
        );
    }

    @Override
    protected void getData() {
    }

    @Override
    protected void setData() {
    }
}
