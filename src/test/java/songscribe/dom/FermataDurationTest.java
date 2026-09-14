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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StaffElement#playedDuration}'s promise that a fermata holds every note value and dot
 * count for the corpus's tick count. The held count is not the written count scaled by a
 * constant: it truncates, so a value whose augmentation leaves an odd tick count loses the
 * half tick rather than rounding it up.
 */
class FermataDurationTest extends UnitTest {

    private record HeldDurationCase(String description, NoteValue noteValue, Dots dots, int heldTicks) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testAFermataHoldsTheNoteForTheCorpusTickCount(HeldDurationCase testCase) {
        var note = (NoteElement) testCase.noteValue().note().newInstance();
        note.setDots(testCase.dots());

        FermataAttachment.addFermata(note);

        assertThat(note.playedDuration()).isEqualTo(testCase.heldTicks());
    }

    @Test
    void testCorpusCoversEveryNoteValueAndDotsCombination() {
        assertThat(cases()).hasSize(NoteValue.values().length * Dots.values().length);
    }

    static Stream<HeldDurationCase> cases() {
        return Stream.of(
            new HeldDurationCase("whole, no dots", NoteValue.WHOLE, Dots.NONE, 576),
            new HeldDurationCase("whole, one dot", NoteValue.WHOLE, Dots.ONE, 864),
            new HeldDurationCase("whole, two dots", NoteValue.WHOLE, Dots.TWO, 1008),
            new HeldDurationCase("half, no dots", NoteValue.HALF, Dots.NONE, 288),
            new HeldDurationCase("half, one dot", NoteValue.HALF, Dots.ONE, 432),
            new HeldDurationCase("half, two dots", NoteValue.HALF, Dots.TWO, 504),
            new HeldDurationCase("quarter, no dots", NoteValue.QUARTER, Dots.NONE, 144),
            new HeldDurationCase("quarter, one dot", NoteValue.QUARTER, Dots.ONE, 216),
            new HeldDurationCase("quarter, two dots", NoteValue.QUARTER, Dots.TWO, 252),
            new HeldDurationCase("eighth, no dots", NoteValue.EIGHTH, Dots.NONE, 72),
            new HeldDurationCase("eighth, one dot", NoteValue.EIGHTH, Dots.ONE, 108),
            new HeldDurationCase("eighth, two dots", NoteValue.EIGHTH, Dots.TWO, 126),
            new HeldDurationCase("sixteenth, no dots", NoteValue.SIXTEENTH, Dots.NONE, 36),
            new HeldDurationCase("sixteenth, one dot", NoteValue.SIXTEENTH, Dots.ONE, 54),
            new HeldDurationCase("sixteenth, two dots", NoteValue.SIXTEENTH, Dots.TWO, 63),
            new HeldDurationCase("thirty-second, no dots", NoteValue.THIRTY_SECOND, Dots.NONE, 18),
            new HeldDurationCase("thirty-second, one dot", NoteValue.THIRTY_SECOND, Dots.ONE, 27),
            new HeldDurationCase("thirty-second, two dots", NoteValue.THIRTY_SECOND, Dots.TWO, 31)
        );
    }
}
