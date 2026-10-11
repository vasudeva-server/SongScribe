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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.ui.component.MainFrame;

public final class PlayStopAction extends SequencerAction {

    private static final String PLAY_NAME = Strings.get(Strings.ACTION_PLAY_PLAY);
    private static final String PLAY_TOOLTIP = Strings.get(Strings.ACTION_PLAY_PLAY_TOOLTIP);

    private static final String STOP_NAME = Strings.get(Strings.ACTION_PLAY_STOP);
    private static final String STOP_TOOLTIP = Strings.get(Strings.ACTION_PLAY_STOP_TOOLTIP);

    private static final String BLOCKED_TOOLTIP = Strings.get(Strings.ACTION_PLAY_BLOCKED_TOOLTIP);

    private static final String PLAY_ICON = "@\uF446";
    private static final String STOP_ICON = "@\uF447";

    private static final int ICON_SIZE = 20;

    // The tooltip of the current Play or Stop state, shown whenever a lint finding does not
    // block playback.
    private String stateTooltip = PLAY_TOOLTIP;

    public static PlayStopAction createAction(MainFrame mainFrame) {
        return new PlayStopAction(mainFrame);
    }

    private PlayStopAction(MainFrame mainFrame) {
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
            Flag.DISABLE_WHEN_SONG_EMPTY,
            Flag.DISABLE_WHEN_MIDI_UNAVAILABLE,
            Flag.DISABLE_WHEN_PLAYBACK_BLOCKED,
            // Playback only reads the song. The playing indices it pushes into a line
            // component color a note and nothing more, so one that grace mode shifts
            // highlights the wrong note until the next update and touches no grace state.
            Flag.ENABLED_IN_GRACE_MODE
        );
    }

    // The label mirrors playback state rather than tracking its own — a play
    // that bails out early posts no notification and must leave the button on Play.
    @Override
    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        super.playbackStateDidChange(message);

        if (message.getState() == PlaybackState.PLAYING) {
            toggleToStop();
        } else {
            toggleToPlay();
        }
    }

    /** Also shows why Play is disabled while a lint finding blocks playback. */
    @Override
    public boolean updateEnabledState() {
        var enabled = super.updateEnabledState();
        applyTooltip();
        return enabled;
    }

    @Override
    protected void performAction(ActionEvent e) {
        PlaybackController.togglePlayStop();
    }

    private void toggleToPlay() {
        setName(PLAY_NAME);
        setIcon(PLAY_ICON, ICON_SIZE);
        stateTooltip = PLAY_TOOLTIP;
        applyTooltip();
    }

    private void toggleToStop() {
        setName(STOP_NAME);
        setIcon(STOP_ICON, ICON_SIZE);
        stateTooltip = STOP_TOOLTIP;
        applyTooltip();
    }

    private void applyTooltip() {
        putValue(SHORT_DESCRIPTION, enableFromPlaybackBlock() ? stateTooltip : BLOCKED_TOOLTIP);
    }
}
