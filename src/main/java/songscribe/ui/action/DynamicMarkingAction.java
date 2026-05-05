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
import songscribe.message.mutation.ElementField;
import songscribe.music.StaffElement;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

public final class DynamicMarkingAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS =
        EnumSet.of(ElementField.DYNAMIC_ATTACHMENT);

    private final DynamicType dynamicType;

    public static DynamicMarkingAction createPianissimoAction() {
        return new DynamicMarkingAction(DynamicType.PIANISSIMO,
            Strings.ACTION_DYNAMIC_PP, "dynamic-pp");
    }

    public static DynamicMarkingAction createPianoAction() {
        return new DynamicMarkingAction(DynamicType.PIANO,
            Strings.ACTION_DYNAMIC_P, "dynamic-p");
    }

    public static DynamicMarkingAction createMezzoPianoAction() {
        return new DynamicMarkingAction(DynamicType.MEZZO_PIANO,
            Strings.ACTION_DYNAMIC_MP, "dynamic-mp");
    }

    public static DynamicMarkingAction createMezzoForteAction() {
        return new DynamicMarkingAction(DynamicType.MEZZO_FORTE,
            Strings.ACTION_DYNAMIC_MF, "dynamic-mf");
    }

    public static DynamicMarkingAction createForteAction() {
        return new DynamicMarkingAction(DynamicType.FORTE,
            Strings.ACTION_DYNAMIC_F, "dynamic-f");
    }

    public static DynamicMarkingAction createFortissimoAction() {
        return new DynamicMarkingAction(DynamicType.FORTISSIMO,
            Strings.ACTION_DYNAMIC_FF, "dynamic-ff");
    }

    private DynamicMarkingAction(DynamicType dynamicType, String stringsKey, String actionCommand) {
        super(
            Strings.get(stringsKey),
            null,
            0,
            actionCommand,
            null,
            withFlags(NoteOnlyAction.FLAGS, Flag.REQUIRES_SINGLE_SELECTION)
        );
        this.dynamicType = dynamicType;
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

        var score = getScore();

        if (score == null) {
            return false;
        }

        var selection = score.getSelectionCoordinator().getSelection();

        if (selection == null) {
            return false;
        }

        var line = selection.line();
        var noteIndex = selection.begin();

        if (line.isInHairpinRange(noteIndex)) {
            setEnabled(false);
            return false;
        }

        return true;
    }
}
