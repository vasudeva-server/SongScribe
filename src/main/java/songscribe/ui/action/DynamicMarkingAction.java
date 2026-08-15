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

import songscribe.Strings;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;

public final class DynamicMarkingAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS =
        EnumSet.of(ElementField.DYNAMIC_ATTACHMENT);

    private final DynamicType dynamicType;

    public static DynamicMarkingAction createPianissimoAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.PIANISSIMO,
            Strings.ACTION_DYNAMIC_PP, "dynamic-pp", Strings.ACTION_EDIT_OP_TOGGLE_PP);
    }

    public static DynamicMarkingAction createPianoAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.PIANO,
            Strings.ACTION_DYNAMIC_P, "dynamic-p", Strings.ACTION_EDIT_OP_TOGGLE_P);
    }

    public static DynamicMarkingAction createMezzoPianoAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.MEZZO_PIANO,
            Strings.ACTION_DYNAMIC_MP, "dynamic-mp", Strings.ACTION_EDIT_OP_TOGGLE_MP);
    }

    public static DynamicMarkingAction createMezzoForteAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.MEZZO_FORTE,
            Strings.ACTION_DYNAMIC_MF, "dynamic-mf", Strings.ACTION_EDIT_OP_TOGGLE_MF);
    }

    public static DynamicMarkingAction createForteAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.FORTE,
            Strings.ACTION_DYNAMIC_F, "dynamic-f", Strings.ACTION_EDIT_OP_TOGGLE_F);
    }

    public static DynamicMarkingAction createFortissimoAction(MainFrame mainFrame) {
        return new DynamicMarkingAction(mainFrame, DynamicType.FORTISSIMO,
            Strings.ACTION_DYNAMIC_FF, "dynamic-ff", Strings.ACTION_EDIT_OP_TOGGLE_FF);
    }

    private DynamicMarkingAction(
        MainFrame mainFrame,
        DynamicType dynamicType,
        String stringsKey,
        String actionCommand,
        String undoOpNameKey
    ) {
        super(
            mainFrame,
            Strings.get(stringsKey),
            null,
            0,
            actionCommand,
            null,
            withFlags(NoteOnlyAction.FLAGS, Flag.REQUIRES_SINGLE_SELECTION)
        );
        this.dynamicType = dynamicType;
        setUndoOpNameKey(undoOpNameKey);
    }

    public DynamicType getDynamicType() {
        return dynamicType;
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        var existing = element.findAttachment(DynamicAttachment.class);
        return existing != null && existing.getType() == dynamicType;
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        var existing = element.findAttachment(DynamicAttachment.class);

        if (existing != null) {
            var isSameType = existing.getType() == dynamicType;
            element.removeAttachment(existing);

            if (isSameType) {
                return;
            }
        }

        if (selected) {
            element.addAttachment(new DynamicAttachment(element, dynamicType));
        }
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }

    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        var scoreView = getScoreView();

        if (scoreView == null) {
            return false;
        }

        var selection = scoreView.getSelectionCoordinator().getSelection();

        if (selection == null) {
            return false;
        }

        var line = selection.line();
        var noteIndex = selection.begin();

        if (line.isInsideHairpin(noteIndex)) {
            setEnabled(false);
            return false;
        }

        return true;
    }
}
