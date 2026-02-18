/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.test;

import java.awt.Component;
import java.io.File;

import org.jetbrains.annotations.NotNull;

import songscribe.MusicChangeListener;
import songscribe.ui.ProfileManager;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.LyricsPanel;
import songscribe.ui.component.Score;

/**
 * Helper class for creating test fixtures.
 */
public class TestHelper {

    /**
     * Creates a minimal IMainFrame mock for testing.
     * Returns an implementation with default properties.
     */
    public static @NotNull IMainFrame createMockMainFrame() {
        return new MockMainFrame();
    }

    /**
     * Minimal implementation of IMainFrame for testing.
     */
    private static class MockMainFrame implements IMainFrame {

        public MockMainFrame() {
            // Do NOT create ProfileManager in tests - it tries to access
            // the filesystem and can call System.exit() if profiles are missing
        }

        @Override
        public void showInfoMessage(String message) {
            // No-op for tests
        }

        @Override
        public void showErrorMessage(String message) {
            // No-op for tests
        }

        @Override
        public int showConfirmDialog(String message, int optionType, int messageType) {
            return 0;
        }

        @Override
        public ProfileManager getProfileManager() {
            // Return null for tests - ProfileManager requires filesystem access
            // and may call System.exit() if profiles are missing
            return null;
        }

        @Override
        public void addMusicChangeListener(MusicChangeListener listener) {
            // No-op for tests
        }

        @Override
        public void setCurrentFile(File saveFile) {
            // No-op for tests
        }

        @Override
        public LyricsPanel getLyricsModePanel() {
            return null;
        }

        @Override
        public void fireMusicChanged(Object source) {
            // No-op for tests
        }

        @Override
        public Component getFocusOwner() {
            return null;
        }

        @Override
        public boolean isDocumentModified() {
            return false;
        }

        @Override
        public void setDocumentModified(boolean documentWasModified) {
            // No-op for tests
        }

        @Override
        public Score getScore() {
            return null;
        }

        @Override
        public void setFrameSize() {
            // No-op for tests
        }
    }
}
