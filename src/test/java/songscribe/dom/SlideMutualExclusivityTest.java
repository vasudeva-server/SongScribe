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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Verifies that a note's single {@link StaffElement.Slide} field makes a connecting glissando and a
 * trailing fall mutually exclusive: setting one clears the other, so a note never holds both.
 */
class SlideMutualExclusivityTest extends UnitTest {

    private static StaffElement newNote() {
        return ElementType.CROTCHET.newInstance();
    }

    @Test
    void testNewNoteHasNeitherSlide() {
        var note = newNote();

        assertThat(note.getSlide()).as("no slide by default").isNull();
        assertThat(note.hasFall()).as("no fall by default").isFalse();
        assertThat(note.getGlissando()).as("no glissando by default").isNull();
    }

    @Test
    void testSetFallExcludesGlissando() {
        var note = newNote();
        note.setFall();

        assertThat(note.hasFall()).as("fall present").isTrue();
        assertThat(note.getSlide()).as("slide is a Fall").isInstanceOf(StaffElement.Fall.class);
        assertThat(note.getGlissando()).as("a fall is not a connecting glissando").isNull();
    }

    @Test
    void testSetGlissandoExcludesFall() {
        var note = newNote();
        note.setGlissando();

        assertThat(note.getGlissando()).as("connecting glissando present").isNotNull();
        assertThat(note.getSlide()).as("slide is a Glissando").isInstanceOf(StaffElement.Glissando.class);
        assertThat(note.hasFall()).as("a connecting glissando is not a fall").isFalse();
    }

    @Test
    void testSettingFallReplacesAnExistingGlissando() {
        var note = newNote();
        note.setGlissando();
        note.setFall();

        assertThat(note.hasFall()).as("fall replaced glissando").isTrue();
        assertThat(note.getGlissando()).as("glissando cleared by fall").isNull();
    }

    @Test
    void testSettingGlissandoReplacesAnExistingFall() {
        var note = newNote();
        note.setFall();
        note.setGlissando();

        assertThat(note.getGlissando()).as("glissando replaced fall").isNotNull();
        assertThat(note.hasFall()).as("fall cleared by glissando").isFalse();
    }

    @Test
    void testRemoveSlideClearsFall() {
        var note = newNote();
        note.setFall();
        note.removeSlide();

        assertThat(note.getSlide()).as("slide removed").isNull();
        assertThat(note.hasFall()).as("fall removed").isFalse();
    }

    @Test
    void testRemoveSlideClearsGlissando() {
        var note = newNote();
        note.setGlissando();
        note.removeSlide();

        assertThat(note.getSlide()).as("slide removed").isNull();
        assertThat(note.getGlissando()).as("glissando removed").isNull();
    }
}
