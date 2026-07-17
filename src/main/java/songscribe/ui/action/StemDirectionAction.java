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

import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.MainFrame;

public final class StemDirectionAction extends UIAction {

    private static final int FLIP_ICON_SIZE = 18;

    /** The auto action is menu-only, so it carries no icon and needs no icon size. */
    private static final int NO_ICON_SIZE = 0;

    private final Supplier<? extends Message> commandFactory;

    public static StemDirectionAction createFlipAction(MainFrame mainFrame) {
        return new StemDirectionAction(
            mainFrame,
            Strings.get(Strings.ACTION_STEM_FLIP),
            "@\uF374",
            FLIP_ICON_SIZE,
            "flip-stem-direction",
            Strings.get(Strings.ACTION_STEM_FLIP_TOOLTIP),
            FlipStemDirectionCommand::new,
            Strings.ACTION_EDIT_OP_FLIP_STEM
        );
    }

    public static StemDirectionAction createAutoAction(MainFrame mainFrame) {
        return new StemDirectionAction(
            mainFrame,
            Strings.get(Strings.ACTION_STEM_AUTO),
            null,
            NO_ICON_SIZE,
            "auto-stem-direction",
            Strings.get(Strings.ACTION_STEM_AUTO_TOOLTIP),
            AutoStemDirectionCommand::new,
            Strings.ACTION_EDIT_OP_AUTO_STEM
        );
    }

    private StemDirectionAction(
        MainFrame mainFrame,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        Supplier<? extends Message> commandFactory,
        String undoOpNameKey
    ) {
        super(
            mainFrame,
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.commandFactory = commandFactory;
        setUndoOpNameKey(undoOpNameKey);
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        var ctrl = message.getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(ctrl.canModifyStemDirection());
        }
    }

    @Override
    protected void performAction(ActionEvent e) {
        super.performAction(e);
        MessageCenter.post(commandFactory.get());
    }
}
