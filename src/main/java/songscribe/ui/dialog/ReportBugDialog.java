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

import java.io.File;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.Version;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.OptionDialogs;
import songscribe.util.DesktopUtils;
import songscribe.ui.component.MainFrame;
import songscribe.util.Utils;

public class ReportBugDialog extends StandardDialog {

    public static final String BUG_EMAIL = "himadri81@zoho.com";
    private @Nullable JButton sendBug = null;

    public ReportBugDialog() {
        super(Strings.get(Strings.DIALOG_BUG_REPORT_TITLE), true, DialogCategory.INFORMATIONAL);
        var logFile = new File(MainFrame.SONGSCRIBE_DIR, "log");
        var area = getjEditorPane(logFile);
        contentPanel.add(area);

        if (DesktopUtils.isDesktopSupported()) {
            sendBug = new JButton(Strings.get(Strings.DIALOG_BUG_REPORT_SEND));
            sendBug.addActionListener(_ -> {
                var bugReportIdx = 0;
                var featureRequestIdx = 1;
                var cancelIdx = 2;
                var answer = OptionDialogs.showOptionDialog(
                    contentPanel,
                    Strings.DIALOG_BUG_REPORT_TITLE,
                    Strings.DIALOG_BUG_REPORT_WHAT_TO_SEND,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[] {
                        Strings.get(Strings.DIALOG_BUG_REPORT_OPTION_BUG),
                        Strings.get(Strings.DIALOG_BUG_REPORT_OPTION_REQUEST),
                        Strings.get(Strings.DIALOG_BUTTON_CANCEL)
                    },
                    null
                );

                if ((answer == cancelIdx) || (answer == JOptionPane.CLOSED_OPTION)) {
                    return;
                }

                var sb = new StringBuilder(270);
                sb.append(BUG_EMAIL);
                sb.append("?SUBJECT=SongScribe ");
                sb.append((answer == bugReportIdx) ? "bug" : "request");

                if (answer == bugReportIdx) {
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
                    Utils.openEmail(sb.toString());
                } catch (Exception e1) {
                    OptionDialogs.showErrorMessage(
                        getMainFrame(),
                        Strings.ALERT_TITLE_EMAIL_ERROR,
                        Strings.ERROR_EMAIL_OPEN_REPORT
                    );
                }
            });

        }
    }

    @Override
    protected Object modifyButtonPanel() {
        buttonPanel.remove(cancelButton);

        if (sendBug != null) {
            buttonPanel.add(sendBug);
        }

        return BorderLayout.SOUTH;
    }

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
    protected String getContentPaddingKey() {
        return FlatLafKeys.DIALOG_NO_PADDING;
    }

    @Override
    protected boolean getData() { return true; }

    @Override
    protected void setData() {}
}
