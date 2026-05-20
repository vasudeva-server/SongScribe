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
import songscribe.dom.ArticulationType;
import songscribe.dom.StaffElement;
import songscribe.dom.Articulation;

public final class DurationArticulationAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.ARTICULATION);

    private final ArticulationType articulationType;

    public static DurationArticulationAction createStaccatoAction() {
        return new DurationArticulationAction(
            ArticulationType.STACCATO,
            Strings.get(Strings.ACTION_STACCATO), "@\uF38E", 22,
            "staccato", Strings.get(Strings.ACTION_STACCATO_TOOLTIP)
        );
    }

    private DurationArticulationAction(
        ArticulationType articulationType,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        super(name, icon, size, actionCommand, tooltip, NoteOnlyAction.FLAGS);
        this.articulationType = articulationType;
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.hasArticulation(articulationType);
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        for (var a : element.getArticulations()) {
            if (a.getType() == articulationType) {
                element.removeArticulation(a);
                break;
            }
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
