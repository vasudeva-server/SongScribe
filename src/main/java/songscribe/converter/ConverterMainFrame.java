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

package songscribe.converter;

import static org.mockito.Mockito.mock;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.swing.*;

import songscribe.MusicChangeListener;
import songscribe.ui.ProfileManager;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.LyricsPanel;
import songscribe.ui.component.Score;
import songscribe.ui.component.StatusBar;
import songscribe.util.Utils;

public class ConverterMainFrame implements IMainFrame {

    private Score score = null;

    private final Properties defaultProps = new Properties();

    private final ProfileManager profileManager;

    public ConverterMainFrame() {
        try (
            var reader = new FileInputStream(
                Utils.getResourcePath("conf/defprops")
            )
        ) {
            defaultProps.load(reader);
        } catch (IOException e) {
            showErrorMessage(
                "The program could not start, because a necessary file is not available. Please " +
                "reinstall the software."
            );
            throw new RuntimeException(e);
        }

        profileManager = new ProfileManager(this);
    }

    @Override
    public void showInfoMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void showErrorMessage(String message) {
        System.out.println(message);
    }

    @Override
    public int showConfirmDialog(
        String message,
        int optionType,
        int messageType
    ) {
        System.out.println(message);
        return JOptionPane.YES_OPTION;
    }

    @Override
    public void setCurrentFile(File saveFile) {}

    @Override
    public boolean isDocumentModified() {
        return false;
    }

    @Override
    public void setDocumentModified(boolean documentWasModified) {}

    @Override
    public void setFrameSize() {}

    @Override
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    @Override
    public void addMusicChangeListener(MusicChangeListener listener) {}

    @Override
    public Properties getProperties() {
        return new Properties();
    }

    @Override
    public LyricsPanel getLyricsModePanel() {
        return mock(LyricsPanel.class);
    }

    @Override
    public void fireMusicChanged(Object source) {}

    @Override
    public StatusBar getStatusBar() {
        return mock(StatusBar.class);
    }

    @Override
    public Component getFocusOwner() {
        return mock(Component.class);
    }

    @Override
    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    @Override
    public Properties getDefaultProps() {
        return defaultProps;
    }
}
