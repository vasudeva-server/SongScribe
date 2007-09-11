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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.*;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.ui.component.MainFrame;

public class UpdateDialog extends StandardDialog {

    private static final String downloadUri = "";
    private final JLabel label = new JLabel("Checking for updates...");

    public UpdateDialog(MainFrame mainFrame) {
        super("Update", true);
        buttonPanel.remove(applyButton);
        buttonPanel.remove(okButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
        var center = new JPanel();
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 10));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setPreferredSize(new Dimension(500, 70));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(label);
        center.add(Box.createVerticalStrut(10));
        contentPanel.add(BorderLayout.CENTER, center);
        var downloadButton = new JButton("Download");
        buttonPanel.add(downloadButton, 0);
        downloadButton.addActionListener(_ -> {
            try {
                Desktop.getDesktop().browse(new URI(downloadUri));
                setVisible(false);
            } catch (IOException | URISyntaxException ex) {
                label.setText(
                    label.getText() + " Navigate to: " + downloadUri
                );
            }
        });
        downloadButton.setVisible(false);
    }

    @Override
    protected void getData() {
        new UpdateInternetThread(false).start();
    }

    @Override
    protected void setData() {}

    public static class UpdateInternetThread extends Thread {

        public UpdateInternetThread(boolean automatic) {}

        @Override
        public void run() {
            //            HttpClient httpClient = new HttpClient();
            String platform;

            if (SystemInfo.isMacOS) {
                platform = "mac";
            } else if (SystemInfo.isWindows) {
                platform = "windows";
            } else if (SystemInfo.isLinux) {
                platform = "linux";
            } else {
                platform = "";
            }
            //            GetMethod versionGetMethod = new GetMethod(Constants
            //            .VERSION_URL+platform);
            //          try {
            //                versionGetMethod.addRequestHeader(Constants.MAX_AGE_HEADER);
            //                httpClient.executeMethod(versionGetMethod);
            //                if (versionGetMethod.getStatusCode() != 200) {
            //                    throw new IOException("Invalid status code: " + versionGetMethod
            //                    .getStatusCode());
            //                }
            //                String remoteVersionJson = versionGetMethod.getResponseBodyAsString();
            //
            //                if (remoteVersionJson != null) {
            //                    Gson gson = new Gson();
            //                    RemoteVersion remoteVersion = gson.fromJson(remoteVersionJson,
            //                    RemoteVersion.class);
            //
            //                    if (remoteVersion.currentVersion.compareTo(Version
            //                    .BUILD_VERSION) <=
            //                    0) {
            //                        label.setText("No update is available. You already have the
            //                        latest version.");
            //                    } else {
            //                        label.setText("New version available!");
            //                        downloadUri = remoteVersion.downloadUrl;
            //                        downloadButton.setVisible(true);
            //                        if (automatic) {
            //                            setVisible(true);
            //                        }
            //                    }
            //                }
            //
            //            } catch (IOException | RuntimeException e) {
            //                label.setText("Could not connect to the download site. Visit the
            //                SongScribe website and download the latest version.");
            //            } finally {
            //                versionGetMethod.releaseConnection();
            //            }
            //          } finally {
            //
        }
    }

    public static class RemoteVersion {

        private static final String currentVersion = null;
        private static final String downloadUrl = null;
    }
}
