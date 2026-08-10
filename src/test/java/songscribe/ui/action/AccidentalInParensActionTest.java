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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.StaffElement;

class AccidentalInParensActionTest extends MainFrameMockTest {

    private AccidentalInParensAction action;

    @BeforeEach
    void createAction() {
        action = AccidentalInParensAction.createAction(mainFrame());
    }

    @Test
    void testApplyToNoteAppliesParentheses() {
        var note = crotchet();
        note.setAccidental(StaffElement.Accidental.SHARP);
        action.applyToElement(note, true);
        assertThat(note.isAccidentalInParentheses()).isTrue();
    }

    @Test
    void testApplyToNoteRemovesParentheses() {
        var note = crotchet();
        note.setAccidentalInParentheses(true);
        action.applyToElement(note, false);
        assertThat(note.isAccidentalInParentheses()).isFalse();
    }

    // M16: matchesNote returns false when accidental is not in parentheses
    @Test
    void testDoesNotMatchWhenNotInParentheses() {
        var note = crotchet();

        assertThat(action.matchesElement(note)).isFalse();
    }

    // M15: matchesNote returns true when accidental is in parentheses
    @Test
    void testMatchesWhenInParentheses() {
        var note = crotchet();
        // Must set a non-NONE accidental first; setter is a no-op otherwise
        note.setAccidental(StaffElement.Accidental.SHARP);
        note.setAccidentalInParentheses(true);

        assertThat(action.matchesElement(note)).isTrue();
    }

    @Test
    void testApplyToNoteWithNoAccidentalIsNoOp() {
        var note = crotchet();
        // note has no accidental; StaffElement.setAccidentalInParentheses guards on getAccidental() != null
        action.applyToElement(note, true);
        assertThat(note.isAccidentalInParentheses()).isFalse();
    }

    @Test
    void testAppliesToReturnsFalseForNoteWithNoAccidental() {
        var note = crotchet();

        assertThat(action.appliesTo(note)).isFalse();
    }

    @Test
    void testAppliesToReturnsTrueForNoteWithAccidental() {
        var note = crotchet();
        note.setAccidental(StaffElement.Accidental.SHARP);

        assertThat(action.appliesTo(note)).isTrue();
    }

    @Test
    void testAppliesToReturnsFalseForNonNoteElement() {
        var rest = crotchetRest();

        assertThat(action.appliesTo(rest)).isFalse();
    }
}
