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
import java.io.File;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import songscribe.Version;
import songscribe.data.MyDesktop;
import songscribe.ui.component.MainFrame;
import songscribe.util.Utils;

public class ReportBugDialog extends StandardDialog {

    public static final String BUG_EMAIL = "himadri81@zoho.com";

    public ReportBugDialog(MainFrame mainFrame) {
        super("Bug report");
        var logFile = new File(MainFrame.SONGSCRIBE_DIR, "log");
        var area = getjEditorPane(logFile);
        contentPanel.add(area);
        buttonPanel.remove(applyButton);
        buttonPanel.remove(cancelButton);

        if (MyDesktop.isDesktopSupported()) {
            var sendBug = new JButton("Send a report");
            sendBug.addActionListener(_ -> {
                var answer = JOptionPane.showOptionDialog(
                    contentPanel,
                    "What would you like to send?",
                    mainFrame.appName,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[] { "Bug", "Request", "Cancel" },
                    null
                );

                if ((answer == 2) || (answer == JOptionPane.CLOSED_OPTION)) {
                    return;
                }

                var sb = new StringBuilder(270);
                sb.append(BUG_EMAIL);
                sb.append("?SUBJECT=SongScribe ");
                sb.append((answer == 0) ? "bug" : "request");

                if (answer == 0) {
                    sb.append("&ATTACHMENT=\"");
                    sb.append(logFile.getAbsolutePath());
                    sb.append('"');
                }

                sb.append("&BODY=Version: ");
                sb.append(Version.PUBLIC_VERSION);
                sb.append("\nOperation system: ");
                sb.append(System.getProperty("os.name"));
                sb.append("\nJVM version: ");
                sb.append(System.getProperty("java.vm.version"));
                sb.append(
                    "\nDescription:\n-------------Write your report here---------------"
                );

                try {
                    Utils.openEmail(mainFrame, sb.toString());
                } catch (Exception e1) {
                    mainFrame.showErrorMessage(
                        "Cannot open the e-mail client. Please make your report manually as " +
                        "described above."
                    );
                }
            });

            buttonPanel.add(sendBug);
        }

        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @NotNull
    private JEditorPane getjEditorPane(File logFile) {
        var area = new JEditorPane(
            "text/html",
            "<html><h1>Bug report</h1><p>If you encounter a program error, a bad behavior or just have a wish to improve the program, you are most welcome to write a bug report to the following e-mail address:</p><p color=\"blue\"><u>" +
            BUG_EMAIL +
            "</u></p><p>If you want to write a report, just click the button below and it will create an e-mail message at your default e-mail client.If you do not use e-mail client, you can write the mail by yourself, but do it in this way: please write \"SongScribe bug\" or \"SongScribe wish\" as subject, write the operation system, the version number (this: " +
            Version.PUBLIC_VERSION +
            ") and attach the log file which can be found here:.</p><p color=\"blue\"><u>" +
            logFile.getAbsolutePath() +
            "</u></p><p>Thank you for helping improve SongScribe.</p><p>Csaba Kavai<br>The author</p></html>"
        );
        area.setEditable(false);
        area.setBackground(contentPanel.getBackground());
        area.setPreferredSize(new Dimension(400, 450));
        return area;
    }

    @Override
    protected void getData() {}

    @Override
    protected void setData() {}
}
