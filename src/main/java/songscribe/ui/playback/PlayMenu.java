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
import songscribe.prefs.Prefs;
import songscribe.ui.action.DialogOpenAction;
import songscribe.ui.message.MessageCenter;

public class PlayMenu extends JMenu {

    private final JCheckBoxMenuItem playWithRepeatsItem;
    private final JMenuItem instrumentItem;
    private final JCheckBoxMenuItem loopPlaybackItem;
    private final ButtonGroup tempoChangeGroup = new ButtonGroup();
    private final JMenu tempoChangeMenu;

    public PlayMenu() {
        super(Strings.get(Strings.MENU_PLAY));
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
        tempoChangeMenu = new JMenu(Strings.get(Strings.MENU_PLAY_TEMPO));

        for (var action : PlaybackController.PLAYBACK_TEMPO_ACTIONS) {
            var menuItem = new JRadioButtonMenuItem(action);
            tempoChangeMenu.add(menuItem);
            tempoChangeGroup.add(menuItem);
        }

        add(tempoChangeMenu);
        addSeparator();
        instrumentItem = new JMenuItem(
            new DialogOpenAction<>(Strings.get(Strings.MENU_PLAY_INSTRUMENTS), InstrumentDialog.class)
        );
        add(instrumentItem);

        MessageCenter.subscribe(this);
        syncPlaybackPrefs();
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateChangedMessage message) {
        var running =
            message.getState() == PlaybackController.PlaybackState.PLAYING;
        tempoChangeMenu.setEnabled(!running);
        instrumentItem.setEnabled(!running);
    }

    public void syncPlaybackPrefs() {
        var prefs = Prefs.getInstance();
        playWithRepeatsItem.setSelected(prefs.getBoolean("playWithRepeats"));
        loopPlaybackItem.setSelected(prefs.getBoolean("loopPlayback"));

        var tempoChange = prefs.getInt("tempoChangePercent");

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

    @Handler
    public void onPlaybackPrefsChanged(PlaybackPrefsChangedMessage message) {
        syncPlaybackPrefs();
    }
}
