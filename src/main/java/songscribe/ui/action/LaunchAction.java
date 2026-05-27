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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

public class LaunchAction extends AbstractAction {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchAction.class);

    public enum App {
        SONGBOOK(Strings.ACTION_LAUNCH_SONG_BOOK, "sb"),
        SONGSHOW(Strings.ACTION_LAUNCH_SONG_SHOW, "ss");

        private final String nameKey;
        private final String command;

        App(String nameKey, String command) {
            this.nameKey = nameKey;
            this.command = command;
        }
    }

    private final App app;

    public LaunchAction(App app) {
        this.app = app;
        putValue(Action.NAME, Strings.get(app.nameKey));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            var currentProcessInfo = ProcessHandle.current().info();
            List<String> commandList = new ArrayList<>();

            if (currentProcessInfo.command().isPresent()) {
                commandList.add(currentProcessInfo.command().get());
            }

            if (currentProcessInfo.arguments().isPresent()) {
                commandList.addAll(
                    Arrays.asList(currentProcessInfo.arguments().get())
                );
            }

            commandList.add(app.command);
            new ProcessBuilder(commandList).start();
        } catch (Exception ex) {
            LOG.error("Failed to spawn process for {}", app.command, ex);
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_LAUNCH_ERROR, Strings.ERROR_LAUNCH_FAILED, Strings.get(app.nameKey));
        }
    }
}
