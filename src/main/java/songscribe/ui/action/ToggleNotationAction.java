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
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;

public final class ToggleNotationAction extends UIAction {

    private final Predicate<? super ScoreViewController> canToggle;
    private final Supplier<? extends Message> commandFactory;

    public static ToggleNotationAction createBeamAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_BEAM_TOGGLE),
            "beam.svg",
            28,
            "toggle-beam",
            Strings.get(Strings.ACTION_BEAM_TOGGLE_TOOLTIP),
            KeyEvent.VK_B,
            ScoreViewController::canToggleBeaming,
            ToggleBeamCommand::new
        );
    }

    public static ToggleNotationAction createTieAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_TIE_TOGGLE),
            "@\uF373",
            20,
            "toggle-tie",
            Strings.get(Strings.ACTION_TIE_TOGGLE_TOOLTIP),
            KeyEvent.VK_T,
            ScoreViewController::canToggleTie,
            ToggleTieCommand::new
        );
    }

    private ToggleNotationAction(
        MainFrame mainFrame,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        Predicate<? super ScoreViewController> canToggle,
        Supplier<? extends Message> commandFactory
    ) {
        super(
            mainFrame,
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
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
    public void songDidChange(SongDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        handleChange();
    }

    private void handleChange() {
        var ctrl = getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(canToggle.test(ctrl));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(commandFactory.get());
    }
}
