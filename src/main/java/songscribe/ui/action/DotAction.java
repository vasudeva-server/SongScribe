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

import songscribe.Strings;
import org.jetbrains.annotations.Nullable;

import songscribe.music.StaffElement;

public class DotAction extends InsertionElementAction implements UIAction.ElementModifiable {

    private final DotLevel dotLevel;

    public static DotAction createDotAction() {
        return new DotAction(
            DotLevel.SINGLE,
            Strings.get(Strings.ACTION_DOT_SINGLE), "@\uF372", 18,
            "add-dot", Strings.get(Strings.ACTION_DOT_SINGLE_TOOLTIP),
            KeyEvent.VK_PERIOD, 0
        );
    }

    public static DotAction createDoubleDotAction() {
        return new DotAction(
            DotLevel.DOUBLE,
            Strings.get(Strings.ACTION_DOT_DOUBLE), "@\uF395", 18,
            "add-double-dot", Strings.get(Strings.ACTION_DOT_DOUBLE_TOOLTIP),
            0, 0
        );
    }

    private DotAction(
        DotLevel dotLevel,
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.ENABLE_WHEN_DURATION_SELECTED,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
        this.dotLevel = dotLevel;
    }

    public DotLevel getDotLevel() {
        return dotLevel;
    }

    @Override
    public boolean appliesTo(StaffElement element) {
        return element.getType().isDuration();
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return switch (dotLevel) {
            case SINGLE -> element.getDotCount() == 1;
            case DOUBLE -> element.getDotCount() == 2;
        };
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setDotCount(selected ? dotLevel.ordinal() + 1 : 0);
    }

    public enum DotLevel {
        SINGLE,
        DOUBLE,
    }
}
