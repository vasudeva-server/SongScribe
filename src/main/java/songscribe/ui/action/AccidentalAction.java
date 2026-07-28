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
import songscribe.message.MessageCenter;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.ElementField;
import songscribe.dom.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.PlayThread;

// Widens: the accidental glyph is drawn to the left of the notehead, inside the note's column.
public final class AccidentalAction extends NoteOnlyAction implements UIAction.WidensColumn {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.ACCIDENTAL);

    private final StaffElement.Accidental accidental;

    public static AccidentalAction createFlatAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_FLAT), "@\uF388", 18,
            "flat", Strings.get(Strings.ACTION_ACCIDENTAL_FLAT_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_FLAT
        );
    }

    public static AccidentalAction createDoubleFlatAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.DOUBLE_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT), "@\uF389", 18,
            "double-flat", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT_TOOLTIP),
            KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK,
            Strings.ACTION_EDIT_OP_TOGGLE_DOUBLE_FLAT
        );
    }

    public static AccidentalAction createNaturalFlatAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.NATURAL_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT), "#\uE267", 32,
            "natural-flat", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_NATURAL_FLAT
        );
    }

    public static AccidentalAction createNaturalAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.NATURAL,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL), "@\uF387", 18,
            "natural", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_TOOLTIP),
            KeyEvent.VK_N, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_NATURAL
        );
    }

    public static AccidentalAction createSharpAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_SHARP), "@\uF386", 18,
            "sharp", Strings.get(Strings.ACTION_ACCIDENTAL_SHARP_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_SHARP
        );
    }

    public static AccidentalAction createDoubleSharpAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.DOUBLE_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP), "@\uF38A", 18,
            "double-sharp", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_DOUBLE_SHARP
        );
    }

    public static AccidentalAction createNaturalSharpAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            StaffElement.Accidental.NATURAL_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP), "#\uE268", 32,
            "natural-sharp", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_NATURAL_SHARP
        );
    }

    private AccidentalAction(
        MainFrame mainFrame,
        StaffElement.Accidental accidental,
        String name,
        String icon,
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
            NoteOnlyAction.FLAGS
        );
        this.accidental = accidental;
        setUndoOpNameKey(undoOpNameKey);
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

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);

        if (!applyToSelectionIfActive()) {
            MessageCenter.post(new UpdatePreviewElementCommand());
            return;
        }

        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            var element = requireScoreView().getSingleSelectedElement();
            if (element != null && element.getType().isNote()) {
                new PlayThread(element.getPitch()).start();
            }
        }
    }
}
