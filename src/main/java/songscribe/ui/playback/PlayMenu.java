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

import java.util.Properties;

import javax.swing.*;

import net.engio.mbassy.listener.Handler;

import songscribe.MusicChangeListener;
import songscribe.ui.Constants;
import songscribe.ui.action.DialogOpenAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.message.MessageCenter;

public class PlayMenu extends JMenu implements MusicChangeListener {

    private final JCheckBoxMenuItem playWithRepeatsItem;
    private final JMenuItem instrumentItem;
    private final JCheckBoxMenuItem loopPlaybackItem;
    private final ButtonGroup tempoChangeGroup = new ButtonGroup();
    private final JMenu tempoChangeMenu;

    public PlayMenu() {
        super("Play");
        add(new JMenuItem(PlaybackController.PLAY_PAUSE_ACTION));
        add(new JMenuItem(PlaybackController.STOP_ACTION));

        addSeparator();

        playWithRepeatsItem = new JCheckBoxMenuItem(
            PlaybackController.PLAY_WITH_REPEATS_ACTION
        );
        add(playWithRepeatsItem);

        loopPlaybackItem = new JCheckBoxMenuItem(
            PlaybackController.LOOP_PLAYBACK_ACTION
        );
        add(loopPlaybackItem);
        tempoChangeMenu = new JMenu("Playback Tempo");

        for (var action : PlaybackController.PLAYBACK_TEMPO_ACTIONS) {
            var menuItem = new JRadioButtonMenuItem(action);
            tempoChangeMenu.add(menuItem);
            tempoChangeGroup.add(menuItem);
        }

        add(tempoChangeMenu);
        instrumentItem = new JMenuItem(
            new DialogOpenAction<>("Instruments...", InstrumentDialog.class)
        );
        add(instrumentItem);

        MainFrame.getInstance().addMusicChangeListener(this);
        MessageCenter.subscribe(this);
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateChangedMessage message) {
        var running =
            message.getState() == PlaybackController.PlaybackState.PLAYING;
        tempoChangeMenu.setEnabled(!running);
        instrumentItem.setEnabled(!running);
    }

    // TODO: Use message center
    @Override
    public void musicDidChange(Properties props) {
        playWithRepeatsItem.setSelected(
            props
                .getProperty(Constants.WITH_REPEAT_PROP)
                .equals(Constants.TRUE_VALUE)
        );

        loopPlaybackItem.setSelected(
            props
                .getProperty(Constants.LOOP_PLAYBACK_PROP)
                .equals(Constants.TRUE_VALUE)
        );

        var tempoChange = Integer.parseInt(
            props.getProperty(Constants.TEMPO_CHANGE_PROP)
        );

        for (
            var menuItems = tempoChangeGroup.getElements();
            menuItems.hasMoreElements();
        ) {
            var menuItem = menuItems.nextElement();

            if (
                ((TempoChangeAction) menuItem.getAction()).getRatio() ==
                tempoChange
            ) {
                tempoChangeGroup.setSelected(menuItem.getModel(), true);
            }
        }
    }
}
