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

import java.util.EnumSet;
import javax.swing.Action;
import javax.swing.KeyStroke;

import songscribe.Strings;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class ArticulationAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.ARTICULATION);

    private final ArticulationType articulationType;

    public static ArticulationAction createAccentAction(MainFrame mainFrame) {
        return new ArticulationAction(
            mainFrame,
            ArticulationType.ACCENT,
            Strings.get(Strings.ACTION_ACCENT), "@\uF38C", 22,
            "accent", Strings.get(Strings.ACTION_ACCENT_TOOLTIP),
            '>',
            Strings.ACTION_EDIT_OP_TOGGLE_ACCENT
        );
    }

    public static ArticulationAction createStaccatoAction(MainFrame mainFrame) {
        return new ArticulationAction(
            mainFrame,
            ArticulationType.STACCATO,
            Strings.get(Strings.ACTION_STACCATO), "@\uF38E", 22,
            "staccato", Strings.get(Strings.ACTION_STACCATO_TOOLTIP),
            '<',
            Strings.ACTION_EDIT_OP_TOGGLE_STACCATO
        );
    }

    /**
     * The accelerator is built from the character rather than from a virtual key code
     * because {@code <} and {@code >} have no virtual key code that works — {@code VK_LESS}
     * and {@code VK_GREATER} exist but are not reliably reported. Swing matches a
     * character-based keystroke against the character actually typed, so this works on
     * any keyboard layout. Do not "fix" it to a virtual key code.
     *
     * <p>The base constructor only registers accelerators given as a virtual key code, so
     * this one is registered here instead, the way {@link DeleteAction} registers its extra
     * keystrokes. Without that, the shortcut would work only for as long as these actions
     * happen to sit in a menu, since Swing wires a menu item's accelerator on its own.
     */
    private ArticulationAction(
        MainFrame mainFrame,
        ArticulationType articulationType,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        char key,
        String undoOpNameKey
    ) {
        super(mainFrame, name, icon, size, actionCommand, tooltip, NoteOnlyAction.FLAGS);
        this.articulationType = articulationType;

        var keyStroke = KeyStroke.getKeyStroke(key);
        putValue(Action.ACCELERATOR_KEY, keyStroke);
        UIUtils.registerActionKeystroke(mainFrame.getRootPane(), keyStroke, this);

        setUndoOpNameKey(undoOpNameKey);
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.hasArticulation(articulationType);
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        var existing = element.findArticulation(articulationType);

        if (existing != null) {
            element.removeArticulation(existing);
        }

        if (selected) {
            element.addArticulation(new Articulation(element, articulationType));
        }
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }
}
