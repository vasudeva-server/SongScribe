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

import java.awt.*;
import java.io.File;

import songscribe.MusicChangeListener;
import songscribe.ui.ProfileManager;

public interface IMainFrame {
    ProfileManager getProfileManager();

    void addMusicChangeListener(MusicChangeListener listener);

    void setCurrentFile(File saveFile);

    LyricsPanel getLyricsModePanel();

    //    void setMode(Mode noteEdit);

    void fireMusicChanged(Object source);

    Component getFocusOwner();

    boolean isDocumentModified();

    void setDocumentModified(boolean documentWasModified);

    Score getScore();

    void setFrameSize();
}
