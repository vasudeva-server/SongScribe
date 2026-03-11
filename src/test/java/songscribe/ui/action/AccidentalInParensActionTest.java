/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;

class AccidentalInParensActionTest extends UnitTest {

    private final AccidentalInParensAction action = new AccidentalInParensAction();

    @Test
    void testApplyToNoteAppliesParentheses() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);
        action.applyToElement(note, true);
        assertThat(note.isAccidentalInParentheses()).isTrue();
    }

    @Test
    void testApplyToNoteRemovesParentheses() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidentalInParentheses(true);
        action.applyToElement(note, false);
        assertThat(note.isAccidentalInParentheses()).isFalse();
    }

    // M16: matchesNote returns false when accidental is not in parentheses
    @Test
    void testDoesNotMatchWhenNotInParentheses() {
        var note = ElementType.CROTCHET.newInstance();

        assertThat(action.matchesElement(note)).isFalse();
    }

    // M15: matchesNote returns true when accidental is in parentheses
    @Test
    void testMatchesWhenInParentheses() {
        var note = ElementType.CROTCHET.newInstance();
        // Must set a non-NONE accidental first; setter is a no-op otherwise
        note.setAccidental(StaffElement.Accidental.SHARP);
        note.setAccidentalInParentheses(true);

        assertThat(action.matchesElement(note)).isTrue();
    }
}
