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
import songscribe.music.StaffElement;

public class AccidentalAction extends NoteOnlyAction {

    private final StaffElement.Accidental accidental;

    public static AccidentalAction createFlatAction() {
        return new AccidentalAction(
            StaffElement.Accidental.FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_FLAT), "@\uF388", 18,
            "flat", Strings.get(Strings.ACTION_ACCIDENTAL_FLAT_TOOLTIP), 0, 0
        );
    }

    public static AccidentalAction createDoubleFlatAction() {
        return new AccidentalAction(
            StaffElement.Accidental.DOUBLE_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT), "@\uF389", 18,
            "double-flat", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT_TOOLTIP),
            KeyEvent.VK_F, 0
        );
    }

    public static AccidentalAction createNaturalFlatAction() {
        return new AccidentalAction(
            StaffElement.Accidental.NATURAL_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT), "#\uE267", 32,
            "natural-flat", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT_TOOLTIP), 0, 0
        );
    }

    public static AccidentalAction createNaturalAction() {
        return new AccidentalAction(
            StaffElement.Accidental.NATURAL,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL), "@\uF387", 18,
            "natural", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_TOOLTIP),
            KeyEvent.VK_N, 0
        );
    }

    public static AccidentalAction createSharpAction() {
        return new AccidentalAction(
            StaffElement.Accidental.SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_SHARP), "@\uF386", 18,
            "sharp", Strings.get(Strings.ACTION_ACCIDENTAL_SHARP_TOOLTIP), 0, 0
        );
    }

    public static AccidentalAction createDoubleSharpAction() {
        return new AccidentalAction(
            StaffElement.Accidental.DOUBLE_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP), "@\uF38A", 18,
            "double-sharp", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP_TOOLTIP), 0, 0
        );
    }

    public static AccidentalAction createNaturalSharpAction() {
        return new AccidentalAction(
            StaffElement.Accidental.NATURAL_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP), "#\uE268", 32,
            "natural-sharp", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP_TOOLTIP), 0, 0
        );
    }

    private AccidentalAction(
        StaffElement.Accidental accidental,
        String name,
        String icon,
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
            withFlags(NoteOnlyAction.FLAGS, Flag.DISABLE_WHEN_EDITING_TEXT)
        );
        this.accidental = accidental;
    }

    public StaffElement.Accidental getAccidental() {
        return accidental;
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.getAccidental() == accidental;
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setAccidental(selected ? accidental : null);
    }
}
