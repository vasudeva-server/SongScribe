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
package songscribe.ui.playback;

import module java.desktop;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.message.Message;
import songscribe.message.notification.PlaybackStateDidChangeNotification;

public final class PlayPauseAction extends SequencerAction {

    private static final String PLAY_NAME = Strings.get(Strings.ACTION_PLAY_PLAY);
    private static final String PLAY_ICON = "@\uF446";
    private static final String PLAY_TOOLTIP = Strings.get(Strings.ACTION_PLAY_PLAY_TOOLTIP);

    private static final String PAUSE_NAME = Strings.get(Strings.ACTION_PLAY_PAUSE);
    private static final String PAUSE_ICON = "@\uF44B";
    private static final String PAUSE_TOOLTIP = Strings.get(Strings.ACTION_PLAY_PAUSE_TOOLTIP);

    private static final int ICON_SIZE = 20;

    public static PlayPauseAction createAction(MainFrame mainFrame) {
        return new PlayPauseAction(mainFrame);
    }

    private PlayPauseAction(MainFrame mainFrame) {
        super(
            mainFrame,
            PLAY_NAME,
            PLAY_ICON,
            ICON_SIZE,
            "play",
            PLAY_TOOLTIP,
            KeyEvent.VK_SPACE,
            0,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_WHEN_SONG_EMPTY,
            Flag.DISABLE_WHEN_MIDI_UNAVAILABLE
        );
    }

    // We need to know if playback stopped
    @Override
    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        super.playbackStateDidChange(message);

        if (message.getState() == PlaybackController.PlaybackState.STOPPED) {
            toggleToPlay();
        }
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleAction();
        PlaybackController.togglePlayPause();
    }

    private void toggleToPlay() {
        setName(PLAY_NAME);
        setIcon(PLAY_ICON, ICON_SIZE);
        putValue(SHORT_DESCRIPTION, PLAY_TOOLTIP);
    }

    private void toggleToPause() {
        setName(PAUSE_NAME);
        setIcon(PAUSE_ICON, ICON_SIZE);
        putValue(SHORT_DESCRIPTION, PAUSE_TOOLTIP);
    }

    void toggleAction() {
        if (getName().equals(PLAY_NAME)) {
            toggleToPause();
        } else {
            toggleToPlay();
        }
    }
}
