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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link NoteElement#isTiedToNext()}'s invariant across a conversion: a tied note converted by
 * {@link StaffElement#convertedFrom} to any note type stays tied exactly when the converted note
 * can carry a tie.
 */
class NoteElementTest extends UnitTest {

    private static final int TIED = 3;

    static Stream<ElementType> noteTypes() {
        return Stream.of(ElementType.values()).filter(type -> type.newInstance() instanceof NoteElement);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noteTypes")
    void testATiedNoteConvertedToANoteTypeStaysTiedOnlyWhenItCanCarryATie(ElementType targetType) {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED + " ~ " + TIED);
        var tied = song.getLine(0).noteAt(0);

        assertThat(tied.isTiedToNext()).as("the fixture reaches the case only if the source is tied").isTrue();

        var converted = (NoteElement) StaffElement.convertedFrom(targetType, tied);

        assertThat(converted.isTiedToNext()).isEqualTo(converted.canCarryTie());
    }
}
