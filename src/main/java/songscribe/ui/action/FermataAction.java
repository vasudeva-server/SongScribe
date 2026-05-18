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
import songscribe.model.StaffElement;

public final class FermataAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.FERMATA);

    public static FermataAction createAction() {
        return new FermataAction();
    }

    private FermataAction() {
        super(
            Strings.get(Strings.ACTION_FERMATA),
            null,
            0,
            "fermata",
            Strings.get(Strings.ACTION_FERMATA_TOOLTIP),
            NoteOnlyAction.FLAGS
        );
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.isFermata();
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
