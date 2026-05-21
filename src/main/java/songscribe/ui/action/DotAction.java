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

import java.util.EnumSet;

import songscribe.Strings;
import org.jspecify.annotations.Nullable;

import songscribe.message.mutation.ElementField;
import songscribe.dom.StaffElement;
import songscribe.ui.component.MainFrame;

public final class DotAction extends PreviewElementAction implements UIAction.ElementModifiable {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.DOT_COUNT);

    private final DotLevel dotLevel;

    public static DotAction createDotAction(MainFrame mainFrame) {
        return new DotAction(
            mainFrame,
            DotLevel.SINGLE,
            Strings.get(Strings.ACTION_DOT_SINGLE), "@\uF372", 18,
            "add-dot", Strings.get(Strings.ACTION_DOT_SINGLE_TOOLTIP),
            KeyEvent.VK_PERIOD, 0
        );
    }

    public static DotAction createDoubleDotAction(MainFrame mainFrame) {
        return new DotAction(
            mainFrame,
            DotLevel.DOUBLE,
            Strings.get(Strings.ACTION_DOT_DOUBLE), "@\uF395", 18,
            "add-double-dot", Strings.get(Strings.ACTION_DOT_DOUBLE_TOOLTIP),
            0, 0
        );
    }

    private DotAction(
        MainFrame mainFrame,
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
            mainFrame,
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
        return element.getDotCount() == switch (dotLevel) {
            case SINGLE -> 1;
            case DOUBLE -> 2;
        };
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setDotCount(selected ? dotLevel.ordinal() + 1 : 0);
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }

    public enum DotLevel {
        SINGLE,
        DOUBLE,
    }
}
