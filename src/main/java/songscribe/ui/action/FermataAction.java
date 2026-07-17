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
import songscribe.message.mutation.ElementField;
import songscribe.dom.StaffElement;
import songscribe.dom.FermataAttachment;
import songscribe.ui.component.MainFrame;

public final class FermataAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.FERMATA);

    public static FermataAction createAction(MainFrame mainFrame) {
        return new FermataAction(mainFrame);
    }

    private FermataAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_FERMATA),
            null,
            0,
            "fermata",
            Strings.get(Strings.ACTION_FERMATA_TOOLTIP),
            withFlags(NoteOnlyAction.FLAGS, Flag.DISABLE_IN_GRACE_MODE, Flag.REQUIRES_SINGLE_SELECTION)
        );
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_TOGGLE_FERMATA);
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);
        applyToSelectionIfActive();
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.findAttachment(FermataAttachment.class) != null;
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setFermata(selected);
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }
}
