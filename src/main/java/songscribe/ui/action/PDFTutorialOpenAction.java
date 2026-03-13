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

package songscribe.ui.action;

import module java.desktop;

import java.io.File;
import java.util.Objects;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.Strings;
import songscribe.data.MyDesktop;
import songscribe.ui.Dialogs;
import songscribe.ui.component.MainFrame;
import songscribe.util.Utils;

public class PDFTutorialOpenAction extends AbstractAction {

    public static final File TUTORIAL_FILE = new File(
        Utils.getResourcePath("help/tutorial.pdf")
    );

    private final MainFrame mainFrame;

    public PDFTutorialOpenAction(String name, MainFrame mainFrame) {
        super(name);
        this.mainFrame = mainFrame;
    }

    @SuppressWarnings(
        { "OverlyBroadCatchBlock", "NewExceptionWithoutArguments" }
    )
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            if (MyDesktop.isDesktopSupported()) {
                Objects.requireNonNull(MyDesktop.getDesktop()).open(
                    TUTORIAL_FILE
                );
            } else {
                throw new RuntimeException();
            }
        } catch (Exception error) {
            Dialogs.showErrorMessage(
                mainFrame,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                "Could not open the external PDF file.\n" +
                "Please navigate to " +
                TUTORIAL_FILE.getAbsolutePath() +
                " with " +
                (SystemInfo.isMacOS ? "Finder" : "Explorer") +
                " and open it.\n" +
                "Also you can try to upgrade your Java version to 1.6 (or higher)."
            );
        }
    }
}
