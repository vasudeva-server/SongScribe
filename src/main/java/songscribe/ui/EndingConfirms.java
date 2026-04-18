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

package songscribe.ui;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.component.MainFrame;
import songscribe.ui.layout.Ending;

/**
 * Utility class for showing confirmation dialogs before ending-invalidating mutations,
 * and for applying compensating element changes that keep the ending valid.
 */
public final class EndingConfirms {

    private EndingConfirms() {}

    public static boolean confirmInvalidation() {
        return showEndingConfirm(Strings.CONFIRM_ENDING_WILL_BE_REMOVED);
    }

    public static boolean confirmCompensateEnd(Ending.EndingEffect.CompensateEnd ce) {
        var key = ce.newEndType() == ElementType.REPEAT_RIGHT
            ? Strings.CONFIRM_ENDING_SPLIT_RIGHT_TO_LEFT_RIGHT
            : Strings.CONFIRM_ENDING_SPLIT_LEFT_RIGHT_TO_RIGHT;
        return showEndingConfirm(key);
    }

    public static boolean confirmCompensateSplit(
        Ending.EndingEffect.CompensateSplit cs, ElementType primaryNewType
    ) {
        var key = cs.newSplitType() == ElementType.REPEAT_LEFT_RIGHT
            ? Strings.CONFIRM_ENDING_END_TO_REPEAT_REQUIRES_LEFT_RIGHT_SPLIT
            : Strings.CONFIRM_ENDING_END_TO_BARLINE_REQUIRES_RIGHT_SPLIT;
        return showEndingConfirm(key, typeNameFor(primaryNewType));
    }

    /**
     * Applies the compensating end-element change to the line. Call before the primary
     * split-element change — both calls will leave the ending retained.
     */
    public static void applyCompensatingEndChange(
        Line line, Ending.EndingEffect.CompensateEnd ce
    ) {
        applyCompensatingChange(line, ce.ending().getEndElement(), ce.newEndType());
    }

    /**
     * Applies the compensating split-element change to the line. Call before the primary
     * end-element change — both calls will leave the ending retained.
     */
    public static void applyCompensatingSplitChange(
        Line line, Ending.EndingEffect.CompensateSplit cs
    ) {
        applyCompensatingChange(line, cs.ending().findRepeatSplitElement(line), cs.newSplitType());
    }

    private static void applyCompensatingChange(
        Line line, @Nullable StaffElement targetEl, ElementType newType
    ) {
        if (targetEl == null) {
            return;
        }

        var index = line.getElementIndex(targetEl);
        var newEl = newType.newInstance();
        newEl.setXOffsetPx(targetEl.getXOffsetPx());
        line.setElement(index, newEl);
    }

    private static boolean showEndingConfirm(String messageKey, Object... args) {
        var result = OptionDialogs.showOptionDialog(
            MainFrame.getInstance(),
            Strings.get(Strings.CONFIRM_TITLE_FIRST_SECOND_ENDING),
            Strings.get(messageKey, args),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{Strings.get(Strings.BUTTON_YES), Strings.get(Strings.BUTTON_NO)},
            Strings.get(Strings.BUTTON_YES)
        );
        return result == 0;
    }

    private static String typeNameFor(ElementType type) {
        return switch (type) {
            case REPEAT_RIGHT -> Strings.get(Strings.ELEMENT_TYPE_NAME_RIGHT_REPEAT);
            case REPEAT_LEFT_RIGHT -> Strings.get(Strings.ELEMENT_TYPE_NAME_LEFT_RIGHT_REPEAT);
            case REPEAT_LEFT -> Strings.get(Strings.ELEMENT_TYPE_NAME_LEFT_REPEAT);
            default -> Strings.get(Strings.ELEMENT_TYPE_NAME_BARLINE);
        };
    }
}
