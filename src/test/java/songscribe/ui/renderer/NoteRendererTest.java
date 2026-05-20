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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;

class NoteRendererTest extends UnitTest {

    @BeforeAll
    static void initializeAccidentalWidths() {
        NoteRenderer.initializeAccidentalWidths();
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static StaffElement newCrotchetWithAccidental(Accidental accidental) {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(accidental);
        return note;
    }

    @Test
    void testGetAccidentalBoundsReturnsNullForGraceNote() {
        var graceNote = ElementType.GRACE_QUAVER.newInstance();
        graceNote.setAccidental(Accidental.SHARP);

        assertThat(NoteRenderer.getAccidentalBoundsSs(graceNote)).isNull();
    }

    @Test
    void testGetAccidentalBoundsReturnsNullForNoAccidental() {
        var note = ElementType.CROTCHET.newInstance();

        assertThat(NoteRenderer.getAccidentalBoundsSs(note)).isNull();
    }

    @Test
    void testGetAccidentalBoundsReturnsSensibleExtentsForDoubleFlat() {
        var bounds = require(
            NoteRenderer.getAccidentalBoundsSs(newCrotchetWithAccidental(Accidental.DOUBLE_FLAT)),
            "double-flat bounds");

        assertThat(bounds.topSs()).isNegative();
        assertThat(bounds.botSs()).isPositive();
        assertThat(bounds.widthSs()).isPositive();
        assertThat(bounds.leftSs()).isNegative();
    }

    @Test
    void testGetAccidentalBoundsReturnsSensibleExtentsForFlat() {
        var bounds = require(
            NoteRenderer.getAccidentalBoundsSs(newCrotchetWithAccidental(Accidental.FLAT)),
            "flat bounds");

        assertThat(bounds.topSs()).isNegative();
        assertThat(bounds.botSs()).isPositive();
        assertThat(bounds.widthSs()).isPositive();
        assertThat(bounds.leftSs()).isNegative();
    }

    @Test
    void testGetAccidentalBoundsReturnsSensibleExtentsForNatural() {
        var bounds = require(
            NoteRenderer.getAccidentalBoundsSs(newCrotchetWithAccidental(Accidental.NATURAL)),
            "natural bounds");

        assertThat(bounds.topSs()).isNegative();
        assertThat(bounds.botSs()).isPositive();
        assertThat(bounds.widthSs()).isPositive();
        assertThat(bounds.leftSs()).isNegative();
    }

    @Test
    void testGetAccidentalBoundsReturnsSensibleExtentsForSharp() {
        var bounds = require(
            NoteRenderer.getAccidentalBoundsSs(newCrotchetWithAccidental(Accidental.SHARP)),
            "sharp bounds");

        assertThat(bounds.topSs()).isNegative();
        assertThat(bounds.botSs()).isPositive();
        assertThat(bounds.widthSs()).isPositive();
        assertThat(bounds.leftSs()).isNegative();
    }

    @Test
    void testGetAccidentalBoundsWidensWhenParenthesized() {
        var bareSharp = require(
            NoteRenderer.getAccidentalBoundsSs(newCrotchetWithAccidental(Accidental.SHARP)),
            "bare-sharp bounds");

        var parenthesizedNote = newCrotchetWithAccidental(Accidental.SHARP);
        parenthesizedNote.setAccidentalInParentheses(true);
        var parenSharp = require(
            NoteRenderer.getAccidentalBoundsSs(parenthesizedNote),
            "parenthesized-sharp bounds");

        // Parentheses extend the drawing on both sides, so the union widens
        // and the left edge moves further from the notehead origin.
        assertThat(parenSharp.widthSs()).isGreaterThan(bareSharp.widthSs());
        assertThat(parenSharp.leftSs()).isLessThan(bareSharp.leftSs());
        assertThat(parenSharp.topSs()).isLessThanOrEqualTo(bareSharp.topSs());
        assertThat(parenSharp.botSs()).isGreaterThanOrEqualTo(bareSharp.botSs());
    }
}
