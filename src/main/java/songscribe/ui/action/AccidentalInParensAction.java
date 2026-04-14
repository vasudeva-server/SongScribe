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

import songscribe.message.mutation.ElementField;
import songscribe.music.StaffElement;

public class AccidentalInParensAction extends NoteOnlyAction {

    private static final EnumSet<ElementField> MODIFIED_FIELDS =
        EnumSet.of(ElementField.ACCIDENTAL_IN_PARENS);

    public static AccidentalInParensAction createAction() {
        return new AccidentalInParensAction();
    }

    private AccidentalInParensAction() {
        super(
            "In Parentheses",
            null,
            0,
            "accidental-in-parens",
            "Add accidental in parentheses",
            NoteOnlyAction.FLAGS
        );
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.isAccidentalInParentheses();
    }

    @Override
    public void applyToElement(StaffElement element, boolean selected) {
        element.setAccidentalInParentheses(selected);
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }
}
