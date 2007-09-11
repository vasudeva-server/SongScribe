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

import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.*;

public class LaunchAction extends AbstractAction {

    public enum App {
        SONGBOOK("Song Book", "sb"),
        SONGSHOW("Song Show", "ss");

        private final String name;
        private final String command;

        App(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    private final App app;

    public LaunchAction(App app) {
        this.app = app;
        putValue(Action.NAME, app.name);
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
            // Ignore
        }
    }
}
