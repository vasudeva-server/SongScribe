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

import songscribe.MainFrameMockTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

class NoteOnlyActionAppliesToTest extends MainFrameMockTest {

    // Use FermataAction as the simplest concrete NoteOnlyAction subclass
    private FermataAction action;

    @BeforeEach
    void createAction() {
        action = FermataAction.createAction(mainFrame());
    }

    // A7: appliesTo returns true for notes
    @Test
    void testAppliesToNote() {
        var note = crotchet();
        assertThat(action.appliesTo(note)).isTrue();
    }

    // A9: appliesTo returns false for barlines
    @Test
    void testDoesNotApplyToBarline() {
        var note = singleBarline();
        assertThat(action.appliesTo(note)).isFalse();
    }

    // A8: appliesTo returns false for rests
    @Test
    void testDoesNotApplyToRest() {
        var note = crotchetRest();
        assertThat(action.appliesTo(note)).isFalse();
    }

    // A10: appliesTo returns true for grace notes (isNote() == true; no DISABLE_IN_GRACE_MODE flag)
    @Test
    void testAppliesToGraceNote() {
        var note = graceQuaver();
        assertThat(action.appliesTo(note)).isTrue();
    }
}
