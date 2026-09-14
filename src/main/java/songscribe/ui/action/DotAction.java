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

import java.awt.event.KeyEvent;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;

// Widens: each dot is drawn to the right of the notehead, inside the note's column.
public final class DotAction<E extends StaffElement & Dottable> extends PreviewElementAction
    implements UIAction.ElementModifiable<E>, UIAction.WidensColumn<E> {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.DOT_COUNT);

    private final Dots dots;

    public static DotAction<?> createDotAction(MainFrame mainFrame) {
        return new DotAction<>(
            mainFrame,
            Dots.ONE,
            Strings.get(Strings.ACTION_DOT_SINGLE), "@\uF372", 18,
            "add-dot", Strings.get(Strings.ACTION_DOT_SINGLE_TOOLTIP),
            KeyEvent.VK_PERIOD, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_DOT
        );
    }

    public static DotAction<?> createDoubleDotAction(MainFrame mainFrame) {
        return new DotAction<>(
            mainFrame,
            Dots.TWO,
            Strings.get(Strings.ACTION_DOT_DOUBLE), "@\uF395", 18,
            "add-double-dot", Strings.get(Strings.ACTION_DOT_DOUBLE_TOOLTIP),
            0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_DOUBLE_DOT
        );
    }

    /**
     * @param dots the dots this action toggles on and off; never {@link Dots#NONE}, since an
     *     action for "no dots" is what deselecting either factory's action already means
     */
    private DotAction(
        MainFrame mainFrame,
        Dots dots,
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        String undoOpNameKey
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
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.ENABLE_WHEN_DURATION_SELECTED,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_WHEN_GRACE_DURATION_SELECTED
        );
        this.dots = dots;
        setUndoOpNameKey(undoOpNameKey);
    }

    public Dots dots() {
        return dots;
    }

    /**
     * A type that occupies time is a pitched note or a rest, and both take dots, so a
     * duration-typed element is always {@link Dottable}.
     */
    @Override
    @SuppressWarnings("unchecked") // safe: every element of a duration type is Dottable
    public @Nullable E admit(StaffElement element) {
        return element.getType().isDuration() ? (E) element : null;
    }

    @Override
    public boolean matchesElement(E element) {
        return element.dots() == dots;
    }

    @Override
    public void applyToElement(Line line, int index, E element, boolean selected) {
        element.setDots(selected ? dots : Dots.NONE);
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }
}
