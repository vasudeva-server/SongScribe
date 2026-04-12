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

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.Score;

public final class ToggleNotationAction extends UIAction {

    private final Predicate<Score> canToggle;
    private final Supplier<Message> commandFactory;

    public static ToggleNotationAction createBeamAction() {
        return new ToggleNotationAction(
            Strings.get(Strings.ACTION_BEAM_TOGGLE),
            "beam.svg",
            28,
            "toggle-beam",
            Strings.get(Strings.ACTION_BEAM_TOGGLE_TOOLTIP),
            KeyEvent.VK_B,
            Score::canToggleBeaming,
            ToggleBeamCommand::new
        );
    }

    public static ToggleNotationAction createTieAction() {
        return new ToggleNotationAction(
            Strings.get(Strings.ACTION_TIE_TOGGLE),
            "@\uF373",
            20,
            "toggle-tie",
            Strings.get(Strings.ACTION_TIE_TOGGLE_TOOLTIP),
            KeyEvent.VK_T,
            Score::canToggleTie,
            ToggleTieCommand::new
        );
    }

    private ToggleNotationAction(
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int key,
        Predicate<Score> canToggle,
        Supplier<Message> commandFactory
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            key,
            0,
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.canToggle = canToggle;
        this.commandFactory = commandFactory;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void compositionDidChange(CompositionDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        handleChange();
    }

    private void handleChange() {
        var score = getScore();

        if (score != null && score.isInitialized() && updateEnabledState()) {
            setEnabled(canToggle.test(score));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(commandFactory.get());
    }
}
